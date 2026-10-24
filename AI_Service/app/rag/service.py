"""RAG 服务：开关、切块、向量化、检索失败统一映射。"""
import asyncio
import logging
from functools import lru_cache

from app.core.config import Settings, get_settings
from app.rag.chunking import build_chunks
from app.rag.embeddings import EmbeddingError, LocalEmbedder
from app.rag.store import VectorStore, VectorStoreError

log = logging.getLogger("ai-service.rag")


class RagError(Exception):
    """检索或同步失败，由接口层映射为 VECTOR_ERROR。"""


class RagService:
    def __init__(
        self,
        settings: Settings,
        embedder: LocalEmbedder | None = None,
        store: VectorStore | None = None,
    ) -> None:
        self._settings = settings
        self._embedder = embedder
        self._store = store

    @property
    def enabled(self) -> bool:
        return self._settings.rag_enabled

    def _components(self) -> tuple[LocalEmbedder, VectorStore]:
        if self._embedder is None:
            self._embedder = LocalEmbedder(
                self._settings.embedding_model, self._settings.embedding_cache_dir
            )
        if self._store is None:
            self._store = VectorStore(self._settings.chroma_dir)
        return self._embedder, self._store

    async def upsert(self, article_id: int, title: str, content: str) -> int:
        """重建一篇文章的向量；返回写入的块数。关闭时直接成功。"""
        if not self.enabled:
            return 0
        chunks = build_chunks(content)
        embedder, store = self._components()
        if not chunks:
            await self.delete(article_id)
            return 0
        passages = [
            f"{title}\n{chunk}" if title.strip() else chunk for chunk in chunks
        ]
        try:
            vectors = await asyncio.to_thread(embedder.embed_passages, passages)
            await asyncio.to_thread(
                store.upsert, str(article_id), title, chunks, vectors
            )
        except (EmbeddingError, VectorStoreError) as exc:
            raise RagError(str(exc)) from exc
        log.info("向量写入完成 article_id=%s chunks=%d", article_id, len(chunks))
        return len(chunks)

    async def delete(self, article_id: int) -> bool:
        """删除文章向量；关闭时直接成功，返回是否存在。"""
        if not self.enabled:
            return True
        _, store = self._components()
        try:
            return await asyncio.to_thread(store.delete, str(article_id))
        except VectorStoreError as exc:
            raise RagError(str(exc)) from exc

    async def search(self, query: str, top_k: int) -> list[dict]:
        """语义检索；关闭或查询为空时返回空列表。"""
        if not self.enabled:
            return []
        query = query.strip()
        if not query:
            return []
        embedder, store = self._components()
        try:
            embedding = await asyncio.to_thread(embedder.embed_query, query)
            hits = await asyncio.to_thread(store.search, embedding, top_k)
        except (EmbeddingError, VectorStoreError) as exc:
            raise RagError(str(exc)) from exc
        log.info("向量检索完成 top_k=%d hits=%d", top_k, len(hits))
        return hits

    def status(self) -> dict:
        """健康检查用：不触发模型加载，只探测向量库。"""
        if not self.enabled:
            return {"enabled": False, "ready": False, "documents": 0}
        _, store = self._components()
        try:
            return {"enabled": True, "ready": True, "documents": store.count()}
        except VectorStoreError:
            return {"enabled": True, "ready": False, "documents": 0}


@lru_cache(maxsize=1)
def get_rag_service() -> RagService:
    return RagService(get_settings())
