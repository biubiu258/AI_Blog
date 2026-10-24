"""接口契约：鉴权、参数校验、SSE 帧形状、占位接口、健康检查。"""
from conftest import TOKEN_HEADER
from fastapi.testclient import TestClient

from app.llm.base import LLMTimeoutError
from app.llm.fake import FakeLLM

OUTLINE_BODY = {"user_id": 1, "task": "outline", "params": {"topic": "旧书摊"}}


def _post(client, body, token=True):
    headers = TOKEN_HEADER if token else {}
    return client.post("/v1/generate", json=body, headers=headers)


def test_missing_token(client):
    resp = _post(client, OUTLINE_BODY, token=False)
    assert resp.status_code == 401
    assert resp.json()["error"]["code"] == "UNAUTHORIZED"


def test_wrong_token(client):
    resp = client.post("/v1/generate", json=OUTLINE_BODY, headers={"X-Internal-Token": "x"})
    assert resp.status_code == 401


def test_invalid_params_before_stream(client):
    resp = _post(client, {"user_id": 1, "task": "draft", "params": {}})
    assert resp.status_code == 400
    assert resp.json()["error"]["code"] == "INVALID_PARAMS"
    assert resp.headers["content-type"].startswith("application/json")


def test_prose_requires_emotion_or_recent_state(client):
    body = {"user_id": 1, "task": "prose", "params": {}, "context": {"image_urls": []}}
    resp = _post(client, body)
    assert resp.status_code == 400


def test_prose_image_limit(client):
    body = {
        "user_id": 1,
        "task": "prose",
        "params": {"emotion": "想念"},
        "context": {"image_urls": [f"https://example.com/{i}.jpg" for i in range(6)]},
    }
    resp = _post(client, body)
    assert resp.status_code == 400


def test_stream_frames_and_done(client, use_llm, sse):
    llm = use_llm(FakeLLM(["# 旧书摊\n\n黄昏时，我又走到那家旧书摊前。\n"]))
    resp = _post(client, OUTLINE_BODY)
    assert resp.status_code == 200
    assert resp.headers["content-type"].startswith("text/event-stream")

    frames = sse(resp.text)
    events = [f["event"] for f in frames]
    assert events.count("done") == 1
    assert events[-1] == "done"
    assert "error" not in events

    joined = "".join(f["data"]["content"] for f in frames if f["event"] == "delta")
    done = next(f["data"] for f in frames if f["event"] == "done")
    assert done["content"] == joined.strip()
    assert done["usage"]["completion_tokens"] > 0
    assert len(llm.calls) == 1


def test_stream_error_has_no_done(client, use_llm, sse):
    use_llm(FakeLLM(["# 标题"], fail_next=2))
    resp = _post(client, OUTLINE_BODY)
    frames = sse(resp.text)
    events = [f["event"] for f in frames]
    assert "error" in events
    assert "done" not in events
    assert next(f["data"] for f in frames if f["event"] == "error")["code"] == "LLM_ERROR"


def test_stream_timeout_maps_to_llm_timeout(client, use_llm, sse):
    use_llm(FakeLLM(["# 标题"], fail_next=1, error=LLMTimeoutError("首令牌超时")))
    resp = _post(client, OUTLINE_BODY)
    frames = sse(resp.text)
    assert next(f["data"] for f in frames if f["event"] == "error")["code"] == "LLM_TIMEOUT"


def test_empty_output(client, use_llm, sse):
    llm = use_llm(FakeLLM([""]))
    resp = _post(client, OUTLINE_BODY)
    frames = sse(resp.text)
    assert next(f["data"] for f in frames if f["event"] == "error")["code"] == "EMPTY_OUTPUT"
    assert "done" not in [f["event"] for f in frames]
    assert len(llm.calls) == 2  # 空输出允许一次重试


def test_llm_not_configured_returns_502(monkeypatch, settings_env):
    for key in ("LLM_BASE_URL", "LLM_API_KEY", "LLM_MODEL"):
        monkeypatch.delenv(key, raising=False)
    from app.core.config import get_settings
    from app.main import create_app

    get_settings.cache_clear()
    client = TestClient(create_app())
    resp = _post(client, OUTLINE_BODY)
    assert resp.status_code == 502
    assert resp.json()["error"]["code"] == "LLM_ERROR"
    assert resp.headers["content-type"].startswith("application/json")


def test_health_without_token(client):
    resp = client.get("/v1/health")
    assert resp.status_code == 200
    body = resp.json()
    assert body["status"] == "ok"
    assert body["prompt_version"]
    assert "api_key" not in str(body).lower()


def test_placeholder_endpoints(client):
    care = client.post("/v1/care/check", json={"user_id": 1, "content": "最近很累"}, headers=TOKEN_HEADER)
    assert care.status_code == 200 and care.json()["level"] == "none"

    search = client.post("/v1/search", json={"query": "旧书"}, headers=TOKEN_HEADER)
    assert search.status_code == 200 and search.json()["hits"] == []

    retrieve = client.post(
        "/v1/retrieve", json={"query": "旧书", "user_id": 1}, headers=TOKEN_HEADER
    )
    assert retrieve.status_code == 200 and retrieve.json()["hits"] == []

    upsert = client.post(
        "/v1/vectors/upsert",
        json={"article_id": "12", "title": "t", "content": "c"},
        headers=TOKEN_HEADER,
    )
    assert upsert.status_code == 204

    delete = client.delete("/v1/vectors/12", headers=TOKEN_HEADER)
    assert delete.status_code == 204


def test_placeholder_requires_token(client):
    assert client.post("/v1/search", json={"query": "x"}).status_code == 401
    assert client.post("/v1/care/check", json={"user_id": 1}).status_code == 401
