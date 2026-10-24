"""文章切块：确定性、可重放，不依赖模型或网络。"""
import re

MAX_CHUNK_CHARS = 480
CHUNK_OVERLAP = 80
MAX_CHUNKS = 40
SNIPPET_LEN = 160

_SENTENCE_END = re.compile(r"(?<=[。！？；!?;])")
_PARAGRAPH_SPLIT = re.compile(r"\n\s*\n+")


def normalize_text(text: str) -> str:
    value = (text or "").replace("\r\n", "\n").replace("\r", "\n")
    value = re.sub(r"[ \t\u3000]+", " ", value)
    value = re.sub(r"\n{3,}", "\n\n", value)
    return value.strip()


def build_chunks(content: str) -> list[str]:
    """按段落/句子聚合到约 480 字，块间保留 80 字重叠。"""
    text = normalize_text(content)
    if not text:
        return []

    units: list[str] = []
    for paragraph in _PARAGRAPH_SPLIT.split(text):
        paragraph = paragraph.strip()
        if not paragraph:
            continue
        for sentence in _SENTENCE_END.split(paragraph):
            sentence = sentence.strip()
            if not sentence:
                continue
            if len(sentence) <= MAX_CHUNK_CHARS:
                units.append(sentence)
                continue
            # 单句仍超长时按固定窗口硬切，保证每块不超过上限
            for i in range(0, len(sentence), MAX_CHUNK_CHARS):
                piece = sentence[i:i + MAX_CHUNK_CHARS].strip()
                if piece:
                    units.append(piece)

    chunks: list[str] = []
    buffer = ""
    for unit in units:
        if buffer and len(buffer) + len(unit) > MAX_CHUNK_CHARS:
            chunks.append(buffer.strip())
            tail = buffer[-CHUNK_OVERLAP:]
            buffer = tail + unit if len(tail) + len(unit) <= MAX_CHUNK_CHARS else unit
        else:
            buffer += unit
    if buffer.strip():
        chunks.append(buffer.strip())

    seen: set[str] = set()
    result: list[str] = []
    for chunk in chunks:
        if chunk in seen:
            continue
        seen.add(chunk)
        result.append(chunk)
        if len(result) >= MAX_CHUNKS:
            break
    return result


def snippet_of(chunk: str, limit: int = SNIPPET_LEN) -> str:
    text = normalize_text(chunk).replace("\n", " ")
    return text if len(text) <= limit else text[:limit].rstrip() + "……"
