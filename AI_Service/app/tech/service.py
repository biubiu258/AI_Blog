"""技术语料编排：解析、切块、向量同步与混合检索。"""
from __future__ import annotations

import asyncio
import logging
from dataclasses import dataclass
from functools import lru_cache

from app.core.config import get_settings
from app.rag.embeddings import EmbeddingError, LocalEmbedder
from app.tech.chunking import Chunk, build_chunks
from app.tech.parser import (
    MAX_FILE_BYTES,
    Block,
    ParseError,
    parse_markdown,
    parse_pdf,
    parse_text,
)
from app.tech.vector_store import TechVectorStore, TechVectorStoreError

log = logging.getLogger("ai-service.tech.service")

SUPPORTED_TYPES = {"markdown", "text", "pdf"}
_EXTENSIONS = {
    "md": "markdown",
    "markdown": "markdown",
    "txt": "text",
    "text": "text",
    "pdf": "pdf",
}


@dataclass
class TechParseResult:
    title: str
    file_type: str
    page_count: int
    chunks: list[Chunk]


def normalize_file_type(file_type: str | None, filename: str | None) -> str:
    """把 file_type 或文件后缀归一化为 markdown/text/pdf。"""
    value = (file_type or "").strip().lower()
    if value in SUPPORTED_TYPES:
        return value
    if value:
        value = _EXTENSIONS.get(value, "")
        if value in SUPPORTED_TYPES:
            return value
        raise ParseError("FILE_TYPE_UNSUPPORTED", "仅支持 Markdown、纯文本与 PDF")
    suffix = (filename or "").rsplit(".", 1)[-1].lower() if "." in (filename or "") else ""
    inferred = _EXTENSIONS.get(suffix)
    if inferred:
        return inferred
    raise ParseError("FILE_TYPE_UNSUPPORTED", "仅支持 Markdown、纯文本与 PDF")


def _decode_text(data: bytes) -> str:
    try:
        return data.decode("utf-8")
    except UnicodeDecodeError:
        try:
            return data.decode("gb18030")
        except UnicodeDecodeError as exc:
            raise ParseError("INVALID_PARAMS", "文本编码无法识别，请使用 UTF-8") from exc


def _parse_blocks(file_type: str, data: bytes) -> tuple[list[Block], int]:
    if file_type == "pdf":
        parsed = parse_pdf(data)
        return parsed.blocks, parsed.page_count
    text = _decode_text(data)
    if file_type == "markdown":
        return parse_markdown(text), 0
    return parse_text(text), 0


def parse_document(
    data: bytes,
    file_type: str | None,
    title: str | None = None,
    filename: str | None = None,
) -> TechParseResult:
    """解析上传内容并切块；所有失败都以 ParseError 抛出。"""
    if len(data) > MAX_FILE_BYTES:
        raise ParseError("FILE_TOO_LARGE", "文件超过 30MB")
    if not data:
        raise ParseError("INVALID_PARAMS", "文件内容为空")

    resolved_type = normalize_file_type(file_type, filename)
    blocks, page_count = _parse_blocks(resolved_type, data)
    chunks = build_chunks(blocks)
    if not chunks:
        raise ParseError("INVALID_PARAMS", "文档没有可索引的内容")

    resolved_title = (title or "").strip()
    if not resolved_title and filename:
        resolved_title = filename.rsplit("/", 1)[-1].rsplit(".", 1)[0]
    if not resolved_title:
        resolved_title = "未命名文档"
    resolved_title = resolved_title[:200]

    log.info(
        "技术语料解析完成 type=%s pages=%d chunks=%d title=%s",
        resolved_type, page_count, len(chunks), resolved_title,
    )
    return TechParseResult(
        title=resolved_title,
        file_type=resolved_type,
        page_count=page_count,
        chunks=chunks,
    )


# ------------------------------------------------------------------ 向量同步


@lru_cache(maxsize=1)
def _get_embedder() -> LocalEmbedder:
    settings = get_settings()
    return LocalEmbedder(settings.embedding_model, settings.embedding_cache_dir)


@lru_cache(maxsize=1)
def _get_store() -> TechVectorStore:
    return TechVectorStore(get_settings().chroma_dir)


def vector_enabled() -> bool:
    """向量能力跟随 RAG 总开关；chromadb 缺失时由检索侧降级处理。"""
    return get_settings().rag_enabled


async def upsert_vectors(
    doc_id: int,
    title: str,
    source_name: str,
    version: str,
    chunks: list[dict],
) -> int:
    """重建单个文档的向量；RAG 关闭时直接返回 0。"""
    if not vector_enabled():
        return 0
    prepared = [chunk for chunk in chunks if str(chunk.get("content") or "").strip()]
    if not prepared:
        await asyncio.to_thread(_get_store().delete, str(doc_id))
        return 0
    passages = [
        f"{title}\n{chunk['content']}" if title.strip() else chunk["content"]
        for chunk in prepared
    ]
    try:
        vectors = await asyncio.to_thread(_get_embedder().embed_passages, passages)
        written = await asyncio.to_thread(
            _get_store().upsert,
            str(doc_id), title, prepared, vectors, source_name, version,
        )
    except (EmbeddingError, TechVectorStoreError) as exc:
        raise ParseError("VECTOR_ERROR", str(exc)) from exc
    log.info("技术语料向量写入完成 doc_id=%s chunks=%d", doc_id, written)
    return written


async def delete_vectors(doc_id: int) -> None:
    """幂等删除；RAG 关闭时直接成功。"""
    if not vector_enabled():
        return
    try:
        await asyncio.to_thread(_get_store().delete, str(doc_id))
    except TechVectorStoreError as exc:
        raise ParseError("VECTOR_ERROR", str(exc)) from exc


async def retrieve_vectors(query: str, top_k: int) -> tuple[list[dict], bool]:
    """语义检索；关闭、依赖缺失或失败时返回空结果并标记 degraded。"""
    if not vector_enabled():
        return [], True
    try:
        embedding = await asyncio.to_thread(_get_embedder().embed_query, query)
        hits = await asyncio.to_thread(_get_store().query, embedding, top_k)
        return hits, False
    except (EmbeddingError, TechVectorStoreError, ImportError) as exc:
        log.warning("技术语料向量检索降级: %s", exc)
        return [], True
    except Exception as exc:  # 向量侧任何异常都不阻塞写作建议
        log.warning("技术语料向量检索异常，已降级: %s", exc)
        return [], True
