"""Skill 数据加载：manifest、SKILL.md frontmatter 与运行时片段，模块级缓存。"""
from functools import lru_cache
from pathlib import Path

import yaml

SKILLS_DIR = Path(__file__).resolve().parents[1] / "skills"


class FragmentError(Exception):
    """Skill 数据文件缺失或格式错误。"""


@lru_cache(maxsize=None)
def _resolve(relative_path: str) -> Path:
    path = (SKILLS_DIR / relative_path).resolve()
    if not path.is_file():
        raise FragmentError(f"Skill 文件不存在: {relative_path}")
    return path


@lru_cache(maxsize=None)
def load_text(relative_path: str) -> str:
    """读取 Skill 目录下的文本文件，去掉首尾空白。"""
    try:
        return _resolve(relative_path).read_text(encoding="utf-8").strip()
    except OSError as exc:
        raise FragmentError(f"Skill 文件无法读取: {relative_path}") from exc


@lru_cache(maxsize=None)
def load_yaml(relative_path: str) -> dict:
    """读取 Skill 目录下的 YAML 文件。"""
    try:
        data = yaml.safe_load(load_text(relative_path))
    except yaml.YAMLError as exc:
        raise FragmentError(f"Skill YAML 无法解析: {relative_path}") from exc
    if not isinstance(data, dict):
        raise FragmentError(f"Skill YAML 顶层必须是对象: {relative_path}")
    return data


@lru_cache(maxsize=None)
def load_frontmatter(relative_path: str) -> dict:
    """读取 Markdown 的 YAML frontmatter。"""
    text = load_text(relative_path)
    if not text.startswith("---"):
        raise FragmentError(f"缺少 frontmatter: {relative_path}")
    parts = text.split("---", 2)
    if len(parts) < 3:
        raise FragmentError(f"frontmatter 未闭合: {relative_path}")
    try:
        data = yaml.safe_load(parts[1]) or {}
    except yaml.YAMLError as exc:
        raise FragmentError(f"frontmatter 无法解析: {relative_path}") from exc
    if not isinstance(data, dict):
        raise FragmentError(f"frontmatter 必须是对象: {relative_path}")
    return data


def format_fragment(template: str, **values) -> str:
    """填充片段中的 {name} 占位符。"""
    return template.format(**values)
