"""POST /v1/self-insight：回顾用户近期公开文章的主题脉络、意象与写作习惯。"""
import logging
import time
import uuid

from fastapi import APIRouter, Depends

from app.api.deps import get_llm_client, verify_internal_token
from app.core.config import get_settings
from app.core.errors import AppError
from app.core.logging import set_request_id
from app.insight.service import mock_insight, review_content
from app.llm.base import LLMClient
from app.prompts.registry import PROMPT_VERSION
from app.schemas.insight import SelfInsightRequest, SelfInsightResponse

log = logging.getLogger("ai-service.insight")

router = APIRouter(dependencies=[Depends(verify_internal_token)])


@router.post("/self-insight", response_model=SelfInsightResponse)
async def self_insight(
    req: SelfInsightRequest,
    client: LLMClient = Depends(get_llm_client),
) -> SelfInsightResponse:
    # 复用 Java 传入的 trace_id，便于与 ai_output 审计记录对齐
    set_request_id(req.trace_id or uuid.uuid4().hex[:8])
    started = time.monotonic()
    settings = get_settings()

    if settings.mock_mode:
        result = mock_insight(req)
    else:
        if not client.is_configured():
            raise AppError("LLM_ERROR", "AI 服务未配置模型，请联系管理员", 502)
        result = await review_content(req, client)

    result.model = "mock" if settings.mock_mode else settings.llm_model
    result.prompt_version = PROMPT_VERSION

    log.info(
        "self-insight user_id=%s trace_id=%s articles=%d themes=%d motifs=%d habits=%d "
        "model=%s prompt_version=%s duration_ms=%d",
        req.user_id, req.trace_id or "-", len(req.articles), len(result.themes),
        len(result.motifs), len(result.habits), result.model, PROMPT_VERSION,
        int((time.monotonic() - started) * 1000),
    )
    return result
