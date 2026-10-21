"""AI Service 配置。

内部共享密钥默认值与 Java application.yml 的 ai.internal-token 默认值对齐；
生产环境必须通过环境变量 AI_INTERNAL_TOKEN 覆盖（契约 5.3）。
"""
import os
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent

INTERNAL_TOKEN: str = os.getenv("AI_INTERNAL_TOKEN", "dev-internal-token")

HOST: str = os.getenv("AI_HOST", "0.0.0.0")
PORT: int = int(os.getenv("AI_PORT", "8000"))

# 散文风格档案：默认取项目根目录（AI_Service 的上一级），部署时可显式覆盖
STYLE_CONFIG_PATH: str = os.getenv(
    "LUXUN_STYLE_CONFIG",
    str(BASE_DIR.parent / "鲁迅散文风格配置.json"),
)

# LLM：OpenAI 兼容接口。三项任一为空时，prose 任务回退为骨架演示文本
LLM_BASE_URL: str = os.getenv("LLM_BASE_URL", "").rstrip("/")
LLM_API_KEY: str = os.getenv("LLM_API_KEY", "")
LLM_MODEL: str = os.getenv("LLM_MODEL", "")
LLM_TEMPERATURE: float = float(os.getenv("LLM_TEMPERATURE", "0.8"))
# deepseek-flash 为推理模型：思考与正文共用 max_tokens，需留足余量
LLM_MAX_TOKENS: int = int(os.getenv("LLM_MAX_TOKENS", "8192"))
# 契约 5.7：首令牌 20s。deepseek-flash 思考阶段也有流式帧，首令牌超时不会误触发
LLM_FIRST_TOKEN_TIMEOUT: float = float(os.getenv("LLM_FIRST_TOKEN_TIMEOUT", "20"))
# Java 侧读超时 120s：总超时须小于它，且思考期间有心跳帧保活
LLM_TOTAL_TIMEOUT: float = float(os.getenv("LLM_TOTAL_TIMEOUT", "110"))
