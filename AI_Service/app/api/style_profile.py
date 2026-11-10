"""POST /v1/style-profile：从用户旧文提炼个人文风特征。"""
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
from app.schemas.style_profile import StyleProfileRequest, StyleProfileResponse
from app.styleprofile.service import mock_profile, profile_content

log = logging.getLogger("ai-service.styleprofile")

router = APIRouter(dependencies=[Depends(verify_internal_token)])


@router.post("/style-profile", response_model=StyleProfileResponse)
async def style_profile(
    req: StyleProfileRequest,
    client: LLMClient = Depends(get_llm_client),
) -> StyleProfileResponse:
    # 复用 Java 传入的 trace_id，便于与 ai_output 审计记录对齐
    set_request_id(req.trace_id or uuid.uuid4().hex[:8])
    started = time.monotonic()
    settings = get_settings()

    if settings.mock_mode:
        result = mock_profile(req)
    else:
        if not client.is_configured():
            raise AppError("LLM_ERROR", "AI 服务未配置模型，请联系管理员", 502)
        result = await profile_content(req, client)

    result.model = "mock" if settings.mock_mode else settings.llm_model
    result.prompt_version = PROMPT_VERSION

    log.info(
        "style-profile user_id=%s trace_id=%s samples=%d summary_len=%d "
        "model=%s prompt_version=%s duration_ms=%d",
        req.user_id, req.trace_id or "-", len(req.samples), len(result.summary),
        result.model, PROMPT_VERSION,
        int((time.monotonic() - started) * 1000),
    )
    return result
