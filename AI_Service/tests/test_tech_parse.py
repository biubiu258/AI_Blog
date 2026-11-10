"""技术语料解析与切块单测：Markdown 结构、PDF 页码、失败分支。"""
import io

import pytest

from app.tech import service as tech_service
from app.tech.chunking import CHUNK_SIZE, build_chunks
from app.tech.parser import ParseError, parse_markdown, parse_pdf
from app.tech.service import parse_document


def _pdf(pages: list[str]) -> bytes:
    """构造仅含文本层的极简 PDF，供 pypdf 逐页提取。"""
    n = len(pages)
    page_ids = [3 + i * 2 for i in range(n)]
    content_ids = [4 + i * 2 for i in range(n)]
    font_id = 3 + n * 2
    objects: list[str] = [
        "<< /Type /Catalog /Pages 2 0 R >>",
        f"<< /Type /Pages /Kids [{' '.join(f'{pid} 0 R' for pid in page_ids)}] /Count {n} >>",
    ]
    for index, text in enumerate(pages):
        safe = text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
        content = f"BT /F1 12 Tf 72 720 Td ({safe}) Tj ET"
        objects.append(
            f"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
            f"/Resources << /Font << /F1 {font_id} 0 R >> >> "
            f"/Contents {content_ids[index]} 0 R >>"
        )
        objects.append(f"<< /Length {len(content)} >>\nstream\n{content}\nendstream")
    objects.append("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>")

    out = b"%PDF-1.4\n"
    offsets: list[int] = []
    for number, obj in enumerate(objects, start=1):
        offsets.append(len(out))
        out += f"{number} 0 obj\n{obj}\nendobj\n".encode("ascii")
    xref_pos = len(out)
    out += f"xref\n0 {len(objects) + 1}\n".encode("ascii")
    out += b"0000000000 65535 f \n"
    for offset in offsets:
        out += f"{offset:010d} 00000 n \n".encode("ascii")
    out += (
        f"trailer\n<< /Size {len(objects) + 1} /Root 1 0 R >>\n"
        f"startxref\n{xref_pos}\n%%EOF\n"
    ).encode("ascii")
    return out


def test_markdown_keeps_section_path_and_code_block():
    text = (
        "# 1. 概述\n\n"
        "令牌用于鉴权。\n\n"
        "## 1.1 刷新流程\n\n"
        "```bash\ncurl -X POST /api/token/refresh\n```\n\n"
        "刷新失败时重新登录。\n"
    )
    chunks = build_chunks(parse_markdown(text))
    code_chunks = [c for c in chunks if c.content.startswith("```bash")]
    assert len(code_chunks) == 1
    assert code_chunks[0].content.strip().endswith("```")
    assert "curl -X POST" in code_chunks[0].content
    assert code_chunks[0].section_path == "1. 概述 > 1.1 刷新流程"
    assert any("令牌用于鉴权" in c.content for c in chunks)


def test_markdown_table_rows_become_text():
    text = "| 字段 | 含义 |\n| --- | --- |\n| exp | 过期时间 |\n"
    chunks = build_chunks(parse_markdown(text))
    assert len(chunks) == 1
    assert "字段 | 含义" in chunks[0].content
    assert "exp | 过期时间" in chunks[0].content


def test_pdf_keeps_page_no_and_does_not_merge_pages():
    parsed = parse_pdf(_pdf(["Page one short text.", "Page two short text."]))
    chunks = build_chunks(parsed.blocks)
    assert parsed.page_count == 2
    assert [chunk.page_no for chunk in chunks] == [1, 2]
    assert "Page one" in chunks[0].content and "Page two" not in chunks[0].content
    assert "Page two" in chunks[1].content


def test_pdf_without_text_is_rejected():
    from pypdf import PdfWriter

    writer = PdfWriter()
    writer.add_blank_page(width=200, height=200)
    buffer = io.BytesIO()
    writer.write(buffer)
    with pytest.raises(ParseError) as error:
        parse_pdf(buffer.getvalue())
    assert error.value.code == "PDF_NO_TEXT"


def test_encrypted_pdf_is_rejected():
    from pypdf import PdfWriter

    writer = PdfWriter()
    writer.add_blank_page(width=200, height=200)
    writer.encrypt("secret")
    buffer = io.BytesIO()
    writer.write(buffer)
    with pytest.raises(ParseError) as error:
        parse_pdf(buffer.getvalue())
    assert error.value.code == "PDF_ENCRYPTED"


def test_pdf_page_limit_is_rejected():
    data = _pdf(["x"] * 501)
    with pytest.raises(ParseError) as error:
        parse_pdf(data)
    assert error.value.code == "FILE_TOO_LARGE"


def test_long_text_is_split_without_exceeding_chunk_size():
    text = "。".join(["这是一段用于验证切块长度的技术说明文字"] * 120)
    result = parse_document(text.encode(), "text")
    assert len(result.chunks) > 1
    assert all(len(chunk.content) <= CHUNK_SIZE for chunk in result.chunks)
    assert [chunk.chunk_index for chunk in result.chunks] == list(range(len(result.chunks)))


def test_parse_rejects_unsupported_type_and_empty_content():
    with pytest.raises(ParseError) as error:
        parse_document(b"content", "docx")
    assert error.value.code == "FILE_TYPE_UNSUPPORTED"

    with pytest.raises(ParseError) as error:
        parse_document(b"", "text")
    assert error.value.code == "INVALID_PARAMS"


def test_parse_rejects_oversized_file(monkeypatch):
    monkeypatch.setattr(tech_service, "MAX_FILE_BYTES", 10)
    with pytest.raises(ParseError) as error:
        parse_document(b"x" * 11, "text")
    assert error.value.code == "FILE_TOO_LARGE"
