<template>
  <div class="prose-page">
    <header class="page-head">
      <div class="page-title">散文</div>
      <div class="page-sub">PROSE · 以影寄情</div>
    </header>
    <el-row :gutter="16">
      <!-- 左侧：素材（风格 + 情绪 + 近况 + 图片） -->
      <el-col :span="10" class="material-col">
        <el-card shadow="never" class="panel-card material-card">
          <template #header>
            <div class="card-header">
              <span>素材</span>
              <el-tag size="small" type="info">图片选填，最多 5 张</el-tag>
            </div>
          </template>

          <el-form label-position="top" class="material-form">
            <el-form-item label="散文风格">
              <el-radio-group v-model="style">
                <el-radio-button value="luxun">鲁迅散文</el-radio-button>
                <el-radio-button value="plain">自由白话</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="此刻的情绪">
              <el-input v-model="emotion" maxlength="200" show-word-limit
                        placeholder="如：平静、疲惫、怅然若失…" />
              <div class="emotion-chips">
                <button v-for="word in emotionPresets" :key="word" type="button"
                        class="emotion-chip" :class="{ active: emotion === word }"
                        @click="emotion = word">{{ word }}</button>
              </div>
            </el-form-item>
            <el-form-item label="近况 / 想说的">
              <el-input v-model="recentState" type="textarea" :rows="5" maxlength="1000"
                        show-word-limit placeholder="最近发生了什么？想借着这张照片写点什么？" />
            </el-form-item>
          </el-form>

          <el-upload drag multiple :show-file-list="false" :limit="5" :http-request="uploadImage"
                     accept="image/jpeg,image/png,image/gif,image/webp" :on-exceed="onExceed"
                     :disabled="images.length >= 5 || uploading" class="upload-drop">
            <el-icon class="upload-icon"><Plus /></el-icon>
            <div class="upload-text">
              {{ uploading ? '上传中…' : `点击或拖拽图片到此处（${images.length}/5）` }}
            </div>
          </el-upload>
          <div class="hint">可以是人物照、风景照，或任何触动你的画面</div>

          <div v-if="images.length" class="thumbs">
            <div v-for="img in images" :key="img.url" class="thumb-item">
              <img :src="img.url" :alt="img.name" />
              <el-icon class="thumb-remove" @click="removeImage(img.url)"><Close /></el-icon>
            </div>
          </div>

          <div class="actions">
            <el-button type="primary" size="large" class="generate-btn" :loading="generating"
                       :disabled="generating || !canGenerate" @click="startGenerate">
              {{ outputMd ? '重新生成散文' : '生成散文初稿' }}
            </el-button>
            <el-button v-if="generating" type="danger" @click="stopGenerate">停止</el-button>
          </div>
          <div v-if="!canGenerate && !generating" class="hint">
            填写情绪或近况其一即可生成；图片可选
          </div>
          <div v-if="materialRestored" class="hint">已恢复上次未完成的素材</div>
        </el-card>
      </el-col>

      <!-- 右侧：初稿结果 -->
      <el-col :span="14">
        <el-card shadow="never" class="result-card panel-card">
          <template #header>
            <div class="card-header">
              <span>初稿</span>
              <div class="header-actions">
                <el-tag size="small" :type="generating ? 'warning' : 'info'">
                  {{ generating ? '流式生成中' : (editing ? '编辑中' : 'SSE 流式') }}
                </el-tag>
                <el-segmented v-if="outputMd && !generating" v-model="viewMode" size="small"
                              :options="viewModeOptions" />
              </div>
            </div>
          </template>

          <div v-if="generating" class="stream-tip">
            <el-icon class="is-loading"><Loading /></el-icon>
            正在生成，已接收 {{ outputMd.length }} 字…
          </div>

          <template v-if="outputMd && !generating">
            <el-input v-model="title" maxlength="200" placeholder="给这篇散文起个标题"
                      class="title-input" />
          </template>

          <template v-if="editing">
            <el-input ref="draftTextarea" v-model="outputMd" type="textarea" :rows="16"
                      class="draft-editor" placeholder="在此修改初稿…"
                      @select="onDraftSelect" @keyup="onDraftSelect" @mouseup="onDraftSelect"
                      @input="onDraftInput" />
            <div class="selection-bar">
              <span class="selection-bar-label">
                {{ draftSelection ? `已选中 ${draftSelection.selectedText.length} 字` : '选中文字后可让 AI 给建议' }}
              </span>
              <template v-if="draftSelection">
                <el-button v-for="m in draftModes" :key="m.mode" size="small"
                           :disabled="draftLoading" @click="askDraft(m.mode)">{{ m.label }}</el-button>
              </template>
            </div>
            <div v-if="draftLoading" class="stream-tip">
              <el-icon class="is-loading"><Loading /></el-icon>
              正在生成建议，已接收 {{ draftResult.length }} 字…
            </div>
            <template v-if="draftResult">
              <div class="pane-caption">
                <span class="pane-mode">{{ draftModeLabel }}</span>
                <span>{{ draftSelection?.selectedText.length || 0 }} 字</span>
              </div>
              <blockquote v-if="draftSelection" class="pane-quote">{{ draftSelection.selectedText }}</blockquote>
              <div class="markdown-body result-body" v-html="renderMarkdown(draftResult)"></div>
              <div class="result-actions">
                <el-button v-if="draftMode !== 'advice'" size="small" type="primary"
                           @click="applyDraftSelectionSuggestion">替换选中</el-button>
                <el-button size="small" @click="draftResult = ''">清除</el-button>
              </div>
            </template>
          </template>
          <template v-else>
            <div v-if="outputMd" ref="previewBodyRef" class="markdown-body result-body preview-body"
                 v-html="renderedOutput"></div>
            <el-empty v-else description="写下情绪或近况，点击生成；配图更有画面感" />
          </template>

          <template v-if="outputMd && !generating">
            <div class="result-actions save-actions">
              <el-button size="small" type="primary" :loading="saving" @click="saveDraft">
                存为草稿并继续编辑
              </el-button>
              <el-button size="small" @click="copyResult">复制</el-button>
            </div>

            <el-divider content-position="left">全文建议</el-divider>
            <div class="review-bar">
              <el-button size="small" type="primary" :loading="reviewLoading"
                         :disabled="reviewLoading" @click="reviewDraft(false)">审阅初稿</el-button>
              <span class="hint">建议基于当前初稿；点「替换」才会写入，可随时忽略</span>
            </div>
            <AiSuggestionPanel :suggestions="reviewSuggestions" :loading="reviewLoading"
                               empty-text="暂无建议，点击「审阅初稿」开始"
                               @locate="locateDraftSuggestion" @apply="applyDraftSuggestion"
                               @ignore="ignoreDraftSuggestion" />
          </template>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import DOMPurify from 'dompurify'
