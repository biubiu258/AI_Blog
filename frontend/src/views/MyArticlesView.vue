<template>
  <div class="page-container">
    <header class="page-head">
      <div class="page-title">我的文章</div>
      <div class="page-sub">MY ARTICLES</div>
    </header>

    <div class="toolbar">
      <el-radio-group v-model="statusFilter" @change="load(1)">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button value="draft">草稿</el-radio-button>
        <el-radio-button value="published">已发布</el-radio-button>
      </el-radio-group>
      <el-button type="primary" @click="$router.push('/write')">
        <el-icon><Plus /></el-icon>&nbsp;写文章
      </el-button>
    </div>

    <el-empty v-if="!loading && items.length === 0" description="还没有文章" />
    <div v-else class="sheet">
      <el-table v-loading="loading" :data="items">
        <el-table-column label="№" width="64" align="center">
          <template #default="{ $index }">
            <span class="serial">{{ String((query.page - 1) * query.size + $index + 1).padStart(2, '0') }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link type="primary" @click="$router.push(`/article/${row.id}`)">{{ row.title }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="分类" width="110">
          <template #default="{ row }">{{ row.categoryName || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="96">
          <template #default="{ row }">
            <el-tag :type="row.status === 'published' ? 'success' : 'warning'" size="small" effect="plain">
              {{ row.status === 'published' ? '已发布' : '草稿' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="170">
          <template #default="{ row }"><span class="date-text">{{ formatDate(row.updatedAt) }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="$router.push(`/write/${row.id}`)">编辑</el-button>
            <el-button v-if="row.status === 'draft'" link type="success"
                       @click="publish(row)">发布</el-button>
            <el-button v-else link type="warning" @click="withdraw(row)">撤回</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="pager">
      <el-pagination background layout="prev, pager, next, total" :total="total"
                     :page-size="query.size" :current-page="query.page" @current-change="load" />
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { articleApi } from '../api'

const loading = ref(false)
const items = ref([])
const total = ref(0)
const statusFilter = ref('')
const query = reactive({ page: 1, size: 10 })

async function load(page = query.page) {
  query.page = page
  loading.value = true
  try {
    const data = await articleApi.mine({
      page: query.page,
      size: query.size,
      status: statusFilter.value || undefined
    })
    items.value = data.items
    total.value = data.total
  } finally {
    loading.value = false
  }
}

async function publish(row) {
  const detail = await articleApi.detail(row.id)
  await articleApi.update(row.id, {
    title: detail.title,
    categoryId: detail.categoryId,
    contentHtml: detail.contentHtml,
    summary: detail.summary,
    status: 'published'
  })
  ElMessage.success('已发布')
  load()
}

async function withdraw(row) {
  const detail = await articleApi.detail(row.id)
  await articleApi.update(row.id, {
    title: detail.title,
    categoryId: detail.categoryId,
    contentHtml: detail.contentHtml,
    summary: detail.summary,
    status: 'draft'
  })
  ElMessage.success('已撤回为草稿')
  load()
}

async function remove(row) {
  await ElMessageBox.confirm(`确定删除《${row.title}》吗？`, '提示', { type: 'warning' })
  await articleApi.remove(row.id)
  ElMessage.success('已删除')
  load()
}

function formatDate(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : ''
}

onMounted(() => load(1))
</script>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 18px;
}

/* 状态筛选：墨块选中、纸面未选 */
.toolbar :deep(.el-radio-button__inner) {
  background: var(--paper-card);
  border-color: var(--hairline-dark);
  color: var(--ink-soft);
  font-family: var(--font-serif);
  letter-spacing: 0.1em;
  box-shadow: none !important;
}
.toolbar :deep(.el-radio-button__inner:hover) {
  color: var(--cinnabar);
}
.toolbar :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner),
.toolbar :deep(.el-radio-button.is-active .el-radio-button__inner) {
  background: var(--ink);
  border-color: var(--ink);
  color: var(--paper);
}

.serial {
  color: var(--ink-faint);
  font-size: 13px;
  letter-spacing: 0.08em;
}
.date-text {
  color: var(--ink-faint);
  font-size: 13px;
  letter-spacing: 0.04em;
}

.pager {
  margin-top: 22px;
  display: flex;
  justify-content: center;
}
</style>
