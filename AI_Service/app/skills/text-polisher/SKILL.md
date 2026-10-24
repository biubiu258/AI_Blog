---
name: "text-polisher"
description: "Polishes language, grammar, punctuation, and rhythm without changing meaning. Use when the polish task is requested."
---

# 润色（Text Polisher）

## 触发条件
- Java 网关调用 task=polish
- 输入包含待润色的原文

## 输入变量
| 变量 | 必填 | 说明 |
| --- | --- | --- |
| material_text | 是 | 原文或分块片段 |
| context_note | 否 | 上一块末尾，仅用于衔接 |
| chunked | 是 | 是否为分块生成 |
| article_type / style / target_reader | 否 | 体裁、风格、目标读者 |

## 输出契约
- 只修语病、标点、错别字、冗余、节奏与用词
- 不改变观点、事实、结构与段落顺序
- 长度为原文的 0.9–1.1 倍，不输出修改说明

## 文件
- `system.md`：系统提示词
- `user.md`：用户消息模板
