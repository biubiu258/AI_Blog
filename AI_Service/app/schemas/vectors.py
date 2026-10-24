"""`/v1/vectors/*` 模型（契约 5.5）。"""
from pydantic import BaseModel, Field


class VectorUpsertRequest(BaseModel):
    # 契约：article_id 为字符串
    article_id: str = Field(min_length=1, max_length=64)
    title: str = Field(default="", max_length=500)
    content: str = Field(default="", max_length=500_000)
