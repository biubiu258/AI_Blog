<template>
  <div class="page-container">
    <header class="page-head">
      <div>
        <div class="page-title">热门排行</div>
        <div class="page-sub">RANKING · 阅读 ×1 + 点赞 ×5 + 收藏 ×8</div>
      </div>
      <el-radio-group v-model="range" @change="load">
        <el-radio-button value="week">本周</el-radio-button>
        <el-radio-button value="month">本月</el-radio-button>
        <el-radio-button value="all">总榜</el-radio-button>
      </el-radio-group>
    </header>

    <el-empty v-if="!loading && items.length === 0" description="暂无上榜文章" />
    <div v-loading="loading" class="rank-list">
      <div v-for="(a, i) in items" :key="a.id" class="rank-item"
           :class="{ top3: i < 3 }" @click="$router.push(`/article/${a.id}`)">
        <span class="rank-no">{{ pad(i + 1) }}</span>
        <div class="rank-main">
          <h2 class="rank-title">{{ a.title }}</h2>
          <div class="rank-meta">
            <span class="rank-author" @click.stop="$router.push(`/user/${a.authorId}`)">
              {{ a.authorNickname || '佚名' }}
            </span>
            <el-tag v-if="a.categoryName" size="small" effect="plain">{{ a.categoryName }}</el-tag>
            <span>{{ formatDate(a.publishedAt) }}</span>
          </div>
        </div>
        <div class="rank-score">
          <div class="score-value">{{ a.score }}</div>
          <div class="score-label">热度</div>
        </div>
        <div class="rank-metrics">
          <span>{{ a.rangeViews }} 阅读</span>
          <span>{{ a.likeCount }} 赞</span>
          <span>{{ a.favoriteCount }} 收藏</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { articleApi } from '../api'

const loading = ref(false)
const items = ref([])
const range = ref('week')

function pad(n) {
  return String(n).padStart(2, '0')
}

function formatDate(value) {
  return value ? String(value).replace('T', ' ').slice(0, 10) : ''
}

async function load() {
  loading.value = true
  try {
    items.value = await articleApi.hot({ range: range.value, limit: 20 })
  } catch {
    items.value = []
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page-head {
  flex-wrap: wrap;
  row-gap: 12px;
}

.rank-list {
  min-height: 200px;
}

.rank-item {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 20px 6px;
  border-bottom: 1px solid var(--hairline);
  cursor: pointer;
  transition: background 0.25s ease;
}
.rank-item:hover {
  background: rgba(253, 251, 244, 0.65);
}
.rank-item:hover .rank-title {
  color: var(--cinnabar);
}

.rank-no {
  width: 46px;
  flex-shrink: 0;
  font-size: 26px;
  font-weight: 900;
  color: var(--hairline-dark);
  text-align: center;
}
.rank-item.top3 .rank-no {
  color: var(--cinnabar);
}

.rank-main {
  flex: 1;
  min-width: 0;
}

.rank-title {
  margin: 0 0 8px;
  font-size: 18px;
  font-weight: 700;
  line-height: 1.5;
  letter-spacing: 0.03em;
  transition: color 0.2s ease;
}

.rank-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  color: var(--ink-faint);
  letter-spacing: 0.05em;
}
.rank-author {
  color: var(--ink-soft);
  font-weight: 700;
}
.rank-author:hover {
  color: var(--cinnabar);
}

.rank-score {
  flex-shrink: 0;
  text-align: center;
  padding: 0 10px;
}
.score-value {
  font-size: 22px;
  font-weight: 900;
  color: var(--ink);
}
.score-label {
  margin-top: 2px;
  font-size: 11px;
  letter-spacing: 0.16em;
  color: var(--ink-faint);
}

.rank-metrics {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
  font-size: 12.5px;
  color: var(--ink-faint);
  letter-spacing: 0.04em;
}

@media (max-width: 720px) {
  .rank-item {
    gap: 12px;
    padding: 16px 2px;
  }
  .rank-no {
    width: 32px;
    font-size: 18px;
  }
  .rank-title {
    font-size: 16px;
  }
  .rank-metrics {
    display: none;
  }
}
</style>
