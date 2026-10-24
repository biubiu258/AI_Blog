"""POST /v1/vectors/*：保持 Chroma 与 MySQL 幂等对齐（契约 5.5/5.6）。"""
import logging

from fastapi import APIRouter, Depends

from app.api.deps import get_rag_service, verify_internal_token
from app.core.errors import AppError
from app.rag.service import RagError, RagService
from app.schemas.vectors import VectorUpsertRequest

log = logging.getLogger("ai-service.vectors")

router = APIRouter(dependencies=[Depends(verify_internal_token)])


@router.post("/vectors/upsert", status_code=204)
async def vector_upsert(
    req: VectorUpsertRequest,
    rag: RagService = Depends(get_rag_service),
) -> None:
    try:
        chunks = await rag.upsert(req.article_id, req.title, req.content)
    except RagError as exc:
        raise AppError("VECTOR_ERROR", "向量写入失败") from exc
    log.info(
        "vector upsert article_id=%s title_len=%d content_len=%d chunks=%d",
        req.article_id, len(req.title), len(req.content), chunks,
    )


@router.delete("/vectors/{article_id}", status_code=204)
async def vector_delete(
    article_id: str,
    rag: RagService = Depends(get_rag_service),
) -> None:
    try:
        found = await rag.delete(article_id)
    except RagError as exc:
        raise AppError("VECTOR_ERROR", "向量删除失败") from exc
    if not found:
        raise AppError("NOT_FOUND", "向量不存在", 404)
    log.info("vector delete article_id=%s", article_id)
