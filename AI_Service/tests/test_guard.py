"""输出守卫：前言、围栏、标题、空值、尾部元评论、重试边界。"""
from app.generation.guard import TextGuard


def test_strip_preamble():
    g = TextGuard(prefix_limit=5000)
    g.feed("好的，以下是文章：\n\n# 标题\n\n正文。")
    out = g.finish()
    assert out.startswith("# 标题")
    assert "以下是" not in out
    assert "正文" in out


def test_strip_markdown_fence():
    g = TextGuard(prefix_limit=5000)
    g.feed("```markdown\n# 标题\n\n正文。\n```")
    assert g.finish() == "# 标题\n\n正文。"


def test_h1_mismatch_blocks_before_emit():
    g = TextGuard(requires_h1=True, expected_title="标题", prefix_limit=10)
    released = g.feed("## 小标题\n\n这是正文内容，足够触发前缀缓冲。")
    assert released == ""
    assert g.blocked is True
    assert g.emitted_text == ""


def test_force_finish_after_block_still_emits():
    g = TextGuard(requires_h1=True, expected_title="标题", prefix_limit=10)
    g.feed("## 小标题\n\n这是正文内容，足够触发前缀缓冲。")
    out = g.finish(force=True)
    assert out.startswith("## 小标题")


def test_expected_title_mismatch_blocks():
    g = TextGuard(requires_h1=True, expected_title="标题A", prefix_limit=5)
    g.feed("# 标题B\n\n" + "正" * 50)
    assert g.blocked is True


def test_tail_meta_comment_removed():
    g = TextGuard(prefix_limit=5000)
    g.feed("# 标题\n\n正文内容。\n\n以上是全文，希望对你有所帮助。")
    out = g.finish()
    assert "以上是" not in out
    assert "希望" not in out
    assert "正文内容" in out


def test_streaming_keeps_tail_buffer():
    g = TextGuard(prefix_limit=10, tail_keep=5)
    first = g.feed("# 标题\n\n这是第一段，已经足够长。")
    assert first.startswith("# 标题")
    second = g.feed("继续写。")
    assert "继续" not in second  # 仍在尾部缓冲
    assert "继续" in g.finish()


def test_empty_output():
    assert TextGuard().finish() == ""
