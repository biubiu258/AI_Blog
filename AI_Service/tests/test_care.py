"""人文关怀：规则分级、误报防护、模型复核边界与 fail-open。"""
import pytest

from app.care import rules
from app.care.llm_review import parse_level
from app.care.rules import evaluate, merge_with_model, prepare_text
from app.llm.fake import FakeLLM

TOKEN_HEADER = {"X-Internal-Token": "test-token"}

HIGH_TEXTS = [
    "我最近真的不想活了。",
    "我已经写好了遗书，放在抽屉里。",
    "我准备今晚跳楼。",
    "我想自杀。",
    "我真想去死。",
    "我打算明天结束自己的生命。",
]

NONE_TEXTS = [
    "我没有想过自杀，只是最近有点累。",
    "如果我死了，谁会记得我。",
    "我想到死去的亲人，眼泪就掉下来。",
    "外婆离开了这个世界，我很想念她。",
    "他不想活了，我劝了很久。",
    "他留下遗书就走了。",
    "我的外婆离开这个世界已经十年了。",
    "我劝他不要跳楼。",
    "我真想死心塌地爱一个人。",
    "好想死磕到底。",
    "最近有点累。",
    "想死你了，什么时候回来。",
]

MID_TEXTS = [
    "每天撑不下去，感觉没有人需要我。",
    "我活着很累，看不到希望。",
]

LOW_TEXTS = [
    "最近很累，压力很大。",
    "这段时间很疲惫，也感到很孤独。",
]


@pytest.mark.parametrize("text", HIGH_TEXTS)
def test_high_signals(text):
    result = evaluate("", text)
    assert result.level == "high"
    assert result.keywords
    assert result.summary


@pytest.mark.parametrize("text", NONE_TEXTS)
def test_false_positive_guards(text):
    assert evaluate("", text).level == "none"


@pytest.mark.parametrize("text", MID_TEXTS)
def test_mid_signals(text):
    assert evaluate("", text).level == "mid"


@pytest.mark.parametrize("text", LOW_TEXTS)
def test_low_signals(text):
    result = evaluate("", text)
    assert result.level == "low"
    assert len(set(result.keywords)) >= 2


def test_title_counts_as_material():
    assert evaluate("我准备今晚跳楼", "").level == "high"


def test_prepare_text_truncates_head_and_tail():
    content = "开头标记" + "甲" * 5000 + "结尾标记"
    text = prepare_text("", content)
    assert len(text) <= rules.MAX_HEAD + rules.MAX_TAIL + 4
    assert text.lstrip("\n").startswith("开头标记")
    assert text.endswith("结尾标记")


def test_keywords_are_capped():
    text = "撑不下去，活着很累，没有人在乎我，我是个废物，绝望，想消失，抑郁，长期失眠，伤害自己，自杀"
    result = evaluate("", text)
    assert result.level == "high" or result.level == "mid"
    assert len(result.keywords) <= 5


@pytest.mark.parametrize(
    ("rule_level", "model_level", "expected"),
    [
        ("none", "high", "mid"),
        ("low", "high", "mid"),
        ("none", "mid", "mid"),
        ("low", "mid", "mid"),
        ("mid", "low", "mid"),
        ("high", "none", "high"),
        ("none", None, "none"),
        ("none", "unknown", "none"),
    ],
)
def test_merge_with_model_only_escalates_to_mid(rule_level, model_level, expected):
    assert merge_with_model(rule_level, model_level) == expected


@pytest.mark.parametrize(
    ("raw", "expected"),
    [
        ('{"level":"mid"}', "mid"),
        ('```json\n{"level":"high"}\n```', "high"),
        ('{"level":"low","reason":"x"}', "low"),
        ('{"level":"critical"}', None),
        ("无法判断", None),
        ("", None),
    ],
)
def test_parse_level(raw, expected):
    assert parse_level(raw) == expected


def test_api_requires_token(client):
    resp = client.post("/v1/care/check", json={"user_id": 1, "content": "我不想活了"})
    assert resp.status_code == 401
    assert resp.json()["error"]["code"] == "UNAUTHORIZED"


