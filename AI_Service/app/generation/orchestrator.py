"""生成编排：计划执行、心跳、超时、取消、usage 与终态。"""
import asyncio
import contextlib
import logging
import time
from collections.abc import AsyncGenerator

from fastapi import Request

from app.core.config import get_settings
from app.core.sse import PING_FRAME, sse
from app.generation.handlers import build_plan
from app.generation.imaging import download_images
from app.generation.pipeline import RunStats, stream_plan
from app.llm.base import LLMClient, LLMError, LLMTimeoutError
from app.prompts.registry import PROMPT_VERSION, is_luxun_style
from app.prompts.renderer import PromptError
from app.prompts.styles import StyleProfileError, find_verbatim_overlap, load_profile
from app.schemas.generate import GenerateRequest

log = logging.getLogger("ai-service.generate")

HEARTBEAT_SECONDS = 5.0


def _error_frame(code: str, message: str) -> str:
    return sse("error", {"code": code, "message": message})


async def generate_stream(
    req: GenerateRequest, request: Request, llm: LLMClient
) -> AsyncGenerator[str, None]:
    settings = get_settings()
    stats = RunStats()
    started = time.monotonic()

    # 耗时工作都在生成器内：响应头已先返回给 Java
    try:
        images = (
            await download_images(req.context.image_urls) if req.task == "prose" else []
        )
        plan = build_plan(req, settings, images)
    except (StyleProfileError, PromptError) as exc:
        log.error("提示词准备失败 task=%s: %s", req.task, exc)
        yield _error_frame("LLM_ERROR", "提示词或风格档案不可用，请联系管理员")
        return
    except Exception:
        log.exception("计划构建失败 task=%s", req.task)
        yield _error_frame("INTERNAL_ERROR", "服务内部错误")
        return

    queue: asyncio.Queue = asyncio.Queue()

    async def producer() -> None:
        try:
            async for kind, payload in stream_plan(plan, llm, stats):
                await queue.put((kind, payload))
            await queue.put(("__end__", None))
        except LLMTimeoutError:
            await queue.put(("__error__", ("LLM_TIMEOUT", "生成超时，请重试")))
        except LLMError as exc:
            log.warning("模型调用失败 task=%s: %s", req.task, exc)
            await queue.put(("__error__", ("LLM_ERROR", "AI 服务异常，请稍后重试")))
        except asyncio.CancelledError:
            raise
        except Exception:
            log.exception("生成过程异常 task=%s", req.task)
            await queue.put(("__error__", ("LLM_ERROR", "AI 服务异常，请稍后重试")))

    producer_task = asyncio.create_task(producer())
    deadline = started + settings.llm_total_timeout
    parts: list[str] = []

    try:
        while True:
            if await request.is_disconnected():
                log.info("客户端断开，已停止生成 task=%s", req.task)
                return

            remaining = deadline - time.monotonic()
            if remaining <= 0:
                log.warning("生成总超时 task=%s", req.task)
                yield _error_frame("LLM_TIMEOUT", "生成超时，请重试")
                return

            try:
                kind, payload = await asyncio.wait_for(
                    queue.get(), timeout=min(HEARTBEAT_SECONDS, remaining)
                )
            except TimeoutError:
                if time.monotonic() >= deadline:
                    log.warning("生成总超时 task=%s", req.task)
                    yield _error_frame("LLM_TIMEOUT", "生成超时，请重试")
                    return
                yield PING_FRAME
                continue

            if kind == "__end__":
                break
            if kind == "__error__":
                code, message = payload
                yield _error_frame(code, message)
                return
            if kind == "delta" and payload:
                parts.append(payload)
                yield sse("delta", {"content": payload})
    finally:
        if not producer_task.done():
            producer_task.cancel()
            with contextlib.suppress(asyncio.CancelledError):
                await producer_task

    text = "".join(parts).strip()
    if not text:
        log.warning("生成结果为空 task=%s", req.task)
        yield _error_frame("EMPTY_OUTPUT", "生成结果为空，请重试")
        return

    # 真实 usage 缺失时用字符数估算，仅用于统计
    usage = stats.usage
    if usage.completion_tokens <= 0:
        usage.completion_tokens = len(text)

    genre = plan.extra.get("genre", "-")
    log.info(
        "生成完成 task=%s trace_id=%s mode=%s genre=%s prompt_version=%s duration_ms=%d "
        "prompt_tokens=%d completion_tokens=%d warnings=%s",
        req.task, req.trace_id or "-", plan.mode, genre, PROMPT_VERSION,
        int((time.monotonic() - started) * 1000),
        usage.prompt_tokens, usage.completion_tokens,
        "；".join(stats.warnings) or "无",
    )

    if req.task == "prose" and is_luxun_style(req.params.style):
        _check_verbatim(text, settings.style_config_path)
    _check_length(req.task, req.context.original_text, text)

    # done 帧同时回传模型与提示词版本，供 Java 写入 ai_output 审计
    model_name = "mock" if settings.mock_mode else settings.llm_model
    yield sse("done", {
        "content": text,
        "usage": usage.as_dict(),
        "model": model_name,
        "prompt_version": PROMPT_VERSION,
    })


_LENGTH_RATIOS = {
    "expand": (1.3, 2.0),
    "condense": (0.4, 0.6),
    "rewrite": (0.8, 1.3),
    "polish": (0.9, 1.1),
}


def _check_length(task: str, original: str | None, text: str) -> None:
    """长度比只做告警，不阻断，也不交给模型自行协商。"""
    bounds = _LENGTH_RATIOS.get(task)
    if not bounds or not original or not original.strip():
        return
    ratio = len(text) / len(original.strip())
    low, high = bounds
    if ratio < low or ratio > high:
        log.warning(
            "长度比超出预期 task=%s ratio=%.2f expected=%.1f-%.1f chars=%d/%d",
            task, ratio, low, high, len(text), len(original.strip()),
        )


def _check_verbatim(text: str, style_path: str) -> None:
    try:
        profile = load_profile(style_path)
        hit = find_verbatim_overlap(text, profile)
    except StyleProfileError:
        return
    if hit:
        log.warning("输出与档案引文存在 ≥12 字重合，请调参: %s", hit[:40])
