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
        path: 'profile',
        name: 'profile',
        component: () => import('../views/ProfileView.vue'),
        meta: { requiresAuth: true }
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
  if (to.meta.requiresAdmin && !auth.isAdmin) {
    return { name: 'home' }
  }
  return true
})

export default router
