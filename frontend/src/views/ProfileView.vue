<template>
  <div class="page-container">
    <header class="page-head">
      <div class="page-title">个人中心</div>
      <div class="page-sub">PROFILE</div>
    </header>

    <el-row :gutter="20">
      <el-col :span="12">
        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="card-head">
              <span class="card-title">个人资料</span>
              <span class="card-sub">ABOUT ME</span>
            </div>
          </template>
          <el-form label-width="80px">
            <el-form-item label="头像">
              <div class="avatar-row">
                <el-avatar :size="56" :src="form.avatarUrl || undefined">
                  {{ (auth.user?.nickname || 'U').slice(0, 1) }}
                </el-avatar>
                <el-upload :show-file-list="false" accept="image/*"
                           :http-request="uploadAvatar">
                  <el-button size="small">更换头像</el-button>
                </el-upload>
              </div>
            </el-form-item>
            <el-form-item label="用户名">
              <el-input :model-value="auth.user?.username" disabled />
            </el-form-item>
            <el-form-item label="昵称">
              <el-input v-model="form.nickname" maxlength="64" />
            </el-form-item>
            <el-form-item label="邮箱">
              <div class="email-row">
                <el-input v-model="form.email" />
                <el-tag v-if="emailVerified" type="success" size="small">已验证</el-tag>
                <el-button v-else size="small" :loading="sendingCode"
                           :disabled="!form.email" @click="sendCode">发验证码</el-button>
              </div>
              <div v-if="codeSent && !emailVerified" class="code-row">
                <el-input v-model="emailCode" placeholder="6 位验证码" maxlength="6" />
                <el-button size="small" :loading="verifying" @click="verifyEmail">验证</el-button>
              </div>
              <div v-if="devCode" class="dev-hint">开发模式验证码：{{ devCode }}</div>
            </el-form-item>
            <el-form-item label="简介">
              <el-input v-model="form.bio" type="textarea" :rows="3" maxlength="500" show-word-limit />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="saving" @click="saveProfile">保存资料</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="card-head">
              <span class="card-title">写作偏好</span>
              <span class="card-sub">AI CONTEXT · 生成时注入</span>
            </div>
          </template>
          <el-form label-width="90px">
            <el-form-item label="口吻/风格">
              <el-input v-model="pref.tone" maxlength="200" placeholder="如：轻松幽默、正式严谨" />
            </el-form-item>
            <el-form-item label="目标读者">
              <el-input v-model="pref.targetReader" maxlength="200"
                        placeholder="如：有 Java 基础的大学生" />
            </el-form-item>
            <el-form-item label="常用结构">
              <el-input v-model="pref.structure" type="textarea" :rows="3"
                        placeholder="描述你偏好的文章结构，或留空" />
            </el-form-item>
            <el-form-item label="其他偏好">
              <el-input v-model="pref.extra" type="textarea" :rows="2" placeholder="其他希望 AI 了解的偏好" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="savingPref" @click="savePreference">保存偏好</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>

    <!-- 人类创作徽章 -->
    <el-card shadow="never" class="panel-card badge-card">
      <template #header>
        <div class="card-head">
          <span class="card-title">人类创作徽章</span>
          <span class="card-sub">HUMAN BADGE</span>
        </div>
      </template>
      <div v-loading="badgeLoading" class="badge-body">
        <template v-if="hasPassedBadge">
          <el-tag type="success" effect="dark">✍ 人类创作作者</el-tag>
          <span class="badge-tip">已通过人工审核，个人主页将展示「人类创作」标识</span>
        </template>
        <template v-else>
          <div v-for="b in badges" :key="b.id" class="badge-record">
            <el-tag v-if="b.status === 'pending'" type="warning" size="small" effect="plain">
              审核中
            </el-tag>
            <template v-else-if="b.status === 'rejected'">
              <el-tag type="info" size="small" effect="plain">未通过</el-tag>
              <span class="badge-note">未通过：{{ b.note || '未填写理由' }}</span>
            </template>
            <span class="badge-time">{{ formatDate(b.createdAt) }}</span>
          </div>
          <div v-if="!hasPendingBadge" class="badge-apply">
            <span class="badge-tip">累计发布 3 篇及以上人类创作的文章即可申请，通过后展示在个人主页</span>
            <el-button type="primary" size="small" :loading="applying" @click="applyBadge">
              申请徽章
            </el-button>
          </div>
        </template>
      </div>
    </el-card>

    <!-- 创作运营入口 -->
    <el-card shadow="never" class="panel-card tools-card">
      <template #header>
        <div class="card-head">
          <span class="card-title">创作运营</span>
          <span class="card-sub">SERIES · STREAK · CURATOR</span>
        </div>
      </template>
      <div class="entry-row">
        <el-button @click="$router.push('/series')">我的连载</el-button>
        <el-button @click="$router.push('/streak')">
          日更打卡
          <span v-if="streakDays > 0" class="entry-streak">连续 {{ streakDays }} 天</span>
        </el-button>
        <el-button v-if="isCurator" type="primary" plain @click="$router.push('/curator')">
          伯乐工作台
        </el-button>
      </div>
    </el-card>

    <!-- AI 洞察回顾（只读） -->
    <el-card shadow="never" class="panel-card insight-card">
      <template #header>
        <div class="card-head">
          <span class="card-title">AI 洞察回顾</span>
          <span class="card-sub">READ-ONLY REVIEW</span>
        </div>
      </template>
      <div v-loading="insightLoading" class="insight-body">
        <div class="insight-head">
          <span class="insight-tip">只回顾，不构成写作建议</span>
          <el-button type="primary" size="small" :loading="insightLoading" @click="generateInsight">
            生成回顾
          </el-button>
        </div>
        <el-alert v-if="insightError" type="info" :closable="false" show-icon
                  :title="insightError" class="insight-alert" />
        <template v-else-if="insight">
          <p class="insight-summary">{{ insight.summary }}</p>
          <div v-if="(insight.themes || []).length" class="insight-group">
            <span class="insight-label">主题</span>
            <el-tag v-for="t in insight.themes" :key="t" size="small" effect="plain" class="insight-tag">
              {{ t }}
            </el-tag>
          </div>
          <div v-if="(insight.motifs || []).length" class="insight-group">
            <span class="insight-label">意象</span>
            <el-tag v-for="m in insight.motifs" :key="m" size="small" type="info" effect="plain"
                    class="insight-tag">
              {{ m }}
            </el-tag>
          </div>
          <div v-if="(insight.habits || []).length" class="insight-group">
            <span class="insight-label">写作习惯</span>
            <ul class="insight-habits">
              <li v-for="h in insight.habits" :key="h">{{ h }}</li>
            </ul>
          </div>
          <p v-if="insight.disclaimer" class="insight-disclaimer">{{ insight.disclaimer }}</p>
          <p class="insight-meta">
            基于 {{ insight.articleCount || 0 }} 篇文章 · {{ formatDate(insight.generatedAt) }}
          </p>
        </template>
        <p v-else class="insight-hint">
          点击「生成回顾」，让 AI 仅回顾你已发布的文章，梳理主题、意象与写作习惯。
        </p>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { authApi, badgeApi, curatorApi, fileApi, insightApi, streakApi, userApi } from '../api'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const saving = ref(false)
