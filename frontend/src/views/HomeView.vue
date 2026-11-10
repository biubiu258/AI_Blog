<template>
  <div class="page-container">
    <header class="page-head home-head">
      <div class="head-left">
        <div class="page-title">目录</div>
        <div class="page-sub">CONTENTS · 共 {{ total }} 篇</div>
      </div>
      <div class="head-right">
        <el-tooltip content="隐藏热门排行与热度标签，仅按发布时间倒序阅读" placement="bottom">
          <div class="slow-toggle">
            <el-switch v-model="slowRead" inline-prompt active-text="慢读" inactive-text="慢读"
                       @change="onSlowChange" />
            <span class="slow-label" :class="{ on: slowRead }">{{ slowRead ? '慢读中' : '慢读' }}</span>
          </div>
        </el-tooltip>
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
      </div>
    </header>

    <!-- 伯乐推荐：推荐理由与伯乐署名对读者可见 -->
    <section v-if="slots.length" class="curator-section">
      <div class="section-head">
        <span class="section-title">伯乐推荐</span>
        <span class="section-sub">CURATOR'S PICKS · 本周</span>
      </div>
      <div class="curator-grid">
        <article v-for="s in slots" :key="s.slotId" class="curator-card"
                 @click="$router.push(`/article/${s.articleId}`)">
          <div class="curator-reason">“{{ s.reason }}”</div>
          <h3 class="curator-title">{{ s.title }}</h3>
          <p class="curator-summary">{{ s.summary }}</p>
          <div class="curator-foot">
            <el-avatar :size="20" :src="s.authorAvatar || undefined">
              {{ (s.authorNickname || '佚名').slice(0, 1) }}
            </el-avatar>
            <span>{{ s.authorNickname || '佚名' }}</span>
            <span class="curator-by">伯乐 · {{ s.curatorNickname || '匿名' }}</span>
          </div>
        </article>
      </div>
    </section>

    <!-- 进行中的创作活动：轻量横幅 -->
    <section v-if="campaigns.length" class="campaign-section">
      <div class="section-head">
        <span class="section-title">创作活动</span>
        <span class="section-sub">CAMPAIGNS · 进行中</span>
      </div>
      <div class="campaign-list">
        <div v-for="c in campaigns" :key="c.id" class="campaign-item">
          <div class="campaign-top">
            <span class="campaign-title">{{ c.title }}</span>
            <el-tag size="small" type="success" effect="plain">进行中</el-tag>
            <span v-if="c.prizePool" class="campaign-prize">奖池 {{ c.prizePool }}</span>
          </div>
          <p v-if="c.description" class="campaign-desc">{{ c.description }}</p>
          <div class="campaign-meta">
            <span>{{ formatDate(c.startAt) }} — {{ formatDate(c.endAt) }}</span>
            <el-tooltip v-if="c.joinRule" :content="`参与条件：${c.joinRule}`" placement="top">
              <span class="campaign-rule">参与条件</span>
            </el-tooltip>
            <el-tooltip v-if="c.judgeRule" :content="`评选方式：${c.judgeRule}`" placement="top">
              <span class="campaign-rule">评选方式</span>
            </el-tooltip>
          </div>
        </div>
      </div>
    </section>

    <div v-if="mode === 'keyword' && tags.length" class="tag-filter">
      <button class="tag-chip" :class="{ active: !query.tagId }" @click="selectTag(null)">全部</button>
      <button v-for="t in tags" :key="t.id" class="tag-chip"
              :class="{ active: query.tagId === t.id }" @click="selectTag(t.id)">
        {{ t.name }}
      </button>
    </div>

    <el-alert v-if="mode === 'semantic' && degraded" class="degraded-tip" type="info" show-icon
              :closable="false" title="语义检索暂不可用，已按关键词检索" />

    <!-- 慢读模式关闭时才展示热门排行；开启后只读发布时间倒序 -->
    <section v-if="mode === 'keyword' && !slowRead && hotItems.length" class="hot-section">
      <div class="section-head">
        <span class="section-title">本周热读</span>
        <span class="section-sub">HOT · 按热度排序</span>
      </div>
      <div class="hot-list">
        <div v-for="(h, i) in hotItems" :key="h.id" class="hot-item"
             @click="$router.push(`/article/${h.id}`)">
          <span class="hot-no" :class="{ top: i < 3 }">{{ pad(i + 1) }}</span>
          <span class="hot-title">{{ h.title }}</span>
          <span class="hot-author">{{ h.authorNickname || '佚名' }}</span>
          <span class="hot-score">{{ h.score }} 热度</span>
        </div>
      </div>
    </section>

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
import { articleApi, campaignApi, categoryApi, curatorApi, searchApi, tagApi } from '../api'
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

const SLOW_KEY = 'aiblog:slow-read'
const slowRead = ref(false)
try {
  slowRead.value = localStorage.getItem(SLOW_KEY) === '1'
} catch {
  slowRead.value = false
}
const slots = ref([])
const campaigns = ref([])
const hotItems = ref([])

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

