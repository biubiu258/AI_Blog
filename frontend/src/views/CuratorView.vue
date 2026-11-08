<template>
  <div class="page-container">
    <header class="page-head">
      <div>
        <div class="page-title">伯乐工作台</div>
        <div class="page-sub">CURATOR · 人工确认，推荐值得被看见的文章</div>
      </div>
      <el-button :loading="loadingCandidates" @click="loadCandidates">刷新候选池</el-button>
    </header>

    <el-result v-if="meChecked && !isCurator" icon="info" title="仅伯乐可进入工作台"
               sub-title="当前账号不在有效伯乐任期内，可联系运营指定任期。" />

    <div v-else v-loading="checking">
      <el-alert class="curator-note" type="warning" :closable="false" show-icon
                title="AI 仅提供排序，需人工确认">
        <template #default>
          候选顺序按 AI 辅助度、完读率与阅读量由系统排序，不代表自动收录；请阅读后由你人工确认并填写推荐理由。
        </template>
      </el-alert>

      <section class="curator-block">
        <div class="block-head">
          <span class="block-title">候选池</span>
          <span class="block-sub">CANDIDATES · {{ candidates.length }} 篇</span>
        </div>
        <div class="sheet">
          <el-table :data="candidates" v-loading="loadingCandidates" empty-text="暂无候选项">
            <el-table-column label="标题" min-width="210" show-overflow-tooltip>
              <template #default="{ row }">
                <el-link type="primary" @click="$router.push(`/article/${row.articleId}`)">
                  {{ row.title }}
                </el-link>
              </template>
            </el-table-column>
            <el-table-column label="作者" width="110">
              <template #default="{ row }">{{ row.authorNickname || '佚名' }}</template>
            </el-table-column>
            <el-table-column label="发布时间" width="120">
              <template #default="{ row }">{{ formatDate(row.publishedAt) }}</template>
            </el-table-column>
            <el-table-column prop="viewCount" label="阅读量" width="86" />
            <el-table-column prop="completeCount" label="完读数" width="86" />
            <el-table-column label="完读率" width="100">
              <template #default="{ row }">{{ row.completionRate || 0 }}%</template>
            </el-table-column>
            <el-table-column label="AI 参与" width="108">
              <template #default="{ row }">
                <el-tag size="small" effect="plain" :type="aiTagType(row.aiUsageLevel)">
                  {{ aiLabel(row.aiUsageLevel) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="辅助度" width="90">
              <template #default="{ row }">{{ row.assistScore ?? 0 }}</template>
            </el-table-column>
            <el-table-column label="人类徽章" width="96">
              <template #default="{ row }">
                <el-tag v-if="row.humanBadge" type="success" size="small" effect="plain">有</el-tag>
                <span v-else class="muted">—</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openRecommend(row)">推荐</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </section>

      <section class="curator-block">
        <div class="block-head">
          <span class="block-title">本周推荐位</span>
          <span class="block-sub">THIS WEEK · {{ slots.length }} 篇</span>
        </div>
        <el-empty v-if="!slots.length" :image-size="56" description="本周还没有推荐" />
        <div v-else class="slot-list">
          <div v-for="slot in slots" :key="slot.slotId" class="slot-item">
            <div class="slot-main">
              <div class="slot-title">
                <el-link type="primary" @click="$router.push(`/article/${slot.articleId}`)">
                  {{ slot.title }}
                </el-link>
                <span class="slot-by">伯乐 · {{ slot.curatorNickname || '匿名' }}</span>
              </div>
              <div class="slot-reason">“{{ slot.reason }}”</div>
            </div>
            <el-button link type="danger" @click="removeSlot(slot)">移除</el-button>
          </div>
        </div>
      </section>
    </div>

    <el-dialog v-model="recommendDialog.visible" title="确认推荐" width="480px">
      <el-form label-width="82px">
        <el-form-item label="文章">
          <span>{{ recommendDialog.article?.title }}</span>
        </el-form-item>
        <el-form-item label="推荐理由" required>
          <el-input v-model="recommendDialog.reason" type="textarea" :rows="4" maxlength="500"
                    show-word-limit placeholder="写给读者看的一句话，说明为什么值得读" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="recommendDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="recommendDialog.saving" @click="confirmRecommend">
          确认推荐
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { curatorApi } from '../api'

const checking = ref(true)
const meChecked = ref(false)
const isCurator = ref(false)
const loadingCandidates = ref(false)
const candidates = ref([])
const slots = ref([])
const recommendDialog = reactive({ visible: false, saving: false, article: null, reason: '' })

function aiLabel(level) {
  return { none: '未使用 AI', assisted: 'AI 辅助', generated: '主要生成' }[level] || '未声明'
}

function aiTagType(level) {
  if (level === 'generated') return 'danger'
  if (level === 'assisted') return 'warning'
  return 'info'
}

function formatDate(value) {
  return value ? String(value).replace('T', ' ').slice(0, 10) : ''
}

async function checkMe() {
  checking.value = true
  try {
    const data = await curatorApi.me()
    isCurator.value = !!data?.isCurator
  } catch {
    isCurator.value = false
  } finally {
    checking.value = false
    meChecked.value = true
  }
  if (isCurator.value) {
    await Promise.all([loadCandidates(), loadSlots()])
  }
}

async function loadCandidates() {
  loadingCandidates.value = true
  try {
    candidates.value = (await curatorApi.candidates()) || []
  } catch {
    candidates.value = []
  } finally {
    loadingCandidates.value = false
  }
}

async function loadSlots() {
  try {
    slots.value = (await curatorApi.slots()) || []
  } catch {
    slots.value = []
  }
}

function openRecommend(row) {
  recommendDialog.article = row
  recommendDialog.reason = ''
  recommendDialog.visible = true
}

async function confirmRecommend() {
  if (!recommendDialog.reason.trim()) {
    ElMessage.warning('推荐理由不能为空')
    return
  }
  recommendDialog.saving = true
  try {
    await curatorApi.recommend({
      articleId: recommendDialog.article.articleId,
      reason: recommendDialog.reason.trim()
    })
    recommendDialog.visible = false
    ElMessage.success('已加入本周推荐位')
    await Promise.all([loadCandidates(), loadSlots()])
  } catch {
    /* 拦截器已提示 */
  } finally {
    recommendDialog.saving = false
  }
}

async function removeSlot(slot) {
  await ElMessageBox.confirm(`确定移除《${slot.title}》的推荐位吗？`, '移除推荐', { type: 'warning' })
  await curatorApi.removeSlot(slot.slotId)
  ElMessage.success('已移除')
  await loadSlots()
}

onMounted(checkMe)
</script>

<style scoped>
.page-head {
  flex-wrap: wrap;
  row-gap: 12px;
}
.curator-note {
  margin-bottom: 22px;
}
.curator-block {
  margin-bottom: 32px;
}
.block-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--ink);
  margin-bottom: 14px;
}
.block-title {
  font-size: 18px;
  font-weight: 900;
  letter-spacing: 0.16em;
}
.block-sub {
  font-size: 11px;
  letter-spacing: 0.18em;
  color: var(--ink-faint);
}
.sheet {
  overflow-x: auto;
}
.muted {
  color: var(--ink-faint);
}
.slot-list {
  border-top: 1px solid var(--hairline);
}
.slot-item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  padding: 14px 4px;
  border-bottom: 1px solid var(--hairline);
}
.slot-main {
  min-width: 0;
}
.slot-title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  font-size: 15px;
  font-weight: 600;
}
.slot-by {
  font-size: 12px;
  color: var(--ink-faint);
  font-weight: 400;
}
.slot-reason {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--cinnabar-deep);
  padding-left: 10px;
  border-left: 2px solid var(--cinnabar);
}
@media (max-width: 720px) {
  .curator-block :deep(.el-table) {
    min-width: 840px;
  }
}
</style>
