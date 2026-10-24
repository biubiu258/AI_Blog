"""人文关怀规则引擎：确定性分级，处理否定、假设与转述。"""
import re
import unicodedata
from dataclasses import dataclass

LEVELS = ("none", "low", "mid", "high")
LEVEL_ORDER = {level: i for i, level in enumerate(LEVELS)}

MAX_HEAD = 2000
MAX_TAIL = 800

# 先剔除“否定/玩笑/想念”等固定说法，避免关键词误报
_NEGATION_PATTERNS = [
    re.compile(r"(我)?(从来|从|并)?(没有|没)(想过|考虑过|打算过)?(去)?(自杀|轻生|自尽|伤害自己|自残)"),
    re.compile(r"(我)?(不会|不想|不打算|不准备|不再)(去)?(自杀|轻生|自尽|伤害自己|自残)"),
    re.compile(r"(我)?不是(真的)?(想死|想自杀|想轻生)"),
    re.compile(r"(我)?不想死"),
    re.compile(r"没有(自杀|轻生|自残|自尽)(的)?(想法|念头|计划|打算|倾向)?"),
    re.compile(r"(我)?(真的)?想死你(了)?"),  # “想死你了”是思念
    re.compile(r"只是(开玩笑|随便说说|说说而已)"),
]

# 假设自己的死亡/离开；只剔除该从句，不误伤其它真实表达
_HYPOTHETICAL = re.compile(
    r"(如果|假如|要是|倘若|万一|假设)[^。！？\n]{0,12}(我|自己)?[^。！？\n]{0,6}"
    r"(死|不在|离开这个世界|消失)"
)

HIGH_RULES: list[tuple[re.Pattern, str]] = [
    # 第一人称的明确意图；排除“想到死去的亲人”“死心/死角”“要死了”等口语
    (re.compile(r"(我|自己)[^。！？\n]{0,8}(想(?!到|起|念)|准备|打算|决定|不如)[^。！？\n]{0,6}"
                r"(自杀|轻生|自尽|去死|死(?![去的亡者人心板角磕尸]))"), "自杀意图"),
    (re.compile(r"(我|自己)[^。！？\n]{0,4}(要|快)[^。！？\n]{0,3}"
                r"死(?![去的亡者人心板角磕尸了])"), "自杀意图"),
    (re.compile(r"(真想|好想|真的想|特别想)(去)?死(?!你了|心|角|板|磕|尸|亡|者|人|了)"), "自杀意图"),
    (re.compile(r"(我|自己)[^。！？\n]{0,8}(割腕|跳楼|上吊|烧炭|服毒|自残)"), "自伤方式"),
    (re.compile(r"不想活(了|下去)?"), "不想活"),
    (re.compile(r"活不下去(了)?"), "活不下去"),
    (re.compile(r"死了算了|一了百了|不如死了"), "轻生念头"),
    (re.compile(r"(我|自己)[^。！？\n]{0,10}(写|留)(好|下|了)?(了)?遗书"), "遗书"),
    (re.compile(r"(我|自己)[^。！？\n]{0,8}结束(自己的)?生命"), "结束生命"),
    (re.compile(r"(我|自己)[^。！？\n]{0,8}离开这个世界"), "离开世界"),
    (re.compile(r"(我|自己)[^。！？\n]{0,8}再也(不想|不用)(醒|睁开眼)"), "不愿醒来"),
]

# 第三人物语境（悼文、他人故事）不判 high
_THIRD_PERSON = (
    "他", "她", "他们", "她们", "别人", "他人", "有人",
    "朋友", "父亲", "母亲", "外婆", "爷爷", "奶奶", "外公", "姥姥",
)

MID_RULES: list[tuple[re.Pattern, str]] = [
    (re.compile(r"撑不下去|撑不住|熬不下去|扛不住|受不了了"), "撑不住"),
    (re.compile(r"活着[^。！？\n]{0,4}(累|没意思|没有意义)"), "活着很累"),
    (re.compile(r"没有(人)?(需要|在乎|关心)我"), "无人需要"),
    (re.compile(r"我是(个)?(累赘|负担|废物)"), "自我否定"),
    (re.compile(r"毫无希望|看不到希望|没有希望|绝望|人生没有意义"), "绝望"),
    (re.compile(r"想(消失|逃走|躲起来)|不如(消失|不存在)"), "想消失"),
    (re.compile(r"抑郁|焦虑症|恐慌发作|精神崩溃"), "情绪困扰"),
    (re.compile(r"长期失眠|整夜睡不着|睡不着觉"), "长期失眠"),
    (re.compile(r"伤害自己"), "自伤念头"),
    (re.compile(r"自杀|轻生|自尽"), "提及自杀"),
]

