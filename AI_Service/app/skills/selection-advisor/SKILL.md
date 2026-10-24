---
name: "selection-advisor"
description: "Advises or rewrites a user-selected passage with surrounding context. Use when the suggest task receives selected text."
---

# 划词建议（Selection Advisor）

## 触发条件
- 写作页或散文页选中文字后调用 task=suggest
- context.selected_text 非空

## 输入变量
| 变量 | 必填 | 说明 |
| --- | --- | --- |
| selected_text | 是 | 选中片段，≤2000 字 |
| text_before / text_after | 否 | 前后文各 ≤300 字 |
| suggestion_mode | 是 | advice / polish / rewrite / expand / condense |
| mode_label / length_hint | 是 | 来自 `_fragments/suggest-modes.yaml` |
| topic / article_type | 否 | 文章标题与类型 |
| instruction | 否 | 用户附加要求 |

## 输出契约
- advice：1–3 条以 `- ` 开头的要点，每条 ≤60 字，不改写整段
- 其他模式：只输出改后片段，无标题、引号、前言、代码围栏
- 输出不得被尾部元评论清洗逻辑截断

## 文件
- `system.md`：系统提示词
- `user.md`：用户消息模板
