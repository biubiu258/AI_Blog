"""生成编排：清洗、重试、分节、分块、空值与超时。"""
import asyncio

import pytest

from app.core.config import Settings
from app.generation.handlers import build_plan
from app.generation.pipeline import RunStats, stream_plan
from app.llm.base import LLMTimeoutError
from app.llm.fake import FakeLLM
from app.schemas.generate import GenerateRequest

from conftest import STYLE_CONFIG


def _settings() -> Settings:
    return Settings(style_config_path=str(STYLE_CONFIG))


def _req(**kwargs) -> GenerateRequest:
    return GenerateRequest(user_id=1, **kwargs)


async def _run(plan, llm):
    stats = RunStats()
    parts = [text async for _, text in stream_plan(plan, llm, stats)]
    return "".join(parts), stats


def test_preamble_is_stripped_before_delta():
    plan = build_plan(_req(task="outline", params={"topic": "旧书摊"}), _settings())
    llm = FakeLLM(["好的，以下是文章：\n\n# 旧书摊\n\n" + "正文。" * 80])
    text, stats = asyncio.run(_run(plan, llm))
    assert text.startswith("# 旧书摊")
    assert "以下是" not in text
    assert stats.usage.completion_tokens == 20


def test_draft_title_mismatch_retries_once():
    outline = "# 标题A\n\n## 一、背景\n- 要点\n"
    plan = build_plan(
        _req(task="draft", params={}, context={"outline": outline}), _settings()
    )
    llm = FakeLLM([
        "# 标题B\n\n" + "错" * 300,
        "# 标题A\n\n" + "对" * 300,
    ])
    text, stats = asyncio.run(_run(plan, llm))
    assert "# 标题A" in text
    assert "标题B" not in text
    assert len(llm.calls) == 2


def test_draft_long_outline_generates_sections():
    parts = ["# 长文标题", ""]
    for i in range(1, 11):
        parts.extend([f"## {i}、章节{i}", "- " + "要点" * 700, ""])
    outline = "\n".join(parts)
    assert len(outline) > 12000

    plan = build_plan(
        _req(task="draft", params={}, context={"outline": outline}), _settings()
    )
    assert plan.mode == "sections"
    assert len(plan.steps) == 10
    assert plan.steps[0].prefix_text.startswith("# 长文标题")
    assert "## 1、章节1" in plan.steps[0].prefix_text

    llm = FakeLLM(["本节正文。" * 40] * 10)
    text, _ = asyncio.run(_run(plan, llm))
    assert text.startswith("# 长文标题")
    assert text.count("## ") == 10
    assert len(llm.calls) == 10


def test_long_transform_uses_chunks():
    original = "\n\n".join(
        f"## 小节{i}\n" + "内容。" * 300 for i in range(20)
    )
    assert len(original) > 12000
    plan = build_plan(
        _req(task="polish", params={}, context={"original_text": original}), _settings()
    )
    assert plan.mode == "chunks"
    assert 1 < len(plan.steps) <= 10
    assert "前文末尾" not in plan.steps[0].messages[1]["content"]
    assert "前文末尾" in plan.steps[1].messages[1]["content"]

    llm = FakeLLM(["润色后的内容。"] * len(plan.steps))
    text, _ = asyncio.run(_run(plan, llm))
    assert text.count("润色后的内容") >= 2


def test_empty_output_retries_then_stops():
    plan = build_plan(_req(task="outline", params={"topic": "x"}), _settings())
    llm = FakeLLM(["", ""])
    text, _ = asyncio.run(_run(plan, llm))
    assert text == ""
    assert len(llm.calls) == 2


def test_timeout_propagates():
    plan = build_plan(_req(task="outline", params={"topic": "x"}), _settings())
    llm = FakeLLM(["# 标题"], fail_next=1, error=LLMTimeoutError("超时"))
    with pytest.raises(LLMTimeoutError):
        asyncio.run(_run(plan, llm))


def test_finish_reason_length_is_warned():
    plan = build_plan(_req(task="outline", params={"topic": "x"}), _settings())
    llm = FakeLLM(["# 标题\n\n正文。"], finish_reason="length")
    _, stats = asyncio.run(_run(plan, llm))
    assert "上游因长度限制截断" in stats.warnings
