"""Chroma 持久化：一篇文章切成多块，按 article_id 幂等覆盖。"""
import logging
import threading

log = logging.getLogger("ai-service.rag.store")

COLLECTION_NAME = "articles"


class VectorStoreError(Exception):
    """向量库不可用或读写失败。"""


class VectorStore:
    def __init__(self, path: str) -> None:
        self._path = path
        self._client = None
        self._collection = None
        self._lock = threading.Lock()

    def _get_collection(self):
        if self._collection is not None:
            return self._collection
        with self._lock:
            if self._collection is None:
                try:
                    import chromadb
                    from chromadb.config import Settings as ChromaSettings
                except ImportError as exc:  # pragma: no cover - 依赖缺失
                    raise VectorStoreError(
                        "未安装 chromadb，请执行 pip install -r requirements.txt"
                    ) from exc
                try:
                    self._client = chromadb.PersistentClient(
                        path=self._path,
                        settings=ChromaSettings(anonymized_telemetry=False),
                    )
                    self._collection = self._client.get_or_create_collection(
                        COLLECTION_NAME, metadata={"hnsw:space": "cosine"}
                    )
                except Exception as exc:
                    log.error("向量库初始化失败 path=%s: %s", self._path, exc)
                    raise VectorStoreError("向量库初始化失败") from exc
        return self._collection

    def upsert(
        self,
        article_id: str,
        title: str,
        chunks: list[str],
        embeddings: list[list[float]],
    ) -> None:
        if len(chunks) != len(embeddings):
            raise VectorStoreError("文本块与向量数量不一致")
        collection = self._get_collection()
        try:
            collection.delete(where={"article_id": article_id})
            if not chunks:
                return
            ids = [f"{article_id}:{index}" for index in range(len(chunks))]
            metadatas = [
                {
                    "article_id": article_id,
                    "title": title,
                    "chunk_index": index,
                    "snippet": chunk[:200],
                }
                for index, chunk in enumerate(chunks)
            ]
            collection.add(
                ids=ids,
                embeddings=embeddings,
                documents=chunks,
                metadatas=metadatas,
            )
        except Exception as exc:
            log.error("向量写入失败 article_id=%s: %s", article_id, exc)
            raise VectorStoreError("向量写入失败") from exc

    def delete(self, article_id: str) -> bool:
        """删除文章全部块；返回删前是否存在。"""
        collection = self._get_collection()
        try:
            existing = collection.get(where={"article_id": article_id}, limit=1, include=[])
            if not existing.get("ids"):
                return False
            collection.delete(where={"article_id": article_id})
            return True
        except Exception as exc:
            log.error("向量删除失败 article_id=%s: %s", article_id, exc)
            raise VectorStoreError("向量删除失败") from exc

    def search(self, embedding: list[float], top_k: int) -> list[dict]:
        """返回按文章去重后的命中，score 为余弦相似度。"""
        collection = self._get_collection()
        try:
            if collection.count() == 0:
                return []
            result = collection.query(
                query_embeddings=[embedding],
                n_results=min(max(top_k * 3, top_k), 100),
                include=["documents", "metadatas", "distances"],
            )
        except Exception as exc:
            log.error("向量检索失败: %s", exc)
            raise VectorStoreError("向量检索失败") from exc

        ids = (result.get("ids") or [[]])[0]
        documents = (result.get("documents") or [[]])[0]
        metadatas = (result.get("metadatas") or [[]])[0]
        distances = (result.get("distances") or [[]])[0]

        best: dict[str, dict] = {}
        for index, _ in enumerate(ids):
            metadata = metadatas[index] if index < len(metadatas) else {}
            article_id = str(metadata.get("article_id") or "")
            if not article_id:
                continue
            distance = float(distances[index]) if index < len(distances) else 1.0
            score = round(max(0.0, 1.0 - distance), 4)
            document = documents[index] if index < len(documents) else ""
            item = {
                "article_id": article_id,
                "title": str(metadata.get("title") or ""),
                "snippet": str(metadata.get("snippet") or document[:200]),
                "score": score,
            }
            current = best.get(article_id)
            if current is None or score > current["score"]:
                best[article_id] = item

        ordered = sorted(best.values(), key=lambda hit: hit["score"], reverse=True)
        return ordered[:top_k]

    def count(self) -> int:
        return self._get_collection().count()
