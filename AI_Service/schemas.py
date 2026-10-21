"""请求/响应模型与跨字段必填校验（契约 5.4.1 / 5.4.2 / 5.5 / 5.10）。

命名约定：Java → Python 的 /v1 接口字段为 snake_case，模型字段名即契约字段名。
校验失败发生在流式开始之前，由全局异常处理器映射为 HTTP 400 INVALID_PARAMS。
"""
from typing import Any, Literal

from pydantic import BaseModel, Field, model_validator

TaskName = Literal[
    "outline", "draft", "continue", "expand", "condense", "rewrite", "polish", "prose"
]

MAX_TOPIC = 200
MAX_INSTRUCTION = 2000
MAX_OUTLINE = 50000
MAX_ORIGINAL_TEXT = 100000
MAX_EMOTION = 200
MAX_RECENT_STATE = 1000
MAX_IMAGES = 5


# ---------- /v1/generate ----------

class GenerateParams(BaseModel):
    topic: str | None = Field(default=None, max_length=MAX_TOPIC)
    article_type: str | None = None
    style: str | None = None
    target_reader: str | None = None
    # 散文任务专用
    emotion: str | None = Field(default=None, max_length=MAX_EMOTION)
    recent_state: str | None = Field(default=None, max_length=MAX_RECENT_STATE)


class GenerateContext(BaseModel):
    outline: str | None = Field(default=None, max_length=MAX_OUTLINE)
    original_text: str | None = Field(default=None, max_length=MAX_ORIGINAL_TEXT)
    instruction: str | None = Field(default=None, max_length=MAX_INSTRUCTION)
    # 散文任务 1-5 张绝对 URL（Java 已拼成 publicBaseUrl + /uploads/**）
    image_urls: list[str] = Field(default_factory=list)


class UserContext(BaseModel):
    # P0 可选；未配置时 Java 传 null
    preferences: dict[str, Any] | None = None
    # P0 恒为 []（RAG 召回属 P1，Python 在 /v1/generate 内部自行处理）
    history_style: list[Any] = Field(default_factory=list)


class GenerateRequest(BaseModel):
    user_id: int = Field(gt=0)
    task: TaskName
    params: GenerateParams = Field(default_factory=GenerateParams)
    context: GenerateContext = Field(default_factory=GenerateContext)
    user_context: UserContext = Field(default_factory=UserContext)

    @model_validator(mode="after")
    def check_required(self) -> "GenerateRequest":
        """契约 5.4.2 的跨字段必填表。"""
        p, c = self.params, self.context

        def has(s: str | None) -> bool:
            return bool(s and s.strip())

        if self.task == "outline" and not has(p.topic):
            raise ValueError("outline 任务需要 params.topic")
        if self.task == "draft" and not has(c.outline):
            raise ValueError("draft 任务需要 context.outline")
        if self.task in ("continue", "expand", "condense", "rewrite", "polish") \
                and not has(c.original_text):
            raise ValueError(f"{self.task} 任务需要 context.original_text")
        if self.task == "prose":
            if len(c.image_urls) > MAX_IMAGES:
                raise ValueError(f"prose 任务 image_urls 最多 {MAX_IMAGES} 张")
            if not (has(p.emotion) or has(p.recent_state)):
                raise ValueError("prose 任务 params.emotion 与 params.recent_state 至少填其一")
        return self


# ---------- /v1/care/check（契约 5.10） ----------

class CareCheckRequest(BaseModel):
    user_id: int = Field(gt=0)
    title: str = ""
    content: str = ""


class CareCheckResponse(BaseModel):
    level: Literal["none", "low", "mid", "high"] = "none"
    summary: str | None = None
    keywords: list[str] | None = None


# ---------- /v1/search 与 /v1/retrieve（契约 5.5） ----------

class SearchRequest(BaseModel):
    query: str
    top_k: int = Field(default=10, ge=1, le=50)


class RetrieveRequest(BaseModel):
    query: str
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


# ---------- /v1/vectors/* ----------

class VectorUpsertRequest(BaseModel):
    # 契约：article_id 为字符串
    article_id: str
    title: str = ""
    content: str = ""
