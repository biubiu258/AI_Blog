"""多步顺序执行：固定顺序、固定重试边界，不允许模型改变流程。"""
import logging
from collections.abc import AsyncIterator
from dataclasses import dataclass, field

from app.generation.guard import TextGuard
from app.generation.handlers import Plan, Step
from app.llm.base import LLMClient, Usage

log = logging.getLogger("ai-service.pipeline")


@dataclass
class RunStats:
    usage: Usage = field(default_factory=Usage)
    warnings: list[str] = field(default_factory=list)
    finish_reason: str | None = None


async def stream_plan(
    plan: Plan, llm: LLMClient, stats: RunStats
) -> AsyncIterator[tuple[str, str]]:
    """按计划顺序产出 ("delta", 文本)；异常向上抛给编排层统一映射。"""
    for index, step in enumerate(plan.steps):
        if index > 0 and plan.joiner:
            yield "delta", plan.joiner
        if step.prefix_text:
            yield "delta", step.prefix_text

        async for text in _stream_step(step, plan, llm, stats):
            yield "delta", text

    if stats.finish_reason == "length":
        stats.warnings.append("上游因长度限制截断")
        log.warning("finish_reason=length，已按现有内容收尾")


async def _stream_step(
    step: Step, plan: Plan, llm: LLMClient, stats: RunStats
) -> AsyncIterator[str]:
    attempt = 0
    while True:
        guard = TextGuard(
            requires_h1=plan.requires_h1,
            expected_title=plan.expected_title,
        )
        stream = llm.stream_chat(
            step.messages,
            temperature=step.temperature,
            max_tokens=step.max_tokens,
        )
        try:
            async for event in stream:
                if event.kind == "usage" and event.usage:
                    stats.usage.add(event.usage)
                elif event.kind == "done":
                    stats.finish_reason = event.finish_reason
                elif event.kind == "content":
                    released = guard.feed(event.text)
                    if released:
                        yield released
                if guard.blocked and attempt == 0:
                    # 前缀不合规且尚未输出：立即停止本轮，准备重试
                    break
        finally:
            aclose = getattr(stream, "aclose", None)
            if aclose is not None:
                await aclose()

        if guard.blocked and attempt == 0:
            attempt += 1
            log.warning("输出标题/格式不合规，重试一次")
            continue

        tail = guard.finish(force=guard.blocked)
        if tail:
            yield tail

        text = guard.emitted_text
        if not text.strip() and attempt == 0:
            attempt += 1
            log.warning("模型输出为空，重试一次")
            continue

        stats.warnings.extend(guard.warnings)
        break