import { marked } from 'marked'
import { articleApi, fileApi } from '../api'
import { aiGenerate, aiReview } from '../api/ai'
import AiSuggestionPanel from '../components/AiSuggestionPanel.vue'

const router = useRouter()

const images = ref([])
const uploading = ref(false)
const style = ref('luxun')
const emotion = ref('')
const recentState = ref('')

const emotionPresets = ['平静', '疲惫', '怅然', '思念', '喜悦', '释然', '焦躁', '感激']

const generating = ref(false)
const outputMd = ref('')
const title = ref('')
const saving = ref(false)
const previewBodyRef = ref(null)
let abortController = null

// ---------- 初稿编辑与划词建议 ----------
const editing = ref(false)
const draftTextarea = ref(null)
const draftSelection = ref(null)
const draftMode = ref('advice')
const draftLoading = ref(false)
const draftResult = ref('')
let draftAbort = null

const viewModeOptions = [
  { label: '预览', value: 'preview' },
  { label: '编辑', value: 'edit' }
]
const viewMode = computed({
  get: () => (editing.value ? 'edit' : 'preview'),
  set: (value) => setEditing(value === 'edit')
})

const draftModes = [
  { mode: 'advice', label: '给建议' },
  { mode: 'polish', label: '润色' },
  { mode: 'rewrite', label: '改写' },
  { mode: 'expand', label: '扩写' },
  { mode: 'condense', label: '缩写' }
]

const DRAFT_MODE_LABELS = {
  advice: 'AI 建议',
  polish: '润色建议',
  rewrite: '改写建议',
  expand: '扩写建议',
  condense: '缩写建议'
}
const draftModeLabel = computed(() => DRAFT_MODE_LABELS[draftMode.value] || 'AI 建议')

