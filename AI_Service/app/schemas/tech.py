"""技术专项接口模型：解析、检索、向量同步与措辞建议。"""
from pydantic import BaseModel, Field


class TechChunkItem(BaseModel):
    chunk_index: int = Field(ge=0)
    page_no: int | None = None
    section_path: str | None = None
    content: str
    token_count: int = 0


class TechParseResponse(BaseModel):
    title: str
    file_type: str
    page_count: int = 0
    chunks: list[TechChunkItem] = Field(default_factory=list)


class TechRetrieveRequest(BaseModel):
    query: str = Field(min_length=1, max_length=500)
    top_k: int = Field(default=20, ge=1, le=50)
    trace_id: str | None = None


class TechHit(BaseModel):
    chunk_id: str
    doc_id: str
    title: str = ""
    source_name: str = ""
    version: str = ""
    section_path: str | None = None
    page_no: int | None = None
    snippet: str = ""
    score: float | None = None


class TechRetrieveResponse(BaseModel):
    hits: list[TechHit] = Field(default_factory=list)
    degraded: bool = False


class TechVectorChunk(BaseModel):
    chunk_index: int = Field(ge=0)
    page_no: int | None = None
    section_path: str | None = None
    content: str


class TechVectorUpsertRequest(BaseModel):
    doc_id: int = Field(gt=0)
    title: str = ""
    source_name: str = ""
    version: str = ""
    chunks: list[TechVectorChunk] = Field(default_factory=list)


class TechEvidence(BaseModel):
    chunk_id: str = ""
    doc_id: str = ""
    title: str = ""
    source_name: str = ""
    version: str = ""
    section_path: str | None = None
    page_no: int | None = None
    snippet: str = ""


class TechWordingRequest(BaseModel):
    trace_id: str | None = None
    user_id: int | None = None
    text: str = Field(min_length=1, max_length=1500)
    section_path: str | None = None
    evidence: list[TechEvidence] = Field(default_factory=list)


class TechWordingIssue(BaseModel):
    id: str = ""
    type: str
    quote: str
    replacement: str | None = None
    reason: str = ""
    severity: str = "mid"
    evidence_chunk_id: str | None = None
    evidence: TechEvidence | None = None


class TechWordingResponse(BaseModel):
    issues: list[TechWordingIssue] = Field(default_factory=list)
    model: str = ""
    prompt_version: str = ""
    usage: dict | None = None
