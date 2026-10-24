---
name: "text-expander"
description: "Expands an existing article to 1.3-2.0x length while keeping facts and structure. Use when the expand task is requested."
---

# 扩写（Text Expander）

## 触发条件
- Java 网关调用 task=expand
- 输入包含待扩写的原文

## 输入变量
| 变量 | 必填 | 说明 |
| --- | --- | --- |
| material_text | 是 | 原文或分块片段 |
| context_note | 否 | 上一块末尾，仅用于衔接 |
| chunked | 是 | 是否为分块生成 |
| article_type / style / target_reader | 否 | 体裁、风格、目标读者 |
| instruction | 否 | 用户附加要求 |

## 输出契约
- 输出完整正文，不是差异片段
- 长度为原文的 1.3–2.0 倍
- 只补充细节、例证、背景与过渡，不新增章节

## 文件
- `system.md`：系统提示词
- `user.md`：用户消息模板
