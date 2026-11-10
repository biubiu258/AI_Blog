"""结构感知切块：标题栈、代码块独立、页边界、480 字 / 80 重叠 / 最多 200 块。"""
from __future__ import annotations

import re
from dataclasses import dataclass

from app.tech.parser import Block

CHUNK_SIZE = 480
OVERLAP = 80
MAX_CHUNKS = 200
_CUT_CHARS = ("。", "！", "？", "；", "\n")


@dataclass
class Chunk:
    chunk_index: int
    page_no: int | None
    section_path: str | None
    content: str
    token_count: int


def estimate_tokens(text: str) -> int:
    """粗估 token 数：CJK 字符按 1 计，ASCII 单词按 1 计。"""
    cjk = sum(1 for ch in text if "\u4e00" <= ch <= "\u9fff")
    words = len(re.findall(r"[A-Za-z0-9_]+", text))
    return max(1, cjk + words)


def _cut_point(text: str, limit: int) -> int:
    head = text[:limit]
    cut = max(head.rfind(ch) for ch in _CUT_CHARS)
    if cut < limit // 2:
        return limit
    return cut + 1


def build_chunks(blocks: list[Block]) -> list[Chunk]:
    """按页分组累计段落；PDF 不跨页合并，保证 page_no 准确。"""
    chunks: list[Chunk] = []
    buffer = ""
    buffer_section: str | None = None
    buffer_page: int | None = None

    def emit(text: str, section: str | None, page: int | None) -> None:
        cleaned = text.strip()
        if not cleaned or len(chunks) >= MAX_CHUNKS:
            return
        chunks.append(Chunk(
            chunk_index=len(chunks),
            page_no=page,
            section_path=section,
            content=cleaned,
            token_count=estimate_tokens(cleaned),
        ))

    def flush() -> None:
        nonlocal buffer, buffer_section, buffer_page
        if buffer.strip():
            emit(buffer, buffer_section, buffer_page)
        buffer = ""
        buffer_section = None
        buffer_page = None

    for block in blocks:
        text = block.text.strip()
        if not text:
            continue
        if buffer and block.page_no != buffer_page:
            flush()

        if block.kind == "code":
            flush()
            emit(text, block.section_path, block.page_no)
            continue

        if buffer_section is None:
            buffer_section = block.section_path
        buffer_page = block.page_no
        buffer = f"{buffer}\n\n{text}" if buffer else text

        while len(buffer) > CHUNK_SIZE:
            cut = _cut_point(buffer, CHUNK_SIZE)
            emit(buffer[:cut], buffer_section, buffer_page)
            buffer = buffer[max(0, cut - OVERLAP):].lstrip()

    flush()
    return chunks[:MAX_CHUNKS]
