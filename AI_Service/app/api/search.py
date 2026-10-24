"""检索与向量同步接口接入 RAG 服务（契约 5.5）。"""
import logging

from fastapi import APIRouter, Depends

from app.api.deps import get_rag_service, verify_internal_token
from app.core.errors import AppError
from app.rag.service import RagError, RagService
from app.schemas.search import Hit, RetrieveRequest, SearchRequest, SearchResponse

log = logging.getLogger("ai-service.search")

router = APIRouter(dependencies=[Depends(verify_internal_token)])


def _to_response(hits: list[dict]) -> SearchResponse:
    return SearchResponse(hits=[
        Hit(
            article_id=str(hit.get("article_id", "")),
            title=str(hit.get("title", "")),
            snippet=str(hit.get("snippet", "")),
            score=hit.get("score"),
        )
        for hit in hits
    ])


@router.post("/search", response_model=SearchResponse)
async def search(
    req: SearchRequest,
    rag: RagService = Depends(get_rag_service),
) -> SearchResponse:
    try:
        hits = await rag.search(req.query, req.top_k)
    except RagError as exc:
        raise AppError("VECTOR_ERROR", "向量检索暂不可用，请稍后重试") from exc
    log.info("search top_k=%d hits=%d rag=%s", req.top_k, len(hits), rag.enabled)
    return _to_response(hits)


@router.post("/retrieve", response_model=SearchResponse)
async def retrieve(
    req: RetrieveRequest,
    rag: RagService = Depends(get_rag_service),
) -> SearchResponse:
    try:
        hits = await rag.search(req.query, req.top_k)
    except RagError as exc:
        raise AppError("VECTOR_ERROR", "向量检索暂不可用，请稍后重试") from exc
    log.info(
        "retrieve user_id=%s top_k=%d hits=%d rag=%s",
        req.user_id, req.top_k, len(hits), rag.enabled,
    )
    return _to_response(hits)