LOW_RULES: list[tuple[re.Pattern, str]] = [
    (re.compile(r"(很|好|太|特别|非常|真的)累|疲惫"), "疲惫"),
    (re.compile(r"压力(很|好|特别|非常)?大"), "压力大"),
    (re.compile(r"难过|伤心|失落|委屈|烦躁|迷茫"), "低落"),
    (re.compile(r"孤独|空虚|麻木"), "孤独感"),
    (re.compile(r"失眠|睡不着|想哭|心情(不好|很差)|提不起(劲|精神)|没精神"), "情绪疲惫"),
]

SUMMARIES = {
    "high": "读到了很重的痛苦。请先保证自己的安全，联系身边可信的人，或拨打心理援助热线。",
    "mid": "检测到最近的情绪压力不小，愿意的话，找个信得过的人聊一聊。",
    "low": "检测到最近有些疲惫，记得给自己留一点休息的时间。",
    "none": None,
}


@dataclass(frozen=True)
class RuleResult:
    level: str
    keywords: tuple[str, ...]
    hits: dict[str, int]

    @property
    def summary(self) -> str | None:
        return SUMMARIES[self.level]


def prepare_text(title: str, content: str) -> str:
    """合并标题与正文，NFKC 归一、去多余空格，超长时保留头尾。"""
    raw = f"{title or ''}\n{content or ''}"
    text = unicodedata.normalize("NFKC", raw).replace("\r\n", "\n").replace("\r", "\n")
    text = re.sub(r"[ \t\u3000]+", "", text)
    if len(text) <= MAX_HEAD + MAX_TAIL:
        return text
    return text[:MAX_HEAD] + "\n……\n" + text[-MAX_TAIL:]


def evaluate(title: str, content: str) -> RuleResult:
    text = prepare_text(title, content)
    for pattern in _NEGATION_PATTERNS:
        text = pattern.sub("", text)
    text = _HYPOTHETICAL.sub("", text)

    high_hits = _collect(text, HIGH_RULES, skip_third_person=True)
    mid_hits = _collect(text, MID_RULES)
    low_hits = _collect(text, LOW_RULES)

    if high_hits:
        level = "high"
        hits = high_hits
    elif mid_hits:
        level = "mid"
        hits = mid_hits
    elif len(set(low_hits)) >= 2:
        level = "low"
        hits = low_hits
    else:
        level = "none"
        hits = {}

    keywords: list[str] = []
    for label in hits:
        if label not in keywords:
            keywords.append(label)
    return RuleResult(level=level, keywords=tuple(keywords[:5]), hits=hits)


def _collect(
    text: str,
    rules: list[tuple[re.Pattern, str]],
    *,
    skip_third_person: bool = False,
) -> dict[str, int]:
    hits: dict[str, int] = {}
    for pattern, label in rules:
        count = 0
        for match in pattern.finditer(text):
            if skip_third_person and _is_third_person_match(text, match):
                continue
            count += 1
        if count:
            hits[label] = hits.get(label, 0) + count
    return hits


def _is_third_person_match(text: str, match: re.Match) -> bool:
    """命中前文或命中范围内出现第三人称，说明叙述对象不是作者本人。

    例如“我的外婆离开这个世界”“我劝他不要跳楼”都不应判 high。
    """
    if _in_third_person_context(text, match.start()):
        return True
    span = match.group(0)
    # 去掉开头的主语“我/自己”，只看其后的叙述对象
    for prefix in ("我自己", "我", "自己"):
        if span.startswith(prefix):
            span = span[len(prefix):]
            break
    return any(token in span for token in _THIRD_PERSON)


def _in_third_person_context(text: str, start: int) -> bool:
    window = text[max(0, start - 6):start]
    return any(token in window for token in _THIRD_PERSON)


def merge_with_model(rule_level: str, model_level: str | None) -> str:
    """模型只能把等级上调到 mid，high 必须由确定性规则给出。"""
    if not model_level or model_level not in LEVEL_ORDER:
        return rule_level
    if rule_level == "high":
        return "high"
    capped = "mid" if LEVEL_ORDER[model_level] > LEVEL_ORDER["mid"] else model_level
    return capped if LEVEL_ORDER[capped] > LEVEL_ORDER[rule_level] else rule_level
