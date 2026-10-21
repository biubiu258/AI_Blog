"""OpenAI 兼容接口的流式客户端。

用 httpx 直接解析 SSE，避免引入额外 SDK 依赖；密钥与模型全部走环境变量，
未配置时由调用方决定降级行为（见 server.py）。
"""
import asyncio
import json
import logging
from collections.abc import AsyncGenerator

import httpx

from config import (LLM_API_KEY, LLM_BASE_URL, LLM_FIRST_TOKEN_TIMEOUT, LLM_MAX_TOKENS,
                    LLM_MODEL, LLM_TOTAL_TIMEOUT, LLM_TEMPERATURE)

log = logging.getLogger("ai-service.llm")


class LLMError(Exception):
    """供应商返回非 2xx 或连接失败。"""


class LLMTimeoutError(Exception):
    """首令牌等待超时。"""


def is_configured() -> bool:
    return bool(LLM_BASE_URL and LLM_API_KEY and LLM_MODEL)


async def stream_chat(messages: list[dict]) -> AsyncGenerator[tuple[str, str], None]:
    """逐块产出 (kind, text)：kind 为 "content"（正文）或 "reasoning"（思考过程）。

    推理模型会先输出大量 reasoning 块再输出 content；调用方可据此发心跳保活。
    [DONE] 或流结束即终止。
    """
    if not is_configured():
        raise LLMError("LLM 未配置：需要 LLM_BASE_URL / LLM_API_KEY / LLM_MODEL")

    payload = {
        "model": LLM_MODEL,
        "messages": messages,
        "stream": True,
        "temperature": LLM_TEMPERATURE,
        "max_tokens": LLM_MAX_TOKENS,
    }
    headers = {
        "Authorization": f"Bearer {LLM_API_KEY}",
        "Content-Type": "application/json",
    }
    timeout = httpx.Timeout(LLM_TOTAL_TIMEOUT, connect=10.0)

    try:
        async with httpx.AsyncClient(timeout=timeout) as client:
            async with client.stream("POST", f"{LLM_BASE_URL}/chat/completions",
                                     headers=headers, json=payload) as resp:
                if resp.status_code >= 400:
                    raw = (await resp.aread()).decode("utf-8", "ignore")
                    raise LLMError(f"HTTP {resp.status_code}: {raw[:300]}")

                lines = resp.aiter_lines()
                first = True
                while True:
                    try:
                        line = await asyncio.wait_for(
                            lines.__anext__(),
                            timeout=LLM_FIRST_TOKEN_TIMEOUT if first else None,
                        )
                    except StopAsyncIteration:
                        break
                    except TimeoutError as e:
                        raise LLMTimeoutError("等待模型首令牌超时") from e

                    if not line.startswith("data:"):
                        continue
                    data = line[5:].strip()
                    if data == "[DONE]":
                        break
                    try:
                        chunk = json.loads(data)
                    except json.JSONDecodeError:
                        continue
                    choices = chunk.get("choices") or []
                    if not choices:
                        continue
                    delta = choices[0].get("delta") or {}
                    if delta.get("content"):
                        first = False
                        yield "content", delta["content"]
                    elif delta.get("reasoning_content"):
                        first = False
                        yield "reasoning", delta["reasoning_content"]
    except httpx.HTTPError as e:
        raise LLMError(f"调用模型失败: {e}") from e
