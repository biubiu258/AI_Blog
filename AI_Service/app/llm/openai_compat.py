"""OpenAI 兼容接口的流式客户端：httpx 直连，不引入额外 SDK。"""
import asyncio
import json
import logging
from collections.abc import AsyncIterator, Sequence

import httpx

from app.core.config import Settings
from app.llm.base import LLMError, LLMTimeoutError, StreamEvent, Usage

log = logging.getLogger("ai-service.llm")


class OpenAICompatClient:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings

    def is_configured(self) -> bool:
        return self._settings.llm_configured

    async def stream_chat(
        self,
        messages: Sequence[dict],
        *,
        temperature: float,
        max_tokens: int,
    ) -> AsyncIterator[StreamEvent]:
        s = self._settings
        if not s.llm_configured:
            raise LLMError("LLM 未配置：需要 LLM_BASE_URL / LLM_API_KEY / LLM_MODEL")

        payload = {
            "model": s.llm_model,
            "messages": list(messages),
            "stream": True,
            "temperature": temperature,
            "max_tokens": max_tokens,
            "stream_options": {"include_usage": True},
        }
        headers = {
            "Authorization": f"Bearer {s.llm_api_key}",
            "Content-Type": "application/json",
        }
        timeout = httpx.Timeout(s.llm_total_timeout, connect=10.0, write=30.0, pool=10.0)

        try:
            async with httpx.AsyncClient(timeout=timeout) as client:
                async with client.stream(
                    "POST",
                    f"{s.llm_base_url.rstrip('/')}/chat/completions",
                    headers=headers,
                    json=payload,
                ) as resp:
                    if resp.status_code >= 400:
                        raw = (await resp.aread()).decode("utf-8", "ignore")
                        # 原始响应只进本地日志，不向调用方泄露
                        log.error("模型返回 HTTP %s: %s", resp.status_code, raw[:300])
                        raise LLMError(f"模型服务返回 HTTP {resp.status_code}")

                    lines = resp.aiter_lines()
                    finish_reason: str | None = None
                    first = True
                    while True:
                        try:
                            line = await asyncio.wait_for(
                                lines.__anext__(),
                                timeout=s.llm_first_token_timeout if first else None,
                            )
                        except StopAsyncIteration:
                            break
                        except TimeoutError as exc:
                            raise LLMTimeoutError("等待模型首令牌超时") from exc

                        if not line.startswith("data:"):
                            continue
                        data = line[5:].strip()
                        if data == "[DONE]":
                            break
                        try:
                            chunk = json.loads(data)
                        except json.JSONDecodeError:
                            continue

                        usage = chunk.get("usage")
                        if isinstance(usage, dict):
                            yield StreamEvent("usage", usage=Usage(
                                prompt_tokens=int(usage.get("prompt_tokens") or 0),
                                completion_tokens=int(usage.get("completion_tokens") or 0),
                            ))
                        choices = chunk.get("choices") or []
                        if not choices:
                            continue
                        choice = choices[0]
                        if choice.get("finish_reason"):
                            finish_reason = choice["finish_reason"]
                        delta = choice.get("delta") or {}
                        if delta.get("content"):
                            first = False
                            yield StreamEvent("content", text=delta["content"])
                        elif delta.get("reasoning_content"):
                            # 思考内容不透传，只作为首令牌到达信号
                            first = False
                            yield StreamEvent("reasoning")
                    yield StreamEvent("done", finish_reason=finish_reason)
        except (httpx.HTTPError, asyncio.TimeoutError) as exc:
            log.warning("模型调用异常: %s", exc)
            raise LLMError("模型调用失败") from exc
