"""可选模型复核：只输出 JSON 等级，超时/异常一律放弃，不影响规则结果。"""
import asyncio
import contextlib
import json
import logging
import re

from app.llm.base import LLMClient
from app.prompts.renderer import PromptError, render

log = logging.getLogger("ai-service.care.llm")

REVIEW_TIMEOUT = 3.0
_JSON_OBJECT = re.compile(r"\{[^{}]*\}")
_LEVELS = {"none", "low", "mid", "high"}


async def review_level(text: str, llm: LLMClient) -> str | None:
    if not llm.is_configured():
        return None
    try:
        system = render("care.system.j2", {})
        user = render("care.user.j2", {"text": text})
    except PromptError as exc:
        log.warning("关怀提示词渲染失败: %s", exc)
        return None

    parts: list[str] = []
    stream = llm.stream_chat(
        [{"role": "system", "content": system},
         {"role": "user", "content": user}],
        temperature=0.0,
        max_tokens=32,
    )
    try:
        async with asyncio.timeout(REVIEW_TIMEOUT):
            async for event in stream:
                if event.kind == "content":
                    parts.append(event.text)
                elif event.kind == "done":
                    break
    except TimeoutError:
        log.info("关怀模型复核超时，回退规则结果")
        return None
    except Exception as exc:
        log.warning("关怀模型复核失败，回退规则结果: %s", type(exc).__name__)
        return None
    finally:
        # 及时释放上游连接，避免超时后连接悬挂
        aclose = getattr(stream, "aclose", None)
        if aclose is not None:
            with contextlib.suppress(Exception):
                await aclose()

    return parse_level("".join(parts))


def parse_level(raw: str) -> str | None:
    match = _JSON_OBJECT.search(raw or "")
    if not match:
        return None
    try:
        data = json.loads(match.group(0))
    except json.JSONDecodeError:
        return None
    level = data.get("level")
    return level if level in _LEVELS else None