function renderMarkdown(md) {
  try {
    return DOMPurify.sanitize(marked.parse(md || '', { breaks: true }))
  } catch {
    return ''
  }
}

function setEditing(value) {
  editing.value = value
  draftSelection.value = null
  draftResult.value = ''
}

function textareaEl() {
  return draftTextarea.value?.textarea || null
}

function onDraftSelect() {
  const el = textareaEl()
  if (!el) return
  const start = el.selectionStart
  const end = el.selectionEnd
  if (start === end || end - start > 2000) {
    draftSelection.value = null
    return
  }
  draftSelection.value = { start, end, selectedText: outputMd.value.slice(start, end) }
}

function onDraftInput() {
  draftSelection.value = null
}

async function askDraft(mode) {
  const snapshot = draftSelection.value
  if (!snapshot || !snapshot.selectedText.trim()) {
    ElMessage.warning('请先在初稿中选中一段文字')
    return
  }
  const text = outputMd.value
  draftMode.value = mode
  draftResult.value = ''
  draftLoading.value = true
  draftAbort?.abort()
  draftAbort = new AbortController()
  try {
    await aiGenerate({
      task: 'suggest',
      params: {
        suggestionMode: mode,
        topic: title.value || undefined,
        style: style.value
      },
      context: {
        selectedText: snapshot.selectedText,
        textBefore: text.slice(Math.max(0, snapshot.start - 300), snapshot.start),
        textAfter: text.slice(snapshot.end, snapshot.end + 300),
        instruction: ''
      }
    }, {
      signal: draftAbort.signal,
      onDelta: (content) => {
        draftResult.value += content
      },
      onDone: (content) => {
        draftResult.value = content
        draftLoading.value = false
        draftAbort?.abort()
      },
      onError: (message, code) => {
        draftLoading.value = false
        if (code === 'LLM_TIMEOUT') ElMessage.error('建议生成超时，请重试')
        else if (code === 'EMPTY_OUTPUT') ElMessage.warning('建议为空，请换个片段试试')
        else ElMessage.error(message)
      }
    })
  } catch (err) {
    if (err?.name !== 'AbortError' && err?.name !== 'CanceledError') {
      ElMessage.error(err?.message || '建议生成失败，请重试')
    }
  } finally {
    draftLoading.value = false
  }
}

function findQuote(text, quote) {
  const q = (quote || '').trim()
  if (!q || !text) return -1
  const idx = text.indexOf(q)
  if (idx >= 0 && text.indexOf(q, idx + 1) === -1) return idx
  return -1
}

function applyDraftSelectionSuggestion() {
  const snapshot = draftSelection.value
  const replacement = draftResult.value.trim()
  if (!snapshot || !replacement) return
  const text = outputMd.value
  let start = snapshot.start
  if (text.slice(start, start + snapshot.selectedText.length) !== snapshot.selectedText) {
    start = findQuote(text, snapshot.selectedText)
    if (start < 0) {
      ElMessage.warning('原文已修改，请重新选中后再试')
      return
    }
  }
  outputMd.value = text.slice(0, start) + replacement
    + text.slice(start + snapshot.selectedText.length)
  ElMessage.success('已替换选中内容')
  draftSelection.value = null
  draftResult.value = ''
}

// ---------- 初稿全文建议 ----------
const reviewLoading = ref(false)
const reviewSuggestions = ref([])

async function reviewDraft() {
  if (reviewLoading.value) return
  const content = outputMd.value.trim()
  if (!content) {
    ElMessage.warning('初稿为空，无需审阅')
    return
  }
  if (content.length > 20000) {
    ElMessage.warning('初稿超过 20000 字，请分段审阅')
    return
  }
  reviewLoading.value = true
  const snapshot = outputMd.value
  try {
    const data = await aiReview({ title: title.value || undefined, content: snapshot })
    const items = (data?.suggestions || []).map((item) => ({
      ...item,
      found: findQuote(snapshot, item.quote) >= 0
    }))
    reviewSuggestions.value = items
    if (!items.length) ElMessage.info('审阅完成：暂未发现需要调整的地方')
    else ElMessage.success(`审阅完成：${items.length} 条建议`)
  } catch {
    /* request 拦截器已统一提示错误 */
  } finally {
    reviewLoading.value = false
  }
}

