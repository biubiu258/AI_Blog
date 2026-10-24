"""鲁迅风格档案的确定性裁剪：按文体取条目、控预算、绝不注入 evidence 原文。"""
import json
import logging
from pathlib import Path

from app.prompts.genre import GenreDecision

log = logging.getLogger("ai-service.prompts.styles")

# 风格卡总量上限（中文字符数）
STYLE_CARD_BUDGET = 2500

GENRE_KEYWORDS: dict[str, tuple[str, ...]] = {
    "memoir": ("回忆", "童年", "故人", "旧事", "朝花夕拾", "温情", "长辈"),
    "elegy": ("悼", "亡", "悲愤", "哀痛", "纪念", "告别", "逝", "牺牲", "故人", "长辈"),
    "prose_poem": ("散文诗", "独白", "象征", "意象", "孤独", "沉郁", "抒情"),
}

# 换算规则与文体的关联较弱，用通用词兜底
ADAPTATION_COMMON = ("细节", "短句", "克制", "情感", "回忆")


class StyleProfileError(Exception):
    """风格档案缺失或格式错误。"""


_profile_cache: dict[str, tuple[float, dict]] = {}


def load_profile(path: str) -> dict:
    """按 mtime 缓存的档案读取；缺失或损坏直接报错，不用默认规则顶替。"""
    file = Path(path)
    if not file.exists():
        raise StyleProfileError(f"风格档案不存在: {path}")
    mtime = file.stat().st_mtime
    cached = _profile_cache.get(str(file))
    if cached and cached[0] == mtime:
        return cached[1]
    try:
        data = json.loads(file.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise StyleProfileError(f"风格档案无法解析: {path}") from exc
    _profile_cache[str(file)] = (mtime, data)
    return data


def _clip(text: str, limit: int) -> str:
    text = (text or "").strip()
    if len(text) <= limit:
        return text
    return text[:limit].rstrip("，、；,;：: ") + "…"


def _item_text(item: dict) -> str:
    return " ".join(
        str(item.get(k) or "")
        for k in ("description", "applicable_conditions", "condition", "origin_feature",
                  "modern_application", "feature")
    )


def _select(items: list[dict], keywords: tuple[str, ...], limit: int) -> list[dict]:
    matched = [i for i in items if any(k in _item_text(i) for k in keywords)]
    return (matched or items)[:limit]


def _rule_line(item: dict, width: int = 260) -> str:
    desc = _clip(item.get("description") or "", width)
    rules = _clip(item.get("writing_rules") or "", width)
    if not desc:
        return f"- {rules}" if rules else ""
    if not rules:
        return f"- {desc}"
    return f"- {desc}｜写法：{rules}"


def _adaptation_line(item: dict, width: int = 200) -> str:
    origin = _clip(item.get("origin_feature") or "", width)
    modern = _clip(item.get("modern_application") or "", width)
    return f"- {origin} → {modern}" if origin else ""


def _fit_lines(lines: list[str], limit: int) -> str:
    """按整行裁剪，避免把句子截断在半途。"""
    kept: list[str] = []
    used = 0
    for line in lines:
        if not line:
            continue
        if used + len(line) + 1 > limit:
            break
        kept.append(line)
        used += len(line) + 1
    return "\n".join(kept)


def build_style_card(profile: dict, decision: GenreDecision,
                     budget: int = STYLE_CARD_BUDGET) -> str:
    """按“恒定特征 > 当前文体模块 > 用词句法 > 换算 > 条件特征 > 禁止项”装配。"""
    keywords = GENRE_KEYWORDS[decision.genre]
    blocks: list[tuple[str, str]] = []

    # 禁止项永远全量保留，先占预算
    avoid_lines = [
        f"- {i.get('pattern', '')}：{i.get('reason', '')}"
        for i in (profile.get("avoid_patterns") or [])
    ]
    avoid_text = "\n".join(avoid_lines)
    remaining = budget - len("绝对禁止") - len(avoid_text) - 4

    def add(title: str, body: str) -> None:
        nonlocal remaining
        if not body:
            return
        cost = len(title) + len(body) + 4
        if cost > remaining:
            return
        blocks.append((title, body))
        remaining -= cost

    summary = _clip(profile.get("summary") or "", 200)
    add("整体基调", summary)

    stable_lines = [f"- {f.get('feature', '')}" for f in (profile.get("stable_features") or [])][:7]
    add("恒定特征", _fit_lines(stable_lines, max(remaining - 20, 0)))

    genre_lines: list[str] = []
    for key, title in (("narrative", "叙事"), ("structure", "结构"),
                       ("emotion", "情感"), ("ending", "结尾")):
        for item in _select(profile.get(key) or [], keywords, 2):
            line = _rule_line(item)
            if line:
                genre_lines.append(f"【{title}】{line[2:]}")
    add(f"当前文体模块（{decision.label}）", _fit_lines(genre_lines, max(remaining - 40, 0)))

    lang_lines = [_rule_line(i) for i in (profile.get("language") or [])]
    add("用词", _fit_lines(lang_lines, max(remaining - 20, 0)))

    syntax_lines = [_rule_line(i) for i in (profile.get("syntax") or [])]
    add("句法", _fit_lines(syntax_lines, max(remaining - 20, 0)))

    adaptation_items = _select(
        profile.get("adaptation_rules") or [], keywords + ADAPTATION_COMMON, 3
    )
    add("现代题材换算", _fit_lines(
        [_adaptation_line(i) for i in adaptation_items], max(remaining - 30, 0)
    ))

    conditional_items = _select(profile.get("conditional_features") or [], keywords, 2)
    conditional_lines = [
        f"- {i.get('feature', '')}：{_clip(i.get('condition') or '', 120)}"
        for i in conditional_items
    ]
    add("条件特征（仅在与本次文体相符时使用）",
        _fit_lines(conditional_lines, max(remaining - 50, 0)))

    blocks.append(("绝对禁止", avoid_text))

    rendered = [f"【{title}】\n{body}" for title, body in blocks if body]
    return "\n\n".join(rendered)


def find_verbatim_overlap(text: str, profile: dict, min_len: int = 12) -> str | None:
    """检查输出是否成句复用了档案引文；命中返回片段，仅用于告警。"""
    normalized = " ".join(text.split())
    for group in ("language", "syntax", "narrative", "structure", "rhetoric", "emotion"):
        for item in profile.get(group) or []:
            evidence = item.get("evidence") or ""
            for quote in _extract_quotes(evidence):
                if len(quote) >= min_len and quote in normalized:
                    return quote
    return None


def _extract_quotes(evidence: str) -> list[str]:
    quotes: list[str] = []
    for left, right in (("“", "”"), ("\"", "\"")):
        start = 0
        while True:
            i = evidence.find(left, start)
            if i < 0:
                break
            j = evidence.find(right, i + 1)
            if j < 0:
                break
            quotes.append(evidence[i + 1:j].strip())
            start = j + 1
    return quotes
