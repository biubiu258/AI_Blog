"""输出守卫：前缀缓冲、前言/围栏剥离、标题校验、尾部元评论裁剪。"""
import re

# 只匹配独立的寒暄行（整行就是一句客套）
_SMALLTALK_RE = re.compile(r"^(好的|好|收到|当然|当然可以|没问题|可以)[，。：:！!]?$")
# 匹配常见的开头前言；仍需行长 ≤ 40 才剥离
_PREAMBLE_RE = re.compile(
    r"^(好的[，,：:]?|以下是|下面是|这是|根据(你|您)的?要求|按照(你|您)的?要求"
    r"|已为你|已为您|收到[，,]|明白[，,]?)"
)
# 末尾元评论
_META_RE = re.compile(
    r"^(以上(是|为|就是|即)|希望(这|以上|本次|这些)|如需|如果需要|如果你需要"
    r"|如需帮助|你可以|（完）|\(完\)|全文完)"
)
_FENCE_RE = re.compile(r"^```[A-Za-z0-9_+-]*$")


class TextGuard:
    """按块释放文本：先清洗前缀，再边流边留尾部缓冲。"""

    def __init__(
        self,
        *,
        requires_h1: bool = False,
        expected_title: str | None = None,
        prefix_limit: int = 200,
        tail_keep: int = 120,
    ) -> None:
        self.requires_h1 = requires_h1
        self.expected_title = (expected_title or "").strip() or None
        self.prefix_limit = prefix_limit
        self.tail_keep = tail_keep
        self.warnings: list[str] = []
        self._buffer = ""
        self._released = False
        self._blocked = False
        self._parts: list[str] = []

    @property
    def blocked(self) -> bool:
        return self._blocked

    @property
    def emitted_text(self) -> str:
        return "".join(self._parts)

    def feed(self, chunk: str) -> str:
        if not chunk:
            return ""
        if self._blocked:
            # 已判定标题不合规：继续缓冲，等调用方决定重试或强制收尾
            self._buffer += chunk
            return ""

        if not self._released:
            self._buffer += chunk
            if len(self._buffer) < self.prefix_limit:
                return ""
            text, ok = self._clean_prefix(self._buffer)
            self._released = True
            if not ok:
                self._blocked = True
                return ""
            self._buffer = ""
            return self._emit(text)

        self._buffer += chunk
        if len(self._buffer) > self.tail_keep:
            out = self._buffer[:-self.tail_keep]
            self._buffer = self._buffer[-self.tail_keep:]
            return self._emit(out)
        return ""

    def finish(self, force: bool = False) -> str:
        if not self._released:
            text, ok = self._clean_prefix(self._buffer)
            if not ok and not force:
                self.warnings.append("首个标题不符合约定")
            self._buffer = ""
            self._released = True
            self._blocked = False
            return self._emit(_clean_tail(text))

        text = _clean_tail(self._buffer)
        self._buffer = ""
        return self._emit(text)

    def _emit(self, text: str) -> str:
        if not text:
            return ""
        self._parts.append(text)
        return text

    def _clean_prefix(self, raw: str) -> tuple[str, bool]:
        text = raw.lstrip("\r\n")
        lines = text.split("\n")

        # 去掉整篇代码围栏的开头行
        while lines and (not lines[0].strip() or _FENCE_RE.match(lines[0].strip())):
            if lines[0].strip() and _FENCE_RE.match(lines[0].strip()):
                lines.pop(0)
                continue
            if not lines[0].strip():
                lines.pop(0)
                continue
            break

        # 去掉寒暄/前言行，最多 3 行
        for _ in range(3):
            while lines and not lines[0].strip():
                lines.pop(0)
            if not lines:
                break
            first = lines[0].strip()
            if _SMALLTALK_RE.match(first) or (
                len(first) <= 40 and _PREAMBLE_RE.match(first)
            ):
                lines.pop(0)
                continue
            break

        text = "\n".join(lines)
        ok = True
        if self.requires_h1:
            first_line = next((l.strip() for l in lines if l.strip()), "")
            if not first_line.startswith("# ") or first_line.startswith("## "):
                ok = False
            elif self.expected_title and first_line[2:].strip() != self.expected_title:
                ok = False
        return text, ok


def _clean_tail(text: str) -> str:
    text = text.rstrip()
    if not text:
        return ""

    lines = text.split("\n")
    while lines and (not lines[-1].strip() or _FENCE_RE.match(lines[-1].strip())):
        if lines[-1].strip() and _FENCE_RE.match(lines[-1].strip()):
            lines.pop()
            continue
        if not lines[-1].strip():
            lines.pop()
            continue
        break

    for i in range(len(lines) - 1, max(len(lines) - 10, -1), -1):
        if _META_RE.match(lines[i].strip()):
            lines = lines[:i]
            break

    return "\n".join(lines).rstrip()
