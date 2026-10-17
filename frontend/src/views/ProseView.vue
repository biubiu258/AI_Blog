<template>
  <div class="prose-page">
    <header class="page-head">
      <div class="page-title">散文</div>
      <div class="page-sub">PROSE · 以影寄情</div>
    </header>
    <el-row :gutter="16">
      <!-- 左侧：素材（图片 + 情绪 / 近况） -->
      <el-col :span="10">
        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="card-header">
              <span>素材</span>
              <el-tag size="small" type="info">图片 1 - 5 张</el-tag>
            </div>
          </template>

          <el-upload :show-file-list="false" :limit="5" :http-request="uploadImage"
                     accept="image/jpeg,image/png,image/gif,image/webp"
                     :on-exceed="onExceed">
            <el-button :disabled="images.length >= 5" :loading="uploading">
              <el-icon><Plus /></el-icon>
              添加图片（{{ images.length }}/5）
            </el-button>
          </el-upload>
          <div class="hint">可以是人物照、风景照，或任何触动你的画面</div>

          <div v-if="images.length" class="thumbs">
            <div v-for="img in images" :key="img.url" class="thumb-item">
              <img :src="img.url" :alt="img.name" />
              <el-icon class="thumb-remove" @click="removeImage(img.url)"><Close /></el-icon>
            </div>
          </div>

          <el-form label-position="top" class="material-form">
            <el-form-item label="此刻的情绪">
              <el-input v-model="emotion" maxlength="200" show-word-limit
                        placeholder="如：平静、疲惫、怅然若失…" />
            </el-form-item>
            <el-form-item label="近况 / 想说的">
              <el-input v-model="recentState" type="textarea" :rows="5" maxlength="1000"
                        show-word-limit placeholder="最近发生了什么？想借着这张照片写点什么？" />
            </el-form-item>
          </el-form>

          <div class="actions">
            <el-button type="primary" :loading="generating" :disabled="generating || !canGenerate"
                       @click="startGenerate">生成散文初稿</el-button>
            <el-button v-if="generating" type="danger" @click="stopGenerate">停止生成</el-button>
          </div>
          <div v-if="!canGenerate && !generating" class="hint">
            请至少上传 1 张图片，并填写情绪或近况其一
          </div>
        </el-card>
      </el-col>

      <!-- 右侧：初稿结果 -->
      <el-col :span="14">
        <el-card shadow="never" class="result-card panel-card">
          <template #header>
            <div class="card-header">
              <span>初稿</span>
              <el-tag size="small" :type="generating ? 'warning' : 'info'">
                {{ generating ? '流式生成中' : 'SSE 流式' }}
              </el-tag>
            </div>
          </template>

          <div v-if="generating" class="stream-tip">
            <el-icon class="is-loading"><Loading /></el-icon>
            正在生成，已接收 {{ outputMd.length }} 字…
          </div>
          <div v-if="outputMd" class="markdown-body result-body" v-html="renderedOutput"></div>
          <el-empty v-else description="左侧上传图片、写下情绪，点击生成" />

          <template v-if="outputMd && !generating">
            <el-form-item label="标题" class="title-item">
              <el-input v-model="title" maxlength="200" placeholder="保存前可修改标题" />
            </el-form-item>
            <div class="result-actions">
              <el-button size="small" type="primary" :loading="saving" @click="saveDraft">
                存为草稿并继续编辑
              </el-button>
              <el-button size="small" @click="copyResult">复制</el-button>
            </div>
          </template>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import DOMPurify from 'dompurify'
import { marked } from 'marked'
import { articleApi, fileApi } from '../api'
import { aiGenerate } from '../api/ai'

const router = useRouter()

const images = ref([])
const uploading = ref(false)
const emotion = ref('')
const recentState = ref('')

const generating = ref(false)
const outputMd = ref('')
const title = ref('')
const saving = ref(false)
let abortController = null

const canGenerate = computed(() =>
  images.value.length > 0 && (!!emotion.value.trim() || !!recentState.value.trim()))

const renderedOutput = computed(() => {
  try {
    return DOMPurify.sanitize(marked.parse(outputMd.value || '', { breaks: true }))
  } catch {
    return ''
  }
})

onBeforeUnmount(() => {
  if (abortController) abortController.abort()
})

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
      emotion: emotion.value.trim() || undefined,
      recentState: recentState.value.trim() || undefined
    },
    context: {
      imageUrls: images.value.map((i) => i.url)
    }
  }
}

async function startGenerate() {
  generating.value = true
  outputMd.value = ''
  title.value = ''
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
      },
      onError: (message, code) => {
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
  const el = document.querySelector('.result-body')
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
  letter-spacing: 0.08em;
}
.hint {
  margin-top: 8px;
  color: var(--ink-faint);
  font-size: 13px;
  line-height: 1.6;
  letter-spacing: 0.04em;
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
.material-form {
  margin-top: 16px;
}
.actions {
  display: flex;
  gap: 8px;
}
.result-card {
  min-height: 420px;
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
.title-item {
  margin-top: 12px;
}
.result-actions {
  display: flex;
  gap: 6px;
}

@media (max-width: 900px) {
  .prose-page :deep(.el-col) {
    width: 100%;
    max-width: 100%;
    flex: 0 0 100%;
    margin-bottom: 16px;
  }
}
</style>
