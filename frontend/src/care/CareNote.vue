<template>
  <transition name="care-note-fade">
    <div v-if="visible" class="care-note" role="status">
      <div class="note-rule"></div>
      <div class="note-body">
        <p class="note-quote">「{{ quote.text }}」</p>
        <p class="note-from">{{ quote.from }}</p>
        <p class="note-text">{{ message }}</p>
      </div>
      <button class="note-close" aria-label="关闭" @click="visible = false">×</button>
    </div>
  </transition>
</template>

<script setup>
// low：发布成功后的信笺条，不打断、8s 自动淡出
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { onCare } from './careBus'
import { pickQuote } from './quotes'

const visible = ref(false)
const quote = ref(pickQuote())
const message = ref('')
let timer = null

function show(payload) {
  if (payload?.level !== 'low') return
  quote.value = pickQuote()
  message.value = payload.summary ||
    '听起来最近似乎不容易。文章已经发出去了，也请照顾好自己的情绪。'
  visible.value = true
  clearTimeout(timer)
  timer = setTimeout(() => { visible.value = false }, 8000)
}

let offCareFn = null

onMounted(() => { offCareFn = onCare(show) })
onBeforeUnmount(() => { offCareFn?.(); clearTimeout(timer) })
</script>

<style scoped>
.care-note {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 2000;
  display: flex;
  max-width: 360px;
  background: var(--paper-card, #fdfbf4);
  border: 1px solid var(--hairline-dark, #d8d2c2);
  box-shadow: 4px 4px 0 rgba(38, 36, 30, 0.08);
  animation: note-in 0.5s cubic-bezier(0.22, 0.8, 0.32, 1) both;
}
.note-rule {
  width: 3px;
  background: var(--cinnabar, #a63b2a);
  flex-shrink: 0;
}
.note-body {
  padding: 16px 14px 14px 16px;
  font-family: var(--font-serif, serif);
}
.note-quote {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.06em;
  color: var(--ink, #26241e);
  line-height: 1.7;
}
.note-from {
  margin: 4px 0 10px;
  font-size: 11px;
  letter-spacing: 0.2em;
  color: var(--ink-faint, #9a937f);
}
.note-text {
  margin: 0;
  font-size: 13px;
  line-height: 1.8;
  color: var(--ink-soft, #54503f);
}
.note-close {
  align-self: flex-start;
  margin: 8px 8px 0 0;
  border: none;
  background: none;
  font-size: 16px;
  line-height: 1;
  color: var(--ink-faint, #9a937f);
  cursor: pointer;
}
.note-close:hover {
  color: var(--ink, #26241e);
}

@keyframes note-in {
  from { opacity: 0; transform: translateY(12px); }
  to { opacity: 1; transform: translateY(0); }
}
.care-note-fade-leave-active {
  transition: opacity 0.8s ease, transform 0.8s ease;
}
.care-note-fade-leave-to {
  opacity: 0;
  transform: translateY(8px);
}
</style>
