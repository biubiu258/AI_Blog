<template>
  <div class="page-container">
    <header class="page-head">
      <div>
        <div class="page-title">技术</div>
        <div class="page-sub">TECH · 技术实践、工具与排障</div>
      </div>
      <div class="head-actions">
        <el-input v-model="keyword" clearable placeholder="搜索技术文章" class="search-input"
                  @keyup.enter="load(1)" @clear="load(1)" />
        <el-button type="primary" @click="load(1)">搜索</el-button>
      </div>
    </header>

    <div class="tech-body">
      <section class="article-col">
        <el-empty v-if="!loading && !items.length" :image-size="60"
                  description="还没有技术文章，等待第一篇实践记录" />
        <div v-loading="loading" class="article-list">
          <article v-for="(a, i) in items" :key="a.id" class="entry"
                   @click="$router.push(`/article/${a.id}`)">
            <span class="entry-index">{{ pad(i + 1) }}</span>
            <div class="entry-main">
              <h2 class="entry-title">{{ a.title }}</h2>
              <p class="entry-summary">{{ a.summary || '——' }}</p>
              <div class="entry-meta">
                <span class="meta-author">{{ a.authorNickname || '佚名' }}</span>
                <el-tag v-if="a.categoryName" size="small" effect="plain">
                  {{ a.categoryName }}
                </el-tag>
                <el-tag v-for="t in a.tags || []" :key="t" size="small" type="info" effect="plain">
                  {{ t }}
                </el-tag>
                <span class="meta-date">{{ formatDate(a.publishedAt || a.createdAt) }}</span>
              </div>
            </div>
            <span class="entry-arrow">→</span>
          </article>
        </div>

        <div v-if="total > 0" class="pager">
          <el-pagination background layout="prev, pager, next, total" :total="total"
                         :page-size="query.size" :current-page="query.page"
                         @current-change="load" />
        </div>
      </section>

      <aside class="term-col">
        <div class="aside-head">
          <span class="aside-title">常用术语</span>
          <router-link to="/tech/glossary" class="aside-link">术语表 →</router-link>
        </div>
        <el-empty v-if="!termLoading && !terms.length" :image-size="48"
                  description="暂无术语" />
        <ul v-else class="term-list">
          <li v-for="t in terms" :key="t.id" class="term-item">
            <div class="term-name">{{ t.canonical }}</div>
            <div class="term-def">{{ t.definition || '——' }}</div>
          </li>
        </ul>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { techApi } from '../api'

const items = ref([])
const total = ref(0)
const loading = ref(false)
const keyword = ref('')
const query = reactive({ page: 1, size: 10 })

const terms = ref([])
const termLoading = ref(false)

function pad(n) {
  return String(n).padStart(2, '0')
}

function formatDate(value) {
  return value ? String(value).replace('T', ' ').slice(0, 10) : ''
}

async function load(page = query.page) {
  query.page = page
  loading.value = true
  try {
    const data = await techApi.articles({
      page: query.page,
      size: query.size,
      keyword: keyword.value.trim() || undefined
    })
    items.value = data.items || []
    total.value = data.total || 0
  } catch {
    items.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function loadTerms() {
  termLoading.value = true
  try {
    terms.value = ((await techApi.terms()) || []).slice(0, 12)
  } catch {
    terms.value = []
  } finally {
    termLoading.value = false
  }
}

onMounted(() => {
  load(1)
  loadTerms()
})
</script>

<style scoped>
.page-head {
  flex-wrap: wrap;
  row-gap: 12px;
}
.head-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}
.search-input {
  width: 220px;
}

.tech-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px;
  gap: 32px;
  align-items: start;
}

.article-list {
  min-height: 80px;
}
.entry {
  display: flex;
  align-items: flex-start;
  gap: 18px;
  padding: 20px 0;
  border-bottom: 1px solid var(--hairline);
  cursor: pointer;
  transition: background 0.2s ease;
}
.entry:hover {
  background: var(--paper-card);
}
.entry-index {
  font-size: 22px;
  font-weight: 900;
  color: var(--ink-faint);
  min-width: 34px;
}
.entry-main {
  flex: 1;
  min-width: 0;
}
.entry-title {
  margin: 0 0 6px;
  font-size: 19px;
  font-weight: 700;
  letter-spacing: 0.04em;
}
.entry:hover .entry-title {
  color: var(--cinnabar);
}
.entry-summary {
  margin: 0 0 10px;
  font-size: 13.5px;
  line-height: 1.75;
  color: var(--ink-soft);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.entry-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 12.5px;
  color: var(--ink-faint);
}
.meta-author {
  color: var(--ink-soft);
}
.entry-arrow {
  color: var(--ink-faint);
  font-size: 18px;
}
.pager {
  display: flex;
  justify-content: center;
  padding: 24px 0 8px;
}

.term-col {
  position: sticky;
  top: 24px;
  padding: 16px;
  background: var(--paper-card);
  border: 1px solid var(--hairline);
  border-top: 3px solid var(--cinnabar);
}
.aside-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--hairline);
  margin-bottom: 12px;
}
.aside-title {
  font-size: 15px;
  font-weight: 900;
  letter-spacing: 0.16em;
}
.aside-link {
  font-size: 12.5px;
  color: var(--cinnabar);
  text-decoration: none;
}
.term-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.term-item + .term-item {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed var(--hairline);
}
.term-name {
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 0.04em;
}
.term-def {
  margin-top: 3px;
  font-size: 12.5px;
  line-height: 1.65;
  color: var(--ink-faint);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

@media (max-width: 860px) {
  .tech-body {
    grid-template-columns: 1fr;
  }
  .term-col {
    position: static;
  }
}
</style>
