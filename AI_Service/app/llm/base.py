"""模型层协议：与具体供应商解耦，编排层只依赖这里。"""
from collections.abc import AsyncIterator, Sequence
from dataclasses import dataclass, field
from typing import Literal, Protocol


@dataclass
class Usage:
    prompt_tokens: int = 0
    completion_tokens: int = 0

    def add(self, other: "Usage") -> None:
        self.prompt_tokens += other.prompt_tokens
        self.completion_tokens += other.completion_tokens

    def as_dict(self) -> dict[str, int]:
        return {
            "prompt_tokens": self.prompt_tokens,
            "completion_tokens": self.completion_tokens,
        }


@dataclass
class StreamEvent:
    """上游流式事件。

    kind=content：正文增量；kind=reasoning：思考增量（不透传，仅保活）；
    kind=usage：供应商给出的 token 统计；kind=done：流结束，携带 finish_reason。
    """

    kind: Literal["content", "reasoning", "usage", "done"]
    text: str = ""
    usage: Usage | None = None
    finish_reason: str | None = None


class LLMError(Exception):
    """供应商返回非 2xx、连接失败或响应不可解析。"""


class LLMTimeoutError(Exception):
    """等待首令牌超时。"""


class LLMClient(Protocol):
    def is_configured(self) -> bool:
        ...

    def stream_chat(
        self,
        messages: Sequence[dict],
        *,
        temperature: float,
        max_tokens: int,
    ) -> AsyncIterator[StreamEvent]:
        ...
