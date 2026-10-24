---
name: "article-reviewer"
description: "Reviews article text and returns up to six anchored JSON suggestions. Use when the review endpoint is called before publishing."
---

# 全文审阅（Article Reviewer）

## 触发条件
- 写作页或散文页请求 `POST /api/ai/review`
- 或用户手动点击“审阅全文”

## 输入变量
| 变量 | 必填 | 说明 |
| --- | --- | --- |
| content | 是 | 全文纯文本，≤20000 字 |
| title | 否 | 文章标题 |
| article_type | 否 | 文章类型 |

## 输出契约
- 只输出 JSON：`{"suggestions":[...]}`，最多 6 条
- 每条包含 type、severity、quote、reason、replacement
- quote 必须逐字复制正文、6–120 字、单段且全文唯一
- 无建议时输出 `{"suggestions": []}`

## 文件
- `system.md`：系统提示词
- `user.md`：用户消息模板
