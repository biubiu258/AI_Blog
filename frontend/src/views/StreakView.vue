<template>
  <div class="page-container">
    <header class="page-head">
      <div>
        <div class="page-title">日更打卡</div>
        <div class="page-sub">WRITING STREAK · 写一点，就点亮一格</div>
      </div>
      <div class="head-actions">
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" :loading="makingUp"
                   :disabled="!summary.makeupCards || summary.checkedInToday"
                   @click="useMakeup">使用补签卡</el-button>
      </div>
    </header>

    <div v-loading="loading" class="streak-body">
      <el-row :gutter="16" class="stat-row">
        <el-col :xs="12" :sm="6">
          <div class="stat-card">
            <div class="stat-value">{{ summary.currentStreak || 0 }}</div>
            <div class="stat-label">当前连续天数</div>
          </div>
        </el-col>
        <el-col :xs="12" :sm="6">
          <div class="stat-card">
            <div class="stat-value">{{ summary.longestStreak || 0 }}</div>
            <div class="stat-label">最长连续记录</div>
          </div>
        </el-col>
        <el-col :xs="12" :sm="6">
          <div class="stat-card">
            <div class="stat-value">{{ summary.makeupCards || 0 }}</div>
            <div class="stat-label">补签卡</div>
          </div>
        </el-col>
        <el-col :xs="12" :sm="6">
          <div class="stat-card">
            <div class="stat-value">{{ summary.checkedInToday ? '已打卡' : '未打卡' }}</div>
            <div class="stat-label">今日状态</div>
          </div>
        </el-col>
      </el-row>

      <section class="calendar-block">
        <div class="block-head">
          <span class="block-title">最近 30 天</span>
          <span class="block-sub">{{ calendarRange }}</span>
          <div class="legend">
            <span class="legend-item"><i class="dot normal" />写作打卡</span>
            <span class="legend-item"><i class="dot makeup" />补签</span>
          </div>
        </div>
        <div class="calendar-grid">
          <el-tooltip v-for="cell in summary.calendar || []" :key="cell.date"
                      :content="cellContent(cell)" placement="top">
            <div class="calendar-cell" :class="{
              active: cell.active,
              makeup: cell.active && cell.source === 'makeup'
            }">
              <span class="cell-day">{{ cell.date.slice(-2) }}</span>
            </div>
          </el-tooltip>
        </div>
        <p class="calendar-note">
          保存或发布文章即当日打卡；断更一天连续天数归零，可用补签卡补签昨天。
          <span v-if="summary.lastActiveDate">最近打卡：{{ summary.lastActiveDate }}</span>
        </p>
      </section>

      <section class="achievement-block">
        <div class="block-head">
          <span class="block-title">连续成就</span>
          <span class="block-sub">ACHIEVEMENTS</span>
        </div>
        <div class="achievement-grid">
          <div v-for="a in summary.achievements || []" :key="a.days" class="achievement-item"
               :class="{ achieved: a.achieved }">
            <div class="achievement-days">{{ a.days }} 天</div>
            <div class="achievement-title">{{ a.title }}</div>
            <div class="achievement-state">{{ a.achieved ? '已达成' : '未达成' }}</div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { streakApi } from '../api'

const loading = ref(false)
const makingUp = ref(false)
const summary = ref({ calendar: [], achievements: [] })

const calendarRange = computed(() => {
  const list = summary.value.calendar || []
  if (!list.length) return ''
  return `${list[0].date} — ${list[list.length - 1].date}`
})

function cellContent(cell) {
  const sourceText = cell.source === 'makeup' ? '补签' : cell.source === 'publish' ? '发布' : '写作'
  return cell.active ? `${cell.date} · ${sourceText}` : `${cell.date} · 未写作`
}

async function load() {
  loading.value = true
  try {
    const data = await streakApi.summary()
    summary.value = {
      ...data,
      calendar: data?.calendar || [],
      achievements: data?.achievements || []
    }
  } catch {
    /* 拦截器已提示 */
  } finally {
    loading.value = false
  }
}

async function useMakeup() {
  makingUp.value = true
  try {
    const data = await streakApi.makeup()
    summary.value = {
      ...data,
      calendar: data?.calendar || [],
      achievements: data?.achievements || []
    }
    ElMessage.success('补签成功，连续天数已更新')
  } catch {
    /* 拦截器已提示 */
  } finally {
    makingUp.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page-head {
  flex-wrap: wrap;
  row-gap: 12px;
}
.head-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.streak-body {
  min-height: 240px;
}

.stat-row {
  margin-bottom: 26px;
}
.stat-card {
  background: var(--paper-card);
  border: 1px solid var(--hairline-dark);
  border-top: 3px solid var(--ink);
  padding: 16px 18px;
  margin-bottom: 12px;
}
.stat-value {
  font-size: 26px;
  font-weight: 900;
  color: var(--ink);
}
.stat-label {
  margin-top: 6px;
  font-size: 12.5px;
  color: var(--ink-faint);
  letter-spacing: 0.08em;
}

.calendar-block,
.achievement-block {
  margin-bottom: 32px;
}
.block-head {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--ink);
  margin-bottom: 16px;
}
.block-title {
  font-size: 18px;
  font-weight: 900;
  letter-spacing: 0.14em;
}
.block-sub {
  font-size: 11.5px;
  color: var(--ink-faint);
  letter-spacing: 0.08em;
}
.legend {
  margin-left: auto;
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: var(--ink-faint);
}
.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}
.dot {
  width: 10px;
  height: 10px;
  display: inline-block;
  border-radius: 1px;
}
.dot.normal {
  background: var(--cinnabar);
}
.dot.makeup {
  background: #b57a22;
}

.calendar-grid {
  display: grid;
  grid-template-columns: repeat(10, 1fr);
  gap: 6px;
}
.calendar-cell {
  aspect-ratio: 1 / 1;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--hairline);
  background: var(--paper-card);
  color: var(--ink-faint);
  font-size: 12px;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}
.calendar-cell:hover {
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(60, 50, 30, 0.12);
}
.calendar-cell.active {
  background: var(--cinnabar);
  border-color: var(--cinnabar);
  color: #fdfbf4;
  font-weight: 700;
}
.calendar-cell.active.makeup {
  background: #b57a22;
  border-color: #b57a22;
}
.calendar-note {
  margin: 12px 0 0;
  font-size: 12.5px;
  line-height: 1.8;
  color: var(--ink-faint);
}
.calendar-note span {
  margin-left: 10px;
}

.achievement-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 12px;
}
.achievement-item {
  padding: 14px 16px;
  border: 1px dashed var(--hairline-dark);
  background: rgba(253, 251, 244, 0.55);
  color: var(--ink-faint);
}
.achievement-item.achieved {
  border: 1px solid var(--cinnabar);
  background: var(--cinnabar-wash);
  color: var(--ink);
}
.achievement-days {
  font-size: 20px;
  font-weight: 900;
  letter-spacing: 0.04em;
}
.achievement-title {
  margin-top: 4px;
  font-size: 14px;
  font-weight: 600;
}
.achievement-state {
  margin-top: 6px;
  font-size: 12px;
  letter-spacing: 0.08em;
}
.achievement-item.achieved .achievement-state {
  color: var(--cinnabar);
}

@media (max-width: 720px) {
  .calendar-grid {
    grid-template-columns: repeat(6, 1fr);
  }
  .legend {
    margin-left: 0;
    width: 100%;
  }
}
</style>
