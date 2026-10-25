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
from app.prompts.registry import PROMPT_VERSION
from app.review.service import mock_review, review_content
from app.schemas.review import ReviewRequest, ReviewResponse

log = logging.getLogger("ai-service.review")

router = APIRouter(dependencies=[Depends(verify_internal_token)])


@router.post("/review", response_model=ReviewResponse)
async def review(
    req: ReviewRequest,
    client: LLMClient = Depends(get_llm_client),
) -> ReviewResponse:
    # 复用 Java 传入的 trace_id，便于与 ai_output 审计记录对齐
    set_request_id(req.trace_id or uuid.uuid4().hex[:8])
    started = time.monotonic()
    settings = get_settings()

    if settings.mock_mode:
        result = mock_review(req)
    else:
        if not client.is_configured():
            raise AppError("LLM_ERROR", "AI 服务未配置模型，请联系管理员", 502)
        result = await review_content(req, client)

    # 回传模型与提示词版本，供 Java 写入 ai_output 审计
    result.model = "mock" if settings.mock_mode else settings.llm_model
    result.prompt_version = PROMPT_VERSION

    log.info(
        "review user_id=%s trace_id=%s title_len=%d content_len=%d suggestions=%d "
        "model=%s prompt_version=%s duration_ms=%d",
        req.user_id, req.trace_id or "-", len(req.title or ""), len(req.content),
        len(result.suggestions), result.model, PROMPT_VERSION,
        int((time.monotonic() - started) * 1000),
    )
    return result