async function locateDraftSuggestion(index) {
  const item = reviewSuggestions.value[index]
  if (!item) return
  const idx = findQuote(outputMd.value, item.quote)
  if (idx < 0) {
    item.found = false
    ElMessage.warning('原文已修改，本条无法定位')
    return
  }
  if (!editing.value) setEditing(true)
  await nextTick()
  const el = textareaEl()
  if (!el) return
  el.focus()
  el.setSelectionRange(idx, idx + item.quote.length)
  draftSelection.value = { start: idx, end: idx + item.quote.length, selectedText: item.quote }
}

function applyDraftSuggestion(index) {
  const item = reviewSuggestions.value[index]
  if (!item?.replacement) return
  const idx = findQuote(outputMd.value, item.quote)
  if (idx < 0) {
    item.found = false
    ElMessage.warning('原文已修改，请重新审阅')
    return
  }
  outputMd.value = outputMd.value.slice(0, idx) + item.replacement
    + outputMd.value.slice(idx + item.quote.length)
  ElMessage.success('已替换')
  reviewSuggestions.value.splice(index, 1)
}

function ignoreDraftSuggestion(index) {
  reviewSuggestions.value.splice(index, 1)
}

const canGenerate = computed(() =>
  !!emotion.value.trim() || !!recentState.value.trim())

const renderedOutput = computed(() => {
  try {
    return DOMPurify.sanitize(marked.parse(outputMd.value || '', { breaks: true }))
  } catch {
    return ''
  }
})

onBeforeUnmount(() => {
  if (abortController) abortController.abort()
  if (draftAbort) draftAbort.abort()
})

// ---------- 素材本地暂存（刷新不丢；保存草稿后清除） ----------
const MATERIAL_KEY = 'aiblog:prose:material'
const materialRestored = ref(false)

function persistMaterial() {
  try {
    if (!emotion.value && !recentState.value && !images.value.length) return
    localStorage.setItem(MATERIAL_KEY, JSON.stringify({
      style: style.value,
      emotion: emotion.value,
      recentState: recentState.value,
      images: images.value
    }))
  } catch {
    // 本地存储不可用时静默降级
  }
}

function restoreMaterial() {
  let raw = null
  try {
    raw = localStorage.getItem(MATERIAL_KEY)
  } catch {
    return
  }
  if (!raw) return
  try {
    const snap = JSON.parse(raw)
    if (snap?.style) style.value = snap.style
    if (snap?.emotion) emotion.value = snap.emotion
    if (snap?.recentState) recentState.value = snap.recentState
    if (Array.isArray(snap?.images)) images.value = snap.images.filter((i) => i?.url)
    materialRestored.value = !!(snap?.emotion || snap?.recentState || images.value.length)
  } catch {
    localStorage.removeItem(MATERIAL_KEY)
  }
}

function clearMaterial() {
  materialRestored.value = false
  try {
    localStorage.removeItem(MATERIAL_KEY)
  } catch {
    // 忽略本地存储异常
  }
}

watch([style, emotion, recentState, images], persistMaterial, { deep: true })

onMounted(restoreMaterial)

// ---------- 图片上传 ----------
async function uploadImage(options) {
  const fd = new FormData()
  fd.append('file', options.file)
  uploading.value = true
  try {
    const data = await fileApi.upload(fd)
    images.value.push({ url: data.url, name: options.file.name })
    options.onSuccess(data)
  } catch (e) {
    options.onError(e)
  } finally {
    uploading.value = false
  }
}

function onExceed() {
  ElMessage.warning('最多上传 5 张图片')
}

function removeImage(url) {
  images.value = images.value.filter((i) => i.url !== url)
}

// ---------- 生成 ----------
function buildPayload() {
  return {
    task: 'prose',
    params: {
      style: style.value,
      emotion: emotion.value.trim() || undefined,
      recentState: recentState.value.trim() || undefined
    },
    context: {
      imageUrls: images.value.map((i) => i.url)
    }
  }
}

