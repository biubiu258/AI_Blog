"""AI 洞察式回顾：只回顾主题脉络、反复意象与写作习惯，不建议、不替写。"""
import asyncio
import json
import logging
import re

from app.core.config import get_settings
from app.core.errors import AppError
from app.llm.base import LLMClient, LLMError, LLMTimeoutError, Usage
from app.prompts.registry import get_spec
from app.prompts.renderer import build_messages
from app.schemas.insight import (
    DISCLAIMER,
    MAX_ITEM,
    MAX_ITEMS,
    MAX_SUMMARY,
    SelfInsightRequest,
    SelfInsightResponse,
)

log = logging.getLogger("ai-service.insight")

_ARTICLE_SEPARATOR = "\n\n---\n\n"


def _build_messages(req: SelfInsightRequest, spec) -> list[dict]:
    parts = []
    for i, article in enumerate(req.articles, start=1):
        title = (article.title or "").strip()
        head = f"【文章 {i}】{title}\n\n" if title else f"【文章 {i}】\n\n"
        parts.append(head + article.content.strip())
    variables = {
        "article_count": len(req.articles),
        "articles_text": _ARTICLE_SEPARATOR.join(parts),
        "locale": (req.locale or "zh-CN").strip(),
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
        raise AppError("LLM_TIMEOUT", "洞察式回顾超时，请重试", 504) from exc
    except LLMError as exc:
        log.warning("洞察式回顾模型调用失败: %s", exc)
        raise AppError("LLM_ERROR", "AI 服务异常，请稍后重试", 502) from exc

    text = "".join(parts).strip()
    if not text:
        raise AppError("EMPTY_OUTPUT", "洞察式回顾结果为空，请重试", 422)
    return text, usage


def _load_json(text: str) -> dict:
    cleaned = text.strip()
    cleaned = re.sub(r"^```(?:json)?\s*", "", cleaned, flags=re.IGNORECASE)
    cleaned = re.sub(r"\s*```$", "", cleaned)
    start, end = cleaned.find("{"), cleaned.rfind("}")
    if start < 0 or end <= start:
        raise AppError("EMPTY_OUTPUT", "洞察式回顾结果为空，请重试", 422)
    try:
        data = json.loads(cleaned[start:end + 1])
    except json.JSONDecodeError as exc:
        log.warning("洞察式回顾 JSON 解析失败: %s", exc)
        raise AppError("EMPTY_OUTPUT", "洞察式回顾结果无法解析，请重试", 422) from exc
    if not isinstance(data, dict):
        raise AppError("EMPTY_OUTPUT", "洞察式回顾结果格式不正确，请重试", 422)
    return data


def _string_list(value) -> list[str]:
    if not isinstance(value, list):
        return []
    items: list[str] = []
    for item in value:
        if isinstance(item, str) and item.strip():
            items.append(item.strip()[:MAX_ITEM])
    return items[:MAX_ITEMS]


def _parse(text: str) -> tuple[str, list[str], list[str], list[str]]:
    data = _load_json(text)
    summary = data.get("summary")
    if not isinstance(summary, str) or not summary.strip():
        raise AppError("EMPTY_OUTPUT", "洞察式回顾缺少总体回顾，请重试", 422)
    return (
        summary.strip()[:MAX_SUMMARY],
        _string_list(data.get("themes")),
        _string_list(data.get("motifs")),
        _string_list(data.get("habits")),
    )


async def review_content(
    req: SelfInsightRequest, llm: LLMClient
) -> SelfInsightResponse:
    spec = get_spec("self_insight")
    messages = _build_messages(req, spec)
    text, usage = await _collect(llm, messages, spec)
    summary, themes, motifs, habits = _parse(text)
    return SelfInsightResponse(
        summary=summary,
        themes=themes,
        motifs=motifs,
        habits=habits,
        disclaimer=DISCLAIMER,
        usage=usage.as_dict() if (usage.prompt_tokens or usage.completion_tokens) else None,
    )


def mock_insight(req: SelfInsightRequest) -> SelfInsightResponse:
    """显式 mock 模式：不调用模型，返回确定性的回顾示例。"""
    return SelfInsightResponse(
        summary=(
            f"mock 模式：回顾了近 {len(req.articles)} 篇公开文章，"
            "书写重心集中在日常经验、时间流逝与自我观察之间。"
        ),
        themes=["日常经验与时间感", "自我观察与回忆"],
        motifs=["夜晚与灯光", "旧物与季节"],
        habits=["多用第一人称叙述", "常以场景细节开篇"],
        disclaimer=DISCLAIMER,
    )
