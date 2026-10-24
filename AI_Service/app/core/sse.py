"""SSE 帧构造与响应头（契约 5.4.3 / 5.4.4）。"""
import json
from collections.abc import AsyncGenerator

from fastapi.responses import StreamingResponse

# 注释行：Java 的 SSE 解析会忽略，但能重置读超时
PING_FRAME = ": ping\n\n"


def sse(event: str, data: dict) -> str:
    return f"event: {event}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"


def sse_response(events: AsyncGenerator[str, None]) -> StreamingResponse:
    return StreamingResponse(
        events,
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "X-Accel-Buffering": "no",
            "Connection": "keep-alive",
        },
    )