const savingPref = ref(false)
const form = reactive({ nickname: '', email: '', bio: '', avatarUrl: '' })
const pref = reactive({ tone: '', targetReader: '', structure: '', extra: '' })
const emailVerified = ref(false)
const codeSent = ref(false)
const emailCode = ref('')
const devCode = ref('')
const sendingCode = ref(false)
const verifying = ref(false)

async function sendCode() {
  sendingCode.value = true
  try {
    const res = await authApi.sendEmailCode()
    codeSent.value = true
    devCode.value = res.devCode || ''
    ElMessage.success('验证码已发送到邮箱')
  } finally {
    sendingCode.value = false
  }
}

async function verifyEmail() {
  if (!emailCode.value.trim()) {
    ElMessage.warning('请输入验证码')
    return
  }
  verifying.value = true
  try {
    await authApi.verifyEmail({ code: emailCode.value.trim() })
    emailVerified.value = true
    codeSent.value = false
    devCode.value = ''
    if (auth.user) {
      auth.user.emailVerified = 1
      localStorage.setItem('user', JSON.stringify(auth.user))
    }
    ElMessage.success('邮箱验证成功')
  } finally {
    verifying.value = false
  }
}

async function uploadAvatar({ file }) {
  const fd = new FormData()
  fd.append('file', file)
  const data = await fileApi.upload(fd)
  form.avatarUrl = data.url
  ElMessage.success('头像已上传，点击保存资料生效')
}

async function saveProfile() {
  saving.value = true
  try {
    const user = await userApi.updateMe({ ...form })
    auth.user = user
    localStorage.setItem('user', JSON.stringify(user))
    ElMessage.success('资料已保存')
  } finally {
    saving.value = false
  }
}

async function savePreference() {
  savingPref.value = true
  try {
    await userApi.updatePreference({ ...pref })
    ElMessage.success('写作偏好已保存')
  } finally {
    savingPref.value = false
  }
}

// ---------- 人类创作徽章 ----------
const badges = ref([])
const badgeLoading = ref(false)
const applying = ref(false)
const hasPassedBadge = computed(() => badges.value.some((b) => b.status === 'passed'))
const hasPendingBadge = computed(() => badges.value.some((b) => b.status === 'pending'))

async function loadBadges() {
  badgeLoading.value = true
  try {
    badges.value = (await badgeApi.mine()) || []
  } catch {
    badges.value = []
  } finally {
    badgeLoading.value = false
  }
}

