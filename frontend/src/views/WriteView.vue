<template>
  <div class="write-page">
    <header class="page-head">
      <div class="page-title">写作</div>
      <div class="page-sub">WRITING DESK</div>
    </header>
    <el-row :gutter="16">
      <!-- 左侧：文章编辑 -->
      <el-col :span="16">
        <el-card shadow="never" class="panel-card">
          <el-input v-model="form.title" placeholder="请输入文章标题" class="title-input"
                    maxlength="200" />
          <div class="meta-row">
            <el-select v-model="form.categoryId" placeholder="选择分类" clearable style="width: 180px">
              <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
            <el-input v-model="form.summary" placeholder="摘要（可选）" maxlength="500"
                      style="flex: 1" />
          </div>
          <Toolbar class="editor-toolbar" :editor="editorRef" :defaultConfig="toolbarConfig"
                   mode="default" />
          <Editor class="editor-content" v-model="form.contentHtml" :defaultConfig="editorConfig"
                  mode="default" @onCreated="handleCreated" />
          <div class="save-row">
            <el-button :loading="saving" @click="save('draft')">存为草稿</el-button>
            <el-button type="primary" :loading="saving" @click="save('published')">
              {{ articleId ? '保存并发布' : '直接发布' }}
            </el-button>
          </div>
        </el-card>
      </el-col>

      <!-- 右侧：AI 辅助写作 -->
      <el-col :span="8">
        <el-card shadow="never" class="ai-card panel-card">
          <template #header>
            <div class="ai-header">
              <span>AI 辅助写作</span>
              <el-tag size="small" type="info">SSE 流式</el-tag>
            </div>
          </template>

          <el-form label-position="top" class="ai-form" label-width="auto">
            <el-form-item label="任务">
              <el-select v-model="ai.task" @change="onTaskChange">
                <el-option v-for="t in taskOptions" :key="t.value" :label="t.label" :value="t.value" />
              </el-select>
            </el-form-item>

            <el-form-item v-if="needsTopic" label="主题（必填）">
              <el-input v-model="ai.topic" maxlength="200" placeholder="如：Spring Boot 入门" />
            </el-form-item>
            <el-form-item v-if="ai.task !== 'condense' && ai.task !== 'polish'" label="文章类型">
              <el-input v-model="ai.articleType" maxlength="100" placeholder="如：技术教程" />
            </el-form-item>
            <el-form-item v-if="['outline', 'draft', 'expand', 'rewrite'].includes(ai.task)"
                          label="写作风格">
              <el-input v-model="ai.style" maxlength="200" placeholder="如：口语化、面向初学者" />
            </el-form-item>
            <el-form-item v-if="['outline', 'draft', 'rewrite'].includes(ai.task)"
                          label="目标读者">
              <el-input v-model="ai.targetReader" maxlength="200" placeholder="如：有一点 Java 基础的学生" />
            </el-form-item>

            <el-form-item v-if="ai.task === 'draft'" label="大纲（必填）">
              <el-input v-model="ai.outline" type="textarea" :rows="6"
                        placeholder="生成正文前需提供已确认的大纲，可由「生成大纲」结果填入" />
            </el-form-item>
            <el-form-item v-if="needsOriginalText" label="原文（必填）">
              <el-input v-model="ai.originalText" type="textarea" :rows="6"
                        placeholder="续写/扩写/缩写/改写/润色的源文本" />
            </el-form-item>
            <el-form-item v-if="ai.task === 'continue' || ai.task === 'expand' || ai.task === 'rewrite'"
                          label="附加指令">
              <el-input v-model="ai.instruction" type="textarea" :rows="2" maxlength="2000"
                        placeholder="对本次生成的自由要求（可选）" />
            </el-form-item>

            <div class="ai-actions">
              <el-button type="primary" :loading="generating" :disabled="generating"
                         @click="startGenerate">开始生成</el-button>
              <el-button v-if="generating" type="danger" @click="stopGenerate">停止生成</el-button>
              <el-button v-if="ai.task === 'draft' && lastResult"
                         @click="ai.outline = lastResult">将上次结果用作大纲</el-button>
              <el-button v-if="needsOriginalText && lastResult"
                         @click="ai.originalText = lastResult">将上次结果用作原文</el-button>
            </div>
          </el-form>

          <el-divider v-if="outputMd || generating" content-position="left">生成结果</el-divider>
          <div v-if="generating" class="stream-tip">
            <el-icon class="is-loading"><Loading /></el-icon>
            正在生成，已接收 {{ outputMd.length }} 字…
          </div>
          <!-- 流式 Markdown 渲染（打字机效果来自逐帧 delta） -->
          <div v-if="outputMd" class="markdown-body result-body" v-html="renderedOutput"></div>

          <div v-if="!generating && outputMd" class="result-actions">
            <el-button size="small" @click="appendResultToEditor">插入编辑器末尾</el-button>
            <el-button size="small" type="primary" @click="replaceEditor">替换编辑器内容</el-button>
            <el-button size="small" @click="copyResult">复制</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, shallowRef } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
