<template>
  <div class="auth-page">
    <router-link to="/" class="back-home">← 返回首页</router-link>

    <!-- 左侧竖排题词（扉页） -->
    <aside class="auth-aside">
      <div class="aside-seal">文</div>
      <div class="aside-vertical">见字如面</div>
      <div class="aside-latin">WELCOME BACK · AI WRITING JOURNAL</div>
    </aside>

    <!-- 右侧纸面表单 -->
    <div class="auth-panel">
      <div class="panel-brand">
        <span class="mini-seal">文</span>
        <span class="panel-brand-name">AI 发文博客</span>
      </div>
      <h2 class="auth-title">登 录</h2>
      <div class="auth-sub">SIGN IN TO YOUR ACCOUNT</div>
      <div class="title-rule"><span class="rule-mark">◆</span></div>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入密码"
                    @keyup.enter="submit" />
        </el-form-item>
        <el-form-item label="验证码" prop="captchaCode">
          <div class="captcha-row">
            <el-input v-model="form.captchaCode" placeholder="输入右侧字符" maxlength="4"
                      @keyup.enter="submit" />
            <img v-if="captcha.image" :src="captcha.image" class="captcha-img"
                 title="看不清？点击刷新" alt="验证码" @click="loadCaptcha" />
          </div>
        </el-form-item>
        <el-button type="primary" size="large" class="auth-btn" :loading="loading" @click="submit">
          登 录
        </el-button>
        <div class="auth-footer">
          还没有账号？
          <router-link to="/register" class="auth-link">去注册</router-link>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import { authApi } from '../api'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const formRef = ref()
const loading = ref(false)
const form = reactive({ username: '', password: '', captchaCode: '' })
const captcha = reactive({ captchaId: '', image: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  captchaCode: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
}

async function loadCaptcha() {
  const data = await authApi.captcha()
  captcha.captchaId = data.captchaId
  captcha.image = data.image
  form.captchaCode = ''
}

async function submit() {
  await formRef.value.validate()
  loading.value = true
  try {
    await auth.login({
      username: form.username,
      password: form.password,
      captchaId: captcha.captchaId,
      captchaCode: form.captchaCode
    })
    ElMessage.success('登录成功')
    router.push(route.query.redirect || '/')
  } catch {
    // 验证码一次性：无论失败原因，刷新以备重试
    loadCaptcha()
  } finally {
    loading.value = false
  }
}

onMounted(loadCaptcha)
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

/* ---------- 左侧扉页题词 ---------- */
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

/* ---------- 右侧纸面表单 ---------- */
.auth-panel {
  width: 400px;
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
  letter-spacing: 0.3em;
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

.captcha-row {
  display: flex;
  gap: 10px;
  width: 100%;
}
.captcha-row .el-input {
  flex: 1;
}
.captcha-img {
  height: 32px;
  border: 1px solid var(--hairline-dark);
  border-radius: 2px;
  cursor: pointer;
  background: var(--paper-deep);
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
    width: min(400px, 94vw);
    padding: 28px 24px 24px;
  }
}
</style>
