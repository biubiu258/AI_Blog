---
name: "self-insight"
description: "Reviews a user's recent published articles and reports only retrospective observations: themes, recurring motifs, and writing habits. Never gives advice."
---

# AI 洞察式回顾（Self Insight）

## 触发条件
- 用户请求「回顾我最近写了什么」（`POST /v1/self-insight`）

## 输入变量
| 变量 | 必填 | 说明 |
| --- | --- | --- |
| articles_text | 是 | 用户近期公开文章（1–20 篇），以分隔线拼接 |
| article_count | 是 | 文章篇数 |
| locale | 是 | 输出语言环境，默认 zh-CN |

## 输出契约
- 只输出 JSON：`{"summary":"...","themes":[...],"motifs":[...],"habits":[...]}`
- summary：总体回顾，≤ 120 字
- themes：主题脉络数组，每项 ≤ 60 字，最多 6 项
- motifs：反复意象数组，每项 ≤ 60 字，最多 6 项
- habits：写作习惯观察数组，每项 ≤ 60 字，最多 6 项
- 只回顾已存在的写作事实，不评价水平，不建议、不替写、不续写

## 文件
- `system.md`：系统提示词
- `user.md`：用户消息模板