import '@wangeditor/editor/dist/css/style.css'
import DOMPurify from 'dompurify'
import { marked } from 'marked'
import { articleApi, categoryApi } from '../api'
import request from '../api/request'
import { aiGenerate } from '../api/ai'
import { emitCare } from '../care/careBus'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const articleId = ref(null)
const categories = ref([])
const saving = ref(false)
const form = reactive({ title: '', categoryId: null, summary: '', contentHtml: '' })

// ---------- 富文本编辑器 ----------
const editorRef = shallowRef(null)
const toolbarConfig = { excludeKeys: ['group-video'] }
const editorConfig = {
  placeholder: '开始写作…（可先用右侧 AI 生成初稿）',
  MENU_CONF: {
    uploadImage: {
      async customUpload(file, insertFn) {
        const fd = new FormData()
        fd.append('file', file)
        try {
          const data = await request.post('/files/upload', fd, {
            headers: { 'Content-Type': 'multipart/form-data' }
          })
          insertFn(data.url, file.name || '图片', '')
        } catch {
          /* 拦截器已提示 */
        }
      }
    }
  }
}

function handleCreated(editor) {
  editorRef.value = editor
}

onBeforeUnmount(() => {
  editorRef.value?.destroy()
  if (abortController) abortController.abort()
})

// ---------- AI 辅助写作 ----------
const taskOptions = [
  { value: 'outline', label: '生成标题与大纲' },
  { value: 'draft', label: '依据大纲生成正文' },
  { value: 'continue', label: '续写' },
  { value: 'expand', label: '扩写' },
  { value: 'condense', label: '缩写' },
  { value: 'rewrite', label: '改写' },
  { value: 'polish', label: '润色' }
]

const ai = reactive({
  task: 'outline',
  topic: '',
  articleType: '',
  style: '',
  targetReader: '',
  outline: '',
  originalText: '',
  instruction: ''
})

const generating = ref(false)
const outputMd = ref('')
const lastResult = ref('')
let abortController = null

const needsTopic = computed(() => ai.task === 'outline')
const needsOriginalText = computed(() =>
  ['continue', 'expand', 'condense', 'rewrite', 'polish'].includes(ai.task))

const renderedOutput = computed(() => {
  try {
    return DOMPurify.sanitize(marked.parse(outputMd.value || '', { breaks: true }))
  } catch {
    return ''
  }
})

function onTaskChange() {
  /* 字段按任务类型动态显隐，无需额外处理 */
}

function buildPayload() {
  // 前端 → Java 用 camelCase（后端 DTO 字段名），Java → Python 才转成 snake_case
  return {
    task: ai.task,
    params: {
      topic: ai.topic || undefined,
      articleType: ai.articleType || undefined,
      style: ai.style || undefined,
      targetReader: ai.targetReader || undefined
    },
    context: {
      outline: ai.outline || '',
      originalText: ai.originalText || '',
      instruction: ai.instruction || ''
    }
  }
}

