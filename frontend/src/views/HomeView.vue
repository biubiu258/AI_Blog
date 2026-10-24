<template>
  <div class="page-container">
    <header class="page-head home-head">
      <div class="head-left">
        <div class="page-title">目录</div>
        <div class="page-sub">CONTENTS · 共 {{ total }} 篇</div>
      </div>
      <div class="search-bar">
        <el-radio-group v-model="mode" class="mode-switch" @change="onModeChange">
          <el-radio-button value="keyword">关键词</el-radio-button>
          <el-tooltip content="登录后可用" :disabled="auth.isLoggedIn" placement="bottom">
            <el-radio-button value="semantic" :disabled="!auth.isLoggedIn">语义</el-radio-button>
          </el-tooltip>
        </el-radio-group>
        <el-select v-if="mode === 'keyword'" v-model="query.categoryId" placeholder="全部分类" clearable
                   class="cat-select" @change="load(1)">
          <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
        <el-input v-model="query.keyword"
                  :placeholder="mode === 'keyword' ? '搜索标题或正文关键词' : '用一句话描述你想找的内容'"
                  clearable class="kw-input" @keyup.enter="submit()">
          <template #append>
            <el-button @click="submit()"><el-icon><Search /></el-icon></el-button>
          </template>
        </el-input>
      </div>
    </header>

    <div v-if="mode === 'keyword' && tags.length" class="tag-filter">
      <button class="tag-chip" :class="{ active: !query.tagId }" @click="selectTag(null)">全部</button>
      <button v-for="t in tags" :key="t.id" class="tag-chip"
              :class="{ active: query.tagId === t.id }" @click="selectTag(t.id)">
        {{ t.name }}
      </button>
    </div>

    <el-alert v-if="mode === 'semantic' && degraded" class="degraded-tip" type="info" show-icon
              :closable="false" title="语义检索暂不可用，已按关键词检索" />

    <el-empty v-if="!loading && displayItems.length === 0"
              :description="mode === 'semantic' ? '没有找到相关内容' : '暂无文章'" />
    <div v-loading="loading" class="article-list">
      <article v-for="(a, i) in displayItems" :key="a.id" class="entry"
               @click="a.id != null && $router.push(`/article/${a.id}`)">
        <span class="entry-index">{{ pad(entryIndex(i)) }}</span>
        <div class="entry-main">
          <h2 class="entry-title">{{ a.title }}</h2>
          <p class="entry-summary">{{ a.snippet || a.summary || plainSummary(a) }}</p>
          <div class="entry-meta">
            <template v-if="mode === 'keyword'">
              <span class="meta-author">{{ a.authorNickname || '佚名' }}</span>
              <el-tag v-if="a.categoryName" size="small" effect="plain">{{ a.categoryName }}</el-tag>
              <el-tag v-for="t in a.tags || []" :key="t" size="small" type="info" effect="plain">
                {{ t }}
              </el-tag>
              <span class="meta-date">{{ formatDate(a.publishedAt || a.createdAt) }}</span>
            </template>
            <template v-else>
              <el-tag size="small" effect="plain" :type="a.matchType === 'semantic' ? 'success' : 'info'">
                {{ a.matchType === 'semantic' ? '语义' : '关键词' }}
              </el-tag>
              <el-tag v-if="a.status === 'draft'" size="small" type="warning" effect="plain">草稿</el-tag>
              <el-tag v-else-if="a.status === 'private'" size="small" type="danger" effect="plain">仅自己可见</el-tag>
              <span v-if="a.score != null" class="meta-score">相似度 {{ a.score.toFixed(2) }}</span>
            </template>
          </div>
        </div>
        <span class="entry-arrow">→</span>
      </article>
    </div>

    <div v-if="mode === 'keyword'" class="pager">
      <el-pagination background layout="prev, pager, next, total" :total="total"
                     :page-size="query.size" :current-page="query.page"
                     @current-change="load" />
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { articleApi, categoryApi, searchApi, tagApi } from '../api'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const loading = ref(false)
const items = ref([])
const total = ref(0)
const categories = ref([])
const tags = ref([])
const mode = ref('keyword')
const semantic = reactive({ degraded: false, items: [] })
const query = reactive({ page: 1, size: 10, categoryId: null, tagId: null, keyword: '' })

const displayItems = computed(() => (mode.value === 'semantic' ? semantic.items : items.value))
const degraded = computed(() => semantic.degraded)

async function load(page = query.page) {
  query.page = page
  loading.value = true
  try {
    const data = await articleApi.list({
      page: query.page,
      size: query.size,
      categoryId: query.categoryId || undefined,
      tagId: query.tagId || undefined,
      keyword: query.keyword || undefined
    })
    items.value = data.items
    total.value = data.total
  } finally {
    loading.value = false
  }
}

async function loadSemantic() {
  if (!query.keyword.trim()) {
    semantic.items = []
    semantic.degraded = false
    return
  }
  loading.value = true
  try {
    const data = await searchApi.search({ query: query.keyword, topK: 10 })
    semantic.items = data.items || []
    semantic.degraded = !!data.degraded
  } finally {
    loading.value = false
  }
}

function submit(page = 1) {
  if (mode.value === 'semantic') {
    return loadSemantic()
  }
  return load(page)
}

function onModeChange(val) {
  if (val === 'semantic') {
    loadSemantic()
  } else {
    load(1)
  }
}

function selectTag(id) {
  query.tagId = id
  load(1)
}

function entryIndex(i) {
  return mode.value === 'semantic' ? i + 1 : (query.page - 1) * query.size + i + 1
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
  try {
    tags.value = await tagApi.list()
  } catch {
    tags.value = []
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
.mode-switch {
  flex-shrink: 0;
}
.degraded-tip {
  margin-bottom: 16px;
}

.tag-filter {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 18px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--hairline);
}
.tag-chip {
  padding: 4px 14px;
  font-size: 13px;
  letter-spacing: 0.06em;
  color: var(--ink-soft);
  background: transparent;
  border: 1px solid var(--hairline-dark);
  border-radius: 999px;
  cursor: pointer;
  transition: all 0.2s ease;
}
.tag-chip:hover,
.tag-chip.active {
  color: #fff;
  background: var(--cinnabar);
  border-color: var(--cinnabar);
}
.meta-score {
  color: var(--ink-faint);
  letter-spacing: 0.05em;
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
