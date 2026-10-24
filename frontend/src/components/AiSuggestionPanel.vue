<template>
  <div class="suggestion-panel">
    <div v-if="loading" class="panel-loading">
      <el-icon class="is-loading"><Loading /></el-icon>
      正在审阅全文…
    </div>
    <el-empty v-else-if="!suggestions.length" :description="emptyText" :image-size="60" />
    <div v-else class="suggestion-list">
      <div v-for="(s, i) in suggestions" :key="`${s.quote}-${i}`" class="suggestion-item"
           :class="{ stale: s.found === false }">
        <div class="item-head">
          <el-tag size="small" :type="severityType(s.severity)">{{ severityLabel(s.severity) }}</el-tag>
          <span class="type-label">{{ typeLabel(s.type) }}</span>
          <span class="item-actions">
            <el-button link size="small" :disabled="s.found === false" @click="emit('locate', i)">
              定位
            </el-button>
            <el-button link size="small" type="primary"
                       :disabled="!s.replacement || s.found === false" @click="emit('apply', i)">
              替换
            </el-button>
            <el-button link size="small" @click="emit('ignore', i)">忽略</el-button>
          </span>
        </div>
        <blockquote class="quote">{{ s.quote }}</blockquote>
        <div class="reason">{{ s.reason }}</div>
        <div v-if="s.replacement" class="replacement">
          <span class="replacement-label">建议改为</span>
          <span>{{ s.replacement }}</span>
        </div>
        <div v-if="s.found === false" class="stale-tip">原文已修改，本条无法定位</div>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  suggestions: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  emptyText: { type: String, default: '暂无建议，点击上方按钮审阅全文' }
})

const emit = defineEmits(['locate', 'apply', 'ignore'])

const TYPE_LABELS = {
  expression: '表达',
  structure: '结构',
  repetition: '重复',
  opening: '开头',
  ending: '结尾',
  logic: '逻辑'
}

function typeLabel(type) {
  return TYPE_LABELS[type] || '表达'
}

function severityLabel(severity) {
  if (severity === 'high') return '重要'
  if (severity === 'low') return '轻微'
  return '一般'
}

function severityType(severity) {
  if (severity === 'high') return 'danger'
  if (severity === 'low') return 'info'
  return 'warning'
}
</script>

<style scoped>
.panel-loading {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 14px 4px;
  color: var(--ink-faint);
  font-size: 13px;
  letter-spacing: 0.05em;
}
.suggestion-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.suggestion-item {
  border: 1px solid var(--hairline);
  border-left: 3px solid var(--cinnabar);
  padding: 10px 12px;
  background: var(--paper-deep);
}
.suggestion-item.stale {
  border-left-color: var(--ink-faint);
  opacity: 0.72;
}
.item-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.type-label {
  font-size: 12px;
  color: var(--ink-faint);
  letter-spacing: 0.08em;
}
.item-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 2px;
}
.quote {
  margin: 8px 0 6px;
  padding-left: 10px;
  border-left: 2px solid var(--hairline-dark);
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 13.5px;
  line-height: 1.7;
}
.reason {
  font-size: 13px;
  line-height: 1.65;
  color: var(--ink);
  letter-spacing: 0.02em;
}
.replacement {
  margin-top: 6px;
  padding: 6px 8px;
  background: rgba(160, 60, 40, 0.05);
  border: 1px dashed var(--hairline-dark);
  font-size: 13px;
  line-height: 1.65;
}
.replacement-label {
  color: var(--cinnabar);
  margin-right: 6px;
  font-size: 12px;
  letter-spacing: 0.06em;
}
.stale-tip {
  margin-top: 6px;
  font-size: 12px;
  color: var(--ink-faint);
}
</style>
