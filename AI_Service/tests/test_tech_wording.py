"""技术措辞建议：JSON 契约、证据归属与防编造校验。"""
import json

from app.llm.base import LLMTimeoutError
from app.llm.fake import FakeLLM

from conftest import TOKEN_HEADER

TEXT = "把数据写入磁盘前，先校验 checksum。如果校验失败就重试一次。"
EVIDENCE = {
    "chunk_id": "1:2",
    "doc_id": "1",
    "title": "存储可靠性手册",
    "source_name": "内部文档",
    "version": "v1.2",
    "section_path": "第三章 > 校验",
    "page_no": 12,
    "snippet": "写入前应计算并校验 checksum，失败时重试一次。",
}


def _post(client, **overrides):
    body = {"text": TEXT, "section_path": "第三章", "evidence": [EVIDENCE]}
    body.update(overrides)
    return client.post("/v1/tech/wording", headers=TOKEN_HEADER, json=body)


def _script(items) -> str:
    return "```json\n" + json.dumps({"issues": items}, ensure_ascii=False) + "\n```"


def test_wording_requires_token(client):
    resp = client.post("/v1/tech/wording", json={"text": TEXT})
    assert resp.status_code == 401


def test_wording_returns_anchorable_issue(client, use_llm):
    use_llm(FakeLLM([_script([{
        "id": "i1",
        "type": "terminology",
        "quote": "先校验 checksum",
        "replacement": "先校验校验和",
        "reason": "术语表规范写法为「校验和」。",
        "severity": "mid",
    }])]))
    resp = _post(client)
    assert resp.status_code == 200
    body = resp.json()
    assert len(body["issues"]) == 1
    issue = body["issues"][0]
    assert issue["quote"] == "先校验 checksum"
    assert issue["type"] == "terminology"
    assert issue["evidence_chunk_id"] is None
    assert body["usage"]["completion_tokens"] > 0
    assert body["model"] == "test-model"
    assert body["prompt_version"]


def test_wording_keeps_fact_with_matching_evidence(client, use_llm):
    use_llm(FakeLLM([_script([{
        "type": "fact",
        "quote": "如果校验失败就重试一次",
        "replacement": "如果校验失败就向调用方报错",
        "reason": "手册要求失败即报错，而不是静默重试。",
        "severity": "high",
        "evidence_chunk_id": "1:2",
    }])]))
    resp = _post(client)
    issue = resp.json()["issues"][0]
    assert issue["type"] == "fact"
    assert issue["evidence_chunk_id"] == "1:2"
    assert issue["evidence"]["title"] == "存储可靠性手册"
    assert issue["evidence"]["page_no"] == 12


def test_wording_drops_fact_without_evidence(client, use_llm):
    use_llm(FakeLLM([_script([
        {
            "type": "fact",
            "quote": "如果校验失败就重试一次",
            "reason": "无证据的事实判断。",
            "severity": "high",
        },
        {
            "type": "fact",
            "quote": "如果校验失败就重试一次",
            "reason": "证据 id 不存在。",
            "severity": "high",
            "evidence_chunk_id": "9:9",
        },
    ])]))
    resp = _post(client)
    assert resp.status_code == 200
    assert resp.json()["issues"] == []


def test_wording_drops_missing_duplicate_or_long_items(client, use_llm):
    use_llm(FakeLLM([_script([
        {"type": "terminology", "quote": "不存在的片段", "reason": "无效"},
        {"type": "terminology", "quote": "先校验 checksum", "reason": "重复"},
        {"type": "terminology", "quote": "先校验 checksum", "reason": "重复"},
        {"type": "terminology", "quote": "如果校验失败就重试一次", "reason": "过长替换",
         "replacement": "字" * 201},
        {"type": "unknown", "quote": "如果校验失败就重试一次", "reason": "类型非法"},
    ])]))
    resp = _post(client)
    assert resp.status_code == 200
    assert [item["quote"] for item in resp.json()["issues"]] == ["先校验 checksum"]


def test_wording_caps_issues_at_eight(client, use_llm):
    # 构造 10 个互不相同且各自唯一命中的 quote
    text = "".join(f"占位{i}。" for i in range(10))
    items = [
        {"type": "clarity", "quote": f"占位{i}。", "reason": f"第{i}条", "severity": "low"}
        for i in range(10)
    ]
    use_llm(FakeLLM([_script(items)]))
    resp = _post(client, text=text)
    assert resp.status_code == 200
    assert len(resp.json()["issues"]) == 8


def test_wording_empty_issues(client, use_llm):
    use_llm(FakeLLM([_script([])]))
    resp = _post(client, evidence=[])
    assert resp.status_code == 200
    assert resp.json()["issues"] == []


def test_wording_broken_json_maps_to_empty_output(client, use_llm):
    use_llm(FakeLLM(["这不是 JSON"]))
    resp = _post(client)
    assert resp.status_code == 422
    assert resp.json()["error"]["code"] == "EMPTY_OUTPUT"


def test_wording_timeout_maps_to_504(client, use_llm):
    use_llm(FakeLLM(["{}"], fail_next=1, error=LLMTimeoutError("超时")))
    resp = _post(client)
    assert resp.status_code == 504
    assert resp.json()["error"]["code"] == "LLM_TIMEOUT"


def test_wording_text_limit(client):
    resp = _post(client, text="字" * 1501)
    assert resp.status_code == 400
