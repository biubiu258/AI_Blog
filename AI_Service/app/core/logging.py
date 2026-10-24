"""日志：统一格式、request_id 注入、正文与密钥不落日志。"""
import logging
from contextvars import ContextVar

_request_id: ContextVar[str] = ContextVar("request_id", default="-")


def set_request_id(value: str) -> None:
    _request_id.set(value)


def get_request_id() -> str:
    return _request_id.get()


class _RequestIdFilter(logging.Filter):
    def filter(self, record: logging.LogRecord) -> bool:
        record.request_id = _request_id.get()
        return True


def setup_logging(level: str = "INFO") -> None:
    root = logging.getLogger()
    root.setLevel(getattr(logging, level.upper(), logging.INFO))
    if root.handlers:
        for h in root.handlers:
            if not any(isinstance(f, _RequestIdFilter) for f in h.filters):
                h.addFilter(_RequestIdFilter())
        return
    handler = logging.StreamHandler()
    handler.setFormatter(logging.Formatter(
        "%(asctime)s %(levelname)s %(name)s [%(request_id)s] %(message)s"
    ))
    handler.addFilter(_RequestIdFilter())
    root.addHandler(handler)
