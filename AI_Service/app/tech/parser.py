"""技术语料解析：Markdown / 纯文本 / PDF。

PDF 只读取文本层，不做 OCR；加密、空文本、超限都给出明确错误码，
由 Java 侧直接写入 tech_doc.error_message 展示给管理员。
"""
from __future__ import annotations

import io
import logging
import re
from dataclasses import dataclass, field

log = logging.getLogger("ai-service.tech.parser")

MAX_FILE_BYTES = 30 * 1024 * 1024
MAX_PDF_PAGES = 500

_HEADING = re.compile(r"^(#{1,6})\s+(.+)$")
_FENCE = re.compile(r"^(```|~~~)")
_TABLE_SEPARATOR = re.compile(r"\|[\s:\-|]*\|")


class ParseError(Exception):
    """解析失败：携带契约错误码，接口层直接映射 HTTP 状态。"""

    def __init__(self, code: str, message: str) -> None:
        super().__init__(message)
        self.code = code
        self.message = message


@dataclass
class Block:
    """结构化块：段落 / 代码 / 表格，带页码与章节路径。"""

    text: str
    page_no: int | None = None
    section_path: str | None = None
    kind: str = "paragraph"


@dataclass
class ParsedDocument:
    file_type: str
    page_count: int
    blocks: list[Block] = field(default_factory=list)


def parse_markdown(text: str) -> list[Block]:
    """按标题栈、代码围栏与表格解析 Markdown，保留章节路径。"""
    lines = text.splitlines()
    blocks: list[Block] = []
    stack: list[tuple[int, str]] = []

    def section() -> str | None:
        return " > ".join(title for _, title in stack) if stack else None

    i = 0
    while i < len(lines):
        stripped = lines[i].strip()
        if not stripped:
            i += 1
            continue

        fence = _FENCE.match(stripped)
        if fence:
            marker = fence.group(1)
            fence_lines = [lines[i]]
            i += 1
            while i < len(lines) and not lines[i].strip().startswith(marker):
                fence_lines.append(lines[i])
                i += 1
            if i < len(lines):
                fence_lines.append(lines[i])
                i += 1
            blocks.append(Block("\n".join(fence_lines), section_path=section(), kind="code"))
            continue

        heading = _HEADING.match(lines[i])
        if heading:
            level = len(heading.group(1))
            title = heading.group(2).strip()
            while stack and stack[-1][0] >= level:
                stack.pop()
            stack.append((level, title))
            i += 1
            continue

        if stripped.startswith("|"):
            rows: list[str] = []
            while i < len(lines) and lines[i].strip().startswith("|"):
                rows.append(lines[i].strip())
                i += 1
            table_lines = []
            for row in rows:
                if _TABLE_SEPARATOR.fullmatch(row):
                    continue
                cells = [cell.strip() for cell in row.strip("|").split("|")]
                table_lines.append(" | ".join(cells))
            if table_lines:
                blocks.append(Block("\n".join(table_lines), section_path=section(), kind="table"))
            continue

        paragraph: list[str] = []
        paragraph_section = section()
        while i < len(lines):
            current = lines[i]
            current_stripped = current.strip()
            if (not current_stripped or _HEADING.match(current)
                    or _FENCE.match(current_stripped) or current_stripped.startswith("|")):
                break
            paragraph.append(current_stripped)
            i += 1
        if paragraph:
            blocks.append(Block("\n".join(paragraph), section_path=paragraph_section))

    return blocks


def parse_text(text: str) -> list[Block]:
    """纯文本按空行分段，不推断标题。"""
    blocks: list[Block] = []
    for paragraph in re.split(r"\n{2,}", text):
        cleaned = paragraph.strip()
        if cleaned:
            blocks.append(Block(cleaned))
    return blocks


def parse_pdf(data: bytes) -> ParsedDocument:
    """逐页提取文本层；空页跳过，全部为空则判定为扫描件。"""
    try:
        from pypdf import PdfReader
    except ImportError as exc:  # pragma: no cover - 依赖缺失
        log.error("pypdf 未安装: %s", exc)
        raise ParseError("INVALID_PARAMS", "PDF 解析组件缺失，请联系管理员") from exc

    try:
        reader = PdfReader(io.BytesIO(data))
    except Exception as exc:
        log.warning("PDF 无法打开: %s", exc)
        raise ParseError("INVALID_PARAMS", "PDF 无法解析，请确认文件未损坏") from exc

    if reader.is_encrypted:
        try:
            if not reader.decrypt(""):
                raise ParseError("PDF_ENCRYPTED", "PDF 已加密，无法读取文本层")
        except ParseError:
            raise
        except Exception as exc:
            log.warning("PDF 解密失败: %s", exc)
            raise ParseError("PDF_ENCRYPTED", "PDF 已加密，无法读取文本层") from exc

    try:
        page_count = len(reader.pages)
    except Exception as exc:
        raise ParseError("INVALID_PARAMS", "PDF 无法解析，请确认文件未损坏") from exc

    if page_count > MAX_PDF_PAGES:
        raise ParseError("FILE_TOO_LARGE", f"PDF 页数超过 {MAX_PDF_PAGES} 页")

    blocks: list[Block] = []
    for page_index, page in enumerate(reader.pages, start=1):
        try:
            page_text = page.extract_text() or ""
        except Exception as exc:  # 单页失败不影响其它页
            log.warning("PDF 第 %d 页文本提取失败: %s", page_index, exc)
            page_text = ""
        for paragraph in re.split(r"\n{2,}", page_text):
            cleaned = paragraph.strip()
            if cleaned:
                blocks.append(Block(cleaned, page_no=page_index))

    if not blocks:
        raise ParseError(
            "PDF_NO_TEXT", "PDF 无文本层，疑似扫描件，请先 OCR 或上传可复制文本的 PDF"
        )
    return ParsedDocument(file_type="pdf", page_count=page_count, blocks=blocks)
