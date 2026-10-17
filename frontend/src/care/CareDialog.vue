<template>
  <el-dialog v-model="visible" width="420px" align-center :show-close="true"
             append-to-body class="care-dialog">
    <div class="letter">
      <p class="letter-eyebrow">A SHORT LETTER · 一封短笺</p>
      <div class="letter-main">
        <p class="letter-quote">{{ quote.text }}</p>
        <div class="letter-seal">安</div>
      </div>
      <p class="letter-from">{{ quote.from }}</p>
      <p class="letter-text">{{ message }}</p>
      <div class="letter-actions">
        <el-button @click="stay">去走走</el-button>
        <el-button @click="writeSomething">写点别的</el-button>
        <el-button type="primary" plain @click="goHotline">看看热线</el-button>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
// mid：发布成功后的「一封短笺」弹窗，轻推动作、不打断发布
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { onCare } from './careBus'
import { pickQuote } from './quotes'

const router = useRouter()
const visible = ref(false)
const quote = ref(pickQuote())
const message = ref('')

function show(payload) {
  if (payload?.level !== 'mid') return
  quote.value = pickQuote()
  message.value = payload.summary ||
    '最近的文字里似乎藏着一些沉重。谢谢你愿意写下来——写出来本身就是一种勇敢。'
  visible.value = true
}

function stay() {
  visible.value = false
  ElMessage.info('去晒晒太阳，喝口热水，回来看看也好。')
}

function writeSomething() {
  visible.value = false
  router.push('/prose')
}

function goHotline() {
  visible.value = false
  router.push('/care/crisis')
}

let offCareFn = null

onMounted(() => { offCareFn = onCare(show) })
onBeforeUnmount(() => { offCareFn?.() })
</script>

<style scoped>
.letter {
  text-align: center;
  padding: 8px 8px 4px;
  font-family: var(--font-serif, serif);
}
.letter-eyebrow {
  margin: 0 0 18px;
  font-size: 10px;
  letter-spacing: 0.32em;
  color: var(--ink-faint, #9a937f);
}
.letter-main {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 22px;
  min-height: 150px;
}
.letter-quote {
  margin: 0;
  writing-mode: vertical-rl;
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 0.18em;
  line-height: 2;
  color: var(--ink, #26241e);
  max-height: 150px;
}
.letter-seal {
  width: 40px;
  height: 40px;
  background: var(--cinnabar, #a63b2a);
  color: #fdfbf4;
  font-size: 22px;
  font-weight: 900;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: inset 0 0 0 2px rgba(253, 251, 244, 0.35), 2px 2px 0 rgba(38, 36, 30, 0.15);
}
.letter-from {
  margin: 14px 0 12px;
  font-size: 11px;
  letter-spacing: 0.24em;
  color: var(--ink-faint, #9a937f);
}
.letter-text {
  margin: 0 auto 20px;
  max-width: 300px;
  font-size: 13.5px;
  line-height: 1.9;
  color: var(--ink-soft, #54503f);
}
.letter-actions {
  display: flex;
  justify-content: center;
  gap: 10px;
}
</style>
