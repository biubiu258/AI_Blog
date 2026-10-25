<template>
  <div class="page-container">
    <header class="page-head">
      <div>
        <div class="page-title">创作数据</div>
        <div class="page-sub">AUTHOR DASHBOARD</div>
      </div>
      <div class="head-actions">
        <el-button @click="$router.push('/my')">我的文章</el-button>
        <el-button type="primary" @click="$router.push('/write')">写文章</el-button>
      </div>
    </header>

    <div v-loading="loading">
      <div class="stat-grid">
        <div v-for="card in cards" :key="card.label" class="stat-card">
          <div class="stat-value">{{ card.value }}</div>
          <div class="stat-label">{{ card.label }}</div>
          <div class="stat-note">{{ card.note }}</div>
        </div>
      </div>

      <div class="panel-grid">
        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="card-head">
              <span class="card-title">近 7 日阅读</span>
              <span class="card-sub">VIEWS · {{ stats.views7d || 0 }} 次</span>
            </div>
          </template>
          <div class="trend">
            <div v-for="p in stats.trend || []" :key="p.date" class="trend-col">
              <span class="trend-count">{{ p.count }}</span>
              <div class="trend-bar" :style="{ height: barHeight(p.count) }" />
              <span class="trend-date">{{ p.date.slice(5) }}</span>
            </div>
          </div>
        </el-card>

        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="card-head">
              <span class="card-title">热门文章</span>
              <span class="card-sub">TOP 5</span>
            </div>
          </template>
          <el-empty v-if="!(stats.topArticles || []).length" description="还没有已发布文章" />
          <ol v-else class="top-list">
            <li v-for="(a, i) in stats.topArticles" :key="a.id" class="top-item"
                @click="$router.push(`/article/${a.id}`)">
              <span class="top-rank">{{ pad(i + 1) }}</span>
              <div class="top-main">
                <div class="top-title">{{ a.title }}</div>
                <div class="top-meta">
                  <span>{{ a.viewCount }} 阅读</span>
                  <span>{{ a.likeCount }} 赞</span>
                  <span>{{ a.favoriteCount }} 收藏</span>
                </div>
              </div>
            </li>
          </ol>
        </el-card>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { statsApi } from '../api'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const loading = ref(false)
const stats = ref({})

const cards = computed(() => [
  { label: '已发布', value: stats.value.publishedCount || 0, note: '公开可见' },
  { label: '草稿', value: stats.value.draftCount || 0, note: '待完成' },
  { label: '定时', value: stats.value.scheduledCount || 0, note: '等待发布' },
  { label: '仅自己可见', value: stats.value.privateCount || 0, note: '关怀保护' },
  { label: '总阅读', value: stats.value.totalViews || 0, note: '累计' },
  { label: '获赞', value: stats.value.likeCount || 0, note: '全部文章' },
  { label: '获收藏', value: stats.value.favoriteCount || 0, note: '全部文章' },
  { label: '关注者', value: stats.value.followerCount || 0, note: '粉丝' }
])

const maxCount = computed(() =>
  Math.max(1, ...(stats.value.trend || []).map((p) => p.count || 0)))

function barHeight(count) {
  const ratio = (count || 0) / maxCount.value
  return `${Math.max(ratio * 120, count ? 6 : 2)}px`
}

function pad(n) {
  return String(n).padStart(2, '0')
}

onMounted(async () => {
  if (!auth.user?.id) return
  loading.value = true
  try {
    stats.value = await statsApi.author(auth.user.id)
  } catch {
    stats.value = {}
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.head-actions {
  display: flex;
  gap: 10px;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 14px;
  margin-bottom: 24px;
}

.stat-card {
  padding: 18px 18px 14px;
  background: var(--paper-card);
  border-top: 3px solid var(--ink);
}

.stat-value {
  font-size: 30px;
  font-weight: 900;
  line-height: 1.2;
  letter-spacing: 0.02em;
}

.stat-label {
  margin-top: 6px;
  font-size: 13px;
  letter-spacing: 0.12em;
  color: var(--ink-soft);
}

.stat-note {
  margin-top: 2px;
  font-size: 11px;
  letter-spacing: 0.14em;
  color: var(--ink-faint);
}

.panel-grid {
  display: grid;
  grid-template-columns: 1.1fr 0.9fr;
  gap: 20px;
}

@media (max-width: 880px) {
  .panel-grid {
    grid-template-columns: 1fr;
  }
}

.panel-card {
  border-top: 3px solid var(--ink);
}

.card-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}
.card-title {
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0.12em;
}
.card-sub {
  font-size: 11px;
  letter-spacing: 0.2em;
  color: var(--ink-faint);
}

.trend {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  min-height: 180px;
  padding: 10px 4px 0;
}

.trend-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}

.trend-count {
  font-size: 12px;
  color: var(--ink-faint);
}

.trend-bar {
  width: 100%;
  max-width: 34px;
  background: var(--ink);
  transition: height 0.4s ease;
}

.trend-date {
  font-size: 11.5px;
  letter-spacing: 0.08em;
  color: var(--ink-faint);
}

.top-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.top-item {
  display: flex;
  gap: 14px;
  padding: 14px 2px;
  border-bottom: 1px solid var(--hairline);
  cursor: pointer;
  transition: background 0.2s ease;
}
.top-item:hover {
  background: rgba(253, 251, 244, 0.65);
}
.top-item:hover .top-title {
  color: var(--cinnabar);
}

.top-rank {
  width: 30px;
  flex-shrink: 0;
  font-size: 20px;
  font-weight: 900;
  color: var(--hairline-dark);
}

.top-main {
  min-width: 0;
}

.top-title {
  font-size: 15px;
  font-weight: 700;
  line-height: 1.6;
  transition: color 0.2s ease;
}

.top-meta {
  display: flex;
  gap: 12px;
  margin-top: 4px;
  font-size: 12.5px;
  color: var(--ink-faint);
  letter-spacing: 0.05em;
}
</style>
