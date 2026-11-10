"""`/v1/self-insight` 请求与响应模型（AI 洞察式回顾）。"""
from pydantic import BaseModel, Field

MAX_ARTICLES = 20
MIN_ARTICLES = 1
MAX_ARTICLE_TEXT = 20000
MAX_ARTICLE_TITLE = 120
MAX_SUMMARY = 120
MAX_ITEM = 60
MAX_ITEMS = 6

DISCLAIMER = "以上仅为回顾，不构成写作建议"


class InsightArticle(BaseModel):
    title: str | None = Field(default=None, max_length=MAX_ARTICLE_TITLE)
    # Java 已去除 HTML 标签并截断到 2000 字，这里放宽上限做兜底
    content: str = Field(min_length=1, max_length=MAX_ARTICLE_TEXT)


class SelfInsightRequest(BaseModel):
    user_id: int = Field(gt=0)
    # Java 生成的调用追踪号，用于日志关联
    trace_id: str | None = None
    articles: list[InsightArticle] = Field(
        min_length=MIN_ARTICLES, max_length=MAX_ARTICLES
    )
    locale: str | None = Field(default=None, max_length=16)


class SelfInsightResponse(BaseModel):
    summary: str
    themes: list[str]
    motifs: list[str]
    habits: list[str]
    disclaimer: str = DISCLAIMER
    usage: dict[str, int] | None = None
    model: str | None = None
    prompt_version: str | None = None
