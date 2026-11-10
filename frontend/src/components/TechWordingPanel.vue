<template>
  <div class="wording-panel">
    <el-alert class="ai-notice" type="info" :closable="false" show-icon
              title="AI 辅助建议，请人工确认；只有点击「替换」才会改动正文" />

    <div class="panel-actions">
      <el-button size="small" type="primary" :loading="loading"
                 :disabled="loading || !hasSelection" @click="$emit('check')">
        检查选中段落
      </el-button>
      <span class="action-hint">
        {{ hasSelection ? `已选 ${selectedText.length} 字` : '请先在正文中选中一段文字' }}
      </span>
    </div>

    <el-alert v-if="degraded" class="degraded-tip" type="warning" :closable="false" show-icon
              :title="message || '依据检索暂不可用，仅展示术语规则'" />

    <blockquote v-if="hasSelection" class="pane-quote">{{ selectedText }}</blockquote>

    <div v-if="loading" class="stream-tip">
      <el-icon class="is-loading"><Loading /></el-icon>
      正在检查术语与措辞…
    </div>

    <el-empty v-else-if="checked && !issues.length" :image-size="52"
              description="未发现需要调整的表述" />

    <div v-else-if="!checked && !issues.length" class="hint">
      选中段落后点击「检查选中段落」，只返回有依据的问题，不会全文扫描。
    </div>

    <div class="issue-list">
      <article v-for="(issue, index) in issues" :key="issue.id || index" class="issue-card">
        <div class="issue-head">
          <el-tag size="small" :type="typeTone(issue.type)" effect="plain">
            {{ typeLabel(issue.type) }}
          </el-tag>
          <el-tag v-if="issue.severity" size="small" type="info" effect="plain">
            {{ severityLabel(issue.severity) }}
          </el-tag>
        </div>

        <div class="issue-body">
          <div class="quote-line">
            <span class="quote-mark">原文</span>
            <span class="quote-text">{{ issue.quote }}</span>
          </div>
          <div v-if="issue.replacement" class="quote-line">
            <span class="quote-mark suggest">建议</span>
            <span class="replace-text">{{ issue.replacement }}</span>
          </div>
          <p class="reason">{{ issue.reason || '——' }}</p>
        </div>

        <el-collapse v-if="issue.evidence" class="evidence">
          <el-collapse-item title="查看依据">
            <div class="evidence-meta">
              <span>来源：{{ issue.evidence.title || '未命名语料' }}</span>
              <span v-if="issue.evidence.version">版本 {{ issue.evidence.version }}</span>
              <span v-if="issue.evidence.sectionPath">章节 {{ issue.evidence.sectionPath }}</span>
              <span v-if="issue.evidence.pageNo">第 {{ issue.evidence.pageNo }} 页</span>
            </div>
            <blockquote class="evidence-snippet">{{ issue.evidence.snippet }}</blockquote>
            <el-button link type="primary" size="small" @click="copyEvidence(issue.evidence)">
              复制依据
            </el-button>
          </el-collapse-item>
        </el-collapse>

        <div class="issue-actions">
          <el-button link size="small" type="primary" @click="$emit('locate', index)">定位</el-button>
          <el-button v-if="issue.replacement" size="small" type="primary"
                     @click="$emit('apply', index)">替换</el-button>
          <el-button size="small" @click="$emit('ignore', index)">忽略</el-button>
        </div>
      </article>
    </div>
  </div>
</template>

<script setup>
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'

defineProps({
  issues: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  degraded: { type: Boolean, default: false },
  message: { type: String, default: '' },
  hasSelection: { type: Boolean, default: false },
  selectedText: { type: String, default: '' },
  checked: { type: Boolean, default: false }
})

defineEmits(['check', 'locate', 'apply', 'ignore'])

const TYPE_LABELS = {
  terminology: '术语',
  fact: '事实',
  collocation: '搭配',
  clarity: '清晰度'
}
const TYPE_TONES = {
  terminology: 'warning',
  fact: 'danger',
  collocation: 'info',
  clarity: 'success'
}
const SEVERITY_LABELS = { high: '高', mid: '中', low: '低' }

function typeLabel(type) {
  return TYPE_LABELS[type] || type || '建议'
}

function typeTone(type) {
  return TYPE_TONES[type] || 'info'
}

function severityLabel(severity) {
  return SEVERITY_LABELS[severity] || severity
}

async function copyEvidence(evidence) {
  const parts = [
    `来源：${evidence.title || '未命名语料'}`,
    evidence.sourceName ? `出处：${evidence.sourceName}` : '',
    evidence.version ? `版本：${evidence.version}` : '',
    evidence.sectionPath ? `章节：${evidence.sectionPath}` : '',
    evidence.pageNo ? `页码：第 ${evidence.pageNo} 页` : '',
    evidence.snippet ? `片段：${evidence.snippet}` : ''
  ].filter(Boolean)
  try {
    await navigator.clipboard.writeText(parts.join('\n'))
    ElMessage.success('依据已复制')
  } catch {
    ElMessage.warning('复制失败，请手动选择文本')
  }
}
</script>

<style scoped>
.wording-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.ai-notice {
  margin-bottom: 2px;
}
.panel-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.action-hint {
  font-size: 12px;
  color: var(--ink-faint);
}
.degraded-tip {
  margin-top: 2px;
}
.pane-quote {
  margin: 0;
  padding: 10px 12px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--ink-soft);
  background: var(--paper-card);
  border-left: 3px solid var(--cinnabar);
  max-height: 140px;
  overflow-y: auto;
  white-space: pre-wrap;
}
.stream-tip {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  color: var(--ink-faint);
}
.hint {
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--ink-faint);
}
.issue-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.issue-card {
  padding: 12px;
  background: var(--paper-card);
  border: 1px solid var(--hairline);
  border-left: 3px solid var(--ink);
}
.issue-head {
  display: flex;
  align-items: center;
  gap: 6px;
}
.issue-body {
  margin-top: 8px;
}
.quote-line {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 13px;
  line-height: 1.7;
}
.quote-line + .quote-line {
  margin-top: 4px;
}
.quote-mark {
  flex: 0 0 auto;
  padding: 0 6px;
  font-size: 11px;
  line-height: 18px;
  color: var(--ink-faint);
  background: rgba(120, 110, 90, 0.08);
}
.quote-mark.suggest {
  color: var(--cinnabar);
  background: rgba(180, 60, 40, 0.08);
}
.quote-text {
  text-decoration: line-through;
  color: var(--ink-soft);
  word-break: break-all;
}
.replace-text {
  font-weight: 700;
  color: var(--ink);
  word-break: break-all;
}
.reason {
  margin: 8px 0 0;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--ink-faint);
}
.evidence {
  margin-top: 6px;
}
.evidence-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  font-size: 12px;
  color: var(--ink-faint);
}
.evidence-snippet {
  margin: 8px 0;
  padding: 8px 10px;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--ink-soft);
  background: rgba(120, 110, 90, 0.06);
  white-space: pre-wrap;
}
.issue-actions {
  display: flex;
  justify-content: flex-end;
  gap: 4px;
  margin-top: 8px;
}
</style>
