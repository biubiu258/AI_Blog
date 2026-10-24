"""`/v1/generate` 请求模型与跨字段必填校验（契约 5.4.1 / 5.4.2）。"""
from typing import Any, Literal
from urllib.parse import urlparse

from pydantic import BaseModel, Field, model_validator

TaskName = Literal[
    "outline", "draft", "continue", "expand", "condense", "rewrite", "polish", "prose",
    "suggest",
]
SuggestionMode = Literal["advice", "polish", "rewrite", "expand", "condense"]

MAX_TOPIC = 200
MAX_INSTRUCTION = 2000
MAX_OUTLINE = 50000
MAX_ORIGINAL_TEXT = 100000
MAX_EMOTION = 200
MAX_RECENT_STATE = 1000
MAX_IMAGES = 5
MAX_SELECTED_TEXT = 2000
MAX_TEXT_CONTEXT = 300


class GenerateParams(BaseModel):
    topic: str | None = Field(default=None, max_length=MAX_TOPIC)
    article_type: str | None = None
    style: str | None = None
    target_reader: str | None = None
    # 散文任务专用
    emotion: str | None = Field(default=None, max_length=MAX_EMOTION)
    recent_state: str | None = Field(default=None, max_length=MAX_RECENT_STATE)
    # 划词建议专用；缺省按 advice 处理
    suggestion_mode: SuggestionMode | None = None


class GenerateContext(BaseModel):
    outline: str | None = Field(default=None, max_length=MAX_OUTLINE)
    original_text: str | None = Field(default=None, max_length=MAX_ORIGINAL_TEXT)
    instruction: str | None = Field(default=None, max_length=MAX_INSTRUCTION)
    # 划词建议专用：选中片段 + 前后文，避免模型脱离语境
    selected_text: str | None = Field(default=None, max_length=MAX_SELECTED_TEXT)
    text_before: str | None = Field(default=None, max_length=MAX_TEXT_CONTEXT)
    text_after: str | None = Field(default=None, max_length=MAX_TEXT_CONTEXT)
    # 散文任务 0-5 张绝对 URL（Java 已拼成 publicBaseUrl + /uploads/**）
    image_urls: list[str] = Field(default_factory=list)


class UserContext(BaseModel):
    preferences: dict[str, Any] | None = None
    history_style: list[Any] = Field(default_factory=list)


def _has_text(value: str | None) -> bool:
    return bool(value and value.strip())


class GenerateRequest(BaseModel):
    user_id: int = Field(gt=0)
    task: TaskName
    params: GenerateParams = Field(default_factory=GenerateParams)
    context: GenerateContext = Field(default_factory=GenerateContext)
    user_context: UserContext = Field(default_factory=UserContext)

    @model_validator(mode="after")
    def check_required(self) -> "GenerateRequest":
        p, c = self.params, self.context

        if self.task == "outline" and not _has_text(p.topic):
            raise ValueError("outline 任务需要 params.topic")
        if self.task == "draft" and not _has_text(c.outline):
            raise ValueError("draft 任务需要 context.outline")
        if self.task in ("continue", "expand", "condense", "rewrite", "polish") \
                and not _has_text(c.original_text):
            raise ValueError(f"{self.task} 任务需要 context.original_text")
        if self.task == "prose":
            if len(c.image_urls) > MAX_IMAGES:
                raise ValueError(f"prose 任务 image_urls 最多 {MAX_IMAGES} 张")
            if not (_has_text(p.emotion) or _has_text(p.recent_state)):
                raise ValueError("prose 任务 params.emotion 与 params.recent_state 至少填其一")
        if self.task == "suggest" and not _has_text(c.selected_text):
            raise ValueError("suggest 任务需要 context.selected_text")
        for url in c.image_urls:
            scheme = urlparse(url).scheme.lower()
            if scheme not in ("http", "https", "data"):
                raise ValueError("image_urls 只接受绝对 http/https 地址")
        return self


class GenerateDone(BaseModel):
    content: str
    usage: dict[str, int] | None = None
