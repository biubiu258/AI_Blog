"""内部鉴权依赖：请求头 X-Internal-Token 用恒定时间比较校验（契约 5.3）。"""
import hmac

from fastapi import Header, HTTPException

from config import INTERNAL_TOKEN

_UNAUTHORIZED = {
    "error": {"code": "UNAUTHORIZED", "message": "内部令牌缺失或不匹配"}
}


async def verify_internal_token(x_internal_token: str | None = Header(default=None)) -> None:
    if not x_internal_token or not hmac.compare_digest(
            x_internal_token.encode(), INTERNAL_TOKEN.encode()):
        raise HTTPException(status_code=401, detail=_UNAUTHORIZED)
