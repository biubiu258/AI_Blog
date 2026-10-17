<template>
  <div class="page-container">
    <header class="page-head home-head">
      <div class="head-left">
        <div class="page-title">目录</div>
        <div class="page-sub">CONTENTS · 共 {{ total }} 篇</div>
      </div>
      <div class="search-bar">
        <el-select v-model="query.categoryId" placeholder="全部分类" clearable class="cat-select"
                   @change="load(1)">
          <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
        <el-input v-model="query.keyword" placeholder="搜索标题或正文关键词" clearable
                  class="kw-input" @keyup.enter="load(1)">
          <template #append>
            <el-button @click="load(1)"><el-icon><Search /></el-icon></el-button>
          </template>
        </el-input>
      </div>
    </header>

    <el-empty v-if="!loading && items.length === 0" description="暂无文章" />
    <div v-loading="loading" class="article-list">
      <article v-for="(a, i) in items" :key="a.id" class="entry"
               @click="$router.push(`/article/${a.id}`)">
        <span class="entry-index">{{ pad((query.page - 1) * query.size + i + 1) }}</span>
        <div class="entry-main">
          <h2 class="entry-title">{{ a.title }}</h2>
          <p class="entry-summary">{{ a.summary || plainSummary(a) }}</p>
          <div class="entry-meta">
            <span class="meta-author">{{ a.authorNickname || '佚名' }}</span>
            <el-tag v-if="a.categoryName" size="small" effect="plain">{{ a.categoryName }}</el-tag>
            <span class="meta-date">{{ formatDate(a.publishedAt || a.createdAt) }}</span>
          </div>
        </div>
        <span class="entry-arrow">→</span>
      </article>
    </div>

    <div class="pager">
      <el-pagination background layout="prev, pager, next, total" :total="total"
                     :page-size="query.size" :current-page="query.page"
                     @current-change="load" />
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { articleApi, categoryApi } from '../api'

const loading = ref(false)
const items = ref([])
const total = ref(0)
const categories = ref([])
const query = reactive({ page: 1, size: 10, categoryId: null, keyword: '' })

async function load(page = query.page) {
  query.page = page
  loading.value = true
  try {
    const data = await articleApi.list({
      page: query.page,
      size: query.size,
      categoryId: query.categoryId || undefined,
      keyword: query.keyword || undefined
    })
    items.value = data.items
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function plainSummary(a) {
  return (a.summary || '').slice(0, 80)
}

function pad(n) {
  return String(n).padStart(2, '0')
}

function formatDate(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : ''
}

onMounted(async () => {
  load(1)
  try {
    categories.value = await categoryApi.list()
  } catch {
    categories.value = []
  }
})
</script>

<style scoped>
.home-head {
  flex-wrap: wrap;
  row-gap: 14px;
}

.search-bar {
  display: flex;
  gap: 10px;
  flex: 1;
  min-width: 280px;
  max-width: 520px;
}
.cat-select {
  width: 140px;
  flex-shrink: 0;
}
.kw-input {
  flex: 1;
}

/* ---------- 目录条目 ---------- */
.article-list {
  min-height: 160px;
}

.entry {
  display: flex;
  align-items: flex-start;
  gap: 22px;
  padding: 22px 6px;
  border-bottom: 1px solid var(--hairline);
  cursor: pointer;
  transition: background 0.25s ease;
}
.entry:hover {
  background: rgba(253, 251, 244, 0.65);
}

.entry-index {
  width: 46px;
  flex-shrink: 0;
  font-size: 24px;
  font-weight: 900;
  line-height: 1.3;
  color: var(--hairline-dark);
  transition: color 0.25s ease;
}

.entry-main {
  flex: 1;
  min-width: 0;
}

.entry-title {
  margin: 0 0 8px;
  font-size: 19px;
  font-weight: 700;
  letter-spacing: 0.04em;
  line-height: 1.5;
  transition: color 0.25s ease, transform 0.25s ease;
}

.entry-summary {
  margin: 0 0 12px;
  color: var(--ink-soft);
  font-size: 14px;
  line-height: 1.8;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.entry-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--ink-faint);
  font-size: 13px;
}
.meta-author {
  color: var(--ink-soft);
  letter-spacing: 0.05em;
}
.meta-date {
  letter-spacing: 0.05em;
}

.entry-arrow {
  flex-shrink: 0;
  margin-top: 6px;
  color: var(--cinnabar);
  opacity: 0;
  transform: translateX(-8px);
  transition: opacity 0.25s ease, transform 0.25s ease;
}
.entry:hover .entry-arrow {
  opacity: 1;
  transform: none;
}
.entry:hover .entry-title {
  color: var(--cinnabar);
}
.entry:hover .entry-index {
  color: var(--cinnabar);
}

.pager {
  margin-top: 28px;
  display: flex;
  justify-content: center;
}

@media (max-width: 720px) {
  .entry {
    gap: 12px;
    padding: 18px 2px;
  }
  .entry-index {
    width: 34px;
    font-size: 18px;
  }
  .entry-title {
    font-size: 17px;
  }
  .entry-arrow {
    display: none;
  }
}
</style>
