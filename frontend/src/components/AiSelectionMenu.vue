<template>
  <div v-if="visible" class="ai-selection-menu"
       :style="{ left: x + 'px', top: y + 'px' }"
       @mousedown.prevent>
    <span class="menu-label">问 AI</span>
    <button v-for="item in items" :key="item.mode" class="menu-btn"
            :disabled="loading" @click="emit('ask', item.mode)">
      {{ item.label }}
    </button>
  </div>
</template>

<script setup>
defineProps({
  visible: { type: Boolean, default: false },
  x: { type: Number, default: 0 },
  y: { type: Number, default: 0 },
  loading: { type: Boolean, default: false }
})

const emit = defineEmits(['ask'])

const items = [
  { mode: 'advice', label: '给建议' },
  { mode: 'polish', label: '润色' },
  { mode: 'rewrite', label: '改写' },
  { mode: 'expand', label: '扩写' },
  { mode: 'condense', label: '缩写' }
]
</script>

<style scoped>
.ai-selection-menu {
  position: fixed;
  z-index: 3000;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 5px 8px;
  background: var(--paper-card);
  border: 1px solid var(--hairline-dark);
  border-radius: 2px;
  box-shadow: 3px 3px 0 rgba(38, 36, 30, 0.12);
  font-family: var(--font-serif);
  white-space: nowrap;
}
.menu-label {
  font-size: 12px;
  color: var(--ink-faint);
  letter-spacing: 0.08em;
  margin-right: 2px;
}
.menu-btn {
  padding: 3px 8px;
  border: none;
  background: transparent;
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 12.5px;
  letter-spacing: 0.05em;
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease;
}
.menu-btn:hover:not(:disabled) {
  background: var(--cinnabar);
  color: #fdfbf4;
}
.menu-btn:disabled {
  color: var(--ink-faint);
  cursor: not-allowed;
}
</style>
