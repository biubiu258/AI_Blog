"""图片素材：并发下载并转 data URL；失败跳过，不阻断生成。"""
import asyncio
import base64
import logging

import httpx

log = logging.getLogger("ai-service.imaging")

MAX_IMAGES = 5


async def _to_data_url(client: httpx.AsyncClient, url: str) -> str | None:
    if url.startswith("data:"):
        return url
    try:
        resp = await client.get(url)
        resp.raise_for_status()
        content_type = resp.headers.get("content-type", "image/jpeg").split(";")[0].strip()
        if not content_type.startswith("image/"):
            log.warning("非图片内容，已跳过")
            return None
        return f"data:{content_type};base64,{base64.b64encode(resp.content).decode()}"
    except httpx.HTTPError as exc:
        log.warning("图片下载失败，已跳过: %s", type(exc).__name__)
        return None


async def download_images(urls: list[str], limit: int = MAX_IMAGES) -> list[str]:
    """最多 limit 张；失败的条目直接丢弃，返回值可能少于输入。"""
    targets = [u for u in (urls or []) if u][:limit]
    if not targets:
        return []
    async with httpx.AsyncClient(timeout=15, follow_redirects=True) as client:
        results = await asyncio.gather(*[_to_data_url(client, u) for u in targets])
    return [r for r in results if r]
