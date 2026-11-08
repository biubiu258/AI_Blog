<template>
  <div class="page-container">
    <el-empty v-if="!loading && !series" description="连载不存在或已被删除" />
    <div v-else-if="series" v-loading="loading" class="series-page">
      <header class="page-head series-head">
        <div class="head-left">
          <div class="series-badges">
            <el-tag size="small" :type="series.status === 'finished' ? 'info' : 'success'" effect="plain">
              {{ statusText(series.status) }}
            </el-tag>
            <span class="series-count">共 {{ series.articleCount }} 篇 · {{ series.subscriberCount || 0 }} 人订阅</span>
          </div>
          <div class="page-title">《{{ series.title }}》</div>
          <div class="series-author">
            <el-avatar :size="22" :src="series.authorAvatar || undefined">
              {{ (series.authorNickname || '佚名').slice(0, 1) }}
            </el-avatar>
            <router-link :to="`/user/${series.authorId}`" class="author-link">
              {{ series.authorNickname || '佚名' }}
            </router-link>
          </div>
        </div>
        <div class="head-actions">
          <el-button v-if="!auth.isLoggedIn"
                     @click="$router.push(`/login?redirect=/series/${series.id}`)">登录后订阅</el-button>
          <el-button v-else-if="!series.mine" :type="series.subscribed ? 'info' : 'primary'"
                     :plain="series.subscribed" :loading="subscribing" @click="toggleSubscribe">
            {{ series.subscribed ? '已订阅 · 取消' : '订阅连载' }}
          </el-button>
          <template v-if="series.mine">
            <el-button size="small" @click="openEdit">编辑</el-button>
            <el-button size="small" type="primary" @click="openAdd">添加文章</el-button>
          </template>
        </div>
      </header>

      <p class="series-desc">{{ series.description || '作者还没有填写连载说明。' }}</p>

      <section class="catalog">
        <div class="catalog-head">
          <span class="catalog-title">目录</span>
          <span class="catalog-sub">CONTENTS · {{ series.articles?.length || 0 }} 篇</span>
        </div>
        <el-empty v-if="!loading && !(series.articles || []).length" :image-size="56"
                  description="还没有文章加入连载" />
        <div v-for="(a, i) in series.articles || []" :key="a.articleId" class="catalog-item"
             @click="$router.push(`/article/${a.articleId}`)">
          <span class="catalog-index">{{ pad(a.index || i + 1) }}</span>
          <div class="catalog-main">
            <div class="catalog-item-title">
              {{ a.title }}
              <el-tag v-if="a.status === 'draft'" size="small" type="warning" effect="plain">草稿</el-tag>
            </div>
            <p v-if="a.summary" class="catalog-summary">{{ a.summary }}</p>
            <div class="catalog-meta">
              <span>{{ formatDate(a.publishedAt) }}</span>
              <span>{{ a.viewCount || 0 }} 阅读</span>
            </div>
          </div>
          <div v-if="series.mine" class="catalog-actions" @click.stop>
            <el-button link size="small" :disabled="i === 0" @click="moveArticle(i, -1)">上移</el-button>
            <el-button link size="small" :disabled="i === series.articles.length - 1"
                       @click="moveArticle(i, 1)">下移</el-button>
            <el-button link size="small" type="danger" @click="removeArticle(a)">移除</el-button>
          </div>
        </div>
      </section>
    </div>

    <!-- 编辑连载 -->
    <el-dialog v-model="editDialog.visible" title="编辑连载" width="480px">
      <el-form label-width="72px">
        <el-form-item label="标题" required>
          <el-input v-model="editDialog.title" maxlength="120" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="editDialog.description" type="textarea" :rows="4" maxlength="500"
                    show-word-limit />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="editDialog.status">
            <el-radio-button value="ongoing">连载中</el-radio-button>
            <el-radio-button value="finished">已完结</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="editDialog.saving" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 添加文章 -->
    <el-dialog v-model="addDialog.visible" title="加入连载" width="500px">
      <div v-loading="addDialog.loading">
        <el-form label-width="72px">
          <el-form-item label="文章" required>
            <el-select v-model="addDialog.articleId" filterable placeholder="选择自己的文章"
                       style="width: 100%">
              <el-option v-for="a in selectableArticles" :key="a.id"
                         :label="a.title" :value="a.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="序号">
            <el-input-number v-model="addDialog.orderNo" :min="1" :max="999" />
            <span class="hint">留空则追加到末位</span>
          </el-form-item>
        </el-form>
        <div v-if="!addDialog.loading && !selectableArticles.length" class="hint">
          暂无可加入的文章（已在本连载中的文章不会重复显示）
        </div>
      </div>
      <template #footer>
        <el-button @click="addDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="addDialog.saving" :disabled="!addDialog.articleId"
                   @click="confirmAdd">加入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { articleApi, seriesApi } from '../api'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const loading = ref(false)
const series = ref(null)
const subscribing = ref(false)

const editDialog = reactive({ visible: false, saving: false, title: '', description: '', status: 'ongoing' })
const addDialog = reactive({
  visible: false, loading: false, saving: false, articleId: null, orderNo: null
})
const myArticles = ref([])

const selectableArticles = computed(() => {
  const inSeries = new Set((series.value?.articles || []).map((a) => a.articleId))
  return myArticles.value.filter((a) => !inSeries.has(a.id))
})

function statusText(status) {
  return status === 'finished' ? '已完结' : '连载中'
}

function pad(n) {
  return String(n).padStart(2, '0')
}

