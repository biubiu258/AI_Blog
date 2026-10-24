"""全文审阅：模型只给结构化建议，服务端负责逐条校验与引文锚定。"""
import asyncio
import json
import logging
import re

from app.core.config import get_settings
from app.core.errors import AppError
from app.llm.base import LLMClient, LLMError, LLMTimeoutError, Usage
from app.prompts.registry import get_spec
from app.prompts.renderer import build_messages
from app.schemas.review import (
    MAX_QUOTE,
    MAX_REPLACEMENT,
    MAX_REVIEW_ITEMS,
    ReviewRequest,
    ReviewResponse,
    ReviewSuggestion,
)

log = logging.getLogger("ai-service.review")

MIN_QUOTE_LEN = 6
_QUOTE_STRIP_CHARS = " \t\r\n\"'“”‘’「」『』"


def _build_messages(req: ReviewRequest, spec) -> list[dict]:
    variables = {
        "title": (req.title or "").strip(),
        "article_type": (req.article_type or "").strip(),
        "content": req.content,
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
        raise AppError("LLM_TIMEOUT", "审阅超时，请重试", 504) from exc
    except LLMError as exc:
        log.warning("审阅模型调用失败: %s", exc)
        raise AppError("LLM_ERROR", "AI 服务异常，请稍后重试", 502) from exc

    text = "".join(parts).strip()
    if not text:
        raise AppError("EMPTY_OUTPUT", "审阅结果为空，请重试", 422)
    return text, usage


def _load_json(text: str) -> dict:
    cleaned = text.strip()
    cleaned = re.sub(r"^```(?:json)?\s*", "", cleaned, flags=re.IGNORECASE)
    cleaned = re.sub(r"\s*```$", "", cleaned)
    start, end = cleaned.find("{"), cleaned.rfind("}")
    if start < 0 or end <= start:
        raise AppError("EMPTY_OUTPUT", "AI 审阅结果为空，请重试", 422)
    try:
        data = json.loads(cleaned[start:end + 1])
    except json.JSONDecodeError as exc:
        log.warning("审阅 JSON 解析失败: %s", exc)
        raise AppError("EMPTY_OUTPUT", "AI 审阅结果无法解析，请重试", 422) from exc
    if not isinstance(data, dict):
        raise AppError("EMPTY_OUTPUT", "AI 审阅结果格式不正确，请重试", 422)
    return data


def _as_text(value: object) -> str:
    return value.strip() if isinstance(value, str) else ""


def _resolve_quote(content: str, quote: str) -> str | None:
    """把模型给的引文对齐到正文中的唯一原文；对空白差异做一次容错。"""
    q = quote.strip().strip(_QUOTE_STRIP_CHARS)
    if not (MIN_QUOTE_LEN <= len(q) <= MAX_QUOTE):
        return None
    if "\n" in q or "\r" in q:
        return None
    if content.count(q) == 1:
        return q

    # 引文中的空白视为可有可无：按空白切分后允许任意空白间隔，
    # 否则模型多写/少写一个空格都会导致定位失败。
    parts = [part for part in re.split(r"\s+", q) if part]
    if not parts:
        return None
    pattern = re.compile(r"\s*".join(re.escape(part) for part in parts))
    matches = list(pattern.finditer(content))
    if len(matches) != 1:
        return None
    found = content[matches[0].start():matches[0].end()]
    if "\n" in found or "\r" in found:
        return None
    if not (MIN_QUOTE_LEN <= len(found) <= MAX_QUOTE):
        return None
    return found


def _parse_suggestions(text: str, content: str) -> list[ReviewSuggestion]:
    data = _load_json(text)
    raw_items = data.get("suggestions", [])
    if not isinstance(raw_items, list):
        raise AppError("EMPTY_OUTPUT", "AI 审阅结果格式不正确，请重试", 422)

    suggestions: list[ReviewSuggestion] = []
    seen: set[str] = set()
    for item in raw_items:
        if len(suggestions) >= MAX_REVIEW_ITEMS:
            break
        if not isinstance(item, dict):
            continue

        quote = _resolve_quote(content, _as_text(item.get("quote")))
        if quote is None or quote in seen:
            continue

        reason = _as_text(item.get("reason"))[:500]
        if not reason:
            continue

        replacement = _as_text(item.get("replacement"))[:MAX_REPLACEMENT] or None
        if replacement == quote:
            replacement = None

        severity = item.get("severity")
        if severity not in ("low", "mid", "high"):
            severity = "mid"

        suggestion_type = _as_text(item.get("type"))[:24] or "expression"
        suggestions.append(ReviewSuggestion(
            type=suggestion_type,
            severity=severity,
            quote=quote,
            reason=reason,
            replacement=replacement,
        ))
        seen.add(quote)
    return suggestions


async def review_content(req: ReviewRequest, llm: LLMClient) -> ReviewResponse:
    spec = get_spec("review")
    messages = _build_messages(req, spec)
    text, usage = await _collect(llm, messages, spec)
    suggestions = _parse_suggestions(text, req.content)
    return ReviewResponse(
        suggestions=suggestions,
        usage=usage.as_dict() if (usage.prompt_tokens or usage.completion_tokens) else None,
    )


def mock_review(req: ReviewRequest) -> ReviewResponse:
    """显式 mock 模式：不调用模型，返回一条可锚定的示例建议。"""
    quote = ""
    for line in req.content.splitlines():
        candidate = line.strip()
        if len(candidate) >= MIN_QUOTE_LEN:
            quote = candidate[:60]
            break
    if not quote or req.content.count(quote) != 1:
        return ReviewResponse(suggestions=[])
    return ReviewResponse(suggestions=[ReviewSuggestion(
        type="expression",
        severity="low",
        quote=quote,
        reason="mock 模式：这一句可以再具体一些，补充一个细节会更有画面感。",
        replacement=None,
    )])
