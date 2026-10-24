"""测试与显式 mock 模式使用的假模型；生产链路不会自动启用。"""
import asyncio
from collections.abc import AsyncIterator, Sequence

from app.llm.base import LLMError, StreamEvent, Usage


MOCK_TEXTS: dict[str, str] = {
    "outline": (
        "# 示例主题\n\n"
        "## 一、背景\n- 为什么写这个主题\n- 当前处境\n\n"
        "## 二、核心概念\n- 关键术语\n- 工作原理\n\n"
        "## 三、实践示例\n- 第一步\n- 第二步\n\n"
        "## 四、常见问题\n- 问题一\n- 问题二\n\n"
        "## 五、总结\n- 结论\n- 下一步\n"
    ),
    "draft": "# 示例主题\n\n## 一、背景\n\n这是 mock 模式生成的正文段落。\n",
    "continue": "这是 mock 模式生成的续写片段。",
    "expand": "# 原文标题\n\n这是 mock 模式扩写后的完整正文。",
    "condense": "# 原文标题\n\n这是 mock 模式精简后的完整正文。",
    "rewrite": "# 原文标题\n\n这是 mock 模式改写后的完整正文。",
    "polish": "# 原文标题\n\n这是 mock 模式润色后的完整正文。",
    "prose": "# 示例标题\n\n这是 mock 模式生成的散文正文。",
    "suggest": (
        "- 可以用一个具体动作替代抽象描述，画面会更清楚。\n"
        "- 这一句可以再补一处声音或光线，让场景更可感。\n"
    ),
}


class FakeLLM:
    """按脚本依次返回文本，用于合同测试与本地演示。

    scripts 中每个元素对应一次模型调用；用尽后重复最后一个。
    fail_next 指定前 N 次调用直接抛错，用于验证降级与重试。
    """

    def __init__(
        self,
        scripts: Sequence[str] | None = None,
        *,
        chunk_size: int = 12,
        fail_next: int = 0,
        error: Exception | None = None,
        usage: Usage | None = None,
        finish_reason: str = "stop",
    ) -> None:
        self._scripts = list(scripts) if scripts else ["# 标题\n\n正文。"]
        self._chunk_size = chunk_size
        self._fail_next = fail_next
        self._error = error or LLMError("fake 模型错误")
        self._usage = usage or Usage(prompt_tokens=10, completion_tokens=20)
        self._finish_reason = finish_reason
        self.calls: list[list[dict]] = []

    def is_configured(self) -> bool:
        return True

    async def stream_chat(
        self,
        messages: Sequence[dict],
        *,
        temperature: float,
        max_tokens: int,
    ) -> AsyncIterator[StreamEvent]:
        self.calls.append(list(messages))
        if self._fail_next > 0:
            self._fail_next -= 1
            raise self._error

        index = min(len(self.calls) - 1, len(self._scripts) - 1)
        text = self._scripts[index]
        for i in range(0, len(text), self._chunk_size):
            await asyncio.sleep(0)
            yield StreamEvent("content", text=text[i:i + self._chunk_size])
        yield StreamEvent("usage", usage=self._usage)
        yield StreamEvent("done", finish_reason=self._finish_reason)


def mock_llm(task: str) -> FakeLLM:
    text = MOCK_TEXTS.get(task, "# 标题\n\nmock 正文。")
    if task == "draft":
        # 分节模式下会按节调用，重复即可
        return FakeLLM([text], chunk_size=16)
    return FakeLLM([text], chunk_size=16)
