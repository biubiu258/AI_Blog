<template>
  <div class="write-page">
    <!-- 吸顶操作栏：状态与保存动作常驻，不再滚动到文尾找按钮 -->
    <div class="write-topbar">
      <div class="topbar-left">
        <span class="topbar-title">写作</span>
        <span class="doc-badge">{{ articleId ? '编辑中' : '新草稿' }}</span>
        <span v-if="autosaveText" class="autosave-hint">{{ autosaveText }}</span>
      </div>
      <div class="topbar-right">
        <span class="word-count">{{ wordCount }} 字</span>
        <el-button v-if="articleId" link size="small" @click="openVersions">版本历史</el-button>
        <el-button size="small" :loading="saving" title="Ctrl / ⌘ + S"
                   @click="save('draft')">存草稿</el-button>
        <el-popover v-model="scheduleOpen" placement="bottom-end" :width="260" trigger="click">
          <template #reference>
            <el-button size="small" :loading="saving">定时</el-button>
          </template>
          <div class="schedule-pop">
            <div class="schedule-title">定时发布</div>
            <el-date-picker v-model="form.scheduledAt" type="datetime" placeholder="选择发布时间"
                            value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" />
            <el-button type="primary" size="small" class="schedule-confirm"
                       :disabled="!form.scheduledAt" :loading="saving"
                       @click="confirmSchedule">确认定时发布</el-button>
          </div>
        </el-popover>
        <el-button size="small" type="primary" :loading="saving" @click="save('published')">
          {{ articleId ? '保存并发布' : '发布' }}
        </el-button>
      </div>
    </div>

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
            <el-select v-model="form.tags" multiple filterable allow-create default-first-option
                       :multiple-limit="8" placeholder="标签（最多 8 个，可新建）" style="flex: 1">
              <el-option v-for="t in tagOptions" :key="t.id" :label="t.name" :value="t.name" />
            </el-select>
          </div>
          <div class="meta-row">
            <el-input v-model="form.summary" placeholder="摘要（可选，不填则发布时由正文提取）"
                      maxlength="500" />
          </div>
          <div v-if="seriesOptions.length" class="meta-row series-row">
            <span class="disclosure-label">加入连载</span>
            <el-select v-model="selectedSeriesId" clearable placeholder="不加入连载"
                       style="width: 260px">
              <el-option v-for="s in seriesOptions" :key="s.id"
                         :label="s.title" :value="s.id" />
            </el-select>
            <span class="disclosure-hint">保存成功后自动加入所选连载</span>
          </div>
          <div class="meta-row disclosure-row">
            <span class="disclosure-label">AI 参与声明</span>
            <el-radio-group v-model="form.aiUsageLevel" size="small">
              <el-radio-button value="none">未使用 AI</el-radio-button>
              <el-radio-button value="assisted">AI 辅助</el-radio-button>
              <el-radio-button value="generated">主要生成</el-radio-button>
            </el-radio-group>
            <el-button v-if="articleId" link size="small" :loading="suggestLoading"
                       @click="loadSuggestion">按 AI 记录推荐</el-button>
            <span class="disclosure-hint">发布前必填 · 将按《标识办法》对外展示标识</span>
          </div>
          <Toolbar class="editor-toolbar" :editor="editorRef" :defaultConfig="toolbarConfig"
                   mode="default" />
          <Editor class="editor-content" v-model="form.contentHtml" :defaultConfig="editorConfig"
                  mode="default" @onCreated="handleCreated" />
          <div class="editor-foot">
            <span class="hint">支持 Markdown 粘贴；Ctrl / ⌘ + S 快速存草稿</span>
          </div>
        </el-card>
      </el-col>

      <!-- 右侧：AI 辅助写作（sticky 跟随滚动） -->
      <el-col :span="8" class="ai-col">
        <el-card shadow="never" class="ai-card panel-card">
          <template #header>
            <div class="ai-header">
              <span>AI 辅助写作</span>
              <el-tag size="small" type="info">SSE 流式</el-tag>
            </div>
          </template>

          <!-- 写作陪伴提示：安静展示，不打断写作 -->
          <div v-if="companion" class="companion-card">
            <span class="companion-text">{{ companion.message }}</span>
            <el-tag size="small" :type="companionToneType" effect="plain" class="companion-tone">
              {{ companionToneText }}
            </el-tag>
          </div>

          <el-tabs v-model="aiTab" class="ai-tabs">
            <el-tab-pane label="生成" name="generate">
              <el-form label-position="top" class="ai-form" label-width="auto">
                <el-form-item label="任务">
                  <el-select v-model="ai.task" @change="onTaskChange">
                    <el-option v-for="t in taskOptions" :key="t.value" :label="t.label" :value="t.value" />
                  </el-select>
                </el-form-item>

                <el-form-item label="文风库">
                  <div class="style-row">
                    <el-select v-model="selectedStyleProfileId" clearable placeholder="不使用文风库"
                               class="style-select">
                      <el-option v-for="p in readyStyleProfiles" :key="p.id"
                                 :label="p.isDefault ? `${p.name}（默认）` : p.name" :value="p.id" />
                    </el-select>
                    <el-button link type="primary" @click="openStyleDialog">管理文风库</el-button>
                  </div>
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
                  <el-input v-model="ai.targetReader" maxlength="200"
                            placeholder="如：有一点 Java 基础的学生" />
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
              <div v-if="outputMd" ref="resultBodyRef" class="markdown-body result-body"
                   v-html="renderedOutput"></div>

              <div v-if="!generating && outputMd" class="result-actions">
                <el-button size="small" @click="appendResultToEditor">插入编辑器末尾</el-button>
                <el-button size="small" type="primary" @click="replaceEditor">替换编辑器内容</el-button>
                <el-button size="small" @click="copyResult">复制</el-button>
              </div>
            </el-tab-pane>

            <el-tab-pane name="selection">
              <template #label>
                <span>片段建议</span>
                <span v-if="selectionLoading || selectionResult" class="tab-dot" />
              </template>
              <div class="selection-pane">
                <el-empty v-if="!selectionSnapshot" :image-size="56"
                          description="在正文中选中一段文字，再点浮层里的「给建议 / 润色 / 改写」" />
                <template v-else>
                  <div class="pane-caption">
                    <span class="pane-mode">{{ selectionModeLabel }}</span>
                    <span>{{ selectionSnapshot.selectedText.length }} 字</span>
                  </div>
                  <blockquote class="pane-quote">{{ selectionSnapshot.selectedText }}</blockquote>
                  <div v-if="selectionLoading" class="stream-tip">
                    <el-icon class="is-loading"><Loading /></el-icon>
                    正在生成建议，已接收 {{ selectionResult.length }} 字…
                  </div>
                  <div v-if="selectionResult" class="markdown-body result-body"
                       v-html="renderMarkdown(selectionResult)"></div>
                  <div v-if="!selectionLoading && selectionResult" class="result-actions">
                    <el-button v-if="selectionMode !== 'advice'" size="small" type="primary"
                               @click="applySelectionSuggestion">替换选中</el-button>
                    <el-button size="small" @click="copySelectionResult">复制</el-button>
                    <el-button size="small" @click="clearSelectionResult">清除</el-button>
                  </div>
                  <div v-if="selectionMode === 'advice' && !selectionLoading && selectionResult"
                       class="hint">
                    建议仅供参照，不会改动正文
                  </div>
                </template>
              </div>
            </el-tab-pane>

            <el-tab-pane name="review">
              <template #label>
                <span>全文建议</span>
                <span v-if="reviewSuggestions.length" class="tab-dot" />
              </template>
              <div class="review-pane">
                <div class="review-actions">
                  <el-button size="small" type="primary" :loading="reviewLoading"
                             :disabled="reviewLoading" @click="reviewFullText(false)">审阅全文</el-button>
                  <el-switch v-model="autoReview" size="small" inline-prompt
                             active-text="自动" inactive-text="手动" />
                </div>
                <div class="hint">
                  停笔 8 秒且内容有变化时自动审阅；建议只做定位，点「替换」才会写入正文
                </div>
                <AiSuggestionPanel :suggestions="reviewSuggestions" :loading="reviewLoading"
                                   empty-text="暂无建议，点击「审阅全文」开始"
                                   @locate="locateSuggestion" @apply="applySuggestion"
                                   @ignore="ignoreSuggestion" />
              </div>
            </el-tab-pane>

            <el-tab-pane name="wording">
              <template #label>
                <span>术语与措辞</span>
                <span v-if="wordingIssues.length" class="tab-dot" />
              </template>
              <div class="review-pane">
                <TechWordingPanel :issues="wordingIssues" :loading="wordingLoading"
                                   :degraded="wordingDegraded" :message="wordingMessage"
                                   :has-selection="!!wordingTarget" :selected-text="wordingTarget"
                                   :checked="wordingChecked"
                                   @check="checkWording" @locate="locateWordingIssue"
                                   @apply="applyWordingIssue" @ignore="ignoreWordingIssue" />
              </div>
            </el-tab-pane>
          </el-tabs>
        </el-card>
      </el-col>
    </el-row>

    <el-drawer v-model="versionDrawer" title="版本历史" size="420px">
      <div v-loading="versionLoading" class="version-list">
        <el-empty v-if="!versionLoading && !versions.length" :image-size="56"
                  description="暂无历史版本，每次保存前会自动留档" />
        <div v-for="v in versions" :key="v.id" class="version-item">
          <div class="version-main">
            <div class="version-title">{{ v.title || '无标题' }}</div>
            <div class="version-time">{{ formatDate(v.savedAt) }}</div>
          </div>
          <el-button link type="primary" @click="restoreVersion(v)">载入</el-button>
        </div>
      </div>
    </el-drawer>

    <AiSelectionMenu :visible="selectionMenu.visible" :x="selectionMenu.x" :y="selectionMenu.y"
                     :loading="selectionLoading" @ask="askSelection" />

    <!-- 文风库管理 -->
    <el-dialog v-model="styleDialog.visible" title="管理文风库" width="560px">
      <div v-loading="styleDialog.loading" class="style-manage">
        <div v-if="!styleProfiles.length" class="hint">
          还没有文风档，从你的历史文章提炼一个吧（3–10 篇）
        </div>
        <div v-for="p in styleProfiles" :key="p.id" class="style-item">
          <div class="style-item-main">
            <div class="style-item-name">
              <span>{{ p.name }}</span>
              <el-tag v-if="p.isDefault" size="small" type="success" effect="plain">默认</el-tag>
              <el-tag v-if="p.status === 'failed'" size="small" type="danger" effect="plain">提炼失败</el-tag>
              <el-tag v-else-if="p.status === 'building'" size="small" type="warning" effect="plain">
                提炼中
              </el-tag>
            </div>
            <div class="style-item-sub">
              样本 {{ p.sampleCount }} 篇 · {{ formatDate(p.createdAt) }}
              <span v-if="p.status === 'failed'"> · 提炼未成功，可删除后重试</span>
            </div>
          </div>
          <div class="style-item-actions">
            <el-button v-if="!p.isDefault && p.status === 'ready'" link type="primary"
                       @click="setDefaultStyle(p)">设为默认</el-button>
            <el-button link type="danger" @click="removeStyle(p)">删除</el-button>
          </div>
        </div>

        <el-divider content-position="left">从我的文章提炼新文风</el-divider>
        <el-form label-position="top">
          <el-form-item label="文风名称">
            <el-input v-model="styleForm.name" maxlength="64" placeholder="如：我的散文体" />
          </el-form-item>
          <el-form-item label="选择文章（3–10 篇）">
            <el-select v-model="styleForm.articleIds" multiple filterable style="width: 100%"
                       placeholder="从自己的历史文章中选择">
              <el-option v-for="a in myArticles" :key="a.id" :label="a.title" :value="a.id" />
            </el-select>
          </el-form-item>
          <el-button type="primary" :loading="styleForm.saving" @click="createStyleProfile">
            开始提炼
          </el-button>
        </el-form>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
