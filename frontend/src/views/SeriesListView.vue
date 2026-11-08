<template>
  <div class="page-container">
    <header class="page-head">
      <div>
        <div class="page-title">连载</div>
        <div class="page-sub">SERIES · 我的连载与我订阅的连载</div>
      </div>
      <el-button type="primary" @click="openCreate">新建连载</el-button>
    </header>

    <section class="series-block">
      <div class="block-head">
        <span class="block-title">我的连载</span>
        <span class="block-sub">MY SERIES</span>
      </div>
      <el-empty v-if="!loadingMine && !mine.length" :image-size="56"
                description="还没有连载，新建一个把文章串起来" />
      <div v-else class="series-grid">
        <article v-for="s in mine" :key="s.id" class="series-card"
                 @click="$router.push(`/series/${s.id}`)">
          <div class="card-top">
            <el-tag size="small" :type="s.status === 'finished' ? 'info' : 'success'" effect="plain">
              {{ statusText(s.status) }}
            </el-tag>
            <span class="card-count">{{ s.articleCount || 0 }} 篇</span>
          </div>
          <h3 class="card-title">《{{ s.title }}》</h3>
          <p class="card-desc">{{ s.description || '暂无说明' }}</p>
          <div class="card-foot">
            <span>{{ formatDate(s.updatedAt) }}</span>
            <el-button link type="danger" size="small" @click.stop="removeSeries(s)">删除</el-button>
          </div>
        </article>
      </div>
    </section>

    <section class="series-block">
      <div class="block-head">
        <span class="block-title">我订阅的连载</span>
        <span class="block-sub">SUBSCRIBED</span>
      </div>
      <el-empty v-if="!loadingSubscribed && !subscribed.length" :image-size="56"
                description="还没有订阅连载，去文章页发现更多" />
      <div v-else class="series-grid">
        <article v-for="s in subscribed" :key="s.id" class="series-card subscribed"
                 @click="$router.push(`/series/${s.id}`)">
          <div class="card-top">
            <el-tag size="small" :type="s.status === 'finished' ? 'info' : 'success'" effect="plain">
              {{ statusText(s.status) }}
            </el-tag>
            <span class="card-count">{{ s.articleCount || 0 }} 篇</span>
          </div>
          <h3 class="card-title">《{{ s.title }}》</h3>
          <p class="card-desc">{{ s.description || '暂无说明' }}</p>
          <div class="card-foot">
            <span>{{ formatDate(s.updatedAt) }}</span>
            <span class="subscribed-mark">已订阅</span>
          </div>
        </article>
      </div>
    </section>

    <el-dialog v-model="createDialog.visible" title="新建连载" width="480px">
      <el-form label-width="72px">
        <el-form-item label="标题" required>
          <el-input v-model="createDialog.title" maxlength="120" placeholder="如：山居杂记" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="createDialog.description" type="textarea" :rows="4" maxlength="500"
                    show-word-limit placeholder="写一段连载简介（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="createDialog.saving" @click="confirmCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { seriesApi } from '../api'

const mine = ref([])
const subscribed = ref([])
const loadingMine = ref(false)
const loadingSubscribed = ref(false)
const createDialog = reactive({ visible: false, saving: false, title: '', description: '' })

function statusText(status) {
  return status === 'finished' ? '已完结' : '连载中'
}

function formatDate(value) {
  return value ? String(value).replace('T', ' ').slice(0, 10) : ''
}

async function loadMine() {
  loadingMine.value = true
  try {
    mine.value = (await seriesApi.mine()) || []
  } catch {
    mine.value = []
  } finally {
    loadingMine.value = false
  }
}

async function loadSubscribed() {
  loadingSubscribed.value = true
  try {
    subscribed.value = (await seriesApi.subscribed()) || []
  } catch {
    subscribed.value = []
  } finally {
    loadingSubscribed.value = false
  }
}

function openCreate() {
  createDialog.title = ''
  createDialog.description = ''
  createDialog.visible = true
}

async function confirmCreate() {
  if (!createDialog.title.trim()) {
    ElMessage.warning('连载标题不能为空')
    return
  }
  createDialog.saving = true
  try {
    const created = await seriesApi.create({
      title: createDialog.title.trim(),
      description: createDialog.description
    })
    createDialog.visible = false
    ElMessage.success('连载已创建')
    await loadMine()
    if (created?.id) ElMessage.info('可在「写作」页把文章加入该连载')
  } catch {
    /* 拦截器已提示 */
  } finally {
    createDialog.saving = false
  }
}

async function removeSeries(row) {
  await ElMessageBox.confirm(
    `确定删除连载「${row.title}」吗？目录与订阅关系会一并移除，文章本身不受影响。`,
    '删除连载', { type: 'warning' }
  )
  await seriesApi.remove(row.id)
  ElMessage.success('已删除')
  await loadMine()
}

onMounted(() => {
  loadMine()
  loadSubscribed()
})
</script>

<style scoped>
.page-head {
  flex-wrap: wrap;
  row-gap: 12px;
}

.series-block {
  margin-bottom: 32px;
}
.block-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--ink);
  margin-bottom: 16px;
}
.block-title {
  font-size: 18px;
  font-weight: 900;
  letter-spacing: 0.16em;
}
.block-sub {
  font-size: 11px;
  letter-spacing: 0.2em;
  color: var(--ink-faint);
}

.series-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: 16px;
}
.series-card {
  padding: 16px;
  background: var(--paper-card);
  border: 1px solid var(--hairline);
  border-top: 3px solid var(--ink);
  cursor: pointer;
  transition: box-shadow 0.25s ease, transform 0.25s ease;
}
.series-card.subscribed {
  border-top-color: var(--cinnabar);
}
.series-card:hover {
  box-shadow: 0 6px 22px rgba(60, 50, 30, 0.1);
  transform: translateY(-2px);
}
.card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.card-count {
  font-size: 12.5px;
  color: var(--ink-faint);
}
.card-title {
  margin: 10px 0 6px;
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 0.04em;
}
.series-card:hover .card-title {
  color: var(--cinnabar);
}
.card-desc {
  margin: 0 0 12px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--ink-soft);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: var(--ink-faint);
}
.subscribed-mark {
  color: var(--cinnabar);
  letter-spacing: 0.08em;
}

@media (max-width: 720px) {
  .series-grid {
    grid-template-columns: 1fr;
  }
}
</style>
