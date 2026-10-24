"""统一错误类型与错误码映射（契约 5.7.1）。"""
from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

# 错误码 → HTTP 状态（流式接口仅在流开始前使用）
CODE_STATUS: dict[str, int] = {
    "INVALID_PARAMS": 400,
    "UNAUTHORIZED": 401,
    "NOT_FOUND": 404,
    "EMPTY_OUTPUT": 422,
    "LLM_TIMEOUT": 504,
    "LLM_ERROR": 502,
    "VECTOR_ERROR": 500,
    "INTERNAL_ERROR": 500,
}


class AppError(Exception):
    """带契约错误码的业务异常。message 必须已脱敏。"""

    def __init__(self, code: str, message: str, status: int | None = None) -> None:
        super().__init__(message)
        self.code = code
        self.message = message
        self.status = status or CODE_STATUS.get(code, 500)


def error_body(code: str, message: str) -> dict:
    return {"error": {"code": code, "message": message}}


def install_error_handlers(app: FastAPI) -> None:
    @app.exception_handler(AppError)
    async def _app_error(_: Request, exc: AppError) -> JSONResponse:
        return JSONResponse(status_code=exc.status, content=error_body(exc.code, exc.message))

    @app.exception_handler(RequestValidationError)
    async def _validation_error(_: Request, exc: RequestValidationError) -> JSONResponse:
        """Pydantic 校验失败 → 400 INVALID_PARAMS，且不进入流式。"""
        first = exc.errors()[0] if exc.errors() else {}
        msg = first.get("msg", "参数不合法")
        loc = ".".join(str(p) for p in first.get("loc", []) if p not in ("body",))
        if loc:
            msg = f"{loc}: {msg}"
        return JSONResponse(status_code=400, content=error_body("INVALID_PARAMS", msg))

    @app.exception_handler(Exception)
    async def _unhandled(_: Request, exc: Exception) -> JSONResponse:
        # 不向调用方暴露堆栈与供应商细节
        return JSONResponse(status_code=500, content=error_body("INTERNAL_ERROR", "服务内部错误"))
