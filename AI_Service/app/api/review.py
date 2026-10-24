"""POST /v1/review：全文审阅，返回可锚定的结构化建议。"""
import logging
import time
import uuid

from fastapi import APIRouter, Depends

from app.api.deps import get_llm_client, verify_internal_token
from app.core.config import get_settings
from app.core.errors import AppError
from app.core.logging import set_request_id
from app.llm.base import LLMClient
from app.review.service import mock_review, review_content
from app.schemas.review import ReviewRequest, ReviewResponse

log = logging.getLogger("ai-service.review")

router = APIRouter(dependencies=[Depends(verify_internal_token)])


@router.post("/review", response_model=ReviewResponse)
async def review(
    req: ReviewRequest,
    client: LLMClient = Depends(get_llm_client),
) -> ReviewResponse:
    set_request_id(uuid.uuid4().hex[:8])
    started = time.monotonic()
    settings = get_settings()

    if settings.mock_mode:
        result = mock_review(req)
    else:
        if not client.is_configured():
            raise AppError("LLM_ERROR", "AI 服务未配置模型，请联系管理员", 502)
        result = await review_content(req, client)

    log.info(
        "review user_id=%s title_len=%d content_len=%d suggestions=%d duration_ms=%d",
        req.user_id, len(req.title or ""), len(req.content), len(result.suggestions),
        int((time.monotonic() - started) * 1000),
    )
    return result
