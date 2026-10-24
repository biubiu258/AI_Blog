"""上下文预算：尾部截取、Markdown 确定性分块、大纲解析。"""
import math
from dataclasses import dataclass, field

SINGLE_LIMIT = 12000      # 单次调用可承载的原文/大纲长度
CONTINUE_TAIL = 8000      # 续写只取原文末尾
MAX_CHUNKS = 10           # 长文最多分块数
TARGET_CHUNK = 4000       # 单块目标长度
OVERLAP = 300             # 衔接上下文长度
MAX_DRAFT_SECTIONS = 10   # 分节生成上限


@dataclass
class Section:
    title: str
    bullets: list[str] = field(default_factory=list)


@dataclass
class Outline:
    title: str
    sections: list[Section] = field(default_factory=list)

    @property
    def usable(self) -> bool:
        return bool(self.title) and len(self.sections) >= 2


@dataclass
class Chunk:
    text: str
    context: str = ""


def tail_text(text: str, limit: int = CONTINUE_TAIL) -> str:
    text = text or ""
    return text if len(text) <= limit else text[-limit:]


def parse_outline(text: str) -> Outline:
    """解析大纲：取第一个 `# ` 为标题，`## ` 为章节，列表行作要点。"""
    title = ""
    sections: list[Section] = []
    current: Section | None = None

    for raw in (text or "").splitlines():
        line = raw.rstrip()
        stripped = line.strip()
        if not stripped:
            continue
        if stripped.startswith("# ") and not stripped.startswith("## "):
            if not title:
                title = stripped[2:].strip()
            continue
        if stripped.startswith("## "):
            current = Section(stripped[3:].strip())
            sections.append(current)
            continue
        if current is not None:
            current.bullets.append(stripped)

    return Outline(title=title, sections=sections)


def _split_blocks(text: str) -> list[str]:
    """按标题与空行切块，不切断代码围栏。"""
    blocks: list[str] = []
    buf: list[str] = []
    in_fence = False

    def flush() -> None:
        if buf:
            blocks.append("\n".join(buf).strip("\n"))
            buf.clear()

    for line in text.splitlines():
        stripped = line.strip()
        if stripped.startswith("```"):
            in_fence = not in_fence
        is_heading = not in_fence and (stripped.startswith("# ") or stripped.startswith("## "))
        if is_heading and buf:
            flush()
        buf.append(line)
        if not in_fence and stripped == "":
            flush()
    flush()
    return [b for b in blocks if b.strip()]


def _split_oversized(block: str, target: int) -> list[str]:
    if len(block) <= target * 1.5:
        return [block]
    pieces: list[str] = []
    buf: list[str] = []
    size = 0
    for line in block.splitlines():
        if buf and size + len(line) > target:
            pieces.append("\n".join(buf))
            buf = []
            size = 0
        buf.append(line)
        size += len(line) + 1
    if buf:
        pieces.append("\n".join(buf))
    return pieces


def chunk_markdown(text: str, max_chunks: int = MAX_CHUNKS) -> list[Chunk]:
    """确定性切块：按标题/段落聚合，最多 max_chunks 块，附带上文衔接。"""
    text = (text or "").strip()
    if not text:
        return []

    total = len(text)
    target = max(TARGET_CHUNK, math.ceil(total / max_chunks))

    blocks: list[str] = []
    for block in _split_blocks(text):
        blocks.extend(_split_oversized(block, target))

    raw_chunks: list[str] = []
    current: list[str] = []
    size = 0
    for block in blocks:
        if current and size + len(block) + 1 > target:
            raw_chunks.append("\n\n".join(current))
            current, size = [block], len(block)
            continue
        current.append(block)
        size += len(block) + 2
    if current:
        raw_chunks.append("\n\n".join(current))

    if len(raw_chunks) > max_chunks:
        head = raw_chunks[:max_chunks - 1]
        raw_chunks = head + ["\n\n".join(raw_chunks[max_chunks - 1:])]

    chunks: list[Chunk] = []
    for i, body in enumerate(raw_chunks):
        context = raw_chunks[i - 1][-OVERLAP:] if i > 0 else ""
        chunks.append(Chunk(text=body, context=context))
    return chunks
