"""GET /v1/health：不含密钥的运行信息。"""
import asyncio

from fastapi import APIRouter

from app.core.config import SERVICE_VERSION, get_settings
from app.prompts.registry import PROMPT_VERSION
from app.rag.service import get_rag_service

router = APIRouter()


@router.get("/health")
async def health() -> dict:
    settings = get_settings()
    rag = await asyncio.to_thread(get_rag_service().status)
    return {
        "status": "ok",
        "version": SERVICE_VERSION,
        "prompt_version": PROMPT_VERSION,
        "llm_configured": settings.llm_configured,
        "mock_mode": settings.mock_mode,
        "rag": rag,
        "care": {"llm_enabled": settings.care_llm_enabled},
    }
