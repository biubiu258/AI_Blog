"""Jinja2 模板渲染；变量缺失直接报错，避免提示词悄悄少一块。"""
import logging
from pathlib import Path

from jinja2 import Environment, FileSystemLoader, StrictUndefined, TemplateNotFound

from app.prompts.registry import PromptSpec

log = logging.getLogger("ai-service.prompts")

TEMPLATE_DIR = Path(__file__).resolve().parent / "templates"

_env = Environment(
    loader=FileSystemLoader(str(TEMPLATE_DIR)),
    undefined=StrictUndefined,
    autoescape=False,
    trim_blocks=True,
    lstrip_blocks=True,
    keep_trailing_newline=False,
)


class PromptError(Exception):
    """模板缺失或渲染变量不全。"""


def render(template_name: str, variables: dict) -> str:
    try:
        template = _env.get_template(template_name)
    except TemplateNotFound as exc:
        raise PromptError(f"提示词模板不存在: {template_name}") from exc
    try:
        return template.render(**variables).strip()
    except Exception as exc:  # StrictUndefined 抛 UndefinedError
        raise PromptError(f"提示词模板渲染失败: {template_name}: {exc}") from exc


def build_messages(spec: PromptSpec, variables: dict) -> list[dict]:
    system = render(spec.system_template, variables)
    user = render(spec.user_template, variables)
    return [
        {"role": "system", "content": system},
        {"role": "user", "content": user},
    ]
