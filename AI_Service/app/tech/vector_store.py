"""技术语料独立向量集合 tech_docs，与文章集合 articles 隔离。"""
from __future__ import annotations

import logging
import threading

log = logging.getLogger("ai-service.tech.vector")

COLLECTION_NAME = "tech_docs"


class TechVectorStoreError(Exception):
    """向量库不可用或读写失败。"""


class TechVectorStore:
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
                    raise TechVectorStoreError(
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
                    log.error("技术语料向量库初始化失败 path=%s: %s", self._path, exc)
                    raise TechVectorStoreError("向量库初始化失败") from exc
        return self._collection

    def upsert(
        self,
        doc_id: str,
        title: str,
        chunks: list[dict],
        embeddings: list[list[float]],
        source_name: str = "",
        version: str = "",
    ) -> int:
        """按 doc_id 幂等覆盖；chunks 含 chunk_index/page_no/section_path/content。"""
        if len(chunks) != len(embeddings):
            raise TechVectorStoreError("文本块与向量数量不一致")
        collection = self._get_collection()
        try:
            collection.delete(where={"doc_id": doc_id})
            if not chunks:
                return 0
            ids = [f"{doc_id}:{chunk['chunk_index']}" for chunk in chunks]
            documents = [chunk["content"] for chunk in chunks]
            metadatas = []
            for chunk in chunks:
                page_no = chunk.get("page_no")
                metadatas.append({
                    "doc_id": doc_id,
                    "title": title,
                    "source_name": source_name,
                    "version": version,
                    "section_path": chunk.get("section_path") or "",
                    "page_no": int(page_no) if page_no else 0,
                    "chunk_index": int(chunk["chunk_index"]),
                    "snippet": chunk["content"][:200],
                })
            collection.add(
                ids=ids,
                embeddings=embeddings,
                documents=documents,
                metadatas=metadatas,
            )
            return len(chunks)
        except Exception as exc:
            log.error("技术语料向量写入失败 doc_id=%s: %s", doc_id, exc)
            raise TechVectorStoreError("向量写入失败") from exc

    def delete(self, doc_id: str) -> None:
        """幂等删除：不存在也视为成功。"""
        collection = self._get_collection()
        try:
            collection.delete(where={"doc_id": doc_id})
        except Exception as exc:
            log.error("技术语料向量删除失败 doc_id=%s: %s", doc_id, exc)
            raise TechVectorStoreError("向量删除失败") from exc

    def query(self, embedding: list[float], top_k: int) -> list[dict]:
        collection = self._get_collection()
        try:
            if collection.count() == 0:
                return []
            result = collection.query(
                query_embeddings=[embedding],
                n_results=min(max(top_k, 1), 100),
                include=["documents", "metadatas", "distances"],
            )
        except Exception as exc:
            log.error("技术语料向量检索失败: %s", exc)
            raise TechVectorStoreError("向量检索失败") from exc

        ids = (result.get("ids") or [[]])[0]
        documents = (result.get("documents") or [[]])[0]
        metadatas = (result.get("metadatas") or [[]])[0]
        distances = (result.get("distances") or [[]])[0]

        hits: list[dict] = []
        for index, chunk_id in enumerate(ids):
            metadata = metadatas[index] if index < len(metadatas) else {}
            document = documents[index] if index < len(documents) else ""
            distance = float(distances[index]) if index < len(distances) else 1.0
            page_no = int(metadata.get("page_no") or 0)
            hits.append({
                "chunk_id": str(chunk_id),
                "doc_id": str(metadata.get("doc_id") or ""),
                "title": str(metadata.get("title") or ""),
                "source_name": str(metadata.get("source_name") or ""),
                "version": str(metadata.get("version") or ""),
                "section_path": str(metadata.get("section_path") or "") or None,
                "page_no": page_no if page_no > 0 else None,
                "snippet": str(metadata.get("snippet") or document[:200]),
                "score": round(max(0.0, 1.0 - distance), 4),
            })
        hits.sort(key=lambda hit: hit["score"], reverse=True)
        return hits[:top_k]

    def count(self) -> int:
        return self._get_collection().count()
