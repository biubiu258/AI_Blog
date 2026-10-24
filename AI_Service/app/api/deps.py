"""内部鉴权与依赖注入。"""
import hmac

from fastapi import Header

from app.core.config import get_settings
from app.core.errors import AppError
from app.llm.base import LLMClient
from app.llm.openai_compat import OpenAICompatClient
from app.rag.service import RagService, get_rag_service as _get_rag_service


async def verify_internal_token(x_internal_token: str | None = Header(default=None)) -> None:
    """恒定时间比较；不一致 401 UNAUTHORIZED（契约 5.3）。"""
    expected = get_settings().internal_token
    if not x_internal_token or not hmac.compare_digest(
        x_internal_token.encode(), expected.encode()
    ):
        raise AppError("UNAUTHORIZED", "内部令牌缺失或不匹配", 401)


def get_llm_client() -> LLMClient:
    return OpenAICompatClient(get_settings())


def get_rag_service() -> RagService:
    return _get_rag_service()
