"""文风提炼：模型输出结构化风格特征，服务端负责 JSON 校验与键值裁剪。"""
import asyncio
import json
import logging
import re

from app.core.config import get_settings
from app.core.errors import AppError
from app.llm.base import LLMClient, LLMError, LLMTimeoutError, Usage
from app.prompts.registry import get_spec
from app.prompts.renderer import build_messages
from app.schemas.style_profile import (
    FEATURE_KEYS,
    MAX_SUMMARY,
    StyleProfileRequest,
    StyleProfileResponse,
)

log = logging.getLogger("ai-service.styleprofile")

_SAMPLE_SEPARATOR = "\n\n---\n\n"


def _build_messages(req: StyleProfileRequest, spec) -> list[dict]:
    parts = []
    for i, sample in enumerate(req.samples, start=1):
        title = (sample.title or "").strip()
        head = f"【样本 {i}】{title}\n\n" if title else f"【样本 {i}】\n\n"
        parts.append(head + sample.text.strip())
    variables = {
        "sample_count": len(req.samples),
        "samples_text": _SAMPLE_SEPARATOR.join(parts),
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
        raise AppError("LLM_TIMEOUT", "文风提炼超时，请重试", 504) from exc
    except LLMError as exc:
        log.warning("文风提炼模型调用失败: %s", exc)
        raise AppError("LLM_ERROR", "AI 服务异常，请稍后重试", 502) from exc

    text = "".join(parts).strip()
    if not text:
        raise AppError("EMPTY_OUTPUT", "文风提炼结果为空，请重试", 422)
    return text, usage


def _load_json(text: str) -> dict:
    cleaned = text.strip()
    cleaned = re.sub(r"^```(?:json)?\s*", "", cleaned, flags=re.IGNORECASE)
    cleaned = re.sub(r"\s*```$", "", cleaned)
    start, end = cleaned.find("{"), cleaned.rfind("}")
    if start < 0 or end <= start:
        raise AppError("EMPTY_OUTPUT", "文风提炼结果为空，请重试", 422)
    try:
        data = json.loads(cleaned[start:end + 1])
    except json.JSONDecodeError as exc:
        log.warning("文风提炼 JSON 解析失败: %s", exc)
        raise AppError("EMPTY_OUTPUT", "文风提炼结果无法解析，请重试", 422) from exc
    if not isinstance(data, dict):
        raise AppError("EMPTY_OUTPUT", "文风提炼结果格式不正确，请重试", 422)
    return data


def _parse(text: str) -> tuple[str, dict]:
    data = _load_json(text)
    summary = data.get("summary")
    features = data.get("features")
    if not isinstance(summary, str) or not summary.strip():
        raise AppError("EMPTY_OUTPUT", "文风提炼缺少摘要，请重试", 422)
    if not isinstance(features, dict):
        raise AppError("EMPTY_OUTPUT", "文风提炼缺少特征，请重试", 422)

    cleaned_features: dict[str, str] = {}
    for key in FEATURE_KEYS:
        value = features.get(key)
        cleaned_features[key] = value.strip()[:200] if isinstance(value, str) else ""
    return summary.strip()[:MAX_SUMMARY], cleaned_features


async def profile_content(req: StyleProfileRequest, llm: LLMClient) -> StyleProfileResponse:
    spec = get_spec("style_profile")
    messages = _build_messages(req, spec)
    text, usage = await _collect(llm, messages, spec)
    summary, features = _parse(text)
    return StyleProfileResponse(
        summary=summary,
        features=features,
        usage=usage.as_dict() if (usage.prompt_tokens or usage.completion_tokens) else None,
    )


def mock_profile(req: StyleProfileRequest) -> StyleProfileResponse:
    """显式 mock 模式：不调用模型，返回固定风格特征。"""
    return StyleProfileResponse(
        summary="mock 模式：平实克制，以细节与物象承载情绪的书面语散文。",
        features={key: f"mock 模式的{key}描述" for key in FEATURE_KEYS},
    )
