"""RAG：切块、Chroma 读写、服务开关与接口映射。"""
from pathlib import Path

import pytest

from app.api.deps import get_rag_service
from app.core.config import Settings
from app.rag.chunking import (
    MAX_CHUNK_CHARS,
    MAX_CHUNKS,
    build_chunks,
    snippet_of,
)
from app.rag.embeddings import EmbeddingError
from app.rag.service import RagError, RagService
from app.rag.store import VectorStore

TOKEN_HEADER = {"X-Internal-Token": "test-token"}


def _settings(tmp_path: Path, enabled: bool = True) -> Settings:
    return Settings(
        rag_enabled=enabled,
        chroma_dir=str(tmp_path / "chroma"),
        embedding_model="test-model",
        embedding_cache_dir=str(tmp_path / "models"),
        internal_token="test-token",
    )


class FakeEmbedder:
    def __init__(self) -> None:
        self.passages: list[str] = []
        self.queries: list[str] = []

    def embed_passages(self, texts):
        self.passages.extend(texts)
        return [[1.0, 0.0] for _ in texts]

    def embed_query(self, text):
        self.queries.append(text)
        return [1.0, 0.0]


class StubRag:
    enabled = True

    def __init__(self) -> None:
        self.deleted: list[int] = []

    async def search(self, query, top_k):
        return [
            {
                "article_id": "12",
                "title": "旧书摊",
                "snippet": "黄昏的旧书摊",
                "score": 0.87,
            }
        ][:top_k]

    async def upsert(self, article_id, title, content):
        return 3

    async def delete(self, article_id):
        self.deleted.append(int(article_id))
        return False

    def status(self):
        return {"enabled": True, "ready": True, "documents": 3}


class BrokenRag(StubRag):
    async def search(self, query, top_k):
        raise RagError("向量库不可用")

    async def upsert(self, article_id, title, content):
        raise RagError("向量库不可用")

    async def delete(self, article_id):
        raise RagError("向量库不可用")


@pytest.fixture()
def override_rag(app):
    def _install(service):
        app.dependency_overrides[get_rag_service] = lambda: service
        return service

    yield _install
    app.dependency_overrides.clear()


def test_build_chunks_empty_and_short():
    assert build_chunks("") == []
    assert build_chunks("   \n\n ") == []
    chunks = build_chunks("黄昏的旧书摊，一本线装书。")
    assert len(chunks) == 1
    assert "旧书摊" in chunks[0]


def test_build_chunks_obeys_limits():
    long_text = "\n\n".join(
        f"第{i}段：" + "青石板路通向河埠头，檐下的灯笼已经亮起。" * 40
        for i in range(5)
    )
    chunks = build_chunks(long_text)
    assert 1 < len(chunks) <= MAX_CHUNKS
    assert all(len(chunk) <= MAX_CHUNK_CHARS for chunk in chunks)
    assert all(chunk for chunk in chunks)


def test_snippet_is_readable():
    text = "甲" * 300
    snippet = snippet_of(text)
    assert len(snippet) <= 162
    assert snippet.endswith("……")


def test_store_upsert_search_delete(tmp_path):
    store = VectorStore(str(tmp_path / "chroma"))
    store.upsert(
        "12",
        "旧书摊",
        ["黄昏的旧书摊", "河埠头的乌篷船"],
        [[1.0, 0.0], [0.0, 1.0]],
    )
    assert store.count() == 2

    hits = store.search([1.0, 0.0], 5)
    assert len(hits) == 1
    assert hits[0]["article_id"] == "12"
    assert hits[0]["title"] == "旧书摊"
    assert hits[0]["snippet"].startswith("黄昏")
    assert 0.9 <= hits[0]["score"] <= 1.0

    # 重复同步按 article_id 覆盖，不会累积旧块
    store.upsert("12", "旧书摊", ["新的内容"], [[1.0, 0.0]])
    assert store.count() == 1

    assert store.delete("12") is True
    assert store.delete("12") is False
    assert store.count() == 0