function formatDate(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : ''
}

async function load() {
  loading.value = true
  try {
    series.value = await seriesApi.detail(route.params.id)
  } catch {
    series.value = null
  } finally {
    loading.value = false
  }
}

async function toggleSubscribe() {
  if (!auth.isLoggedIn) {
    router.push(`/login?redirect=/series/${route.params.id}`)
    return
  }
  subscribing.value = true
  try {
    const res = series.value.subscribed
      ? await seriesApi.unsubscribe(series.value.id)
      : await seriesApi.subscribe(series.value.id)
    series.value.subscribed = res.subscribed
    series.value.subscriberCount = res.subscriberCount
    ElMessage.success(res.subscribed ? '已订阅连载' : '已取消订阅')
  } catch {
    /* 拦截器已提示 */
  } finally {
    subscribing.value = false
  }
}

function openEdit() {
  editDialog.title = series.value.title
  editDialog.description = series.value.description || ''
  editDialog.status = series.value.status || 'ongoing'
  editDialog.visible = true
}

async function saveEdit() {
  if (!editDialog.title.trim()) {
    ElMessage.warning('连载标题不能为空')
    return
  }
  editDialog.saving = true
  try {
    await seriesApi.update(series.value.id, {
      title: editDialog.title.trim(),
      description: editDialog.description,
      status: editDialog.status
    })
    editDialog.visible = false
    ElMessage.success('已保存')
    await load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    editDialog.saving = false
  }
}

async function openAdd() {
  addDialog.articleId = null
  addDialog.orderNo = null
  addDialog.visible = true
  addDialog.loading = true
  try {
    const data = await articleApi.mine({ page: 1, size: 100 })
    myArticles.value = data.items || []
  } catch {
    myArticles.value = []
  } finally {
    addDialog.loading = false
  }
}

async function confirmAdd() {
  if (!addDialog.articleId) {
    ElMessage.warning('请选择要加入的文章')
    return
  }
  addDialog.saving = true
  try {
    await seriesApi.addArticle(series.value.id, {
      articleId: addDialog.articleId,
      orderNo: addDialog.orderNo || undefined
    })
    addDialog.visible = false
    ElMessage.success('已加入连载')
    await load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    addDialog.saving = false
  }
}

async function removeArticle(article) {
  await ElMessageBox.confirm(`确定把「${article.title}」移出连载吗？`, '移除文章', { type: 'warning' })
  await seriesApi.removeArticle(series.value.id, article.articleId)
  ElMessage.success('已移除')
  await load()
}

async function moveArticle(index, delta) {
  const list = [...(series.value.articles || [])]
  const target = index + delta
  if (target < 0 || target >= list.length) return
  const [moved] = list.splice(index, 1)
  list.splice(target, 0, moved)
  try {
    await seriesApi.reorder(series.value.id, list.map((a) => a.articleId))
    await load()
  } catch {
    /* 拦截器已提示 */
  }
}

onMounted(load)
</script>

<style scoped>
.series-page {
  min-height: 200px;
}

.series-head {
  align-items: flex-start;
  flex-wrap: wrap;
  row-gap: 12px;
}
.series-head .page-title {
  margin-top: 8px;
  font-size: 28px;
}
.series-badges {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.series-count {
  font-size: 12.5px;
  color: var(--ink-faint);
  letter-spacing: 0.05em;
}
.series-author {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
  font-size: 13px;
  color: var(--ink-soft);
}
.author-link {
  color: var(--ink-soft);
  text-decoration: none;
  font-weight: 600;
}
.author-link:hover {
  color: var(--cinnabar);
}
.head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.series-desc {
  margin: 0 0 28px;
  font-size: 14px;
  line-height: 1.9;
  color: var(--ink-soft);
  letter-spacing: 0.02em;
}

.catalog-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--ink);
  margin-bottom: 8px;
}
.catalog-title {
  font-size: 18px;
  font-weight: 900;
  letter-spacing: 0.16em;
}
.catalog-sub {
  font-size: 11px;
  letter-spacing: 0.18em;
  color: var(--ink-faint);
}

.catalog-item {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 16px 4px;
  border-bottom: 1px solid var(--hairline);
  cursor: pointer;
  transition: background 0.2s ease;
}
.catalog-item:hover {
  background: rgba(253, 251, 244, 0.65);
}
.catalog-index {
  width: 34px;
  flex-shrink: 0;
  font-size: 19px;
  font-weight: 900;
  color: var(--hairline-dark);
}
.catalog-item:hover .catalog-index {
  color: var(--cinnabar);
}
.catalog-main {
  flex: 1;
  min-width: 0;
}
.catalog-item-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0.03em;
}
.catalog-item:hover .catalog-item-title {
  color: var(--cinnabar);
}
.catalog-summary {
  margin: 6px 0 8px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--ink-soft);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.catalog-meta {
  display: flex;
  gap: 14px;
  font-size: 12.5px;
  color: var(--ink-faint);
}
.catalog-actions {
  flex-shrink: 0;
  display: flex;
  align-items: center;
}
.hint {
  margin-left: 10px;
  font-size: 12.5px;
  color: var(--ink-faint);
}

@media (max-width: 720px) {
  .series-head .page-title {
    font-size: 22px;
  }
  .catalog-item {
    gap: 10px;
    flex-wrap: wrap;
  }
  .catalog-actions {
    width: 100%;
    justify-content: flex-end;
    margin-top: 4px;
  }
}
</style>