async function startGenerate() {
  // 已有初稿时重新生成前先确认，避免覆盖未保存的手工修改
  if (outputMd.value && !generating.value) {
    try {
      await ElMessageBox.confirm(
        '重新生成将丢弃当前初稿（含手工修改），是否继续？',
        '重新生成',
        { type: 'warning', confirmButtonText: '重新生成', cancelButtonText: '取消' }
      )
    } catch {
      return
    }
  }
  generating.value = true
  outputMd.value = ''
  title.value = ''
  reviewSuggestions.value = []
  setEditing(false)
  abortController = new AbortController()
  try {
    // delta 追加；done 用完整文本整体替换（契约 5.4.3）
    await aiGenerate(buildPayload(), {
      signal: abortController.signal,
      onDelta: (content) => {
        outputMd.value += content
        scrollResult()
      },
      onDone: (content) => {
        outputMd.value = content
        if (!title.value) title.value = deriveTitle(content)
        // 服务端已给出终态：立即解锁 UI 并主动断开连接。
        // 不能只依赖 await 返回——底层连接若未正常收尾，会一直停在“正在生成”，
        // 存为草稿的按钮也不会出现。
        generating.value = false
        abortController?.abort()
      },
      onError: (message, code) => {
        // 错误帧也是终态：先解锁 UI，再提示原因
        generating.value = false
        if (code === 'LLM_TIMEOUT') {
          ElMessage.error('生成超时，请重试')
        } else if (code === 'EMPTY_OUTPUT') {
          ElMessage.warning('生成结果为空，请重试')
        } else {
          ElMessage.error(message)
        }
      }
    })
  } catch (err) {
    if (err?.name === 'AbortError' || err?.name === 'CanceledError') {
      ElMessage.info('已停止生成（已接收内容保留在下方，可手动使用）')
    } else {
      ElMessage.error(err?.message || '生成失败，请重试')
    }
  } finally {
    generating.value = false
  }
}

function stopGenerate() {
  abortController?.abort()
}

async function scrollResult() {
  await nextTick()
  const el = previewBodyRef.value
  if (el) el.scrollTop = el.scrollHeight
}