async function startGenerate() {
  if (ai.task === 'outline' && !ai.topic.trim()) {
    ElMessage.warning('请先填写主题')
    return
  }
  if (ai.task === 'draft' && !ai.outline.trim()) {
    ElMessage.warning('生成正文前请提供大纲（可先用「生成大纲」）')
    return
  }
  if (needsOriginalText.value && !ai.originalText.trim()) {
    ElMessage.warning('该任务需要提供原文')
    return
  }

  generating.value = true
  outputMd.value = ''
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
        lastResult.value = content
        // 终态即解锁：不依赖底层连接是否正常收尾
        generating.value = false
        abortController?.abort()
      },
      onError: (message, code) => {
        generating.value = false
        if (code === 'LLM_TIMEOUT') {
          ElMessage.error('生成超时，请重试')
        } else if (code === 'EMPTY_OUTPUT') {
          ElMessage.warning('生成结果为空，请重试')
        } else {
          ElMessage.error(message)
        }
        if (!outputMd.value) lastResult.value = ''
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

function mdToHtml(md) {
  return DOMPurify.sanitize(marked.parse(md || '', { breaks: true }))
}

function appendResultToEditor() {
  const editor = editorRef.value
  if (!editor) return
  editor.dangerouslyInsertHtml('<p><br/></p>' + mdToHtml(lastResult.value || outputMd.value))
  ElMessage.success('已插入编辑器末尾')
}

function replaceEditor() {
  const editor = editorRef.value
  if (!editor) return
  editor.setHtml(mdToHtml(lastResult.value || outputMd.value))
  ElMessage.success('已替换编辑器内容')
}

async function copyResult() {
  try {
    await navigator.clipboard.writeText(lastResult.value || outputMd.value)
    ElMessage.success('已复制')
  } catch {
    ElMessage.warning('复制失败，请手动选择复制')
  }
}

// ---------- 保存 ----------
async function save(status) {
  if (!form.title.trim()) {
    ElMessage.warning('请输入文章标题')
    return
  }
  saving.value = true
  try {
    const payload = {
      title: form.title,
      categoryId: form.categoryId || undefined,
      contentHtml: form.contentHtml,
      summary: form.summary || undefined,
      status
    }
    let data
    if (articleId.value) {
      data = await articleApi.update(articleId.value, payload)
    } else {
      data = await articleApi.create(payload)
    }
    afterPublish(status, data)
  } finally {
    saving.value = false
  }
}

/** 发布响应关怀分支（契约 5.8）：high → 危机页（文章已私密发布）；low/mid → 信笺/短笺 */
function afterPublish(status, data) {
  if (status === 'published' && data?.care?.level === 'high') {
    router.push(`/care/crisis?article=${data.id}`)
    return
  }
  if (status === 'published' && (data?.care?.level === 'low' || data?.care?.level === 'mid')) {
    emitCare({ level: data.care.level, summary: data.care.summary })
  }
  ElMessage.success(status === 'published' ? '发布成功' : '草稿已保存')
  router.push('/my')
}

onMounted(async () => {
  try {
    categories.value = await categoryApi.list()
  } catch {
    categories.value = []
  }
  if (route.params.id) {
    articleId.value = route.params.id
    const detail = await articleApi.detail(articleId.value)
    if (auth.user?.id !== detail.authorId && !auth.isAdmin) {
      ElMessage.error('仅作者或管理员可编辑')
      router.push(`/article/${articleId.value}`)
      return
    }
    form.title = detail.title
    form.categoryId = detail.categoryId
    form.summary = detail.summary || ''
    form.contentHtml = detail.contentHtml || ''
  }
})
</script>

<style scoped>
.write-page {
  max-width: 1400px;
  margin: 0 auto;
  padding: 28px 20px 64px;
  animation: page-in 0.45s cubic-bezier(0.22, 0.8, 0.32, 1) both;
}
.write-page .page-head {
  margin-bottom: 20px;
}

/* 面板：纸面 + 顶部粗墨线 */
.panel-card {
  border-top: 3px solid var(--ink);
}

/* 标题输入：稿纸式，无底盒、底部细线 */
.title-input {
  margin-bottom: 14px;
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
  font-size: 24px;
  font-weight: 900;
  letter-spacing: 0.04em;
  color: var(--ink);
}
.title-input :deep(.el-input__inner::placeholder) {
  color: var(--ink-faint);
  font-weight: 500;
}

.meta-row {
  display: flex;
  gap: 12px;
  margin-bottom: 14px;
}

/* ---------- 富文本编辑器 ---------- */
.editor-toolbar {
  border: 1px solid var(--hairline-dark);
  border-bottom: none;
}
.editor-toolbar :deep(.w-e-toolbar) {
  background: var(--paper-deep);
  font-family: var(--font-serif);
}
.editor-toolbar :deep(.w-e-bar-item button:hover) {
  background: var(--hairline);
}
.editor-content {
  border: 1px solid var(--hairline-dark);
  height: 480px;
  overflow-y: hidden;
}
.editor-content :deep(.w-e-text-container) {
  background: var(--paper-card);
  font-family: var(--font-serif);
}
.editor-content :deep([data-slate-editor]) {
  font-family: var(--font-serif);
  font-size: 15.5px;
  line-height: 1.9;
  color: var(--ink);
  letter-spacing: 0.02em;
}
.editor-content :deep(.w-e-text-placeholder) {
  font-family: var(--font-serif);
  color: var(--ink-faint);
  font-style: normal;
}

.save-row {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

/* ---------- AI 辅助栏 ---------- */
.ai-card {
  max-height: calc(100vh - 140px);
  overflow-y: auto;
}
.ai-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  letter-spacing: 0.08em;
}
.ai-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
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
  max-height: 420px;
  overflow-y: auto;
  font-size: 14px;
  background: var(--paper-deep);
}
.result-actions {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

@media (max-width: 900px) {
  .write-page :deep(.el-col) {
    width: 100%;
    max-width: 100%;
    flex: 0 0 100%;
    margin-bottom: 16px;
  }
}
</style>
