"""AI Service 骨架：实现契约 5.4 / 5.5 / 5.10 的全部 /v1 接口。

骨架策略：
- /v1/generate：prose 任务在配置 LLM 后按风格档案装配提示词并流式调用模型；
  其余任务与未配置 LLM 时仍返回演示文本，保证端到端链路可跑通。
  错误路径按契约 5.7.1 发 error 帧。
- /v1/care/check：一律放行 none（fail-open，Java 侧 5s 超时）。
- /v1/search、/v1/retrieve：返回空 hits，Java 自动降级为关键词检索。
- /v1/vectors/upsert、DELETE /v1/vectors/{id}：仅记日志；接入 Chroma 后删除不存在目标需返回 404 NOT_FOUND。
"""
import asyncio
import json
import logging
import time
from collections.abc import AsyncGenerator

import uvicorn
from fastapi import Depends, FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse, StreamingResponse

import llm
import prompts
from auth import verify_internal_token
from config import HOST, LLM_TOTAL_TIMEOUT, PORT
from schemas import (CareCheckRequest, CareCheckResponse, GenerateRequest,
                     RetrieveRequest, SearchRequest, SearchResponse,
                     VectorUpsertRequest)

logging.basicConfig(level=logging.INFO,
                    format="%(asctime)s %(levelname)s %(name)s: %(message)s")
log = logging.getLogger("ai-service")

app = FastAPI(title="AI Service (Skeleton)", version="0.1.0")


# ==================== 全局错误格式：{"error": {"code", "message"}} ====================

@app.exception_handler(RequestValidationError)
async def validation_handler(_: Request, exc: RequestValidationError) -> JSONResponse:
    """Pydantic 校验失败 → HTTP 400 INVALID_PARAMS（契约 5.7.1）。"""
    first = exc.errors()[0] if exc.errors() else {}
    msg = first.get("msg", "参数不合法")
    loc = ".".join(str(p) for p in first.get("loc", []) if p not in ("body",))
    if loc:
        msg = f"{loc}: {msg}"
    return JSONResponse(status_code=400,
                        content={"error": {"code": "INVALID_PARAMS", "message": msg}})


@app.exception_handler(HTTPException)
async def http_handler(_: Request, exc: HTTPException) -> JSONResponse:
    # auth 抛出的 detail 已是 {"error": {...}} 结构，直接透传
    if isinstance(exc.detail, dict) and "error" in exc.detail:
        return JSONResponse(status_code=exc.status_code, content=exc.detail)
    return JSONResponse(
        status_code=exc.status_code,
        content={"error": {"code": "LLM_ERROR", "message": str(exc.detail)}})


# ==================== SSE 工具（契约 5.4.3） ====================

def sse(event: str, data: dict) -> str:
    return f"event: {event}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"


def render_demo(req: GenerateRequest) -> str:
    """骨架演示文本：接入真实 LLM 后删除。按任务类型返回可辨识的输出。"""
    p, c = req.params, req.context
    topic = (p.topic or "未命名主题").strip()
    match req.task:
        case "outline":
            return (f"# {topic}\n\n"
                    f"## 一、引言\n- 背景与意义\n- 本文目标\n\n"
                    f"## 二、核心概念\n- 关键术语\n- 工作原理\n\n"
                    f"## 三、实践示例\n- 环境准备\n- 分步实现\n- 常见问题\n\n"
                    f"## 四、总结与展望\n")
        case "draft":
            first_line = (c.outline or "").strip().splitlines()[0] if c.outline else topic
            return (f"{first_line}\n\n"
                    f"这是根据大纲生成的演示正文。真实接入后，此处将由模型按大纲逐节展开，"
                    f"结合 params 中的 article_type / style / target_reader 控制语气与深度。\n\n"
                    f"正文应包含若干小节，每节先给结论，再展开细节，最后给出示例或反例。\n\n"
                    f"以上即本次生成的正文骨架。\n")
        case "continue":
            return ("\n\n（演示续写）顺着前文的思路，我们可以进一步展开下一个论点，"
                    "并给出对应的例子加以佐证。")
        case "expand":
            return (c.original_text or "") + \
                   "\n\n（演示扩写）在原有论述基础上补充背景、细节与示例，使论证更充分。"
        case "condense":
            original = (c.original_text or "").strip()
            return f"（演示缩写）{original[:80]}{'…' if len(original) > 80 else ''}"
        case "rewrite":
            return f"（演示改写）{(c.original_text or '').strip()}"
        case "polish":
            return f"（演示润色）{(c.original_text or '').strip()}"
        case "prose":
            mood = (p.emotion or p.recent_state or "平静").strip()
            lead = (f"窗外的光影落在第 {len(c.image_urls)} 张照片上，"
                    if c.image_urls else "窗外的光影落在桌角，")
            return (f"{lead}"
                    f"像把「{mood}」轻轻摊开。\n\n"
                    f"这一刻无需宏大的叙事，只要如实记录心跳的节律、风的来向，"
                    f"以及那些还没来得及命名的感受。\n")
        case _:  # pragma: no cover - TaskName 已限定取值
            return ""


async def demo_stream(req: GenerateRequest) -> AsyncGenerator[str, None]:
    """按契约 5.4.3 发帧：delta 多次追加 → done 一次终态（content 为完整文本）。"""
    text = render_demo(req)
    if not text:
        yield sse("error", {"code": "EMPTY_OUTPUT", "message": "生成结果为空"})
        return
    chunk_size = 24
    for i in range(0, len(text), chunk_size):
        yield sse("delta", {"content": text[i:i + chunk_size]})
        await asyncio.sleep(0.03)
    yield sse("done", {
        "content": text,
        "usage": {"prompt_tokens": 0, "completion_tokens": len(text)},
    })


