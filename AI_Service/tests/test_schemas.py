"""请求模型：snake_case、任务必填、长度与图片上限。"""
import pytest
from pydantic import ValidationError

from app.schemas.generate import GenerateRequest


def test_snake_case_fields_exist():
    fields = GenerateRequest.model_fields
    assert "user_id" in fields and "task" in fields and "params" in fields
    assert "context" in fields and "user_context" in fields


def test_outline_requires_topic():
    with pytest.raises(ValidationError):
        GenerateRequest(user_id=1, task="outline", params={})


def test_draft_requires_outline():
    with pytest.raises(ValidationError):
        GenerateRequest(user_id=1, task="draft", params={"topic": "x"})


@pytest.mark.parametrize("task", ["continue", "expand", "condense", "rewrite", "polish"])
def test_transform_requires_original_text(task):
    with pytest.raises(ValidationError):
        GenerateRequest(user_id=1, task=task, params={})


def test_prose_requires_emotion_or_state():
    with pytest.raises(ValidationError):
        GenerateRequest(user_id=1, task="prose", params={})
    ok = GenerateRequest(user_id=1, task="prose", params={"recent_state": "加班"})
    assert ok.task == "prose"


def test_image_limit_and_scheme():
    with pytest.raises(ValidationError):
        GenerateRequest(
            user_id=1, task="prose", params={"emotion": "难过"},
            context={"image_urls": [f"http://a/{i}.jpg" for i in range(6)]},
        )
    with pytest.raises(ValidationError):
        GenerateRequest(
            user_id=1, task="prose", params={"emotion": "难过"},
            context={"image_urls": ["ftp://a/1.jpg"]},
        )


def test_original_text_upper_bound():
    with pytest.raises(ValidationError):
        GenerateRequest(
            user_id=1, task="polish", params={},
            context={"original_text": "字" * 100001},
        )
