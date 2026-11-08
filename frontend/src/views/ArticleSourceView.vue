<template>
  <div class="page-container">
    <!-- 加载失败（404 / 草稿不可见） -->
    <div v-if="!loading && !trace" class="trace-fail">
      <el-empty description="溯源数据不存在，或该文章尚未发布" />
      <el-button type="primary" plain @click="goBackArticle">返回文章</el-button>
    </div>

    <div v-else v-loading="loading" class="trace-wrap">
      <div class="trace-top">
        <router-link :to="`/article/${articleId}`" class="trace-back">← 返回文章</router-link>
        <span class="trace-kicker">CREATION TRACE</span>
      </div>

      <h1 class="trace-title">{{ trace.title }}</h1>

      <!-- 1. 三档标识条 -->
      <div class="tier-bar" :class="`tier-${tier}`">
        <div class="tier-main">
          <span class="tier-tag">{{ tierText }}</span>
          <span class="tier-label">{{ trace.label?.text || '创作方式说明' }}</span>
          <el-tag v-if="trace.humanBadge" type="success" size="small" effect="dark">人类创作徽章</el-tag>
        </div>
        <div v-if="trace.assistScore != null" class="tier-score">
          <el-progress type="circle" :percentage="trace.assistScore" :width="68" :stroke-width="6"
                       :color="tierColor" />
          <span class="tier-score-label">AI 辅助度评分</span>
        </div>
      </div>

      <!-- 2. 统计卡片区 -->
      <div class="trace-stats">
        <div class="stat-card">
          <div class="stat-value">{{ trace.aiCallCount ?? 0 }}</div>
          <div class="stat-label">AI 调用次数</div>
        </div>
        <div class="stat-card">
          <div class="stat-value">{{ trace.adoptedCount ?? 0 }}</div>
          <div class="stat-label">采纳次数</div>
        </div>
        <div class="stat-card">
          <div class="stat-value">{{ trace.failedCount ?? 0 }}</div>
          <div class="stat-label">失败次数</div>
        </div>
        <div class="stat-card">
          <div class="stat-value">{{ trace.manualSaveCount ?? 0 }}</div>
          <div class="stat-label">人工保存次数</div>
        </div>
        <div class="stat-card">
          <div class="stat-value">{{ humanEditRatioText }}</div>
          <div class="stat-label">人工修改占比</div>
        </div>
      </div>

      <!-- 3. 环节分布 -->
      <section v-if="trace.actionStats?.length" class="trace-section">
        <div class="section-title">环节分布</div>
        <el-table :data="trace.actionStats" size="small" class="action-table">
          <el-table-column prop="name" label="环节" min-width="140" />
          <el-table-column prop="calls" label="调用" width="100" />
          <el-table-column prop="adopted" label="采纳" width="100" />
        </el-table>
      </section>

      <!-- 4. 时间线 -->
      <section class="trace-section">
        <div class="section-title">创作时间线</div>
        <el-empty v-if="!timeline.length" description="本篇无 AI 调用记录，人工完成创作" :image-size="72" />
        <el-timeline v-else class="trace-timeline">
          <el-timeline-item v-for="(item, i) in timeline" :key="i"
                            :timestamp="formatTime(item.time)" placement="top"
                            :type="timelineType(item.status)">
            <div class="tl-item" :class="`tl-${item.status}`">
              <div class="tl-head">
                <span class="tl-name">{{ item.name || item.action }}</span>
                <el-tag v-if="item.task" size="small" effect="plain">{{ item.task }}</el-tag>
                <el-tag size="small" effect="plain" :type="statusTagType(item.status)">
                  {{ statusText(item.status) }}
                </el-tag>
                <el-tag v-if="item.adopted" size="small" type="success" effect="light">已采纳</el-tag>
              </div>
              <div v-if="item.flagged" class="tl-flagged">内容已被标记，预览隐藏</div>
              <div v-else-if="item.preview" class="tl-preview">{{ item.preview }}</div>
              <div class="tl-meta">
                <span v-if="item.outputLength != null">输出 {{ item.outputLength }} 字</span>
                <span v-if="item.durationMs != null">耗时 {{ item.durationMs }} ms</span>
              </div>
            </div>
          </el-timeline-item>
        </el-timeline>
      </section>

      <!-- 5. 底部说明 -->
      <div class="trace-footnote">溯源数据来自全链路审计日志，不包含提示词与全文内容</div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { articleApi } from '../api'

const route = useRoute()
const articleId = computed(() => route.params.id)
const loading = ref(false)
const trace = ref(null)

const tier = computed(() => trace.value?.label?.tier
  || (trace.value?.aiUsageLevel === 'generated' ? 'generated'
    : trace.value?.aiUsageLevel === 'assisted' ? 'assisted' : 'human'))
const tierText = computed(() => ({
  generated: '属于生成合成', assisted: 'AI 辅助创作', human: '人类创作'
}[tier.value] || '创作方式说明'))
const tierColor = computed(() => ({
  generated: '#c0453a', assisted: '#d98a2b', human: '#4a8c5c'
}[tier.value] || '#8a8578'))

