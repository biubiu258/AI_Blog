<template>
  <div class="page-container">
    <header class="page-head">
      <div class="page-title">作者主页</div>
      <div class="page-sub">AUTHOR</div>
    </header>

    <el-card v-loading="loading" shadow="never" class="panel-card profile-card">
      <div class="profile-row">
        <el-avatar :size="64" :src="profile.avatarUrl || undefined">
          {{ (profile.nickname || 'U').slice(0, 1) }}
        </el-avatar>
        <div class="profile-main">
          <div class="profile-name">{{ profile.nickname || '未知作者' }}</div>
          <div class="profile-bio">{{ profile.bio || '这个人很安静，还没有写简介。' }}</div>
          <div class="profile-stats">
            <span>{{ profile.articleCount || 0 }} 篇文章</span>
            <span>{{ profile.followerCount || 0 }} 关注者</span>
            <span>{{ profile.followingCount || 0 }} 关注中</span>
          </div>
        </div>
        <el-button v-if="isSelf" @click="$router.push('/profile')">编辑资料</el-button>
        <el-button v-else type="primary" :loading="followingBusy" @click="toggleFollow">
          {{ profile.following ? '已关注' : '关注' }}
        </el-button>
      </div>
    </el-card>

    <el-tabs v-model="tab" class="profile-tabs" @tab-change="onTabChange">
      <el-tab-pane label="文章" name="articles">
        <el-empty v-if="!listLoading && !articles.length" description="还没有公开文章" />
        <div v-else v-loading="listLoading" class="article-list">
          <div v-for="a in articles" :key="a.id" class="article-item">
            <div class="article-title" @click="$router.push(`/article/${a.id}`)">{{ a.title }}</div>
            <div class="article-summary">{{ a.summary || '暂无摘要' }}</div>
            <div class="article-meta">
              <span v-if="a.categoryName">{{ a.categoryName }}</span>
              <el-tag v-for="t in a.tags || []" :key="t" size="small" effect="plain">{{ t }}</el-tag>
              <span>{{ formatDate(a.publishedAt || a.createdAt) }}</span>
              <span>{{ a.viewCount || 0 }} 次阅读</span>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="关注者" name="followers">
        <el-empty v-if="!listLoading && !people.length" description="暂无关注者" />
        <div v-else v-loading="listLoading" class="people-list">
          <div v-for="u in people" :key="u.id" class="people-item"
               @click="$router.push(`/user/${u.id}`)">
            <el-avatar :size="40" :src="u.avatarUrl || undefined">
              {{ (u.nickname || 'U').slice(0, 1) }}
            </el-avatar>
            <div class="people-main">
              <div class="people-name">{{ u.nickname || '用户' }}</div>
              <div class="people-bio">{{ u.bio || '暂无简介' }}</div>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="关注中" name="following">
        <el-empty v-if="!listLoading && !people.length" description="还没有关注任何人" />
        <div v-else v-loading="listLoading" class="people-list">
          <div v-for="u in people" :key="u.id" class="people-item"
               @click="$router.push(`/user/${u.id}`)">
            <el-avatar :size="40" :src="u.avatarUrl || undefined">
              {{ (u.nickname || 'U').slice(0, 1) }}
            </el-avatar>
            <div class="people-main">
              <div class="people-name">{{ u.nickname || '用户' }}</div>
              <div class="people-bio">{{ u.bio || '暂无简介' }}</div>
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { userApi } from '../api'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const auth = useAuthStore()

const loading = ref(false)
const listLoading = ref(false)
const followingBusy = ref(false)
const profile = ref({})
const articles = ref([])
const people = ref([])
const tab = ref('articles')
const query = reactive({ page: 1, size: 10 })
const total = ref(0)

const userId = computed(() => Number(route.params.id))
const isSelf = computed(() => auth.user?.id === userId.value)

async function loadProfile() {
  loading.value = true
  try {
    profile.value = await userApi.profile(userId.value)
  } catch {
    profile.value = {}
  } finally {
    loading.value = false
  }
}

async function loadArticles(page = 1) {
  query.page = page
  listLoading.value = true
  try {
    const data = await userApi.articles(userId.value, { page: query.page, size: query.size })
    articles.value = data.items || []
    total.value = data.total || 0
  } catch {
    articles.value = []
  } finally {
    listLoading.value = false
  }
}

async function loadPeople(kind) {
  listLoading.value = true
  try {
    people.value = kind === 'followers'
      ? await userApi.followers(userId.value)
      : await userApi.following(userId.value)
  } catch {
    people.value = []
  } finally {
    listLoading.value = false
  }
}

async function onTabChange(name) {
  people.value = []
  if (name === 'articles') await loadArticles(1)
  else await loadPeople(name)
}

async function toggleFollow() {
  if (!auth.isLoggedIn) {
    ElMessage.warning('登录后即可关注')
    return
  }
  followingBusy.value = true
  try {
    const data = await userApi.follow(userId.value)
    profile.value.following = data?.following ?? !profile.value.following
    profile.value.followerCount = data?.followerCount ?? profile.value.followerCount
    ElMessage.success(profile.value.following ? '已关注' : '已取消关注')
  } finally {
    followingBusy.value = false
  }
}

function formatDate(value) {
  if (!value) return ''
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return String(value)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

async function bootstrap() {
  await loadProfile()
  await loadArticles(1)
}

onMounted(bootstrap)
watch(() => route.params.id, bootstrap)
</script>

<style scoped>
.profile-card {
  border-top: 3px solid var(--ink);
  margin-bottom: 20px;
}
.profile-row {
  display: flex;
  align-items: center;
  gap: 18px;
}
.profile-main {
  flex: 1;
  min-width: 0;
}
.profile-name {
  font-size: 20px;
  font-weight: 800;
  color: var(--ink);
}
.profile-bio {
  margin-top: 6px;
  color: var(--ink-soft);
  font-size: 13.5px;
  line-height: 1.6;
}
.profile-stats {
  margin-top: 10px;
  display: flex;
  gap: 18px;
  color: var(--ink-faint);
  font-size: 12.5px;
  letter-spacing: 0.03em;
}
.article-list,
.people-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.article-item {
  padding: 16px 2px;
  border-bottom: 1px solid var(--hairline);
}
.article-title {
  font-size: 17px;
  font-weight: 700;
  color: var(--ink);
  cursor: pointer;
  transition: color 0.2s ease;
}
.article-title:hover {
  color: var(--cinnabar);
}
.article-summary {
  margin-top: 6px;
  color: var(--ink-soft);
  font-size: 13.5px;
  line-height: 1.65;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.article-meta {
  margin-top: 10px;
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  color: var(--ink-faint);
  font-size: 12.5px;
}
.people-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 2px;
  border-bottom: 1px solid var(--hairline);
  cursor: pointer;
}
.people-name {
  font-weight: 700;
  color: var(--ink);
}
.people-bio {
  margin-top: 4px;
  color: var(--ink-faint);
  font-size: 12.5px;
}
</style>
