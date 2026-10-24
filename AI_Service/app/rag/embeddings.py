"""本地 Embedding：fastembed 加载 ONNX 模型，首次调用时才下载/加载。"""
import logging
import threading
from collections.abc import Iterable

log = logging.getLogger("ai-service.rag.embedding")


class EmbeddingError(Exception):
    """模型缺失、下载失败或推理失败。"""


class LocalEmbedder:
    def __init__(self, model_name: str, cache_dir: str) -> None:
        self._model_name = model_name
        self._cache_dir = cache_dir
        self._model = None
        self._lock = threading.Lock()

    def _ensure_model(self):
        if self._model is not None:
            return self._model
        with self._lock:
            if self._model is None:
                try:
                    from fastembed import TextEmbedding
                except ImportError as exc:  # pragma: no cover - 依赖缺失
                    raise EmbeddingError(
                        "未安装 fastembed，请执行 pip install -r requirements.txt"
                    ) from exc
                log.info(
                    "加载本地 Embedding 模型 model=%s cache=%s",
                    self._model_name, self._cache_dir,
                )
                try:
                    self._model = TextEmbedding(
                        model_name=self._model_name, cache_dir=self._cache_dir
                    )
                except Exception as exc:
                    log.error("Embedding 模型加载失败: %s", exc)
                    raise EmbeddingError("Embedding 模型不可用") from exc
        return self._model

    def embed_passages(self, texts: Iterable[str]) -> list[list[float]]:
        """文章块向量；批量推理在调用线程内完成。"""
        items = [text for text in texts if text.strip()]
        if not items:
            return []
        model = self._ensure_model()
        try:
            with self._lock:
                embed = getattr(model, "passage_embed", model.embed)
                vectors = embed(items)
                return [list(map(float, vector)) for vector in vectors]
        except Exception as exc:
            log.error("文章向量计算失败: %s", exc)
            raise EmbeddingError("文章向量计算失败") from exc

    def embed_query(self, text: str) -> list[float]:
        model = self._ensure_model()
        try:
            with self._lock:
                embed = getattr(model, "query_embed", model.embed)
                vector = next(iter(embed([text])))
                return list(map(float, vector))
        except Exception as exc:
            log.error("查询向量计算失败: %s", exc)
            raise EmbeddingError("查询向量计算失败") from exc
