---
name: "prose-liangshiqiu-writer"
description: "Writes a Chinese essay in Liang Shiqiu's essay style (public-domain writer). Use when the prose task selects the liangshiqiu style."
---

# 梁实秋风格散文（Prose Liangshiqiu Writer）

## 触发条件
- 散文任务 `task=prose` 且 `params.style` 命中梁实秋风格

## 输入变量
见 `_shared/prose.user.md`：情绪、近况、图片、文体判定结果。

## 输出契约
- 第一行 `# 标题`，其后为正文；不写创作说明
- 模仿梁实秋散文的一般文风特征（见 system.md），不逐字引用原文

## 文件
- `system.md`：系统提示词
- 用户消息复用 `_shared/prose.user.md`
