---
name: "outline-writer"
description: "Generates a Markdown article outline with a title and 6-10 numbered sections. Use when the outline task is requested from the writing workspace."
---

# 大纲生成（Outline Writer）

## 触发条件
- Java 网关调用 task=outline
- 用户已提供主题，尚未生成大纲

## 输入变量
| 变量 | 必填 | 说明 |
| --- | --- | --- |
| topic | 是 | 文章主题 |
| article_type / style / target_reader | 否 | 体裁、风格、目标读者 |
| instruction | 否 | 用户附加要求，只调整方向 |

## 输出契约
- 第一行 `# 标题`，不超过 30 字
- 其后 6–10 个 `## 一、章节名` 形式的二级章节
- 每章 2–4 条 `- ` 要点，不写正文段落
- 不输出解释、推荐语与字数说明

## 文件
- `system.md`：系统提示词
- `user.md`：用户消息模板
