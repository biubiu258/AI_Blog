"""散文文体判定：关键词打分，Python 固定结果，模型不得改判。"""
from dataclasses import dataclass

GENRE_LABELS = {
    "memoir": "回忆性叙事散文",
    "elegy": "悼念性散文",
    "prose_poem": "散文诗（象征性抒情）",
}

GENRE_RULES = {
    "memoir": (
        "结构：现在—回忆—现在回环；以一个具体物件或场景贯穿全文；"
        "开篇落在当下的一个空镜头，结尾回到当下并附加新的判断。"
        "情绪：温情底色，克制，不煽情。"
    ),
    "elegy": (
        "结构：由具体的人事或物件切入，事实细节先行，议论紧贴事实；"
        "篇幅较长时可用“一、二、三”分节。"
        "情绪：悲愤与哀痛压在细节之下，不空泛颂扬，不堆叠呼告。"
    ),
    "prose_poem": (
        "结构：意象自足，独白体，短句收束；以一两个孤峭意象承载情绪，"
        "不解释象征，不给意象下定义。"
        "情绪：孤独沉郁，不直喊。"
    ),
}

GENRE_LENGTHS = {
    "memoir": (800, 1500),
    "elegy": (1000, 1800),
    "prose_poem": (400, 800),
}

ELEGY_WORDS = (
    "去世", "离世", "病逝", "故去", "永远离开", "永别", "葬礼", "追悼",
    "悼", "逝", "遗物", "生离死别",
)
PROSE_POEM_WORDS = (
    "孤独", "失眠", "深夜", "夜里", "绝望", "窒息", "压抑", "焦虑",
    "迷茫", "城市", "灯", "梦", "雨夜", "漂泊", "沉默", "疲惫",
)


@dataclass(frozen=True)
class GenreDecision:
    genre: str
    basis: str
    hits: tuple[str, ...]

    @property
    def label(self) -> str:
        return GENRE_LABELS[self.genre]

    @property
    def rules(self) -> str:
        return GENRE_RULES[self.genre]

    @property
    def length_text(self) -> str:
        low, high = GENRE_LENGTHS[self.genre]
        return f"{low}–{high}"


def decide_genre(emotion: str | None, recent_state: str | None) -> GenreDecision:
    """固定规则：悼念词优先 → 意象词 ≥2 → 默认回忆性叙事散文。"""
    text = f"{emotion or ''} {recent_state or ''}"
    elegy_hits = tuple(w for w in ELEGY_WORDS if w in text)
    if elegy_hits:
        return GenreDecision("elegy", "素材命中离别/悼念信号", elegy_hits)

    poem_hits = tuple(w for w in PROSE_POEM_WORDS if w in text)
    if len(poem_hits) >= 2:
        return GenreDecision(
            "prose_poem",
            f"素材以情绪与意象为主（命中 {len(poem_hits)} 个意象词）",
            poem_hits,
        )

    return GenreDecision("memoir", "未命中悼念或意象信号，按默认文体处理", ())
