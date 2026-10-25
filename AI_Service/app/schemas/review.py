"""`/v1/review` 请求与响应模型：结构化局部建议。"""
from typing import Literal

from pydantic import BaseModel, Field

MAX_REVIEW_CONTENT = 20000
MAX_REVIEW_ITEMS = 6
MAX_QUOTE = 120
MAX_REASON = 500
MAX_REPLACEMENT = 2000

Severity = Literal["low", "mid", "high"]


class ReviewRequest(BaseModel):
    user_id: int = Field(gt=0)
    # Java 生成的调用追踪号，用于与 ai_output 审计记录对齐
    trace_id: str | None = None
    title: str | None = Field(default=None, max_length=200)
    content: str = Field(min_length=1, max_length=MAX_REVIEW_CONTENT)
    article_type: str | None = Field(default=None, max_length=100)
    style: str | None = Field(default=None, max_length=200)


class ReviewSuggestion(BaseModel):
    type: str = Field(default="expression", max_length=24)
    severity: Severity = "mid"
    quote: str = Field(min_length=1, max_length=MAX_QUOTE)
    reason: str = Field(min_length=1, max_length=MAX_REASON)
    replacement: str | None = Field(default=None, max_length=MAX_REPLACEMENT)


class ReviewResponse(BaseModel):
    suggestions: list[ReviewSuggestion] = Field(default_factory=list)
    usage: dict[str, int] | None = None
    # 模型元数据，写入 Java 侧审计记录
    model: str | None = None
    prompt_version: str | None = None