async function applyBadge() {
  applying.value = true
  try {
    await badgeApi.apply()
    ElMessage.success('申请已提交，等待人工审核')
    await loadBadges()
  } catch {
    // 400（不足 3 篇 / 已有待审申请）等错误提示已由请求层统一展示
  } finally {
    applying.value = false
  }
}

// ---------- 创作运营入口 ----------
const streakDays = ref(0)
const isCurator = ref(false)

async function loadEntryStats() {
  try {
    const summary = await streakApi.summary()
    streakDays.value = summary?.currentStreak || 0
  } catch {
    streakDays.value = 0
  }
  try {
    const me = await curatorApi.me()
    isCurator.value = !!me?.isCurator
  } catch {
    isCurator.value = false
  }
}

// ---------- AI 洞察回顾（只读） ----------
const insight = ref(null)
const insightLoading = ref(false)
const insightError = ref('')

async function generateInsight() {
  insightLoading.value = true
  insightError.value = ''
  try {
    insight.value = await insightApi.generate()
  } catch (err) {
    insight.value = null
    insightError.value = err?.message || err?.response?.data?.message || '回顾生成失败，请稍后再试'
  } finally {
    insightLoading.value = false
  }
}

function formatDate(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : ''
}

onMounted(async () => {
  loadBadges()
  loadEntryStats()
  try {
    const me = await userApi.me()
    Object.assign(form, {
      nickname: me.nickname || '',
      email: me.email || '',
      bio: me.bio || '',
      avatarUrl: me.avatarUrl || ''
    })
    emailVerified.value = me.emailVerified === 1
    auth.user = me
  } catch { /* 拦截器已处理 */
  }
  try {
    const p = await userApi.getPreference()
    if (p) Object.assign(pref, p)
  } catch { /* 偏好可能未配置 */
  }
})
</script>

<style scoped>
.panel-card {
  height: 100%;
}

/* 卡头：中文题 + 拉丁小注 */
.card-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}
.card-title {
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0.12em;
}
.card-sub {
  font-size: 11px;
  letter-spacing: 0.2em;
  color: var(--ink-faint);
}

.avatar-row {
  display: flex;
  align-items: center;
  gap: 14px;
}
.email-row {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}
.email-row :deep(.el-input) {
  flex: 1;
}
.code-row {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  margin-top: 10px;
}
.code-row :deep(.el-input) {
  flex: 1;
}
.dev-hint {
  margin-top: 8px;
  font-size: 12.5px;
  color: var(--cinnabar);
}
.avatar-row :deep(.el-avatar) {
  border: 1px solid var(--hairline-dark);
  background: var(--paper-deep);
  color: var(--ink-soft);
  font-weight: 700;
}

/* 人类创作徽章 */
.badge-card {
  margin-top: 20px;
}
.badge-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 48px;
}
.badge-record {
  display: flex;
  align-items: center;
  gap: 10px;
}
.badge-note {
  font-size: 13px;
  color: var(--ink-faint);
}
.badge-time {
  margin-left: auto;
  font-size: 12.5px;
  color: var(--ink-faint);
}
.badge-apply {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  flex-wrap: wrap;
}
.badge-tip {
  font-size: 13px;
  color: var(--ink-soft);
}

/* 创作运营入口 */
.tools-card,
.insight-card {
  margin-top: 20px;
}
.entry-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}
.entry-row .el-button {
  margin-left: 0;
}
.entry-streak {
  margin-left: 6px;
  font-size: 12px;
  color: var(--cinnabar);
}

/* AI 洞察回顾 */
.insight-body {
  min-height: 92px;
}
.insight-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 10px;
}
.insight-tip {
  font-size: 12.5px;
  letter-spacing: 0.08em;
  color: var(--cinnabar);
}
.insight-alert {
  margin-top: 6px;
}
.insight-summary {
  margin: 4px 0 14px;
  font-size: 14px;
  line-height: 1.9;
  color: var(--ink);
  white-space: pre-wrap;
}
.insight-group {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}
.insight-label {
  font-size: 12.5px;
  color: var(--ink-faint);
  letter-spacing: 0.1em;
  flex-shrink: 0;
}
.insight-tag {
  margin: 0;
}
.insight-habits {
  margin: 0;
  padding-left: 18px;
  font-size: 13px;
  line-height: 1.9;
  color: var(--ink-soft);
}
.insight-disclaimer {
  margin: 12px 0 4px;
  padding: 8px 12px;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--ink-faint);
  background: rgba(0, 0, 0, 0.02);
  border-left: 2px solid var(--hairline-dark);
}
.insight-meta {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--ink-faint);
  letter-spacing: 0.04em;
}
.insight-hint {
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 1.8;
  color: var(--ink-faint);
}
</style>