async function loadSlots() {
  try {
    slots.value = (await curatorApi.slots()) || []
  } catch {
    slots.value = []
  }
}

async function loadCampaigns() {
  try {
    campaigns.value = (await campaignApi.list()) || []
  } catch {
    campaigns.value = []
  }
}

async function loadHot() {
  if (slowRead.value) {
    hotItems.value = []
    return
  }
  try {
    hotItems.value = (await articleApi.hot({ range: 'week', limit: 5 })) || []
  } catch {
    hotItems.value = []
  }
}

function onSlowChange(val) {
  try {
    localStorage.setItem(SLOW_KEY, val ? '1' : '0')
  } catch {
    /* 本地存储不可用时仅当次生效 */
  }
  if (val) hotItems.value = []
  else loadHot()
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
  loadSlots()
  loadCampaigns()
  loadHot()
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

.head-right {
  display: flex;
  align-items: center;
  gap: 16px;
  flex: 1;
  justify-content: flex-end;
  flex-wrap: wrap;
}
.slow-toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}
.slow-label {
  font-size: 12.5px;
  letter-spacing: 0.12em;
  color: var(--ink-faint);
}
.slow-label.on {
  color: var(--cinnabar);
  font-weight: 600;
}

/* ---------- 伯乐推荐 / 创作活动 / 热读 ---------- */
.section-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 14px;
}
.section-title {
  font-size: 17px;
  font-weight: 900;
  letter-spacing: 0.14em;
}
.section-sub {
  font-size: 10.5px;
  letter-spacing: 0.22em;
  color: var(--ink-faint);
}

.curator-section,
.campaign-section,
.hot-section {
  margin-bottom: 28px;
}

.curator-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(230px, 1fr));
  gap: 14px;
}
.curator-card {
  padding: 16px 16px 14px;
  background: var(--paper-card);
  border: 1px solid var(--hairline);
  border-top: 3px solid var(--cinnabar);
  cursor: pointer;
  transition: box-shadow 0.25s ease, transform 0.25s ease;
}
.curator-card:hover {
  box-shadow: 0 6px 22px rgba(60, 50, 30, 0.1);
  transform: translateY(-2px);
}
.curator-reason {
  font-size: 13.5px;
  line-height: 1.75;
  color: var(--cinnabar-deep);
  padding-left: 10px;
  border-left: 2px solid var(--cinnabar);
  margin-bottom: 10px;
}
.curator-title {
  margin: 0 0 6px;
  font-size: 16px;
  font-weight: 700;
  line-height: 1.5;
  letter-spacing: 0.03em;
}
.curator-card:hover .curator-title {
  color: var(--cinnabar);
}
.curator-summary {
  margin: 0 0 12px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--ink-soft);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.curator-foot {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  color: var(--ink-faint);
}
.curator-by {
  margin-left: auto;
  color: var(--ink-faint);
  letter-spacing: 0.04em;
}

.campaign-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.campaign-item {
  padding: 12px 16px;
  background: rgba(253, 251, 244, 0.7);
  border: 1px solid var(--hairline);
  border-left: 3px solid var(--ink);
}
.campaign-top {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}
.campaign-title {
  font-size: 15.5px;
  font-weight: 700;
  letter-spacing: 0.05em;
}
.campaign-prize {
  margin-left: auto;
  color: var(--cinnabar);
  font-size: 13px;
  letter-spacing: 0.04em;
}
.campaign-desc {
  margin: 6px 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--ink-soft);
}
.campaign-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 14px;
  font-size: 12.5px;
  color: var(--ink-faint);
}
.campaign-rule {
  cursor: help;
  border-bottom: 1px dashed var(--hairline-dark);
  color: var(--ink-soft);
}

.hot-list {
  border-top: 1px solid var(--hairline);
}
.hot-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px 4px;
  border-bottom: 1px solid var(--hairline);
  cursor: pointer;
  transition: background 0.2s ease;
}
.hot-item:hover {
  background: rgba(253, 251, 244, 0.65);
}
.hot-no {
  width: 26px;
  flex-shrink: 0;
  font-size: 15px;
  font-weight: 900;
  color: var(--hairline-dark);
}
.hot-no.top {
  color: var(--cinnabar);
}
.hot-title {
  flex: 1;
  min-width: 0;
  font-size: 14.5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hot-item:hover .hot-title {
  color: var(--cinnabar);
}
.hot-author {
  flex-shrink: 0;
  font-size: 12.5px;
  color: var(--ink-faint);
}
.hot-score {
  flex-shrink: 0;
  font-size: 12.5px;
  color: var(--ink-faint);
  letter-spacing: 0.04em;
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
  .head-right {
    width: 100%;
    justify-content: flex-start;
  }
  .search-bar {
    max-width: none;
    width: 100%;
  }
  .campaign-prize {
    margin-left: 0;
    width: 100%;
  }
  .curator-grid {
    grid-template-columns: 1fr;
  }
  .hot-author {
    display: none;
  }
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
