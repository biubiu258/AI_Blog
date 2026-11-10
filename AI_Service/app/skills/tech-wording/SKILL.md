---
name: "tech-wording"
description: "Checks a selected technical paragraph for terminology, factual accuracy, collocation and clarity, returning anchored JSON issues backed by retrieved evidence."
---

# 技术术语与措辞检查（Tech Wording）

## 触发条件
- 写作页「术语与措辞」面板请求 `POST /api/tech/assist/wording`
- 只检查用户选中的一段文字，不扫描全文

## 输入变量
| 变量 | 必填 | 说明 |
| --- | --- | --- |
| text | 是 | 待检查段落，20–1500 字 |
| section_path | 否 | 当前章节路径 |
| evidence_text | 是 | 检索到的技术语料片段（可能为空） |

## 输出契约
- 只输出 JSON：`{"issues":[...]}`，最多 8 条
- 每条包含 id、type、quote、replacement、reason、severity、evidence_chunk_id
- type 仅 `terminology` / `fact` / `collocation` / `clarity`
- `fact` 必须引用本次提供的证据；没有证据时不得输出 fact
- quote 必须逐字复制且在本段唯一；replacement ≤ 200 字
- 无问题时输出 `{"issues": []}`

## 文件
- `system.md`：系统提示词
- `user.md`：用户消息模板
