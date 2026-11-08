"""`/v1/style-profile` 请求与响应模型（个人文风提炼）。"""
from typing import Any

from pydantic import BaseModel, Field

MAX_SAMPLES = 10
MIN_SAMPLES = 3
MAX_SAMPLE_TEXT = 20000
MAX_SAMPLE_TITLE = 120
MAX_SUMMARY = 200

FEATURE_KEYS = ("tone", "diction", "sentence", "imagery", "themes", "structure", "avoid")


class StyleSample(BaseModel):
    title: str | None = Field(default=None, max_length=MAX_SAMPLE_TITLE)
    text: str = Field(min_length=1, max_length=MAX_SAMPLE_TEXT)


class StyleProfileRequest(BaseModel):
    user_id: int = Field(gt=0)
    # Java 生成的调用追踪号，用于与 ai_output 审计记录对齐
    trace_id: str | None = None
    samples: list[StyleSample] = Field(min_length=MIN_SAMPLES, max_length=MAX_SAMPLES)


class StyleProfileResponse(BaseModel):
    summary: str
    features: dict[str, Any]
    usage: dict[str, int] | None = None
    model: str | None = None
    prompt_version: str | None = None
