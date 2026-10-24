"""Skill 目录规范：manifest 完整、SKILL.md 合法、模板与片段存在。"""
import pytest

from app.prompts.fragments import SKILLS_DIR, load_frontmatter, load_yaml
from app.prompts.registry import PROMPT_VERSION, get_spec

EXPECTED_TASKS = {
    "outline", "draft", "draft_section", "continue", "expand", "condense",
    "rewrite", "polish", "prose", "suggest", "review", "care",
}


def test_manifest_covers_all_tasks():
    manifest = load_yaml("manifest.yaml")
    assert set(manifest["tasks"]) == EXPECTED_TASKS
    assert len(PROMPT_VERSION.split(".")) == 3


def test_every_skill_has_valid_manifest():
    skills = [
        path for path in SKILLS_DIR.iterdir()
        if path.is_dir() and not path.name.startswith("_")
    ]
    assert len(skills) == 13
    for skill in skills:
        meta = load_frontmatter(f"{skill.name}/SKILL.md")
        assert meta["name"] == skill.name
        assert meta["description"]
        assert len(meta["description"]) <= 200


@pytest.mark.parametrize("task,style", [
    ("outline", None), ("draft", None), ("draft_section", None),
    ("continue", None), ("expand", None), ("condense", None),
    ("rewrite", None), ("polish", None),
    ("prose", "luxun"), ("prose", "plain"),
    ("suggest", None), ("review", None), ("care", None),
])
def test_spec_files_exist(task, style):
    spec = get_spec(task, style)
    assert (SKILLS_DIR / spec.system_template).is_file()
    assert (SKILLS_DIR / spec.user_template).is_file()
    assert spec.max_tokens > 0


@pytest.mark.parametrize("relative", [
    "_shared/constraints.md",
    "_shared/prose.user.md",
    "_fragments/genres.yaml",
    "_fragments/suggest-modes.yaml",
    "_fragments/prose-notes.yaml",
    "_fragments/placeholders.yaml",
    "_fragments/style-card.yaml",
    "_fragments/genre-rules/memoir.md",
    "_fragments/genre-rules/elegy.md",
    "_fragments/genre-rules/prose-poem.md",
])
def test_fragment_files_exist(relative):
    assert (SKILLS_DIR / relative).is_file()


def test_legacy_template_dir_is_removed():
    assert not (SKILLS_DIR.parent / "prompts" / "templates").exists()
