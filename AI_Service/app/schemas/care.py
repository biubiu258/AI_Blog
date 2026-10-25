"""`/v1/care/check` 请求响应模型（契约 5.10）。"""
from typing import Literal

from pydantic import BaseModel, Field


class CareCheckRequest(BaseModel):
    user_id: int = Field(gt=0)
    # Java 生成的调用追踪号，用于日志关联
    trace_id: str | None = None
    title: str = ""
    content: str = ""


class CareCheckResponse(BaseModel):
    level: Literal["none", "low", "mid", "high"] = "none"
    summary: str | None = None
    keywords: list[str] | None = None