const humanEditRatioText = computed(() =>
  trace.value?.humanEditRatio == null ? '—' : `${trace.value.humanEditRatio}%`)

const timeline = computed(() => trace.value?.timeline || [])

function timelineType(status) {
  if (status === 'success') return 'success'
  if (status === 'failed') return 'danger'
  if (status === 'streaming') return 'warning'
  return 'info'
}

function statusTagType(status) {
  if (status === 'success') return 'success'
  if (status === 'failed') return 'danger'
  if (status === 'streaming') return 'warning'
  return 'info'
}

function statusText(status) {
  return { success: '成功', failed: '失败', streaming: '进行中' }[status] || status || '-'
}

function formatTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : ''
}

function goBackArticle() {
  if (history.length > 1) history.back()
}

async function load() {
  loading.value = true
  try {
    trace.value = await articleApi.trace(articleId.value)
  } catch {
    // 404 等：拦截器已提示，这里进入失败态展示
    trace.value = null
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.trace-fail {
  padding: 80px 0;
  text-align: center;
}
.trace-fail .el-button {
  margin-top: 8px;
}
.trace-wrap {
  max-width: 780px;
  margin: 0 auto;
}
.trace-top {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}
.trace-back {
  font-size: 13.5px;
  color: var(--ink-soft);
  text-decoration: none;
  transition: color 0.2s ease;
}
.trace-back:hover {
  color: var(--cinnabar);
}
.trace-kicker {
  font-size: 11px;
  letter-spacing: 0.24em;
  color: var(--ink-faint);
}
.trace-title {
  margin: 14px 0 20px;
  font-size: 28px;
  font-weight: 900;
  letter-spacing: 0.04em;
  line-height: 1.45;
}

/* 三档标识条 */
.tier-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 18px;
  border: 1px solid var(--hairline-dark);
  border-radius: 4px;
  background: var(--paper-card);
}
.tier-generated {
  border-left: 4px solid #c0453a;
  background: rgba(192, 69, 58, 0.05);
}
.tier-assisted {
  border-left: 4px solid #d98a2b;
  background: rgba(217, 138, 43, 0.06);
}
.tier-human {
  border-left: 4px solid #4a8c5c;
  background: rgba(74, 140, 92, 0.06);
}
.tier-main {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}
.tier-tag {
  padding: 3px 10px;
  border-radius: 3px;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: #fff;
  background: #8a8578;
}
.tier-generated .tier-tag { background: #c0453a; }
.tier-assisted .tier-tag { background: #d98a2b; }
.tier-human .tier-tag { background: #4a8c5c; }
.tier-label {
  font-size: 13.5px;
  color: var(--ink-soft);
}
.tier-score {
  display: flex;
  align-items: center;
  gap: 10px;
}
.tier-score-label {
  font-size: 12px;
  color: var(--ink-faint);
  letter-spacing: 0.06em;
  writing-mode: vertical-rl;
}

/* 统计卡片 */
.trace-stats {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
  margin: 18px 0 26px;
}
.stat-card {
  padding: 14px 10px;
  text-align: center;
  border: 1px solid var(--hairline-dark);
  border-top: 3px solid var(--ink);
  background: var(--paper-card);
}
.stat-value {
  font-size: 24px;
  font-weight: 900;
  color: var(--ink);
  font-variant-numeric: tabular-nums;
}
.stat-label {
  margin-top: 4px;
  font-size: 12px;
  color: var(--ink-faint);
  letter-spacing: 0.06em;
}

.trace-section {
  margin-bottom: 30px;
}
.section-title {
  margin-bottom: 12px;
  padding-left: 10px;
  border-left: 3px solid var(--cinnabar);
  font-size: 15px;
  font-weight: 800;
  letter-spacing: 0.12em;
}
.action-table {
  width: 100%;
}

/* 时间线 */
.trace-timeline {
  padding: 4px 4px 0;
}
.tl-item {
  padding: 10px 14px;
  border: 1px solid var(--hairline);
  border-radius: 3px;
  background: var(--paper-deep);
}
.tl-item.tl-failed {
  border-left: 3px solid #c0453a;
}
.tl-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.tl-name {
  font-weight: 700;
  font-size: 14px;
  color: var(--ink);
}
.tl-preview {
  margin-top: 8px;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--ink-faint);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.tl-flagged {
  margin-top: 8px;
  font-size: 12.5px;
  color: #c0453a;
}
.tl-meta {
  display: flex;
  gap: 14px;
  margin-top: 6px;
  font-size: 12px;
  color: var(--ink-faint);
  font-variant-numeric: tabular-nums;
}
.trace-footnote {
  margin-top: 8px;
  padding-top: 16px;
  border-top: 1px solid var(--hairline);
  text-align: center;
  font-size: 12px;
  letter-spacing: 0.06em;
  color: var(--ink-faint);
}

@media (max-width: 720px) {
  .trace-stats {
    grid-template-columns: repeat(2, 1fr);
  }
  .tier-bar {
    flex-direction: column;
    align-items: flex-start;
  }
  .tier-score-label {
    writing-mode: horizontal-tb;
  }
}
</style>
