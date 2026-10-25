"""POST /v1/search 与 /v1/retrieve 模型（契约 5.5）。"""
from pydantic import BaseModel, Field


class SearchRequest(BaseModel):
    query: str = Field(max_length=500)
    top_k: int = Field(default=10, ge=1, le=50)
    # Java 生成的调用追踪号，用于日志关联
    trace_id: str | None = None


class RetrieveRequest(BaseModel):
    query: str = Field(max_length=500)
    user_id: int = Field(gt=0)
    top_k: int = Field(default=5, ge=1, le=50)


class Hit(BaseModel):
    # 契约：article_id 为字符串
    article_id: str
    title: str = ""
    snippet: str = ""
    score: float | None = None


class SearchResponse(BaseModel):
    hits: list[Hit] = Field(default_factory=list)
