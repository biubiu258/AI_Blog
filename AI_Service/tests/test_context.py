"""上下文：尾部截取、大纲解析、Markdown 确定性分块。"""
from app.generation.context import chunk_markdown, parse_outline, tail_text


def test_tail_text():
    assert tail_text("abc", 2) == "bc"
    assert tail_text("abc", 10) == "abc"


def test_parse_outline():
    outline = """# 旧书摊

## 一、缘起
- 第一次经过
- 摊主的样子

## 二、再见
- 书页的气味
"""
    parsed = parse_outline(outline)
    assert parsed.title == "旧书摊"
    assert [s.title for s in parsed.sections] == ["一、缘起", "二、再见"]
    assert parsed.sections[0].bullets == ["- 第一次经过", "- 摊主的样子"]
    assert parsed.usable is True


def test_parse_outline_without_structure():
    parsed = parse_outline("只有正文，没有标题")
    assert parsed.title == ""
    assert parsed.sections == []
    assert parsed.usable is False


def test_chunk_count_and_context():
    text = "\n\n".join("## 小节" + str(i) + "\n" + "内容。" * 300 for i in range(20))
    chunks = chunk_markdown(text)
    assert 1 < len(chunks) <= 10
    assert chunks[0].context == ""
    for chunk in chunks[1:]:
        assert len(chunk.context) <= 300


def test_code_fence_is_not_split():
    text = (
        "# 标题\n\n" + "前文。" * 800
        + "\n\n```python\nprint('a')\nprint('b')\n```\n\n" + "后文。" * 800
    )
    chunks = chunk_markdown(text)
    assert any("```python\nprint('a')\nprint('b')\n```" in c.text for c in chunks)


def test_max_chunks_cap():
    text = "\n\n".join("段落" + str(i) + "。" * 400 for i in range(60))
    chunks = chunk_markdown(text)
    assert len(chunks) <= 10
    assert "".join(c.text for c in chunks).count("段落") >= 60
