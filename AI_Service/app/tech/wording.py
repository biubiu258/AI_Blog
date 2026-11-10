"""技术措辞建议：调用 tech_wording 技能，服务端强制校验 JSON 与证据归属。"""
from __future__ import annotations

import asyncio
import json
import logging
import re

from app.core.config import get_settings
from app.core.errors import AppError
from app.llm.base import LLMClient, LLMError, LLMTimeoutError, Usage
from app.prompts.registry import get_spec
from app.prompts.renderer import build_messages
from app.schemas.tech import (
    TechEvidence,
    TechWordingIssue,
    TechWordingRequest,
    TechWordingResponse,
)

log = logging.getLogger("ai-service.tech.wording")

MAX_ISSUES = 8
MAX_REPLACEMENT = 200
ISSUE_TYPES = {"terminology", "fact", "collocation", "clarity"}
SEVERITIES = {"low", "mid", "high"}


def _format_evidence(evidence: list[TechEvidence]) -> str:
    if not evidence:
        return "（本次检索没有可用证据：禁止输出 fact 类型）"
    lines: list[str] = []
    for item in evidence:
        meta = [
            f"evidence_chunk_id={item.chunk_id}",
            f"标题={item.title or '未命名语料'}",
            f"出处={item.source_name or '未提供'}",
            f"版本={item.version or '未提供'}",
            f"章节={item.section_path or '未提供'}",
            f"页码={item.page_no if item.page_no else '未提供'}",
        ]
        lines.append(" | ".join(meta))
        lines.append(f"片段：{item.snippet}")
        lines.append("")
    return "\n".join(lines).strip()


def _build_messages(req: TechWordingRequest, spec) -> list[dict]:
    variables = {
        "text": req.text,
        "section_path": (req.section_path or "").strip(),
        "evidence_text": _format_evidence(req.evidence),
    }
    return build_messages(spec, variables)


async def _collect(llm: LLMClient, messages: list[dict], spec) -> tuple[str, Usage]:
    settings = get_settings()
    parts: list[str] = []
    usage = Usage()
    try:
        async with asyncio.timeout(settings.llm_total_timeout):
            async for event in llm.stream_chat(
                messages, temperature=spec.temperature, max_tokens=spec.max_tokens
            ):
                if event.kind == "content" and event.text:
                    parts.append(event.text)
                elif event.kind == "usage" and event.usage:
                    usage.add(event.usage)
    except (TimeoutError, LLMTimeoutError) as exc:
        raise AppError("LLM_TIMEOUT", "措辞检查超时，请重试", 504) from exc
    except LLMError as exc:
        log.warning("措辞检查模型调用失败: %s", exc)
        raise AppError("LLM_ERROR", "AI 服务异常，请稍后重试", 502) from exc

    text = "".join(parts).strip()
    if not text:
        raise AppError("EMPTY_OUTPUT", "措辞检查结果为空，请重试", 422)
    return text, usage


def _load_json(text: str) -> dict:
    cleaned = text.strip()
    cleaned = re.sub(r"^```(?:json)?\s*", "", cleaned, flags=re.IGNORECASE)
    cleaned = re.sub(r"\s*```$", "", cleaned)
    start, end = cleaned.find("{"), cleaned.rfind("}")
    if start < 0 or end <= start:
        raise AppError("EMPTY_OUTPUT", "措辞检查结果为空，请重试", 422)
    try:
        data = json.loads(cleaned[start:end + 1])
    except json.JSONDecodeError as exc:
        log.warning("措辞检查 JSON 解析失败: %s", exc)
        raise AppError("EMPTY_OUTPUT", "措辞检查结果无法解析，请重试", 422) from exc
    if not isinstance(data, dict):
        raise AppError("EMPTY_OUTPUT", "措辞检查结果格式不正确，请重试", 422)
    return data


def _as_text(value: object) -> str:
    return value.strip() if isinstance(value, str) else ""


def _parse_issues(text: str, data: dict, req: TechWordingRequest) -> list[TechWordingIssue]:
    raw_items = data.get("issues", [])
    if not isinstance(raw_items, list):
        raise AppError("EMPTY_OUTPUT", "措辞检查结果格式不正确，请重试", 422)

    evidence_by_chunk = {item.chunk_id: item for item in req.evidence if item.chunk_id}
    issues: list[TechWordingIssue] = []
    seen: set[str] = set()
    for item in raw_items:
        if len(issues) >= MAX_ISSUES:
            break
        if not isinstance(item, dict):
            continue

        issue_type = _as_text(item.get("type"))
        if issue_type not in ISSUE_TYPES:
            continue

        quote = _as_text(item.get("quote"))
        if not quote or "\n" in quote or text.count(quote) != 1 or quote in seen:
            continue

        replacement = _as_text(item.get("replacement")) or None
        if replacement is not None and len(replacement) > MAX_REPLACEMENT:
            continue

        chunk_id = _as_text(item.get("evidence_chunk_id")) or None
        evidence = evidence_by_chunk.get(chunk_id) if chunk_id else None
        if issue_type == "fact" and evidence is None:
            continue

        severity = _as_text(item.get("severity"))
        if severity not in SEVERITIES:
            severity = "mid"

        issues.append(TechWordingIssue(
            id=_as_text(item.get("id")) or f"ai-{len(issues)}",
            type=issue_type,
            quote=quote,
            replacement=replacement,
            reason=_as_text(item.get("reason"))[:500],
            severity=severity,
            evidence_chunk_id=evidence.chunk_id if evidence else None,
            evidence=evidence,
        ))
        seen.add(quote)
    return issues


async def wording_assist(req: TechWordingRequest, llm: LLMClient) -> TechWordingResponse:
    spec = get_spec("tech_wording")
    messages = _build_messages(req, spec)
    text, usage = await _collect(llm, messages, spec)
    issues = _parse_issues(req.text, _load_json(text), req)
    return TechWordingResponse(
        issues=issues,
        usage=usage.as_dict() if (usage.prompt_tokens or usage.completion_tokens) else None,
    )


def mock_wording() -> TechWordingResponse:
    """mock 模式不制造无依据结论。"""
    return TechWordingResponse(issues=[])
