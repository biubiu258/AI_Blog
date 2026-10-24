"""POST /v1/generate：SSE 流式生成入口。"""
import logging
import uuid

from fastapi import APIRouter, Depends, Request
from fastapi.responses import StreamingResponse

from app.api.deps import get_llm_client, verify_internal_token
from app.core.config import get_settings
from app.core.errors import AppError
from app.core.logging import set_request_id
from app.core.sse import sse_response
from app.generation.orchestrator import generate_stream
from app.llm.base import LLMClient
from app.llm.fake import mock_llm
from app.prompts.registry import PROMPT_VERSION
from app.schemas.generate import GenerateRequest

log = logging.getLogger("ai-service.api")

router = APIRouter(dependencies=[Depends(verify_internal_token)])


@router.post("/generate")
async def generate(
    req: GenerateRequest,
    request: Request,
    client: LLMClient = Depends(get_llm_client),
) -> StreamingResponse:
    settings = get_settings()
    set_request_id(uuid.uuid4().hex[:8])

    llm = mock_llm(req.task) if settings.mock_mode else client
    if not llm.is_configured():
        # 流建立前失败：普通 JSON 错误，避免把配置故障伪装成一次生成
        raise AppError("LLM_ERROR", "AI 服务未配置模型，请联系管理员", 502)

    log.info(
        "generate 开始 task=%s user_id=%s prompt_version=%s mock=%s",
        req.task, req.user_id, PROMPT_VERSION, settings.mock_mode,
    )
    return sse_response(generate_stream(req, request, llm))
