<template>
  <div class="suggestion-panel">
    <div v-if="loading" class="panel-loading">
      <el-icon class="is-loading"><Loading /></el-icon>
      正在审阅全文…
    </div>
    <el-empty v-else-if="!suggestions.length" :description="emptyText" :image-size="60" />
    <div v-else class="suggestion-list">
      <div v-for="g in groups" :key="g.type" class="suggestion-group">
        <div class="group-head">
          <span class="group-dot" :style="{ background: g.color }" />
          <span class="group-title" :style="{ color: g.color }">{{ g.label }}</span>
          <span class="group-count">{{ g.items.length }}</span>
        </div>
        <div v-for="s in g.items" :key="`${g.type}-${s.__index}`" class="suggestion-item"
             :class="[`sev-${s.severity || 'mid'}`, { stale: s.found === false }]">
          <div class="item-head">
            <el-tag size="small" :type="severityType(s.severity)">{{ severityLabel(s.severity) }}</el-tag>
            <span class="type-label" :style="{ color: g.color }">{{ g.label }}</span>
            <span class="item-actions">
              <el-button link size="small" :disabled="s.found === false" @click="emit('locate', s.__index)">
                定位
              </el-button>
              <el-button link size="small" type="primary"
                         :disabled="!s.replacement || s.found === false" @click="emit('apply', s.__index)">
                替换
              </el-button>
              <el-button link size="small" @click="emit('ignore', s.__index)">忽略</el-button>
            </span>
          </div>
          <blockquote class="quote">{{ s.quote }}</blockquote>
          <div class="reason">{{ s.reason }}</div>
          <div v-if="s.replacement" class="replacement">
            <span class="replacement-label">建议改为</span>
            <span class="replacement-text">{{ s.replacement }}</span>
          </div>
          <div v-if="s.found === false" class="stale-tip">原文已修改，本条无法定位</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  suggestions: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  emptyText: { type: String, default: '暂无建议，点击上方按钮审阅全文' }
})

const emit = defineEmits(['locate', 'apply', 'ignore'])

// 建议类型：中文名 + 分组色（红=错别字/病句，橙=陈词滥调/冗余/重复，蓝=表达，紫=结构/逻辑，青=开头/结尾）
const TYPE_META = {
  typo: { label: '错别字', color: '#c0453a' },
  grammar: { label: '病句', color: '#c0453a' },
  cliche: { label: '陈词滥调', color: '#d98a2b' },
  redundancy: { label: '冗余', color: '#d98a2b' },
  repetition: { label: '重复', color: '#d98a2b' },
  expression: { label: '表达', color: '#3a6ea5' },
  structure: { label: '结构', color: '#7c5cad' },
  logic: { label: '逻辑', color: '#7c5cad' },
  opening: { label: '开头', color: '#2e8b8b' },
  ending: { label: '结尾', color: '#2e8b8b' }
}

/** 按固定顺序分组，组内保留原数组下标供定位/替换使用 */
const groups = computed(() => {
  const map = new Map()
  props.suggestions.forEach((s, i) => {
    const type = TYPE_META[s.type] ? s.type : 'expression'
    if (!map.has(type)) map.set(type, [])
    map.get(type).push({ ...s, __index: i })
  })
  return Object.keys(TYPE_META)
    .filter((t) => map.has(t))
    .map((t) => ({ type: t, ...TYPE_META[t], items: map.get(t) }))
})

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
  gap: 14px;
}
.group-head {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-bottom: 6px;
}
.group-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}
.group-title {
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 0.1em;
}
.group-count {
  font-size: 12px;
  color: var(--ink-faint);
  font-variant-numeric: tabular-nums;
}
.suggestion-item {
  border: 1px solid var(--hairline);
  border-left: 3px solid var(--hairline-dark);
  padding: 10px 12px;
  margin-bottom: 8px;
  background: var(--paper-deep);
}
.suggestion-item:last-child {
  margin-bottom: 0;
}
/* severity 影响文字深浅：high 加重，low 减淡 */
.suggestion-item.sev-high {
  border-left-color: var(--cinnabar);
  background: var(--paper-card);
}
.suggestion-item.sev-high .reason {
  font-weight: 600;
}
.suggestion-item.sev-low {
  opacity: 0.78;
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
  font-weight: 600;
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
  background: rgba(74, 140, 92, 0.06);
  border: 1px dashed rgba(74, 140, 92, 0.4);
  font-size: 13px;
  line-height: 1.65;
}
.replacement-label {
  color: var(--ink-faint);
  margin-right: 6px;
  font-size: 12px;
  letter-spacing: 0.06em;
}
.replacement-text {
  color: #3c7a4e;
}
.stale-tip {
  margin-top: 6px;
  font-size: 12px;
  color: var(--ink-faint);
}
</style>