def test_store_rejects_mismatched_vectors(tmp_path):
    store = VectorStore(str(tmp_path / "chroma"))
    with pytest.raises(Exception):
        store.upsert("12", "t", ["a", "b"], [[1.0, 0.0]])


async def test_service_disabled_returns_empty(tmp_path):
    service = RagService(_settings(tmp_path, enabled=False))
    assert await service.search("旧书", 5) == []
    assert await service.upsert(12, "t", "内容") == 0
    assert await service.delete(12) is True
    assert service.status() == {"enabled": False, "ready": False, "documents": 0}


async def test_service_upsert_prefixes_title_and_chunks(tmp_path):
    embedder = FakeEmbedder()
    store = VectorStore(str(tmp_path / "chroma"))
    service = RagService(_settings(tmp_path), embedder=embedder, store=store)

    count = await service.upsert(12, "旧书摊", "黄昏的旧书摊。河埠头的乌篷船。")
    assert count == 1
    assert embedder.passages[0].startswith("旧书摊\n")

    hits = await service.search("旧书", 5)
    assert hits and hits[0]["article_id"] == "12"
    assert embedder.queries == ["旧书"]


async def test_service_maps_embedding_error(tmp_path):
    class BrokenEmbedder(FakeEmbedder):
        def embed_query(self, text):
            raise EmbeddingError("模型不可用")

    service = RagService(
        _settings(tmp_path), embedder=BrokenEmbedder(), store=VectorStore(str(tmp_path / "chroma"))
    )
    with pytest.raises(RagError):
        await service.search("旧书", 5)


def test_api_search_maps_hits(client, override_rag):
    override_rag(StubRag())
    resp = client.post("/v1/search", json={"query": "旧书"}, headers=TOKEN_HEADER)
    assert resp.status_code == 200
    hit = resp.json()["hits"][0]
    assert hit == {
        "article_id": "12",
        "title": "旧书摊",
        "snippet": "黄昏的旧书摊",
        "score": 0.87,
    }


def test_api_retrieve_accepts_user_id(client, override_rag):
    override_rag(StubRag())
    resp = client.post(
        "/v1/retrieve",
        json={"query": "旧书", "user_id": 7, "top_k": 1},
        headers=TOKEN_HEADER,
    )
    assert resp.status_code == 200
    assert len(resp.json()["hits"]) == 1


def test_api_delete_missing_vector_returns_404(client, override_rag):
    service = override_rag(StubRag())
    resp = client.delete("/v1/vectors/12", headers=TOKEN_HEADER)
    assert resp.status_code == 404
    assert resp.json()["error"]["code"] == "NOT_FOUND"
    assert service.deleted == [12]


def test_api_vector_failure_maps_to_vector_error(client, override_rag):
    override_rag(BrokenRag())
    search = client.post("/v1/search", json={"query": "旧书"}, headers=TOKEN_HEADER)
    assert search.status_code == 500
    assert search.json()["error"]["code"] == "VECTOR_ERROR"

    upsert = client.post(
        "/v1/vectors/upsert",
        json={"article_id": "12", "title": "t", "content": "c"},
        headers=TOKEN_HEADER,
    )
    assert upsert.status_code == 500
    assert upsert.json()["error"]["code"] == "VECTOR_ERROR"

    delete = client.delete("/v1/vectors/12", headers=TOKEN_HEADER)
    assert delete.status_code == 500
    assert delete.json()["error"]["code"] == "VECTOR_ERROR"


def test_api_rag_disabled_keeps_contract_shape(client):
    search = client.post("/v1/search", json={"query": "旧书"}, headers=TOKEN_HEADER)
    assert search.status_code == 200
    assert search.json() == {"hits": []}

    upsert = client.post(
        "/v1/vectors/upsert",
        json={"article_id": "12", "title": "t", "content": "c"},
        headers=TOKEN_HEADER,
    )
    assert upsert.status_code == 204


def test_health_reports_rag_disabled(client):
    body = client.get("/v1/health").json()
    assert body["rag"] == {"enabled": False, "ready": False, "documents": 0}
    assert body["version"].startswith("0.3")
