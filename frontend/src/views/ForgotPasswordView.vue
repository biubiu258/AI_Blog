<template>
  <div class="auth-page">
    <router-link to="/login" class="back-home">← 返回登录</router-link>

    <aside class="auth-aside">
      <div class="aside-seal">文</div>
      <div class="aside-vertical">重拾旧钥</div>
      <div class="aside-latin">RESET PASSWORD · AI WRITING JOURNAL</div>
    </aside>

    <div class="auth-panel">
      <div class="panel-brand">
        <span class="mini-seal">文</span>
        <span class="panel-brand-name">AI 发文博客</span>
      </div>
      <h2 class="auth-title">找回密码</h2>
      <div class="auth-sub">RESET YOUR PASSWORD</div>
      <div class="title-rule"><span class="rule-mark">◆</span></div>

      <el-form :model="form" label-position="top" @submit.prevent>
        <el-form-item label="注册邮箱">
          <div class="code-row">
            <el-input v-model="form.email" placeholder="请输入注册时使用的邮箱" />
            <el-button :loading="sending" :disabled="!form.email" @click="sendCode">
              发送验证码
            </el-button>
          </div>
        </el-form-item>
        <el-form-item label="验证码">
          <el-input v-model="form.code" maxlength="6" placeholder="6 位数字验证码" />
          <div v-if="devCode" class="dev-tip">开发环境验证码：{{ devCode }}</div>
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="form.newPassword" type="password" show-password
                    placeholder="6-64 位新密码" />
        </el-form-item>
        <el-form-item label="确认新密码">
          <el-input v-model="form.confirmPassword" type="password" show-password
                    placeholder="再次输入新密码" @keyup.enter="submit" />
        </el-form-item>

        <el-button type="primary" size="large" class="auth-btn" :loading="submitting"
                   @click="submit">
          重置密码
        </el-button>
        <div class="auth-footer">
          想起密码了？
          <router-link to="/login" class="auth-link">去登录</router-link>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '../api'

const router = useRouter()

const sending = ref(false)
const submitting = ref(false)
const devCode = ref('')
const form = reactive({ email: '', code: '', newPassword: '', confirmPassword: '' })

async function sendCode() {
  if (!form.email.trim()) {
    ElMessage.warning('请先填写邮箱')
    return
  }
  sending.value = true
  try {
    const data = await authApi.sendResetCode({ email: form.email.trim() })
    devCode.value = data?.devCode || ''
    ElMessage.success('若邮箱存在，验证码已发送，请查收')
  } finally {
    sending.value = false
  }
}

async function submit() {
  if (!form.email.trim() || !form.code.trim()) {
    ElMessage.warning('请填写邮箱与验证码')
    return
  }
  if (form.newPassword.length < 6) {
    ElMessage.warning('新密码至少 6 位')
    return
  }
  if (form.newPassword !== form.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  submitting.value = true
  try {
    await authApi.resetPassword({
      email: form.email.trim(),
      code: form.code.trim(),
      newPassword: form.newPassword
    })
    ElMessage.success('密码已重置，请重新登录')
    router.push('/login')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 72px;
  padding: 40px 20px;
  position: relative;
}
.back-home {
  position: absolute;
  top: 22px;
  left: 26px;
  color: var(--ink-faint);
  font-size: 13px;
  letter-spacing: 0.12em;
  text-decoration: none;
  transition: color 0.2s;
}
.back-home:hover {
  color: var(--cinnabar);
}
.auth-aside {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 26px;
  user-select: none;
}
.aside-seal {
  width: 44px;
  height: 44px;
  background: var(--cinnabar);
  color: #fdfbf4;
  font-size: 26px;
  font-weight: 900;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: inset 0 0 0 2px rgba(253, 251, 244, 0.35),
    3px 3px 0 rgba(38, 36, 30, 0.15);
}
.aside-vertical {
  writing-mode: vertical-rl;
  font-size: 46px;
  font-weight: 900;
  letter-spacing: 0.4em;
  color: var(--ink);
  padding: 20px 12px;
  border-left: 1px solid var(--hairline-dark);
  border-right: 1px solid var(--hairline-dark);
}
.aside-latin {
  writing-mode: vertical-rl;
  font-size: 10px;
  letter-spacing: 0.42em;
  color: var(--ink-faint);
}
.auth-panel {
  width: 420px;
  background: var(--paper-card);
  border: 1px solid var(--hairline-dark);
  border-top: 3px solid var(--ink);
  padding: 34px 36px 30px;
  box-shadow: 8px 8px 0 rgba(38, 36, 30, 0.06);
}
.panel-brand {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-bottom: 18px;
}
.mini-seal {
  width: 22px;
  height: 22px;
  background: var(--cinnabar);
  color: #fdfbf4;
  font-size: 13px;
  font-weight: 900;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.panel-brand-name {
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 0.18em;
}
.auth-title {
  margin: 0;
  text-align: center;
  font-size: 26px;
  font-weight: 900;
  letter-spacing: 0.2em;
}
.auth-sub {
  margin-top: 6px;
  text-align: center;
  font-size: 10px;
  letter-spacing: 0.32em;
  color: var(--ink-faint);
}
.title-rule {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 20px 0 24px;
}
.title-rule::before,
.title-rule::after {
  content: '';
  flex: 1;
  border-top: 1px solid var(--hairline-dark);
}
.rule-mark {
  color: var(--cinnabar);
  font-size: 8px;
}
.auth-btn {
  width: 100%;
  letter-spacing: 0.4em;
}
.code-row {
  display: flex;
  gap: 10px;
  width: 100%;
}
.code-row .el-input {
  flex: 1;
}
.dev-tip {
  margin-top: 4px;
  font-size: 12px;
  color: var(--cinnabar);
}
.auth-footer {
  margin-top: 16px;
  text-align: center;
  color: var(--ink-faint);
  font-size: 14px;
  letter-spacing: 0.06em;
}
.auth-link {
  color: var(--cinnabar);
  text-decoration: none;
  border-bottom: 1px solid rgba(176, 58, 38, 0.35);
}
@media (max-width: 860px) {
  .auth-aside {
    display: none;
  }
  .auth-panel {
    width: min(420px, 94vw);
    padding: 28px 24px 24px;
  }
}
</style>
