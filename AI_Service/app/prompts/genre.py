"""散文文体判定：关键词打分，Python 固定结果，模型不得改判。"""
from dataclasses import dataclass

from app.prompts.fragments import load_text, load_yaml

_GENRES = load_yaml("_fragments/genres.yaml")["genres"]


@dataclass(frozen=True)
class GenreDecision:
    genre: str
    basis: str
    hits: tuple[str, ...]

    @property
    def label(self) -> str:
        return _GENRES[self.genre]["label"]

    @property
    def rules(self) -> str:
        return load_text(f"_fragments/{_GENRES[self.genre]['rules']}")

    @property
    def length_text(self) -> str:
        low, high = _GENRES[self.genre]["length"]
        return f"{low}–{high}"


def decide_genre(emotion: str | None, recent_state: str | None) -> GenreDecision:
    """固定规则：悼念词优先 → 意象词 ≥2 → 默认回忆性叙事散文。"""
    text = f"{emotion or ''} {recent_state or ''}"
    elegy_words = _GENRES["elegy"]["classifier_keywords"]
    elegy_hits = tuple(w for w in elegy_words if w in text)
    if elegy_hits:
        return GenreDecision("elegy", "素材命中离别/悼念信号", elegy_hits)

    poem_words = _GENRES["prose_poem"]["classifier_keywords"]
    poem_hits = tuple(w for w in poem_words if w in text)
    if len(poem_hits) >= 2:
        return GenreDecision(
            "prose_poem",
            f"素材以情绪与意象为主（命中 {len(poem_hits)} 个意象词）",
            poem_hits,
        )

    return GenreDecision("memoir", "未命中悼念或意象信号，按默认文体处理", ())
