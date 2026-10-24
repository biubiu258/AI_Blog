---
name: "article-drafter"
description: "Expands an approved Markdown outline into a full article while preserving section order. Use when the draft task receives an outline."
---

# 正文生成（Article Drafter）

## 触发条件
- Java 网关调用 task=draft
- 输入包含已确认的大纲

## 输入变量
| 变量 | 必填 | 说明 |
| --- | --- | --- |
| outline | 是 | 已确认的 Markdown 大纲 |
| article_type / style / target_reader | 否 | 体裁、风格、目标读者 |
| instruction | 否 | 用户附加要求，只调整表达 |
| preferences | 否 | 用户写作偏好 JSON |

## 输出契约
- 第一行标题与大纲逐字一致
- 严格按大纲章节顺序与名称输出 `##` 标题
- 每章 1–3 个自然段，全文 1200–2500 字
- 要点不得原样罗列为项目符号

## 文件
- `system.md`：系统提示词
- `user.md`：用户消息模板
