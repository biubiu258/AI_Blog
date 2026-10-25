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
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { authApi, fileApi, userApi } from '../api'
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

onMounted(async () => {
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
</style>
