"""划词建议：任务必填、提示词渲染与 SSE 输出。"""
import pytest
from pydantic import ValidationError

from app.generation.handlers import build_plan
from app.llm.fake import FakeLLM
from app.schemas.generate import GenerateRequest

from conftest import TOKEN_HEADER


def _request(**kwargs) -> GenerateRequest:
    return GenerateRequest(user_id=1, task="suggest", **kwargs)


def test_suggest_requires_selected_text():
    with pytest.raises(ValidationError):
        _request(params={"suggestion_mode": "advice"}, context={})


def test_suggest_selected_text_limit():
    with pytest.raises(ValidationError):
        _request(context={"selected_text": "字" * 2001})


def test_suggest_rejects_unknown_mode():
    with pytest.raises(ValidationError):
        _request(params={"suggestion_mode": "unknown"}, context={"selected_text": "原文片段"})


def test_suggest_prompt_contains_selection_and_context(settings_env):
    from app.core.config import get_settings

    plan = build_plan(
        _request(
            params={"suggestion_mode": "polish", "topic": "旧书摊"},
            context={"selected_text": "他慢慢走过去。", "text_before": "前文", "text_after": "后文"},
        ),
        get_settings(),
    )
    assert plan.strip_meta is False
    system = plan.steps[0].messages[0]["content"]
    user = plan.steps[0].messages[1]["content"]
    assert "不得" in system and "润色" in system
    assert "他慢慢走过去。" in user
    assert "前文" in user and "后文" in user


def test_suggest_stream_keeps_advice_lines(client, use_llm, sse):
    use_llm(FakeLLM(["- 这句可以更具体\n- 你可以补充一个细节"]))
    resp = client.post("/v1/generate", headers=TOKEN_HEADER, json={
        "user_id": 1,
        "task": "suggest",
        "params": {"suggestion_mode": "advice"},
        "context": {"selected_text": "这里写得很空。"},
    })
    assert resp.status_code == 200
    frames = sse(resp.text)
    done = next(f["data"] for f in frames if f["event"] == "done")
    assert "你可以补充一个细节" in done["content"]
