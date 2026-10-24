"""PromptSpec 注册表：任务、模板、温度与输出上限的唯一来源。"""
from dataclasses import dataclass

PROMPT_VERSION = "2026.10.4"

LUXUN_STYLES = {"luxun", "luxun-prose", "鲁迅", "鲁迅风格"}


@dataclass(frozen=True)
class PromptSpec:
    task: str
    system_template: str
    user_template: str
    temperature: float
    max_tokens: int


# 温度整体下调：除散文外都不允许自由发挥
_SPECS: dict[str, PromptSpec] = {
    "outline": PromptSpec("outline", "outline.system.j2", "outline.user.j2", 0.5, 2048),
    "draft": PromptSpec("draft", "draft.system.j2", "draft.user.j2", 0.5, 8192),
    "draft_section": PromptSpec(
        "draft_section", "draft_section.system.j2", "draft_section.user.j2", 0.5, 1600
    ),
    "continue": PromptSpec("continue", "continue.system.j2", "continue.user.j2", 0.6, 2048),
    "expand": PromptSpec("expand", "expand.system.j2", "expand.user.j2", 0.5, 8192),
    "condense": PromptSpec("condense", "condense.system.j2", "condense.user.j2", 0.2, 4096),
    "rewrite": PromptSpec("rewrite", "rewrite.system.j2", "rewrite.user.j2", 0.5, 8192),
    "polish": PromptSpec("polish", "polish.system.j2", "polish.user.j2", 0.2, 8192),
    "prose_luxun": PromptSpec(
        "prose", "prose.luxun.system.j2", "prose.user.j2", 0.7, 8192
    ),
    "prose_plain": PromptSpec(
        "prose", "prose.plain.system.j2", "prose.user.j2", 0.7, 8192
    ),
    "suggest": PromptSpec("suggest", "suggest.system.j2", "suggest.user.j2", 0.4, 1600),
}


def is_luxun_style(style: str | None) -> bool:
    return (style or "luxun").strip().lower() in LUXUN_STYLES


def get_spec(task: str, style: str | None = None) -> PromptSpec:
    if task == "prose":
        return _SPECS["prose_luxun" if is_luxun_style(style) else "prose_plain"]
    if task in _SPECS:
        return _SPECS[task]
    raise KeyError(f"未知任务: {task}")
