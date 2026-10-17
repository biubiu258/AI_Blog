<template>
  <div class="auth-page">
    <router-link to="/" class="back-home">← 返回首页</router-link>

    <!-- 左侧竖排题词（扉页） -->
    <aside class="auth-aside">
      <div class="aside-seal">文</div>
      <div class="aside-vertical">以文会友</div>
      <div class="aside-latin">JOIN THE JOURNAL</div>
    </aside>

    <!-- 右侧纸面表单 -->
    <div class="auth-panel">
      <div class="panel-brand">
        <span class="mini-seal">文</span>
        <span class="panel-brand-name">AI 发文博客</span>
      </div>
      <h2 class="auth-title">注 册</h2>
      <div class="auth-sub">CREATE YOUR ACCOUNT</div>
      <div class="title-rule"><span class="rule-mark">◆</span></div>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="登录用户名" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password
                    placeholder="至少 6 位" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirm">
          <el-input v-model="form.confirm" type="password" show-password placeholder="再次输入密码"
                    @keyup.enter="submit" />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" placeholder="展示昵称（可选，默认用户名）" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="邮箱（可选）" />
        </el-form-item>
        <el-button type="primary" size="large" class="auth-btn" :loading="loading" @click="submit">
          注 册
        </el-button>
        <div class="auth-footer">
          已有账号？
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
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()

const formRef = ref()
const loading = ref(false)
const form = reactive({ username: '', password: '', confirm: '', nickname: '', email: '' })

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { max: 64, message: '用户名最长 64 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 64, message: '密码长度需在 6 到 64 位之间', trigger: 'blur' }
  ],
  confirm: [
    {
      validator: (_rule, value, callback) => {
        if (value !== form.password) callback(new Error('两次输入的密码不一致'))
        else callback()
      },
      trigger: 'blur'
    }
  ],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
}

async function submit() {
  await formRef.value.validate()
  loading.value = true
  try {
    await auth.register({
      username: form.username,
      password: form.password,
      nickname: form.nickname || undefined,
      email: form.email || undefined
    })
    ElMessage.success('注册成功，请登录')
    router.push('/login')
  } finally {
    loading.value = false
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
