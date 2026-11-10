---
name: "style-profiler"
description: "Distills a user's writing style features from 3-10 sample articles and returns strict JSON. Use when the style-profile endpoint is called."
---

# 文风提炼（Style Profiler）

## 触发条件
- 用户在写作页请求「提炼我的文风」（`POST /v1/style-profile`）

## 输入变量
| 变量 | 必填 | 说明 |
| --- | --- | --- |
| samples_text | 是 | 用户旧文样本（3–10 篇），以分隔线拼接 |
| sample_count | 是 | 样本篇数 |

## 输出契约
- 只输出 JSON：`{"summary":"...","features":{...}}`
- summary：一句话风格摘要，≤ 80 字
- features 固定七个键：tone / diction / sentence / imagery / themes / structure / avoid，值均为字符串（每键 ≤ 80 字）
- 只描述客观可复现的语言特征，不做水平评价，不给写作建议

## 文件
- `system.md`：系统提示词
- `user.md`：用户消息模板
