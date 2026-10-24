"""集中配置：所有环境变量只在此处读取，其它模块通过 get_settings() 获取。"""
from functools import lru_cache
from pathlib import Path

from pydantic import Field, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

# app/core/config.py → core → app → AI_Service
SERVICE_DIR = Path(__file__).resolve().parents[2]

SERVICE_VERSION = "0.3.0"

DEFAULT_CHROMA_DIR = SERVICE_DIR / "data" / "chroma"
DEFAULT_EMBEDDING_CACHE = SERVICE_DIR / "data" / "models"


class Settings(BaseSettings):
    """服务配置。字段别名即契约中的环境变量名，避免各处自行 os.getenv。"""

    model_config = SettingsConfigDict(
        env_file=str(SERVICE_DIR / ".env"),
        env_file_encoding="utf-8",
        extra="ignore",
        populate_by_name=True,
    )

    internal_token: str = Field("dev-internal-token", alias="AI_INTERNAL_TOKEN")
    host: str = Field("0.0.0.0", alias="AI_HOST")
    port: int = Field(8000, alias="AI_PORT")

    llm_base_url: str = Field("", alias="LLM_BASE_URL")
    llm_api_key: str = Field("", alias="LLM_API_KEY")
    llm_model: str = Field("", alias="LLM_MODEL")
    llm_first_token_timeout: float = Field(20.0, alias="LLM_FIRST_TOKEN_TIMEOUT")
    llm_total_timeout: float = Field(110.0, alias="LLM_TOTAL_TIMEOUT")

    style_config_path: str = Field(
        str(SERVICE_DIR.parent / "鲁迅散文风格配置.json"), alias="LUXUN_STYLE_CONFIG"
    )

    mock_mode: bool = Field(False, alias="AI_MOCK_MODE")
    log_level: str = Field("INFO", alias="LOG_LEVEL")

    # RAG：默认关闭；关闭时检索接口返回空、同步接口直接成功
    rag_enabled: bool = Field(False, alias="RAG_ENABLED")
    chroma_dir: str = Field(str(DEFAULT_CHROMA_DIR), alias="CHROMA_DIR")
    embedding_model: str = Field("BAAI/bge-small-zh-v1.5", alias="EMBEDDING_MODEL")
    embedding_cache_dir: str = Field(
        str(DEFAULT_EMBEDDING_CACHE), alias="EMBEDDING_CACHE_DIR"
    )
    # 关怀模型复核默认关闭，开启后超时回退规则
    care_llm_enabled: bool = Field(False, alias="CARE_LLM_ENABLED")

    @model_validator(mode="after")
    def _fill_style_default(self) -> "Settings":
        if not self.style_config_path.strip():
            self.style_config_path = str(SERVICE_DIR.parent / "鲁迅散文风格配置.json")
        if not self.chroma_dir.strip():
            self.chroma_dir = str(DEFAULT_CHROMA_DIR)
        if not self.embedding_cache_dir.strip():
            self.embedding_cache_dir = str(DEFAULT_EMBEDDING_CACHE)
        return self

    @property
    def llm_configured(self) -> bool:
        return bool(self.llm_base_url.strip() and self.llm_api_key.strip() and self.llm_model.strip())


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    return Settings()