async def llm_stream(req: GenerateRequest) -> AsyncGenerator[str, None]:
    """真实模型流式生成。提示词装配（含图片下载转 data URL）放在生成器内部：
    响应头先于耗时工作返回，Java 侧等待头的时间就不会被图片下载拖爆。
    content 透传 delta；推理模型思考期间发空 delta 心跳，防止 Java 读超时；
    首次调用失败且携带图片时降级纯文本重试。"""
    parts: list[str] = []
    last_beat = time.monotonic()
    try:
        messages = await prompts.build_prose_messages(req)
    except (FileNotFoundError, json.JSONDecodeError) as e:
        log.error("风格档案不可用: %s", e)
        yield sse("error", {"code": "LLM_ERROR", "message": "风格档案缺失或格式错误，请联系管理员"})
        return
    # 模型不支持视觉或图片全挂时的降级路径
    fallback_msgs = prompts.strip_images(messages) if prompts.has_images(messages) else None

    async def pump(msgs: list[dict]) -> AsyncGenerator[str, None]:
        nonlocal last_beat
        async for kind, chunk in llm.stream_chat(msgs):
            if kind == "content":
                parts.append(chunk)
                yield sse("delta", {"content": chunk})
            else:  # reasoning：只用于保活，不透传思考内容
                now = time.monotonic()
                if now - last_beat >= 3.0:
                    last_beat = now
                    yield sse("delta", {"content": ""})

    try:
        async with asyncio.timeout(LLM_TOTAL_TIMEOUT):
            try:
                async for frame in pump(messages):
                    yield frame
            except llm.LLMError as e:
                if parts or fallback_msgs is None:
                    raise
                log.warning("带图调用失败，降级纯文本重试: %s", e)
                async with asyncio.timeout(LLM_TOTAL_TIMEOUT):
                    async for frame in pump(fallback_msgs):
                        yield frame
    except (TimeoutError, llm.LLMTimeoutError):
        log.warning("LLM 超时 task=%s user_id=%s", req.task, req.user_id)
        yield sse("error", {"code": "LLM_TIMEOUT", "message": "生成超时，请重试"})
        return
    except llm.LLMError as e:
        log.warning("LLM 调用失败 task=%s user_id=%s: %s", req.task, req.user_id, e)
        yield sse("error", {"code": "LLM_ERROR", "message": "AI 服务异常，请稍后重试"})
        return

    text = "".join(parts).strip()
    if not text:
        yield sse("error", {"code": "EMPTY_OUTPUT", "message": "生成结果为空"})
        return
    yield sse("done", {
        "content": text,
        "usage": {"prompt_tokens": 0, "completion_tokens": len(text)},
    })


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


# ==================== 路由 ====================

@app.get("/v1/health")
async def health() -> dict:
    return {"status": "ok"}


@app.post("/v1/generate", dependencies=[Depends(verify_internal_token)])
async def generate(req: GenerateRequest) -> StreamingResponse:
    """SSE 流式生成。Pydantic 校验失败已在依赖注入前抛 400，不会进入流式。"""
    log.info("generate task=%s user_id=%s style=%s",
             req.task, req.user_id, req.params.style)

    if req.task == "prose":
        if not llm.is_configured():
            log.warning("LLM 未配置，prose 回退为演示文本")
            return sse_response(demo_stream(req))
        return sse_response(llm_stream(req))

    return sse_response(demo_stream(req))


@app.post("/v1/care/check", response_model=CareCheckResponse,
          dependencies=[Depends(verify_internal_token)])
async def care_check(req: CareCheckRequest) -> CareCheckResponse:
    """人文关怀检测（契约 5.10）。骨架占位：一律放行 none。

    接入真实检测时替换本函数；Java 侧 5s 超时 + fail-open，检测异常不会阻断发布。
    """
    log.info("care_check user_id=%s title_len=%d content_len=%d (skeleton: none)",
             req.user_id, len(req.title), len(req.content))
    return CareCheckResponse(level="none")


@app.post("/v1/search", response_model=SearchResponse,
          dependencies=[Depends(verify_internal_token)])
async def search(req: SearchRequest) -> SearchResponse:
    """站内语义搜索（契约 5.5）。骨架占位：空 hits，Java 自动降级为关键词检索。"""
    log.info("search query=%r top_k=%d (skeleton: empty)", req.query, req.top_k)
    return SearchResponse(hits=[])


@app.post("/v1/retrieve", response_model=SearchResponse,
          dependencies=[Depends(verify_internal_token)])
async def retrieve(req: RetrieveRequest) -> SearchResponse:
    """RAG 内部召回（契约 5.5）。骨架占位：空 hits。"""
    log.info("retrieve user_id=%s top_k=%d (skeleton: empty)", req.user_id, req.top_k)
    return SearchResponse(hits=[])


@app.post("/v1/vectors/upsert", status_code=204,
          dependencies=[Depends(verify_internal_token)])
async def vector_upsert(req: VectorUpsertRequest) -> None:
    """向量同步（契约 5.5）。骨架占位：仅记日志。"""
    log.info("vector upsert article_id=%s title_len=%d content_len=%d (skeleton)",
             req.article_id, len(req.title), len(req.content))


@app.delete("/v1/vectors/{article_id}", status_code=204,
            dependencies=[Depends(verify_internal_token)])
async def vector_delete(article_id: str) -> None:
    """删除向量（契约 5.5）。骨架占位：按成功处理；接入 Chroma 后目标不存在需返回 404。"""
    log.info("vector delete article_id=%s (skeleton)", article_id)


if __name__ == "__main__":
    uvicorn.run("server:app", host=HOST, port=PORT, reload=False)