import '@wangeditor/editor/dist/css/style.css'
import DOMPurify from 'dompurify'
import { marked } from 'marked'
import { articleApi, categoryApi, companionApi, seriesApi, styleProfileApi, tagApi, techApi } from '../api'
import request from '../api/request'
import { aiAdopt, aiGenerate, aiReview } from '../api/ai'
import AiSelectionMenu from '../components/AiSelectionMenu.vue'
import AiSuggestionPanel from '../components/AiSuggestionPanel.vue'
import TechWordingPanel from '../components/TechWordingPanel.vue'
import {
  getPlainText,
  getSelectionInfo,
  locateQuote,
  rangeFromOffsets,
  replaceOffsets,
  highlightQuotes,
  clearQuoteHighlights
} from '../ai/editorAnchor'
import { emitCare } from '../care/careBus'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const articleId = ref(null)
const categories = ref([])
const tagOptions = ref([])
const saving = ref(false)
const suggestLoading = ref(false)
const scheduleOpen = ref(false)
const form = reactive({
  title: '',
  categoryId: null,
  summary: '',
  contentHtml: '',
  tags: [],
  scheduledAt: null,
  aiUsageLevel: null
})

// ---------- 加入连载 ----------
const seriesOptions = ref([])
const selectedSeriesId = ref(null)
let originalSeriesId = null

