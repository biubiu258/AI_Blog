---
name: "prose-plain-writer"
description: "Writes a Chinese prose draft in contemporary plain prose. Use when prose generation selects the plain style."
---

# 自由白话散文（Prose Plain Writer）

## 触发条件
- Java 网关调用 task=prose
- style 为 plain 等非鲁迅风格

## 输入变量
| 变量 | 必填 | 说明 |
| --- | --- | --- |
| genre_label | 是 | 由 Python 固定的文体名称 |
| genre_rules | 是 | 当前文体结构要求 |
| length_text | 是 | 目标字数区间 |
| emotion / recent_state | 否 | 素材 |
| image_note | 是 | 有无图片的说明文案 |
| preferences / instruction | 否 | 写作偏好与附加要求 |

## 输出契约
- 第一行 `# 标题`，正文按文体长度输出
- 使用当代通顺书面语，不模仿特定作家腔调
- 不得改判文体，不写创作说明与元评论

## 文件
- `system.md`：系统提示词
- 用户模板：`_shared/prose.user.md`
