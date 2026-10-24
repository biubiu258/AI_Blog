---
name: "text-continuer"
description: "Continues a passage from its tail without repeating existing text. Use when the continue task is requested for an article."
---

# 续写（Text Continuer）

## 触发条件
- Java 网关调用 task=continue
- 输入包含原文末尾片段

## 输入变量
| 变量 | 必填 | 说明 |
| --- | --- | --- |
| material_text | 是 | 原文末尾片段，不超过 8000 字 |
| instruction | 否 | 用户附加要求，只调整续写方向 |

## 输出契约
- 只输出新增内容，300–800 字
- 不重复原文句子，不输出标题与衔接套话
- 沿用原文人称、语气与叙事视角

## 文件
- `system.md`：系统提示词
- `user.md`：用户消息模板
