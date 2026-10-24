---
name: "text-rewriter"
description: "Rewrites an article per user instruction while preserving meaning and facts. Use when the rewrite task is requested."
---

# 改写（Text Rewriter）

## 触发条件
- Java 网关调用 task=rewrite
- 输入包含待改写的原文

## 输入变量
| 变量 | 必填 | 说明 |
| --- | --- | --- |
| material_text | 是 | 原文或分块片段 |
| context_note | 否 | 上一块末尾，仅用于衔接 |
| chunked | 是 | 是否为分块生成 |
| article_type / style / target_reader | 否 | 体裁、风格、目标读者 |
| instruction | 否 | 改写方向与侧重点 |

## 输出契约
- 输出完整正文，保留标题与结构层级
- 长度为原文的 0.8–1.3 倍
- 不改变观点、事实与结论，不新增数据与引文

## 文件
- `system.md`：系统提示词
- `user.md`：用户消息模板
