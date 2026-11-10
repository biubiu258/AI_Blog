"""技术专项接口：解析、检索、向量同步与措辞建议。"""
from __future__ import annotations

import logging
import uuid

from fastapi import APIRouter, Depends, File, Form, UploadFile

from app.api.deps import get_llm_client, verify_internal_token
from app.core.config import get_settings
from app.core.errors import AppError
from app.core.logging import set_request_id
from app.llm.base import LLMClient
from app.prompts.registry import PROMPT_VERSION
from app.schemas.tech import (
    TechChunkItem,
    TechHit,
    TechParseResponse,
    TechRetrieveRequest,
    TechRetrieveResponse,
    TechVectorUpsertRequest,
    TechWordingRequest,
    TechWordingResponse,
)
from app.tech.parser import ParseError
from app.tech.service import (
    delete_vectors,
    parse_document,
    retrieve_vectors,
    upsert_vectors,
)
from app.tech.wording import mock_wording, wording_assist

log = logging.getLogger("ai-service.tech.api")

router = APIRouter(dependencies=[Depends(verify_internal_token)])


@router.post("/tech/parse", response_model=TechParseResponse)
async def parse(
    file: UploadFile = File(...),
    file_type: str = Form(""),
    title: str = Form(""),
) -> TechParseResponse:
    """解析 Markdown / 纯文本 / PDF，返回带页码与章节路径的切块。"""
    data = await file.read()
    try:
        result = parse_document(
            data, file_type=file_type, title=title, filename=file.filename
        )
    except ParseError as exc:
        raise AppError(exc.code, exc.message) from exc
    return TechParseResponse(
        title=result.title,
        file_type=result.file_type,
        page_count=result.page_count,
        chunks=[
            TechChunkItem(
                chunk_index=chunk.chunk_index,
                page_no=chunk.page_no,
                section_path=chunk.section_path,
                content=chunk.content,
                token_count=chunk.token_count,
            )
            for chunk in result.chunks
        ],
    )


@router.post("/tech/retrieve", response_model=TechRetrieveResponse)
async def retrieve(request: TechRetrieveRequest) -> TechRetrieveResponse:
    """技术语料语义检索；RAG 关闭或向量库缺失时返回空结果并标记降级。"""
    hits, degraded = await retrieve_vectors(request.query, request.top_k)
    return TechRetrieveResponse(
        hits=[TechHit(**hit) for hit in hits],
        degraded=degraded,
    )


@router.post("/tech/vectors/upsert", status_code=204)
async def vectors_upsert(request: TechVectorUpsertRequest) -> None:
    """按 doc_id 幂等重建向量；RAG 关闭时直接成功。"""
    chunks = [
        {
            "chunk_index": chunk.chunk_index,
            "page_no": chunk.page_no,
            "section_path": chunk.section_path,
            "content": chunk.content,
        }
        for chunk in request.chunks
    ]
    try:
        await upsert_vectors(
            request.doc_id, request.title, request.source_name, request.version, chunks
        )
    except ParseError as exc:
        raise AppError(exc.code, exc.message) from exc


@router.delete("/tech/vectors/{doc_id}", status_code=204)
async def vectors_delete(doc_id: int) -> None:
    """幂等删除：文档不存在也返回 204。"""
    try:
        await delete_vectors(doc_id)
    except ParseError as exc:
        raise AppError(exc.code, exc.message) from exc


@router.post("/tech/wording", response_model=TechWordingResponse)
async def wording(
    req: TechWordingRequest,
    client: LLMClient = Depends(get_llm_client),
) -> TechWordingResponse:
    """对一段技术文字给出可定位的措辞建议；mock 模式不臆造结论。"""
    # 复用 Java 传入的 trace_id，便于与 ai_output 审计记录对齐
    set_request_id(req.trace_id or uuid.uuid4().hex[:8])
    settings = get_settings()

    if settings.mock_mode:
        result = mock_wording()
    else:
        if not client.is_configured():
            raise AppError("LLM_ERROR", "AI 服务未配置模型，请联系管理员", 502)
        result = await wording_assist(req, client)

    # 回传模型与提示词版本，供 Java 写入 ai_output 审计
    result.model = "mock" if settings.mock_mode else settings.llm_model
    result.prompt_version = PROMPT_VERSION
    return result
