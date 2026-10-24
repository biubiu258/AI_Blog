"""测试夹具：固定环境、隔离真实 LLM、提供 SSE 解析。"""
import json
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

PROJECT_ROOT = Path(__file__).resolve().parents[2]
STYLE_CONFIG = PROJECT_ROOT / "鲁迅散文风格配置.json"
TOKEN_HEADER = {"X-Internal-Token": "test-token"}


def parse_sse(raw: str) -> list[dict]:
    frames: list[dict] = []
    for block in raw.split("\n\n"):
        block = block.strip()
        if not block or block.startswith(":"):
            continue
        event = None
        data = None
        for line in block.splitlines():
            if line.startswith("event:"):
                event = line[6:].strip()
            elif line.startswith("data:"):
                data = json.loads(line[5:].strip())
        if event:
            frames.append({"event": event, "data": data})
    return frames


@pytest.fixture()
def settings_env(monkeypatch):
    monkeypatch.setenv("AI_INTERNAL_TOKEN", "test-token")
    monkeypatch.setenv("LLM_BASE_URL", "http://llm.invalid/v1")
    monkeypatch.setenv("LLM_API_KEY", "test-key")
    monkeypatch.setenv("LLM_MODEL", "test-model")
    monkeypatch.setenv("AI_MOCK_MODE", "false")
    monkeypatch.setenv("LUXUN_STYLE_CONFIG", str(STYLE_CONFIG))
    from app.core.config import get_settings
    from app.rag.service import get_rag_service

    get_settings.cache_clear()
    get_rag_service.cache_clear()
    yield
    get_settings.cache_clear()
    get_rag_service.cache_clear()


@pytest.fixture()
def app(settings_env):
    from app.main import create_app

    return create_app()


@pytest.fixture()
def client(app):
    return TestClient(app)


@pytest.fixture()
def use_llm(app):
    from app.api.deps import get_llm_client

    def _install(llm):
        app.dependency_overrides[get_llm_client] = lambda: llm
        return llm

    yield _install
    app.dependency_overrides.clear()


@pytest.fixture()
def sse():
    return parse_sse
