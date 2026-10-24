---
name: "prose-luxun-writer"
description: "Writes a Chinese prose draft in Lu Xun's technique using a curated style card. Use when prose generation selects the luxun style."
---

# 鲁迅风格散文（Prose Luxun Writer）

## 触发条件
- Java 网关调用 task=prose
- style 为 luxun / luxun-prose / 鲁迅 / 鲁迅风格

## 输入变量
| 变量 | 必填 | 说明 |
| --- | --- | --- |
| genre_label | 是 | 由 Python 固定的文体名称 |
| genre_rules | 是 | 当前文体结构要求 |
| length_text | 是 | 目标字数区间 |
| style_card | 是 | 风格档案裁剪结果，≤2500 字 |
| emotion / recent_state | 否 | 素材 |
| image_note | 是 | 有无图片的说明文案 |
| preferences / instruction | 否 | 写作偏好与附加要求 |

## 输出契约
- 第一行 `# 标题`，正文 400–1800 字，按文体浮动
- 不得改判文体，不得复述风格档案
- 不得成句复用鲁迅原文，只迁移手法
- 不写“鲁迅风格”“模仿”等元评论

## 文件
- `system.md`：系统提示词
- 用户模板：`_shared/prose.user.md`
