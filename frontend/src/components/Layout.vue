<template>
  <div class="layout">
    <header class="masthead">
      <div class="masthead-inner">
        <div class="brand" @click="$router.push('/')">
          <div class="seal">文</div>
          <div class="brand-text">
            <div class="brand-name">AI 发文博客</div>
            <div class="brand-sub">AI-ASSISTED WRITING JOURNAL</div>
          </div>
        </div>

        <nav class="nav">
          <router-link to="/" class="nav-item" :class="{ active: activeMenu === '/' }">
            首页
          </router-link>
          <router-link v-if="auth.isLoggedIn" to="/write" class="nav-item"
                       :class="{ active: activeMenu === '/write' }">
            写作
          </router-link>
          <router-link v-if="auth.isLoggedIn" to="/prose" class="nav-item"
                       :class="{ active: activeMenu === '/prose' }">
            散文
          </router-link>
          <router-link v-if="auth.isLoggedIn" to="/my" class="nav-item"
                       :class="{ active: activeMenu === '/my' }">
            我的文章
          </router-link>
          <router-link v-if="auth.isAdminLike" to="/admin" class="nav-item"
                       :class="{ active: activeMenu === '/admin' }">
            管理后台
          </router-link>
        </nav>

        <div class="header-right">
          <template v-if="auth.isLoggedIn">
            <el-dropdown @command="onCommand">
              <span class="user-entry">
                <el-avatar :size="30" :src="auth.user?.avatarUrl || undefined">
                  {{ (auth.user?.nickname || auth.user?.username || 'U').slice(0, 1) }}
                </el-avatar>
                <span class="user-name">{{ auth.user?.nickname || auth.user?.username }}</span>
                <el-icon><ArrowDown /></el-icon>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                  <el-dropdown-item command="my">我的文章</el-dropdown-item>
                  <el-dropdown-item v-if="auth.isAdminLike" command="admin">管理后台</el-dropdown-item>
                  <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
          <template v-else>
            <span class="ink-link login-link" @click="$router.push('/login')">登录</span>
            <el-button type="primary" size="small" @click="$router.push('/register')">
              注册
            </el-button>
          </template>
        </div>
      </div>
    </header>

    <main class="layout-main">
      <router-view />
    </main>

    <footer class="colophon">
      <span>以 AI 为笔 · 以文会友</span>
      <span class="colophon-dot">◆</span>
      <span>AI 辅助发文博客系统</span>
    </footer>

    <!-- 人文关怀呈现组件：发布响应经 careBus 分发（low 信笺 / mid 短笺） -->
    <CareNote />
    <CareDialog />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import CareNote from '../care/CareNote.vue'
import CareDialog from '../care/CareDialog.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const activeMenu = computed(() => '/' + (route.path.split('/')[1] || ''))

async function onCommand(command) {
  if (command === 'logout') {
    await auth.logout()
    router.push('/')
  } else {
    router.push('/' + command)
  }
}
</script>

<style scoped>
.layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

/* ---------- 刊头 ---------- */
.masthead {
  border-top: 3px solid var(--ink);
  border-bottom: 1px solid var(--ink);
  position: relative;
}
/* 刊头下双细线 */
.masthead::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -5px;
  border-bottom: 1px solid var(--hairline-dark);
  pointer-events: none;
}

.masthead-inner {
  max-width: 1080px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  gap: 32px;
  min-height: 72px;
  padding: 0 20px;
}

/* 印章 + 报名 */
.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  white-space: nowrap;
  user-select: none;
}
.seal {
  width: 36px;
  height: 36px;
  background: var(--cinnabar);
  color: #fdfbf4;
  font-size: 21px;
  font-weight: 900;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: inset 0 0 0 2px rgba(253, 251, 244, 0.35),
    2px 2px 0 rgba(38, 36, 30, 0.15);
  transition: transform 0.25s ease;
}
.brand:hover .seal {
  transform: rotate(-4deg);
}
.brand-name {
  font-size: 21px;
  font-weight: 900;
  letter-spacing: 0.14em;
  line-height: 1.2;
}
.brand-sub {
  font-size: 9px;
  letter-spacing: 0.3em;
  color: var(--ink-faint);
  margin-top: 2px;
}

/* ---------- 导航：文字链 + 朱砂短线 ---------- */
.nav {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 6px;
  overflow-x: auto;
  scrollbar-width: none;
}
.nav::-webkit-scrollbar {
  display: none;
}
.nav-item {
  position: relative;
  padding: 8px 14px;
  font-size: 15px;
  letter-spacing: 0.18em;
  color: var(--ink-soft);
  text-decoration: none;
  white-space: nowrap;
  transition: color 0.2s ease;
}
.nav-item::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 0;
  width: 0;
  height: 2px;
  background: var(--cinnabar);
  transform: translateX(-50%);
  transition: width 0.25s ease;
}
.nav-item:hover {
  color: var(--cinnabar);
}
.nav-item.active {
  color: var(--ink);
  font-weight: 700;
}
.nav-item.active::after {
  width: 1.6em;
}

/* ---------- 右侧用户区 ---------- */
.header-right {
  display: flex;
  align-items: center;
  gap: 14px;
  white-space: nowrap;
}
.login-link {
  font-size: 14px;
  letter-spacing: 0.15em;
}
.user-entry {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: var(--ink);
}
.user-name {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
  letter-spacing: 0.05em;
}

.layout-main {
  flex: 1;
}

/* ---------- 版记 ---------- */
.colophon {
  margin-top: 48px;
  padding: 20px 16px 26px;
  border-top: 1px solid var(--hairline);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: var(--ink-faint);
  font-size: 12.5px;
  letter-spacing: 0.25em;
}
.colophon-dot {
  color: var(--cinnabar);
  font-size: 8px;
}

@media (max-width: 720px) {
  .masthead-inner {
    gap: 14px;
    min-height: 64px;
  }
  .brand-sub {
    display: none;
  }
  .brand-name {
    font-size: 17px;
  }
  .nav-item {
    padding: 8px 9px;
    font-size: 14px;
  }
}
</style>