def test_api_rejects_invalid_user_id(client):
    resp = client.post(
        "/v1/care/check", json={"user_id": 0, "content": "x"}, headers=TOKEN_HEADER
    )
    assert resp.status_code == 400
    assert resp.json()["error"]["code"] == "INVALID_PARAMS"


def test_api_high_returns_summary_and_keywords(client):
    payload = {"user_id": 1, "title": "无题", "content": "我已经写好了遗书，放在抽屉里。"}
    resp = client.post("/v1/care/check", json=payload, headers=TOKEN_HEADER)
    assert resp.status_code == 200
    body = resp.json()
    assert body["level"] == "high"
    assert body["summary"]
    assert "遗书" in body["keywords"]
    # 响应只带标签，不回传原文
    assert "放在抽屉里" not in resp.text


def test_api_none_returns_empty_fields(client):
    resp = client.post(
        "/v1/care/check", json={"user_id": 1, "content": "今天天气不错。"},
        headers=TOKEN_HEADER,
    )
    assert resp.status_code == 200
    assert resp.json() == {"level": "none", "summary": None, "keywords": None}


def test_api_does_not_call_llm_when_disabled(client, use_llm):
    llm = use_llm(FakeLLM(['{"level":"mid"}']))
    resp = client.post(
        "/v1/care/check", json={"user_id": 1, "content": "今天天气不错。"},
        headers=TOKEN_HEADER,
    )
    assert resp.json()["level"] == "none"
    assert llm.calls == []


def test_api_llm_can_escalate_to_mid(client, use_llm, monkeypatch):
    monkeypatch.setenv("CARE_LLM_ENABLED", "true")
    from app.core.config import get_settings

    get_settings.cache_clear()
    llm = use_llm(FakeLLM(['{"level":"mid"}']))

    resp = client.post(
        "/v1/care/check", json={"user_id": 1, "content": "今天天气不错。"},
        headers=TOKEN_HEADER,
    )
    assert resp.json()["level"] == "mid"
    assert len(llm.calls) == 1


def test_api_llm_cannot_escalate_to_high(client, use_llm, monkeypatch):
    monkeypatch.setenv("CARE_LLM_ENABLED", "true")
    from app.core.config import get_settings

    get_settings.cache_clear()
    use_llm(FakeLLM(['{"level":"high"}']))

    resp = client.post(
        "/v1/care/check", json={"user_id": 1, "content": "今天天气不错。"},
        headers=TOKEN_HEADER,
    )
    assert resp.json()["level"] == "mid"


def test_api_high_skips_llm_review(client, use_llm, monkeypatch):
    monkeypatch.setenv("CARE_LLM_ENABLED", "true")
    from app.core.config import get_settings

    get_settings.cache_clear()
    llm = use_llm(FakeLLM(['{"level":"none"}']))

    resp = client.post(
        "/v1/care/check", json={"user_id": 1, "content": "我不想活了。"},
        headers=TOKEN_HEADER,
    )
    assert resp.json()["level"] == "high"
    assert llm.calls == []


def test_api_llm_failure_falls_back_to_rules(client, use_llm, monkeypatch):
    monkeypatch.setenv("CARE_LLM_ENABLED", "true")
    from app.core.config import get_settings

    get_settings.cache_clear()
    use_llm(FakeLLM(["x"], fail_next=1))

    resp = client.post(
        "/v1/care/check", json={"user_id": 1, "content": "最近很累，压力很大。"},
        headers=TOKEN_HEADER,
    )
    assert resp.status_code == 200
    assert resp.json()["level"] == "low"


def test_api_internal_error_fails_open(client, monkeypatch):
    async def _boom(*_args, **_kwargs):
        raise RuntimeError("boom")

    monkeypatch.setattr("app.api.care.check_care", _boom)
    resp = client.post(
        "/v1/care/check", json={"user_id": 1, "content": "我不想活了。"},
        headers=TOKEN_HEADER,
    )
    assert resp.status_code == 200
    assert resp.json()["level"] == "none"
