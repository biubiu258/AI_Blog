---
name: "care-guardian"
description: "Classifies emotional risk in article text as none, low, mid, or high. Use when pre-publish care review calls the model."
---

# 情绪风险分级（Care Guardian）

## 触发条件
- 文章发布前，规则初筛后调用模型复核
- 超时或异常时回退规则结果，不阻断发布流程

## 输入变量
| 变量 | 必填 | 说明 |
| --- | --- | --- |
| text | 是 | 待复核的文章文本 |

## 输出契约
- 只输出一个 JSON 对象：`{"level":"none|low|mid|high"}`
- 不输出解释、前后缀、Markdown 或其它字段
- 否定句、假设句、转述与引用不判 high

## 文件
- `system.md`：系统提示词
- `user.md`：用户消息模板
