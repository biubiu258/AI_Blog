"""文风提炼与个人文风卡注入的单测。"""
import pytest
from pydantic import ValidationError

from app.generation.handlers import build_user_style_card
from app.schemas.style_profile import StyleProfileRequest, StyleSample
from app.styleprofile.service import _parse, mock_profile


def _request(count=3):
    return StyleProfileRequest(
        user_id=1,
        samples=[StyleSample(title=f"第{i}篇", text="这是样本正文。" * 20) for i in range(count)],
    )


def test_request_requires_three_to_ten_samples():
    with pytest.raises(ValidationError):
        _request(2)
    with pytest.raises(ValidationError):
        _request(11)
    assert len(_request(3).samples) == 3


def test_parse_accepts_valid_json_and_fills_missing_keys():
    text = '```json\n{"summary":"平实克制","features":{"tone":"冷峻","diction":"口语化"}}\n```'
    summary, features = _parse(text)
    assert summary == "平实克制"
    assert features["tone"] == "冷峻"
    # 未给出的键补空串，键集固定
    assert features["avoid"] == ""
    assert set(features) == {"tone", "diction", "sentence", "imagery", "themes", "structure", "avoid"}


def test_parse_rejects_invalid_payloads():
    import app.core.errors as errors

    with pytest.raises(errors.AppError):
        _parse("完全不是 JSON")
    with pytest.raises(errors.AppError):
        _parse('{"features":{"tone":"缺 summary"}}')
    with pytest.raises(errors.AppError):
        _parse('{"summary":"缺 features"}')


def test_mock_profile_returns_fixed_features():
    result = mock_profile(_request())
    assert "mock" in result.summary
    assert len(result.features) == 7


def test_user_style_card_renders_features():
    card = build_user_style_card({
        "id": 3,
        "name": "我的散文体",
        "summary": "平实克制",
        "features": {"tone": "冷峻", "diction": " ", "avoid": "少用成语"},
    })
    assert "【个人文风卡 · 我的散文体】" in card
    assert "- 整体：平实克制" in card
    assert "- 语调：冷峻" in card
    assert "- 避免：少用成语" in card
    # 空白特征不展示
    assert "用词" not in card


def test_user_style_card_empty_without_profile():
    assert build_user_style_card(None) == ""
    assert build_user_style_card({"name": "只有名字"}) == ""
