"""全文审阅：结构化 JSON、引文唯一性与错误映射。"""
import json

from app.llm.base import LLMTimeoutError
from app.llm.fake import FakeLLM

from conftest import TOKEN_HEADER

CONTENT = "黄昏时我走到旧书摊前。摊主正在收拾一摞旧杂志，动作很慢。"


def _post(client, **overrides):
    body = {"user_id": 1, "title": "旧书摊", "content": CONTENT, "article_type": "随笔"}
    body.update(overrides)
    return client.post("/v1/review", headers=TOKEN_HEADER, json=body)


def _script(items) -> str:
    return "```json\n" + json.dumps({"suggestions": items}, ensure_ascii=False) + "\n```"


def test_review_requires_token(client):
    resp = client.post("/v1/review", json={"user_id": 1, "content": CONTENT})
    assert resp.status_code == 401


def test_review_returns_anchorable_suggestion(client, use_llm):
    use_llm(FakeLLM([_script([{
        "type": "expression",
        "severity": "mid",
        "quote": "摊主正在收拾一摞旧杂志，动作很慢。",
        "reason": "动作可以更具体。",
        "replacement": "摊主慢慢收拢一摞卷边的旧杂志。",
    }])]))
    resp = _post(client)
    assert resp.status_code == 200
    body = resp.json()
    assert len(body["suggestions"]) == 1
    assert body["suggestions"][0]["quote"] == "摊主正在收拾一摞旧杂志，动作很慢。"
    assert body["usage"]["completion_tokens"] > 0


def test_review_drops_duplicate_or_missing_quote(client, use_llm):
    duplicated = "旧书摊前的灯亮着。旧书摊前的灯亮着。他在收拾杂志。"
    use_llm(FakeLLM([_script([
        {"quote": "旧书摊", "reason": "太短", "replacement": None},
        {"quote": "不存在的片段内容", "reason": "无效", "replacement": None},
        {"quote": "旧书摊前的灯亮着。", "reason": "重复", "replacement": None},
        {"quote": "旧书摊前的灯亮着。", "reason": "重复", "replacement": None},
    ])]))
    resp = _post(client, content=duplicated)
    assert resp.status_code == 200
    assert resp.json()["suggestions"] == []


def test_review_tolerates_whitespace_and_resolves_exact_quote(client, use_llm):
    use_llm(FakeLLM([_script([{
        "quote": "摊主 正在 收拾 一摞旧杂志",
        "reason": "节奏偏慢。",
        "replacement": "摊主麻利地收拢一摞旧杂志。",
    }])]))
    resp = _post(client)
    quote = resp.json()["suggestions"][0]["quote"]
    assert CONTENT.count(quote) == 1
    assert "\n" not in quote


def test_review_empty_suggestions(client, use_llm):
    use_llm(FakeLLM([_script([])]))
    resp = _post(client)
    assert resp.status_code == 200
    assert resp.json()["suggestions"] == []


def test_review_broken_json_maps_to_empty_output(client, use_llm):
    use_llm(FakeLLM(["这不是 JSON"]))
    resp = _post(client)
    assert resp.status_code == 422
    assert resp.json()["error"]["code"] == "EMPTY_OUTPUT"


def test_review_timeout_maps_to_504(client, use_llm):
    use_llm(FakeLLM(["{}"], fail_next=1, error=LLMTimeoutError("超时")))
    resp = _post(client)
    assert resp.status_code == 504
    assert resp.json()["error"]["code"] == "LLM_TIMEOUT"


def test_review_content_limit(client):
    resp = _post(client, content="字" * 20001)
    assert resp.status_code == 400
