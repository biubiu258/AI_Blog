"""散文任务的提示词装配。

把《鲁迅散文风格配置.json》里的条目渲染成可执行的写作规则，再与本次素材
（情绪、近况、图片、偏好）拼成 chat messages。风格档案是唯一事实来源，
此处只做选取与排版，不新增风格判断。
"""
import asyncio
import base64
import json
import logging
from functools import lru_cache
from pathlib import Path

import httpx

from config import STYLE_CONFIG_PATH
from schemas import GenerateRequest

log = logging.getLogger("ai-service.prompts")

# 文体内部分类：与配置 conditional_features 的 condition 对应
GENRES = {
    "memoir": "回忆性叙事散文",
    "elegy": "悼念性散文",
    "prose_poem": "散文诗（象征性抒情）",
}

ELEGY_WORDS = ("去世", "离世", "走了", "葬礼", "悼", "逝", "故去", "永远离开", "遗物")
PROSE_POEM_WORDS = (
    "孤独", "失眠", "深夜", "夜里", "绝望", "窒息", "压抑", "焦虑", "迷茫", "城市", "灯", "梦",
)

LUXUN_STYLES = {"luxun", "luxun-prose", "鲁迅", "鲁迅风格"}

# 分类标题与配置字段的对应关系
CATEGORY_TITLES = (
    ("language", "用词"),
    ("syntax", "句法"),
    ("narrative", "叙事"),
    ("structure", "结构"),
    ("rhetoric", "修辞"),
    ("emotion", "情感"),
    ("thought_progression", "思路推进"),
    ("ending", "结尾"),
)


@lru_cache(maxsize=1)
def load_style_profile(path: str = STYLE_CONFIG_PATH) -> dict:
    """读取风格档案；缺失时直接抛错，避免用记忆中的风格规则顶替。"""
    file = Path(path)
    if not file.exists():
        raise FileNotFoundError(f"风格档案不存在: {path}")
    return json.loads(file.read_text(encoding="utf-8"))


def _genre_hint(emotion: str, recent_state: str) -> tuple[str, str]:
    """按关键词给出文体建议与依据；只是建议，允许模型按素材改判。"""
    text = f"{emotion} {recent_state}"
    if any(w in text for w in ELEGY_WORDS):
        return "elegy", "素材含离别或悼念信号"
    hits = [w for w in PROSE_POEM_WORDS if w in text]
    if len(hits) >= 2:
        return "prose_poem", f"素材以情绪与意象为主（命中：{'、'.join(hits[:3])}）"
    return "memoir", "素材以具体旧事、人物或场景为主"


def _render_items(profile: dict, key: str) -> str:
    items = profile.get(key) or []
    lines = []
    for item in items:
        desc = (item.get("description") or "").strip()
        rules = (item.get("writing_rules") or "").strip()
        bad = (item.get("failure_modes") or "").strip()
        cautious = "（谨慎使用）" if item.get("confidence") == "medium" else ""
        line = f"- {desc}{cautious}｜写法：{rules}"
        if bad:
            line += f"｜禁忌：{bad}"
        lines.append(line)
    return "\n".join(lines)


def _render_style_card(profile: dict, genre: str, basis: str) -> str:
    sections = [
        "你是中文散文写作者，按下述风格档案写作。档案是执行规则，不得在正文中复述。",
        f"\n【本次文体建议】{GENRES[genre]}（依据：{basis}；如与素材明显不符可改判，但必须遵守该文体的差异约束）",
        "\n【恒定特征】\n" + "\n".join(
            f"- {f.get('feature', '')}" for f in profile.get("stable_features") or []),
    ]
    for key, title in CATEGORY_TITLES:
        rendered = _render_items(profile, key)
        if rendered:
            sections.append(f"\n【{title}】\n{rendered}")

    conditional = profile.get("conditional_features") or []
    if conditional:
        sections.append("\n【文体差异（只取与选定文体相符的条目，不得混用）】\n" + "\n".join(
            f"- {f.get('feature', '')}：{f.get('condition', '')}" for f in conditional))

    adaptation = profile.get("adaptation_rules") or []
    if adaptation:
        sections.append("\n【现代题材换算】\n" + "\n".join(
            f"- {a.get('origin_feature', '')} → {a.get('modern_application', '')}"
            f"（注意：{a.get('caution', '')}）" for a in adaptation))

    avoid = profile.get("avoid_patterns") or []
    if avoid:
        sections.append("\n【绝对禁止】\n" + "\n".join(
            f"- {a.get('pattern', '')}：{a.get('reason', '')}" for a in avoid))

    sections.append(
        "\n【交付要求】\n"
        "1. 只输出 Markdown：首行 `# 标题`，其后为正文；不要创作说明、要点列表或对话口吻。\n"
        "2. 正文 800–1500 字；散文诗不超过 800 字；悼念文或超过 1500 字才可用“一、二、三”分节。\n"
        "3. 不得出现“鲁迅风格”“模仿”“反讽”等元评论；反语不加引号、不作注解。\n"
        "4. 不得成句复用鲁迅原文，只迁移手法。\n"
        "5. 从图片中的具体物象起笔，但不得逐张描述，不得写“照片里”。\n"
        "6. 情绪用身体化比喻、冷硬判断或细节承载，不直陈“我很痛苦”。\n"
        "7. 档案基于十篇散文归纳，不得套用小说或杂文笔法。"
    )
    return "\n".join(sections)


