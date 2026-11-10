"""PromptSpec 注册表：任务、Skill、模板、温度与输出上限的唯一来源。"""
from dataclasses import dataclass

from app.prompts.fragments import FragmentError, SKILLS_DIR, load_frontmatter, load_yaml

LUXUN_STYLES = {"luxun", "luxun-prose", "鲁迅", "鲁迅风格"}


@dataclass(frozen=True)
class PromptSpec:
    task: str
    system_template: str
    user_template: str
    temperature: float
    max_tokens: int


def _load_manifest() -> dict:
    manifest = load_yaml("manifest.yaml")
    _validate_manifest(manifest)
    return manifest


def _validate_entry(task: str, entry: dict) -> None:
    skill = entry.get("skill")
    if not skill:
        raise FragmentError(f"任务 {task} 缺少 skill")
    system = entry.get("system") or f"{skill}/system.md"
    user = entry.get("user") or f"{skill}/user.md"
    for relative in (system, user):
        if not (SKILLS_DIR / relative).is_file():
            raise FragmentError(f"任务 {task} 的模板不存在: {relative}")
    meta = load_frontmatter(f"{skill}/SKILL.md")
    if meta.get("name") != skill:
        raise FragmentError(f"Skill 目录与 name 不一致: {skill}")
    if not meta.get("description"):
        raise FragmentError(f"Skill 缺少 description: {skill}")


def _validate_manifest(manifest: dict) -> None:
    tasks = manifest.get("tasks")
    if not isinstance(tasks, dict) or not tasks:
        raise FragmentError("manifest.yaml 缺少 tasks")
    for task, entry in tasks.items():
        if not isinstance(entry, dict):
            raise FragmentError(f"任务 {task} 配置必须是对象")
        variants = entry.get("variants")
        if variants is None:
            _validate_entry(task, entry)
            continue
        if not isinstance(variants, dict) or not variants:
            raise FragmentError(f"任务 {task} 的 variants 不能为空")
        for name, variant in variants.items():
            _validate_entry(f"{task}.{name}", variant)


_MANIFEST = _load_manifest()

PROMPT_VERSION = str(_MANIFEST.get("version", ""))


def _to_spec(task: str, entry: dict) -> PromptSpec:
    skill = entry["skill"]
    return PromptSpec(
        task=task,
        system_template=entry.get("system") or f"{skill}/system.md",
        user_template=entry.get("user") or f"{skill}/user.md",
        temperature=float(entry["temperature"]),
        max_tokens=int(entry["max_tokens"]),
    )


def is_luxun_style(style: str | None) -> bool:
    return (style or "luxun").strip().lower() in LUXUN_STYLES


# 公版作家风格矩阵：variants 键 → 命中关键词（全部为已故公版作家）
WRITER_STYLE_VARIANTS: dict[str, set[str]] = {
    "luxun": LUXUN_STYLES,
    "liangshiqiu": {"liangshiqiu", "liang-shiqiu", "梁实秋", "梁实秋风格"},
    "wangzengqi": {"wangzengqi", "wang-zengqi", "汪曾祺", "汪曾祺风格"},
}


def writer_variant_key(style: str | None) -> str:
    """把 style 参数映射到 manifest prose.variants 的键；未命中回落 plain。"""
    value = (style or "").strip().lower()
    for key, names in WRITER_STYLE_VARIANTS.items():
        if value in names:
            return key
    return "plain"


def get_spec(task: str, style: str | None = None) -> PromptSpec:
    entry = _MANIFEST["tasks"].get(task)
    if entry is None:
        raise KeyError(f"未知任务: {task}")
    if "variants" in entry:
        key = writer_variant_key(style)
        if key not in entry["variants"]:
            key = "plain"
        entry = entry["variants"][key]
    return _to_spec(task, entry)
