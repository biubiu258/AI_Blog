import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const routes = [
  {
    path: '/',
    component: () => import('../components/Layout.vue'),
    children: [
      { path: '', name: 'home', component: () => import('../views/HomeView.vue') },
      {
        path: 'article/:id',
        name: 'article-detail',
        component: () => import('../views/ArticleDetailView.vue')
      },
      {
        // 创作溯源页：公开访问，draft/私享文章由后端返回 404
        path: 'article/:id/source',
        name: 'article-source',
        component: () => import('../views/ArticleSourceView.vue')
      },
      {
        path: 'user/:id',
        name: 'user-profile',
        component: () => import('../views/UserProfileView.vue')
      },
      {
        path: 'write',
        name: 'write',
        component: () => import('../views/WriteView.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'write/:id',
        name: 'write-edit',
        component: () => import('../views/WriteView.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('../views/DashboardView.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'ranking',
        name: 'ranking',
        component: () => import('../views/RankingView.vue')
      },
      {
        path: 'prose',
        name: 'prose',
        component: () => import('../views/ProseView.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'my',
        name: 'my-articles',
        component: () => import('../views/MyArticlesView.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'notifications',
        name: 'notifications',
        component: () => import('../views/NotificationsView.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'profile',
        name: 'profile',
        component: () => import('../views/ProfileView.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'series',
        name: 'series-list',
        component: () => import('../views/SeriesListView.vue'),
        meta: { requiresAuth: true }
      },
      {
        // 连载详情公开，未登录用户可浏览目录
        path: 'series/:id',
        name: 'series-detail',
        component: () => import('../views/SeriesDetailView.vue')
      },
      {
        path: 'streak',
        name: 'streak',
        component: () => import('../views/StreakView.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'curator',
        name: 'curator',
        component: () => import('../views/CuratorView.vue'),
        meta: { requiresAuth: true }
      },
      {
        // 技术板块公开访问
        path: 'tech',
        name: 'tech-list',
        component: () => import('../views/TechListView.vue')
      },
      {
        path: 'tech/glossary',
        name: 'tech-glossary',
        component: () => import('../views/GlossaryView.vue')
      },
      {
        path: 'admin',
        name: 'admin',
        component: () => import('../views/AdminView.vue'),
        meta: { requiresAuth: true, requiresAdmin: true }
      }
    ]
  },
  { path: '/login', name: 'login', component: () => import('../views/LoginView.vue') },
  { path: '/register', name: 'register', component: () => import('../views/RegisterView.vue') },
  {
    path: '/forgot-password',
    name: 'forgot-password',
    component: () => import('../views/ForgotPasswordView.vue')
  },
  {
    // 危机关怀页：独立全屏、不套 Layout、未登录可开
    path: '/care/crisis',
    name: 'care-crisis',
    component: () => import('../care/CrisisView.vue')
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.requiresAuth && !auth.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.requiresAdmin && !auth.isAdminLike) {
    return { name: 'home' }
  }
  return true
})

export default router
