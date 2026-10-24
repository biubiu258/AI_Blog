"""POST /v1/care/check：发布前的情绪/危机检测（契约 5.10）。"""
import logging
import time
import uuid

from fastapi import APIRouter, Depends

from app.api.deps import get_llm_client, verify_internal_token
from app.care.service import check_care, none_result
from app.core.logging import set_request_id
from app.llm.base import LLMClient
from app.schemas.care import CareCheckRequest, CareCheckResponse

log = logging.getLogger("ai-service.care")

router = APIRouter(dependencies=[Depends(verify_internal_token)])


@router.post("/care/check", response_model=CareCheckResponse)
async def care_check(
    req: CareCheckRequest,
    client: LLMClient = Depends(get_llm_client),
) -> CareCheckResponse:
    """fail-open：任何异常都按 none 放行，绝不阻断发布；日志不含正文。"""
    set_request_id(uuid.uuid4().hex[:8])
    started = time.monotonic()
    try:
        result = await check_care(req, client)
    except Exception:
        log.exception("关怀检测异常，按 none 放行 user_id=%s", req.user_id)
        result = none_result()

    log.info(
        "care_check user_id=%s level=%s title_len=%d content_len=%d duration_ms=%d keywords=%s",
        req.user_id, result.level, len(req.title), len(req.content),
        int((time.monotonic() - started) * 1000),
        "、".join(result.keywords) or "无",
    )
    return CareCheckResponse(
        level=result.level,
        summary=result.summary,
        keywords=list(result.keywords) or None,
    )
