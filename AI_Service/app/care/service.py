"""关怀检测服务：规则引擎为准，模型复核可选且只能小幅上调。"""
import logging

from app.care.llm_review import review_level
from app.care.rules import SUMMARIES, evaluate, merge_with_model, prepare_text
from app.core.config import get_settings
from app.llm.base import LLMClient
from app.schemas.care import CareCheckRequest

log = logging.getLogger("ai-service.care")


class CareResult:
    __slots__ = ("level", "summary", "keywords")

    def __init__(self, level: str, summary: str | None, keywords: tuple[str, ...]) -> None:
        self.level = level
        self.summary = summary
        self.keywords = keywords


def none_result() -> CareResult:
    return CareResult("none", None, ())


async def check_care(req: CareCheckRequest, llm: LLMClient) -> CareResult:
    rule = evaluate(req.title, req.content)
    level = rule.level

    settings = get_settings()
    if settings.care_llm_enabled and level != "high":
        model_level = await review_level(prepare_text(req.title, req.content), llm)
        level = merge_with_model(level, model_level)

    if level == "none":
        return none_result()
    return CareResult(level, SUMMARIES[level], rule.keywords)