def _user_text(req: GenerateRequest, genre: str) -> str:
    p, c = req.params, req.context
    parts = [f"情绪：{(p.emotion or '').strip() or '未提供'}"]
    parts.append(f"近况：{(p.recent_state or '').strip() or '未提供'}")
    if (c.instruction or "").strip():
        parts.append(f"附加要求：{c.instruction.strip()}")
    prefs = req.user_context.preferences
    if prefs:
        parts.append(f"写作偏好：{json.dumps(prefs, ensure_ascii=False)}")
    parts.append(f"请据此写一篇{GENRES[genre]}。")
    return "\n".join(parts)


async def _to_data_url(client: httpx.AsyncClient, url: str) -> str | None:
    """把图片 URL 下载后转为 data URL：云端模型无法访问 localhost 等内网地址。

    已是 data URL 的直接透传；下载失败返回 None（由调用方跳过该图）。
    必须用 AsyncClient：本函数在事件循环上执行，同步下载会卡住整个服务。
    """
    if url.startswith("data:"):
        return url
    try:
        resp = await client.get(url)
        resp.raise_for_status()
        ctype = resp.headers.get("content-type", "image/jpeg").split(";")[0].strip()
        if not ctype.startswith("image/"):
            return None
        return f"data:{ctype};base64,{base64.b64encode(resp.content).decode()}"
    except httpx.HTTPError as e:
        log.warning("图片下载失败，跳过该图: %s (%s)", url, e)
        return None


async def _with_images(text: str, image_urls: list[str]) -> list[dict]:
    if not image_urls:
        return [{"type": "text", "text": text}]
    content: list[dict] = [{"type": "text", "text": text}]
    async with httpx.AsyncClient(timeout=15, follow_redirects=True) as client:
        data_urls = await asyncio.gather(*[_to_data_url(client, u) for u in image_urls])
    content.extend(
        {"type": "image_url", "image_url": {"url": d}} for d in data_urls if d)
    return content


def has_images(messages: list[dict]) -> bool:
    for m in messages:
        content = m.get("content")
        if isinstance(content, list) and any(
                p.get("type") == "image_url" for p in content):
            return True
    return False


def strip_images(messages: list[dict]) -> list[dict]:
    """去掉消息中的图片部分：模型不支持视觉或图片全部加载失败时的降级路径。"""
    out = []
    for m in messages:
        content = m.get("content")
        if isinstance(content, list):
            kept = [p for p in content if p.get("type") != "image_url"]
            if not kept:
                kept = [{"type": "text", "text": "（图片素材无法加载，请依据文字素材创作）"}]
            out.append({**m, "content": kept})
        else:
            out.append(m)
    return out


async def build_luxun_messages(req: GenerateRequest) -> list[dict]:
    """鲁迅风格：渲染整份档案 + 本次素材，返回 system/user 两条消息。"""
    profile = load_style_profile()
    genre, basis = _genre_hint(req.params.emotion or "", req.params.recent_state or "")
    log.info("prose style=luxun genre=%s images=%d", genre, len(req.context.image_urls))
    user_content = await _with_images(_user_text(req, genre), req.context.image_urls)
    return [
        {"role": "system", "content": _render_style_card(profile, genre, basis)},
        {"role": "user", "content": user_content},
    ]


PLAIN_SYSTEM = (
    "你是中文散文写作者。依据用户提供的图片与心境写一篇散文初稿。\n"
    "要求：首行 `# 标题`；正文 800–1500 字；从画面的具体物象起笔，不逐张描述；"
    "情绪克制、以细节承载，不直陈口号；不输出创作说明或对话。"
)


async def build_plain_messages(req: GenerateRequest) -> list[dict]:
    """自由白话：不套用风格档案，只保留交付契约。"""
    genre, _ = _genre_hint(req.params.emotion or "", req.params.recent_state or "")
    user_content = await _with_images(_user_text(req, genre), req.context.image_urls)
    return [
        {"role": "system", "content": PLAIN_SYSTEM},
        {"role": "user", "content": user_content},
    ]


async def build_prose_messages(req: GenerateRequest) -> list[dict]:
    style = (req.params.style or "luxun").strip().lower()
    if style in LUXUN_STYLES:
        return await build_luxun_messages(req)
    return await build_plain_messages(req)
