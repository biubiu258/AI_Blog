"""按 task 构造确定性执行计划；模型只按步骤填字。"""
import json
import logging
from dataclasses import dataclass, field

from app.core.config import Settings
from app.generation.context import (
    MAX_DRAFT_SECTIONS,
    SINGLE_LIMIT,
    chunk_markdown,
    parse_outline,
    tail_text,
)
from app.prompts.genre import decide_genre
from app.prompts.registry import get_spec, is_luxun_style
from app.prompts.renderer import build_messages
from app.prompts.styles import build_style_card, load_profile
from app.schemas.generate import GenerateRequest

log = logging.getLogger("ai-service.handlers")


@dataclass
class Step:
    messages: list[dict]
    temperature: float
    max_tokens: int
    prefix_text: str = ""


@dataclass
class Plan:
    task: str
    steps: list[Step]
    joiner: str = ""
    requires_h1: bool = False
    expected_title: str | None = None
    mode: str = "single"
    extra: dict = field(default_factory=dict)


def _base_vars(req: GenerateRequest) -> dict:
    """一次性给出全部模板变量，保证 StrictUndefined 下渲染稳定。"""
    p, c = req.params, req.context
    prefs = req.user_context.preferences
    return {
        "topic": (p.topic or "").strip(),
        "article_type": (p.article_type or "").strip(),
        "style": (p.style or "").strip(),
        "target_reader": (p.target_reader or "").strip(),
        "emotion": (p.emotion or "").strip(),
        "recent_state": (p.recent_state or "").strip(),
        "instruction": (c.instruction or "").strip(),
        "preferences": json.dumps(prefs, ensure_ascii=False) if prefs else "",
        "outline": c.outline or "",
        "material_text": "",
        "context_note": "",
        "chunked": False,
        "title": "",
        "section_title": "",
        "bullets": "",
        "previous_section": "",
        "next_section": "",
        "genre_label": "",
        "genre_rules": "",
        "length_text": "",
        "style_card": "",
        "image_note": "",
    }


def _single_step(spec, variables: dict) -> Step:
    return Step(build_messages(spec, variables), spec.temperature, spec.max_tokens)


def build_plan(req: GenerateRequest, settings: Settings,
               image_data_urls: list[str] | None = None) -> Plan:
    variables = _base_vars(req)
    task = req.task

    if task == "outline":
        spec = get_spec(task)
        return Plan(task, [_single_step(spec, variables)], requires_h1=True, mode="single")

    if task == "draft":
        return _build_draft_plan(req, variables)

    if task == "continue":
        spec = get_spec(task)
        variables["material_text"] = tail_text(req.context.original_text or "")
        variables["chunked"] = False
        return Plan(task, [_single_step(spec, variables)], mode="single")

    if task in ("expand", "condense", "rewrite", "polish"):
        return _build_transform_plan(req, variables)

    if task == "prose":
        return _build_prose_plan(req, settings, variables, image_data_urls or [])

    raise ValueError(f"未知任务: {task}")


def _build_draft_plan(req: GenerateRequest, variables: dict) -> Plan:
    outline_text = req.context.outline or ""
    parsed = parse_outline(outline_text)
    title = parsed.title or variables["topic"]

    sectioned = (
        parsed.usable
        and len(outline_text) > SINGLE_LIMIT
        and len(parsed.sections) <= MAX_DRAFT_SECTIONS
    )
    if not sectioned:
        spec = get_spec("draft")
        variables["outline"] = outline_text
        return Plan(
            "draft",
            [_single_step(spec, variables)],
            requires_h1=True,
            expected_title=title or None,
            mode="single",
        )

    spec = get_spec("draft_section")
    steps: list[Step] = []
    for i, section in enumerate(parsed.sections):
        bullets = "\n".join(
            b if b.startswith(("-", "*")) else f"- {b}" for b in section.bullets
        ) or "（本节无要点，按章节名自然展开）"
        step_vars = dict(variables)
        step_vars.update({
            "title": title,
            "section_title": section.title,
            "bullets": bullets,
            "previous_section": parsed.sections[i - 1].title if i > 0 else "",
            "next_section": (
                parsed.sections[i + 1].title if i + 1 < len(parsed.sections) else ""
            ),
        })
        prefix = ""
        if i == 0 and title:
            prefix += f"# {title}\n\n"
        prefix += f"## {section.title}\n\n"
        steps.append(Step(
            build_messages(spec, step_vars), spec.temperature, spec.max_tokens, prefix
        ))

    log.info("draft 分节生成 sections=%d title=%s", len(steps), title)
    return Plan("draft", steps, joiner="", requires_h1=False, mode="sections")


def _build_transform_plan(req: GenerateRequest, variables: dict) -> Plan:
    original = req.context.original_text or ""
    spec = get_spec(req.task)

    if len(original) > SINGLE_LIMIT:
        chunks = chunk_markdown(original)
        if len(chunks) > 1:
            steps: list[Step] = []
            for chunk in chunks:
                step_vars = dict(variables)
                step_vars.update({
                    "material_text": chunk.text,
                    "context_note": chunk.context,
                    "chunked": True,
                })
                steps.append(Step(
                    build_messages(spec, step_vars), spec.temperature, spec.max_tokens
                ))
            log.info("%s 分块生成 chunks=%d", req.task, len(steps))
            return Plan(req.task, steps, joiner="\n\n", mode="chunks")

    variables["material_text"] = original
    variables["chunked"] = False
    return Plan(req.task, [_single_step(spec, variables)], mode="single")


def _build_prose_plan(
    req: GenerateRequest,
    settings: Settings,
    variables: dict,
    image_data_urls: list[str],
) -> Plan:
    decision = decide_genre(req.params.emotion, req.params.recent_state)
    variables.update({
        "genre_label": decision.label,
        "genre_rules": decision.rules,
        "length_text": decision.length_text,
        "image_note": (
            f"本次附带 {len(image_data_urls)} 张图片，仅作物象与氛围来源。"
            if image_data_urls
            else "本次没有图片素材；从文字素材中的具体物象起笔。"
        ),
    })

    if is_luxun_style(req.params.style):
        profile = load_profile(settings.style_config_path)
        variables["style_card"] = build_style_card(profile, decision)
        log.info(
            "prose 文体=%s 依据=%s 命中=%s 风格卡字数=%d 图片=%d",
            decision.genre, decision.basis, "、".join(decision.hits) or "无",
            len(variables["style_card"]), len(image_data_urls),
        )

    spec = get_spec("prose", req.params.style)
    messages = build_messages(spec, variables)
    if image_data_urls:
        user_text = messages[1]["content"]
        messages[1] = {
            "role": "user",
            "content": [{"type": "text", "text": user_text}] + [
                {"type": "image_url", "image_url": {"url": url}} for url in image_data_urls
            ],
        }

    return Plan(
        "prose",
        [Step(messages, spec.temperature, spec.max_tokens)],
        requires_h1=True,
        mode="single",
        extra={"genre": decision.genre},
    )
