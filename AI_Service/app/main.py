"""应用装配：日志、错误处理、路由。"""
import logging
from pathlib import Path

from fastapi import FastAPI

from app.api import care, generate, health, review, search, vectors
from app.core.config import SERVICE_VERSION, get_settings
from app.core.errors import install_error_handlers
from app.core.logging import setup_logging
from app.prompts.registry import PROMPT_VERSION

log = logging.getLogger("ai-service")


def create_app() -> FastAPI:
    settings = get_settings()
    setup_logging(settings.log_level)

    app = FastAPI(title="AI Service", version=SERVICE_VERSION)
    install_error_handlers(app)

    app.include_router(health.router, prefix="/v1")
    app.include_router(generate.router, prefix="/v1")
    app.include_router(review.router, prefix="/v1")
    app.include_router(care.router, prefix="/v1")
    app.include_router(search.router, prefix="/v1")
    app.include_router(vectors.router, prefix="/v1")

    log.info(
        "AI Service %s 启动 prompt_version=%s llm_configured=%s mock=%s "
        "rag=%s care_llm=%s style_profile=%s",
        SERVICE_VERSION, PROMPT_VERSION, settings.llm_configured, settings.mock_mode,
        settings.rag_enabled, settings.care_llm_enabled,
        Path(settings.style_config_path).exists(),
    )
    if not settings.llm_configured and not settings.mock_mode:
        log.warning("LLM 未配置：/v1/generate 将返回 502，直至补齐 LLM_BASE_URL/LLM_API_KEY/LLM_MODEL")
    return app


app = create_app()