async function loadSeriesOptions() {
  if (!auth.isLoggedIn) return
  try {
    seriesOptions.value = (await seriesApi.mine()) || []
  } catch {
    seriesOptions.value = []
  }
}

// ---------- 版本历史 ----------
const versionDrawer = ref(false)
const versionLoading = ref(false)
const versions = ref([])

async function openVersions() {
  if (!articleId.value) return
  versionDrawer.value = true
  versionLoading.value = true
  try {
    versions.value = await articleApi.versions(articleId.value)
  } catch {
    versions.value = []
  } finally {
    versionLoading.value = false
  }
}

async function restoreVersion(version) {
  try {
    await ElMessageBox.confirm(
      `将用 ${formatDate(version.savedAt)} 的版本覆盖当前编辑内容，是否继续？`,
      '载入历史版本',
      { type: 'warning', confirmButtonText: '载入', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    const detail = await articleApi.version(articleId.value, version.id)
    form.title = detail.title || ''
    form.summary = detail.summary || ''
    form.contentHtml = detail.contentHtml || ''
    editorRef.value?.setHtml(detail.contentHtml || '')
    form.tags = form.tags || []
    versionDrawer.value = false
    ElMessage.success('已载入历史版本，保存后才会生效')
  } catch {
    /* 拦截器已提示 */
  }
}

function formatDate(value) {
  if (!value) return '-'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return String(value)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

// ---------- 富文本编辑器 ----------
const editorRef = shallowRef(null)
const wordCount = ref(0)
const toolbarConfig = { excludeKeys: ['group-video'] }
const editorConfig = {
  placeholder: '开始写作…（可先用右侧 AI 生成初稿）',
  onChange: () => onEditorContentChanged(),
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

function updateWordCount() {
  const editor = editorRef.value
  wordCount.value = editor ? getPlainText(editor).replace(/\s/g, '').length : 0
}

function handleCreated(editor) {
  editorRef.value = editor
  const container = editor.getEditableContainer?.()
  container?.addEventListener('mouseup', onEditorSelectionChange)
  container?.addEventListener('keyup', onEditorSelectionChange)
  document.addEventListener('mousedown', onDocumentMouseDown)
  nextTick(updateWordCount)
}

function onDocumentMouseDown(event) {
  const target = event.target
  if (target?.closest?.('.ai-selection-menu')) return
  const container = editorRef.value?.getEditableContainer?.()
  if (container && target instanceof Node && container.contains(target)) return
  selectionMenu.visible = false
}

// Ctrl / ⌘ + S 快速存草稿
function onKeydown(event) {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') {
    event.preventDefault()
    if (!saving.value) save('draft')
  }
}

onBeforeUnmount(() => {
  document.removeEventListener('mousedown', onDocumentMouseDown)
  window.removeEventListener('keydown', onKeydown)
  window.removeEventListener('beforeunload', onBeforeUnload)
  if (idleTimer) clearTimeout(idleTimer)
  if (autosaveTimer) clearTimeout(autosaveTimer)
  // 离开前落盘一次，避免最后 1.5 秒内的修改丢失
  if (dirty.value) persistSnapshot()
  if (abortController) abortController.abort()
  if (selectionAbort) selectionAbort.abort()
  clearQuoteHighlights()
  editorRef.value?.destroy()
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
const resultBodyRef = ref(null)
let abortController = null

// ---------- 写作陪伴提示（2.9）：失败静默 ----------
const companion = ref(null)
const COMPANION_TONE_TEXT = { gentle: '轻轻的', encourage: '加油', warm: '温暖', steady: '平稳' }
const COMPANION_TONE_TYPE = { gentle: 'info', encourage: 'warning', warm: 'success', steady: 'primary' }
const companionToneText = computed(() => COMPANION_TONE_TEXT[companion.value?.tone] || '陪伴')
const companionToneType = computed(() => COMPANION_TONE_TYPE[companion.value?.tone] || 'info')

async function loadCompanion() {
  try {
    companion.value = await companionApi.get()
  } catch {
    companion.value = null
  }
}

// ---------- 个人文风库 ----------
const styleProfiles = ref([])
const selectedStyleProfileId = ref(null)
const readyStyleProfiles = computed(() => styleProfiles.value.filter((p) => p.status === 'ready'))
const styleDialog = reactive({ visible: false, loading: false })
const styleForm = reactive({ name: '', articleIds: [], saving: false })
const myArticles = ref([])

async function loadStyleProfiles() {
  try {
    styleProfiles.value = (await styleProfileApi.list()) || []
  } catch {
    styleProfiles.value = []
  }
}

async function openStyleDialog() {
  styleDialog.visible = true
  styleDialog.loading = true
  try {
    if (!myArticles.value.length) {
      const data = await articleApi.mine({ page: 1, size: 50 })
      myArticles.value = data.items || []
    }
  } catch {
    myArticles.value = []
  } finally {
    styleDialog.loading = false
  }
  loadStyleProfiles()
}

async function createStyleProfile() {
  if (!styleForm.name.trim()) {
    ElMessage.warning('请填写文风名称')
    return
  }
  if (styleForm.articleIds.length < 3 || styleForm.articleIds.length > 10) {
    ElMessage.warning('请选择 3–10 篇自己的文章')
    return
  }
  styleForm.saving = true
  try {
    const created = await styleProfileApi.create({
      name: styleForm.name.trim(),
      articleIds: styleForm.articleIds
    })
    styleForm.name = ''
    styleForm.articleIds = []
    await loadStyleProfiles()
    if (created?.status === 'failed') ElMessage.warning('文风提炼失败，可删除后重试')
    else ElMessage.success('文风档已创建，提炼完成后即可选用')
  } finally {
    styleForm.saving = false
  }
}

async function setDefaultStyle(profile) {
  await styleProfileApi.setDefault(profile.id)
  ElMessage.success(`已将「${profile.name}」设为默认文风`)
  loadStyleProfiles()
}

async function removeStyle(profile) {
  await ElMessageBox.confirm(`确定删除文风档「${profile.name}」吗？`, '提示', { type: 'warning' })
  await styleProfileApi.remove(profile.id)
  if (selectedStyleProfileId.value === profile.id) selectedStyleProfileId.value = null
  ElMessage.success('已删除')
  loadStyleProfiles()
}

// ---------- 划词问 AI ----------
const aiTab = ref('generate')
const selectionMenu = reactive({ visible: false, x: 0, y: 0 })
const selectionSnapshot = ref(null)
const selectionMode = ref('advice')
const selectionLoading = ref(false)
const selectionResult = ref('')
let selectionAbort = null
let suppressSelectionMenu = false

const SELECTION_MODE_LABELS = {
  advice: 'AI 建议',
  polish: '润色建议',
  rewrite: '改写建议',
  expand: '扩写建议',
  condense: '缩写建议'
}
const selectionModeLabel = computed(() =>
  SELECTION_MODE_LABELS[selectionMode.value] || 'AI 建议')

function renderMarkdown(md) {
  try {
    return DOMPurify.sanitize(marked.parse(md || '', { breaks: true }))
  } catch {
    return ''
  }
}

function onEditorSelectionChange() {
  window.setTimeout(() => updateSelectionMenu(), 0)
}

function currentSelectionRect() {
  const sel = window.getSelection()
  if (!sel || !sel.rangeCount) return null
  const rect = sel.getRangeAt(0).getBoundingClientRect()
  if (!rect || (!rect.width && !rect.height)) return null
  return rect
}

function updateSelectionMenu() {
  const editor = editorRef.value
  if (!editor) return
  if (suppressSelectionMenu) {
    selectionMenu.visible = false
    return
  }
  const info = getSelectionInfo(editor)
  if (!info || !info.selectedText.trim() || info.selectedText.length > 2000) {
    selectionMenu.visible = false
    return
  }
  // 记录最近一次有效选区，供「术语与措辞」面板检查使用
  if (info.selectedText.length <= 1500) {
    wordingTarget.value = info.selectedText.trim()
  }
  const rect = currentSelectionRect()
  if (!rect) {
    selectionMenu.visible = false
    return
  }
  selectionMenu.x = Math.max(12, Math.min(window.innerWidth - 380, rect.left))
  selectionMenu.y = rect.top > 64 ? rect.top - 46 : rect.bottom + 10
  selectionMenu.visible = true
}

async function askSelection(mode) {
  const editor = editorRef.value
  const info = getSelectionInfo(editor)
  if (!info || !info.selectedText.trim()) {
    ElMessage.warning('请先在正文中选中一段文字')
    return
  }
  if (info.selectedText.length > 2000) {
    ElMessage.warning('选中片段请控制在 2000 字以内')
    return
  }

  selectionSnapshot.value = info
  selectionMode.value = mode
  selectionResult.value = ''
  selectionMenu.visible = false
  aiTab.value = 'selection'
  selectionLoading.value = true
  selectionAbort?.abort()
  selectionAbort = new AbortController()
  try {
    await aiGenerate({
      task: 'suggest',
      articleId: articleId.value || undefined,
      params: {
        suggestionMode: mode,
        topic: form.title || undefined,
        styleProfileId: selectedStyleProfileId.value || undefined
      },
      context: {
        selectedText: info.selectedText,
        textBefore: info.plainText.slice(Math.max(0, info.start - 300), info.start),
        textAfter: info.plainText.slice(info.end, info.end + 300),
        instruction: ''
      }
    }, {
      signal: selectionAbort.signal,
      onDelta: (content) => {
        selectionResult.value += content
      },
      onDone: (content) => {
        selectionResult.value = content
        selectionLoading.value = false
        selectionAbort?.abort()
      },
      onError: (message, code) => {
        selectionLoading.value = false
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
    selectionLoading.value = false
  }
}

function applySelectionSuggestion() {
  const editor = editorRef.value
  const snapshot = selectionSnapshot.value
  const replacement = selectionResult.value.trim()
  if (!editor || !snapshot || !replacement) return

  // 用原文在最新正文中重新定位，避免编辑后偏移漂移
  const located = locateQuote(getPlainText(editor), snapshot.selectedText)
  if (!located) {
    ElMessage.warning('原文已修改，请重新选中后再试')
    return
  }
  if (!replaceOffsets(editor, located.start, located.end, replacement)) {
    ElMessage.error('替换失败，请重试')
    return
  }
  ElMessage.success('已替换选中内容，可用 Ctrl+Z 撤销')
  aiAdopt('generate')
  selectionSnapshot.value = null
  selectionResult.value = ''
}

async function copySelectionResult() {
  try {
    await navigator.clipboard.writeText(selectionResult.value)
    ElMessage.success('已复制')
  } catch {
    ElMessage.warning('复制失败，请手动选择复制')
  }
}

function clearSelectionResult() {
  selectionSnapshot.value = null
  selectionResult.value = ''
}

// ---------- 全文主动建议 ----------
const reviewLoading = ref(false)
const reviewSuggestions = ref([])
const autoReview = ref(true)
let idleTimer = null
let lastReviewedText = ''

function onEditorContentChanged() {
  selectionMenu.visible = false
  updateWordCount()
  if (!autoReview.value) return
  if (idleTimer) clearTimeout(idleTimer)
  idleTimer = window.setTimeout(() => {
    const text = getPlainText(editorRef.value)
    if (!text.trim() || text === lastReviewedText) return
    reviewFullText(true)
  }, 8000)
}

async function reviewFullText(auto = false) {
  const editor = editorRef.value
  if (!editor || reviewLoading.value) return

  const content = getPlainText(editor)
  if (!content.trim()) {
    if (!auto) ElMessage.warning('正文为空，无需审阅')
    return
  }
  if (content.length > 20000) {
    if (!auto) ElMessage.warning('正文超过 20000 字，请分段审阅')
    return
  }

  reviewLoading.value = true
  aiTab.value = 'review'
  const snapshot = content
  try {
    const data = await aiReview({
      title: form.title || undefined,
      content: snapshot
    })
    const items = (data?.suggestions || []).map((item) => ({
      ...item,
      found: !!locateQuote(snapshot, item.quote)
    }))
    reviewSuggestions.value = items
    lastReviewedText = snapshot
    refreshReviewHighlights()
    if (auto) {
      if (items.length) ElMessage.success(`全文审阅完成：${items.length} 条建议`)
    } else if (!items.length) {
      ElMessage.info('全文审阅完成：暂未发现需要调整的地方')
    }
  } catch {
    /* request 拦截器已统一提示错误 */
  } finally {
    reviewLoading.value = false
  }
}

function refreshReviewHighlights() {
  const editor = editorRef.value
  if (!editor) return
  const text = getPlainText(editor)
  const ranges = reviewSuggestions.value
    .map((item) => locateQuote(text, item.quote))
    .filter(Boolean)
  if (!ranges.length) clearQuoteHighlights()
  else highlightQuotes(editor, ranges)
}

function locateSuggestion(index) {
  const editor = editorRef.value
  const item = reviewSuggestions.value[index]
  if (!editor || !item) return
  const located = locateQuote(getPlainText(editor), item.quote)
  if (!located) {
    item.found = false
    ElMessage.warning('原文已修改，本条无法定位')
    return
  }
  const range = rangeFromOffsets(editor, located.start, located.end)
  if (!range) return

  suppressSelectionMenu = true
  editor.select(range)
  editor.restoreSelection?.()
  editor.getEditableContainer?.()?.scrollIntoView({ block: 'center', behavior: 'smooth' })
  window.setTimeout(() => {
    suppressSelectionMenu = false
  }, 400)
}

function applySuggestion(index) {
  const editor = editorRef.value
  const item = reviewSuggestions.value[index]
  if (!editor || !item?.replacement) return
  const located = locateQuote(getPlainText(editor), item.quote)
  if (!located) {
    item.found = false
    ElMessage.warning('原文已修改，请重新审阅')
    return
  }
  if (!replaceOffsets(editor, located.start, located.end, item.replacement)) {
    ElMessage.error('替换失败，请重试')
    return
  }
  ElMessage.success('已替换，可用 Ctrl+Z 撤销')
  aiAdopt('review')
  reviewSuggestions.value.splice(index, 1)
  refreshReviewHighlights()
}

function ignoreSuggestion(index) {
  reviewSuggestions.value.splice(index, 1)
  refreshReviewHighlights()
}

// ---------- 术语与措辞 ----------
const wordingTarget = ref('')
const wordingIssues = ref([])
const wordingLoading = ref(false)
const wordingDegraded = ref(false)
const wordingMessage = ref('')
const wordingChecked = ref(false)

async function checkWording() {
  const editor = editorRef.value
  if (!editor || wordingLoading.value) return
  const info = getSelectionInfo(editor)
  const text = (info?.selectedText || wordingTarget.value || '').trim()
  if (!text) {
    ElMessage.warning('请先在正文中选中一段文字')
    return
  }
  if (text.length < 20) {
    ElMessage.warning('选中内容少于 20 字，请选中完整段落')
    return
  }
  if (text.length > 1500) {
    ElMessage.warning('选中内容超过 1500 字，请缩短后再检查')
    return
  }
  wordingTarget.value = text
  wordingLoading.value = true
  wordingChecked.value = true
  aiTab.value = 'wording'
  try {
    const data = await techApi.wording({
      articleId: articleId.value || undefined,
      sectionPath: form.title || undefined,
      text
    })
    wordingIssues.value = data.issues || []
    wordingDegraded.value = !!data.degraded
    wordingMessage.value = data.message || ''
    if (!wordingIssues.value.length && !wordingDegraded.value) {
      ElMessage.info('未发现需要调整的表述')
    }
  } catch {
    wordingIssues.value = []
    wordingDegraded.value = true
    wordingMessage.value = 'AI 服务暂时不可用，可稍后重试'
  } finally {
    wordingLoading.value = false
  }
}

function locateWordingIssue(index) {
  const editor = editorRef.value
  const item = wordingIssues.value[index]
  if (!editor || !item) return
  const located = locateQuote(getPlainText(editor), item.quote)
  if (!located) {
    ElMessage.warning('原文已修改，本条无法定位')
    return
  }
  const range = rangeFromOffsets(editor, located.start, located.end)
  if (!range) return
  suppressSelectionMenu = true
  editor.select(range)
  editor.restoreSelection?.()
  editor.getEditableContainer?.()?.scrollIntoView({ block: 'center', behavior: 'smooth' })
  window.setTimeout(() => {
    suppressSelectionMenu = false
  }, 400)
}

function applyWordingIssue(index) {
  const editor = editorRef.value
  const item = wordingIssues.value[index]
  if (!editor || !item?.replacement) return
  const located = locateQuote(getPlainText(editor), item.quote)
  if (!located) {
    ElMessage.warning('原文已修改，请重新检查')
    return
  }
  if (!replaceOffsets(editor, located.start, located.end, item.replacement)) {
    ElMessage.error('替换失败，请重试')
    return
  }
  ElMessage.success('已替换，可用 Ctrl+Z 撤销')
  aiAdopt('tech_assist')
  wordingIssues.value.splice(index, 1)
}

function ignoreWordingIssue(index) {
  wordingIssues.value.splice(index, 1)
}

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
    // 当前编辑文章 id：供 AI 输出审计溯源到具体文章
    articleId: articleId.value || undefined,
    params: {
      topic: ai.topic || undefined,
      articleType: ai.articleType || undefined,
      style: ai.style || undefined,
      targetReader: ai.targetReader || undefined,
      styleProfileId: selectedStyleProfileId.value || undefined
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
  const el = resultBodyRef.value
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

// ---------- 草稿自动保存（本地快照，防刷新/误关丢失） ----------
const AUTOSAVE_PREFIX = 'aiblog:autosave:'
const autosaveAt = ref(null)
const dirty = ref(false)
let autosaveTimer = null
let autosaveReady = false

const autosaveKey = computed(() => `${AUTOSAVE_PREFIX}${articleId.value || 'new'}`)

const autosaveText = computed(() => {
  if (!autosaveReady) return ''
  if (dirty.value) return '有未保存的修改'
  if (!autosaveAt.value) return ''
  const d = autosaveAt.value
  const p = (n) => String(n).padStart(2, '0')
  return `已自动保存 ${p(d.getHours())}:${p(d.getMinutes())}`
})

function persistSnapshot() {
  if (!autosaveReady) return
  if (!form.title.trim() && !form.contentHtml.trim()) return
  try {
    localStorage.setItem(autosaveKey.value, JSON.stringify({
      title: form.title,
      categoryId: form.categoryId,
      summary: form.summary,
      contentHtml: form.contentHtml,
      tags: form.tags,
      scheduledAt: form.scheduledAt,
      aiUsageLevel: form.aiUsageLevel,
      savedAt: Date.now()
    }))
    autosaveAt.value = new Date()
  } catch {
    // 本地存储不可用时静默降级，不影响写作
  }
  dirty.value = false
}

function scheduleAutosave() {
  if (!autosaveReady) return
  dirty.value = true
  if (autosaveTimer) clearTimeout(autosaveTimer)
  autosaveTimer = window.setTimeout(persistSnapshot, 1500)
}

async function maybeRestoreSnapshot(serverUpdatedAt) {
  let raw = null
  try {
    raw = localStorage.getItem(autosaveKey.value)
  } catch {
    return
  }
  if (!raw) return
  let snap
  try {
    snap = JSON.parse(raw)
  } catch {
    localStorage.removeItem(autosaveKey.value)
    return
  }
  if (!snap?.savedAt) return
  // 服务端已有更新版本，本地快照过期
  if (serverUpdatedAt && snap.savedAt <= new Date(serverUpdatedAt).getTime()) {
    localStorage.removeItem(autosaveKey.value)
    return
  }
  if ((snap.title || '') === form.title && (snap.contentHtml || '') === form.contentHtml) return
  try {
    await ElMessageBox.confirm(
      `发现 ${formatDate(new Date(snap.savedAt).toISOString())} 的本地草稿，是否恢复？`,
      '自动保存',
      { confirmButtonText: '恢复', cancelButtonText: '丢弃', type: 'info' }
    )
  } catch {
    localStorage.removeItem(autosaveKey.value)
    return
  }
  form.title = snap.title || ''
  form.categoryId = snap.categoryId ?? null
  form.summary = snap.summary || ''
  form.contentHtml = snap.contentHtml || ''
  form.tags = snap.tags || []
  form.scheduledAt = snap.scheduledAt || null
  form.aiUsageLevel = snap.aiUsageLevel || null
  editorRef.value?.setHtml(snap.contentHtml || '')
  autosaveAt.value = new Date(snap.savedAt)
  ElMessage.success('已恢复本地草稿')
}

function clearSnapshot() {
  if (autosaveTimer) clearTimeout(autosaveTimer)
  dirty.value = false
  try {
    localStorage.removeItem(autosaveKey.value)
  } catch {
    // 忽略本地存储异常
  }
}

function onBeforeUnload(event) {
  if (!dirty.value) return
  event.preventDefault()
  event.returnValue = ''
}

watch(() => [form.title, form.categoryId, form.summary, form.contentHtml,
  form.tags, form.scheduledAt, form.aiUsageLevel], scheduleAutosave, { deep: true })

// ---------- 保存 ----------
async function save(status) {
  if (!form.title.trim()) {
    ElMessage.warning('请输入文章标题')
    return
  }
  if (status === 'scheduled' && !form.scheduledAt) {
    ElMessage.warning('请先选择定时发布时间')
    return
  }
  if ((status === 'published' || status === 'scheduled') && !form.aiUsageLevel) {
    ElMessage.warning('发布前请选择 AI 参与声明（未使用 / 辅助 / 主要生成）')
    return
  }
  saving.value = true
  try {
    const payload = {
      title: form.title,
      categoryId: form.categoryId || undefined,
      contentHtml: form.contentHtml,
      summary: form.summary || undefined,
      status,
      tags: form.tags,
      scheduledAt: status === 'scheduled' ? form.scheduledAt : undefined,
      aiUsageLevel: form.aiUsageLevel || undefined
    }
    let data
    if (articleId.value) {
      data = await articleApi.update(articleId.value, payload)
    } else {
      data = await articleApi.create(payload)
    }
    // 保存成功后按选择加入连载（编辑时仅在连载选择发生变化时提交）
    if (selectedSeriesId.value && selectedSeriesId.value !== originalSeriesId && data?.id) {
      try {
        await seriesApi.addArticle(selectedSeriesId.value, { articleId: data.id })
        originalSeriesId = selectedSeriesId.value
      } catch {
        /* 加入失败不影响文章保存，拦截器已提示 */
      }
    }
    afterPublish(status, data)
  } finally {
    saving.value = false
  }
}

function confirmSchedule() {
  scheduleOpen.value = false
  save('scheduled')
}

/** 按该文章的 AI 调用审计记录推荐声明等级 */
async function loadSuggestion() {
  if (!articleId.value) return
  suggestLoading.value = true
  try {
    const data = await articleApi.aiDisclosureSuggest(articleId.value)
    form.aiUsageLevel = data.suggestedLevel
    const count = Number(data.aiCallCount || 0)
    const label = { none: '未使用 AI', assisted: 'AI 辅助', generated: '主要生成' }
    ElMessage.info(count === 0
      ? '该文章暂无 AI 调用记录，建议选择「未使用 AI」'
      : `检测到 ${count} 次 AI 调用（采纳 ${data.adoptedCount} 次），建议选择「${label[data.suggestedLevel] || data.suggestedLevel}」`)
  } catch (err) {
    ElMessage.error(err?.message || '推荐获取失败，请手动选择')
  } finally {
    suggestLoading.value = false
  }
}

/** 发布响应关怀分支（契约 5.8）：high → 危机页（文章已私密发布）；low/mid → 信笺/短笺 */
function afterPublish(status, data) {
  autosaveReady = false
  clearSnapshot()
  if (status === 'published' && data?.care?.level === 'high') {
    router.push(`/care/crisis?article=${data.id}`)
    return
  }
  if (status === 'published' && (data?.care?.level === 'low' || data?.care?.level === 'mid')) {
    emitCare({ level: data.care.level, summary: data.care.summary })
  }
  if (status === 'scheduled') ElMessage.success('已加入定时发布')
  else ElMessage.success(status === 'published' ? '发布成功' : '草稿已保存')
  router.push('/my')
}

onMounted(async () => {
  window.addEventListener('keydown', onKeydown)
  window.addEventListener('beforeunload', onBeforeUnload)
  loadCompanion()
  loadStyleProfiles()
  try {
    categories.value = await categoryApi.list()
  } catch {
    categories.value = []
  }
  try {
    tagOptions.value = await tagApi.list()
  } catch {
    tagOptions.value = []
  }
  loadSeriesOptions()
  let serverUpdatedAt = null
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
    form.tags = detail.tags || []
    form.scheduledAt = detail.scheduledAt || null
    form.aiUsageLevel = detail.aiUsageLevel || null
    originalSeriesId = detail.series?.id ?? null
    selectedSeriesId.value = originalSeriesId
    serverUpdatedAt = detail.updatedAt
  }
  await maybeRestoreSnapshot(serverUpdatedAt)
  autosaveReady = true
})
</script>

<style scoped>
.write-page {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 20px 64px;
  animation: page-in 0.45s cubic-bezier(0.22, 0.8, 0.32, 1) both;
}

/* ---------- 吸顶操作栏 ---------- */
.write-topbar {
  position: sticky;
  top: 0;
  z-index: 200;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 0;
  margin-bottom: 16px;
  background: var(--paper);
  border-bottom: 1px solid var(--ink);
}
.topbar-left,
.topbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.topbar-title {
  font-size: 19px;
  font-weight: 900;
  letter-spacing: 0.12em;
}
.doc-badge {
  padding: 1px 8px;
  border: 1px solid var(--hairline-dark);
  font-size: 12px;
  letter-spacing: 0.08em;
  color: var(--ink-soft);
  white-space: nowrap;
}
.word-count {
  font-size: 12.5px;
  color: var(--ink-faint);
  letter-spacing: 0.06em;
  white-space: nowrap;
}
.autosave-hint {
  font-size: 12.5px;
  letter-spacing: 0.06em;
  color: var(--ink-faint);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.schedule-pop .schedule-title {
  margin-bottom: 8px;
  font-weight: 600;
  letter-spacing: 0.08em;
}
.schedule-confirm {
  width: 100%;
  margin-top: 10px;
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

.disclosure-row,
.series-row {
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.disclosure-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--ink, #2c2a26);
}

.disclosure-hint {
  font-size: 12px;
  color: var(--muted, #8a8578);
}

/* ---------- 富文本编辑器：高度跟随视口 ---------- */
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
  height: calc(100vh - 330px);
  min-height: 440px;
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
.editor-foot {
  margin-top: 10px;
  display: flex;
  justify-content: flex-end;
}
.hint {
  color: var(--ink-faint);
  font-size: 12.5px;
  line-height: 1.6;
  letter-spacing: 0.03em;
}

/* ---------- AI 辅助栏：sticky 跟随 ---------- */
.ai-col {
  align-self: flex-start;
  position: sticky;
  top: 62px;
}
.ai-card {
  max-height: calc(100vh - 84px);
  overflow-y: auto;
}
.ai-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  letter-spacing: 0.08em;
}

/* 写作陪伴提示卡 */
.companion-card {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 14px;
  padding: 10px 12px;
  border: 1px solid var(--hairline);
  border-left: 3px solid #4a8c5c;
  border-radius: 3px;
  background: rgba(74, 140, 92, 0.05);
}
.companion-text {
  font-size: 13px;
  line-height: 1.7;
  color: var(--ink-soft);
  letter-spacing: 0.02em;
}
.companion-tone {
  flex-shrink: 0;
}

/* 文风库 */
.style-row {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}
.style-select {
  flex: 1;
}
.style-manage {
  min-height: 120px;
}
.style-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 2px;
  border-bottom: 1px solid var(--hairline);
}
.style-item-main {
  min-width: 0;
}
.style-item-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 700;
  color: var(--ink);
}
.style-item-sub {
  margin-top: 4px;
  font-size: 12.5px;
  color: var(--ink-faint);
}
.style-item-actions {
  flex-shrink: 0;
  display: flex;
  align-items: center;
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

/* ---------- 页签：片段建议 / 全文建议 ---------- */
.ai-tabs :deep(.el-tabs__header) {
  margin-bottom: 12px;
}
.tab-dot {
  display: inline-block;
  width: 5px;
  height: 5px;
  margin-left: 4px;
  border-radius: 50%;
  background: var(--cinnabar);
  vertical-align: super;
}
.selection-pane,
.review-pane {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.pane-caption {
  display: flex;
  justify-content: space-between;
  color: var(--ink-faint);
  font-size: 12px;
  letter-spacing: 0.06em;
}
.pane-mode {
  color: var(--cinnabar);
}
.pane-quote {
  margin: 0;
  padding: 8px 10px;
  border-left: 2px solid var(--cinnabar);
  background: var(--paper-deep);
  font-family: var(--font-serif);
  font-size: 13px;
  line-height: 1.7;
  color: var(--ink);
}
.review-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

/* 版本历史 */
.version-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid var(--hairline);
}
.version-title {
  font-weight: 600;
  color: var(--ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.version-time {
  margin-top: 4px;
  font-size: 12.5px;
  color: var(--ink-faint);
}

@media (max-width: 900px) {
  .write-topbar {
    flex-wrap: wrap;
  }
  .write-page :deep(.el-col) {
    width: 100%;
    max-width: 100%;
    flex: 0 0 100%;
    margin-bottom: 16px;
  }
  .ai-col {
    position: static;
  }
  .ai-card {
    max-height: none;
  }
  .editor-content {
    height: auto;
    min-height: 380px;
  }
}
</style>

<!-- CSS Custom Highlight API：建议引文的非侵入高亮 -->
<style>
::highlight(ai-suggestion) {
  background: rgba(160, 60, 40, 0.16);
  text-decoration: underline;
  text-decoration-color: rgba(160, 60, 40, 0.55);
}
</style>
