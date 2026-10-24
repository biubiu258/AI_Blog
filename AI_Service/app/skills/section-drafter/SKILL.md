---
name: "section-drafter"
description: "Writes one section body from a fixed outline section and bullets. Use when draft is split into section-level generation steps."
---

# 单节生成（Section Drafter）

## 触发条件
- task=draft 且大纲超过单次上限，编排器按章节拆分
- 每次只生成大纲中的一节

## 输入变量
| 变量 | 必填 | 说明 |
| --- | --- | --- |
| title | 是 | 文章标题 |
| section_title | 是 | 当前章节名 |
| bullets | 是 | 当前章节要点，可能为占位文案 |
| previous_section / next_section | 否 | 前后章节名，用于衔接 |
| article_type / style / target_reader | 否 | 体裁、风格、目标读者 |
| instruction | 否 | 用户附加要求 |

## 输出契约
- 只输出本节正文，不输出任何标题
- 1–3 个自然段，200–600 字
- 不引入其它章节主题，不写过渡套话

## 文件
- `system.md`：系统提示词
- `user.md`：用户消息模板