// ---------- 结果处理 ----------
function deriveTitle(md) {
  const line = (md || '').split('\n').map((s) => s.trim()).find(Boolean) || ''
  return line.replace(/^#+\s*/, '').slice(0, 60)
}

function mdToHtml(md) {
  return DOMPurify.sanitize(marked.parse(md || '', { breaks: true }))
}

function deriveSummary(md) {
  return (md || '')
    .replace(/^#+\s*/gm, '')
    .replace(/[*_`>#-]/g, '')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 120)
}

async function copyResult() {
  try {
    await navigator.clipboard.writeText(outputMd.value)
    ElMessage.success('已复制')
  } catch {
    ElMessage.warning('复制失败，请手动选择复制')
  }
}

async function saveDraft() {
  if (!title.value.trim()) {
    ElMessage.warning('请输入文章标题')
    return
  }
  saving.value = true
  try {
    const created = await articleApi.create({
      title: title.value.trim(),
      contentHtml: mdToHtml(outputMd.value),
      summary: deriveSummary(outputMd.value),
      status: 'draft'
    })
    clearMaterial()
    ElMessage.success('草稿已保存')
    router.push(`/write/${created.id}`)
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.prose-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 28px 20px 64px;
  animation: page-in 0.45s cubic-bezier(0.22, 0.8, 0.32, 1) both;
}
.prose-page .page-head {
  margin-bottom: 20px;
}

/* 面板：纸面 + 顶部粗墨线 */
.panel-card {
  border-top: 3px solid var(--ink);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  letter-spacing: 0.08em;
}
.hint {
  margin-top: 8px;
  color: var(--ink-faint);
  font-size: 13px;
  line-height: 1.6;
  letter-spacing: 0.04em;
}

/* ---------- 素材栏 ---------- */
.material-col {
  align-self: flex-start;
  position: sticky;
  top: 20px;
}
.material-form :deep(.el-form-item) {
  margin-bottom: 14px;
}
.emotion-chips {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.emotion-chip {
  padding: 2px 10px;
  border: 1px solid var(--hairline-dark);
  border-radius: 999px;
  background: transparent;
  color: var(--ink-soft);
  font-family: var(--font-serif);
  font-size: 12.5px;
  letter-spacing: 0.06em;
  cursor: pointer;
  transition: all 0.2s ease;
}
.emotion-chip:hover {
  border-color: var(--cinnabar);
  color: var(--cinnabar);
}
.emotion-chip.active {
  background: var(--cinnabar);
  border-color: var(--cinnabar);
  color: #fdfbf4;
}

/* 拖拽上传区 */
.upload-drop :deep(.el-upload) {
  width: 100%;
}
.upload-drop :deep(.el-upload-dragger) {
  width: 100%;
  padding: 18px 12px;
  background: var(--paper-deep);
  border: 1px dashed var(--hairline-dark);
  border-radius: 2px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  transition: border-color 0.2s ease;
}
.upload-drop :deep(.el-upload-dragger:hover) {
  border-color: var(--cinnabar);
}
.upload-drop :deep(.el-upload.is-disabled .el-upload-dragger) {
  opacity: 0.55;
  cursor: not-allowed;
}
.upload-icon {
  font-size: 20px;
  color: var(--ink-faint);
}
.upload-text {
  font-size: 13px;
  color: var(--ink-soft);
  letter-spacing: 0.05em;
}

.thumbs {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 12px;
}
.thumb-item {
  position: relative;
  width: 96px;
  height: 96px;
  border-radius: 2px;
  overflow: hidden;
  border: 1px solid var(--hairline-dark);
  box-shadow: 3px 3px 0 rgba(38, 36, 30, 0.08);
}
.thumb-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.thumb-remove {
  position: absolute;
  top: 4px;
  right: 4px;
  padding: 2px;
  border-radius: 50%;
  background: rgba(38, 36, 30, 0.6);
  color: #fdfbf4;
  cursor: pointer;
  font-size: 12px;
}
.actions {
  margin-top: 16px;
  display: flex;
  gap: 8px;
}
.generate-btn {
  flex: 1;
}

/* ---------- 初稿栏 ---------- */
.result-card {
  min-height: 420px;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.header-actions :deep(.el-segmented) {
  --el-segmented-bg-color: var(--paper-deep);
  --el-segmented-item-selected-bg-color: var(--cinnabar);
  --el-segmented-item-selected-color: #fdfbf4;
  font-family: var(--font-serif);
}
.title-input {
  margin-bottom: 12px;
}
.title-input :deep(.el-input__wrapper) {
  background: transparent;
  box-shadow: none;
  border-radius: 0;
  border-bottom: 1px solid var(--hairline-dark);
  padding-left: 2px;
  padding-right: 2px;
  transition: border-color 0.25s ease;
}
.title-input :deep(.el-input__wrapper.is-focus) {
  box-shadow: none !important;
  border-bottom-color: var(--cinnabar);
}
.title-input :deep(.el-input__inner) {
  font-size: 19px;
  font-weight: 900;
  letter-spacing: 0.04em;
  color: var(--ink);
}
.stream-tip {
  color: var(--ink-faint);
  font-size: 13px;
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  gap: 6px;
  letter-spacing: 0.05em;
}
.result-body {
  border: 1px solid var(--hairline);
  border-radius: 2px;
  padding: 12px;
  max-height: 520px;
  overflow-y: auto;
  font-size: 14px;
  background: var(--paper-deep);
}
.preview-body {
  min-height: 260px;
}
.result-actions {
  display: flex;
  gap: 6px;
}
.save-actions {
  margin-top: 12px;
}

/* ---------- 初稿编辑 / 划词建议 ---------- */
.draft-editor :deep(.el-textarea__inner) {
  font-family: var(--font-serif);
  font-size: 14px;
  line-height: 1.9;
  background: var(--paper-deep);
  color: var(--ink);
  border-radius: 2px;
}
.selection-bar {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}
.selection-bar-label {
  margin-right: auto;
  color: var(--ink-faint);
  font-size: 12.5px;
  letter-spacing: 0.04em;
}
.pane-caption {
  display: flex;
  justify-content: space-between;
  margin-top: 8px;
  color: var(--ink-faint);
  font-size: 12px;
  letter-spacing: 0.06em;
}
.pane-mode {
  color: var(--cinnabar);
}
.pane-quote {
  margin: 6px 0 0;
  padding: 8px 10px;
  border-left: 2px solid var(--cinnabar);
  background: var(--paper-deep);
  font-family: var(--font-serif);
  font-size: 13px;
  line-height: 1.7;
  color: var(--ink);
}
.review-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

@media (max-width: 900px) {
  .prose-page :deep(.el-col) {
    width: 100%;
    max-width: 100%;
    flex: 0 0 100%;
    margin-bottom: 16px;
  }
  .material-col {
    position: static;
  }
}
</style>
