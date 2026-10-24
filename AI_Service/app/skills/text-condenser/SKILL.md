---
name: "text-condenser"
description: "Condenses an article to 40-60% length while preserving key facts. Use when the condense task is requested."
---

# 缩写（Text Condenser）

## 触发条件
- Java 网关调用 task=condense
- 输入包含待压缩的原文

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
- 长度为原文的 40%–60%
- 不新增信息，保留标题与章节层级

## 文件
- `system.md`：系统提示词
- `user.md`：用户消息模板
