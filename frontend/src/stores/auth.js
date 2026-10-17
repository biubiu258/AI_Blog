import { defineStore } from 'pinia'
import { authApi, userApi } from '../api'

/**
 * 认证状态：令牌与用户信息持久化到 localStorage。
 */
export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    user: JSON.parse(localStorage.getItem('user') || 'null')
  }),
  getters: {
    isLoggedIn: (s) => !!s.token,
    isAdmin: (s) => s.user?.role === 'ADMIN'
  },
  actions: {
    async login(payload) {
      const data = await authApi.login(payload)
      this.token = data.token
      this.user = data.user
      localStorage.setItem('token', data.token)
      localStorage.setItem('user', JSON.stringify(data.user))
      await this.refreshMe()
      return data.user
    },
    async register(payload) {
      return authApi.register(payload)
    },
    async refreshMe() {
      if (!this.token) return
      try {
        const user = await userApi.me()
        this.user = user
        localStorage.setItem('user', JSON.stringify(user))
      } catch {
        /* 令牌失效时由 request 拦截器统一处理 */
      }
    },
    async updateProfileLocal(patch) {
      if (this.user) {
        this.user = { ...this.user, ...patch }
        localStorage.setItem('user', JSON.stringify(this.user))
      }
    },
    async logout() {
      try {
        await authApi.logout()
      } catch {
        /* 后端无状态登出，失败也继续清理 */
      }
      this.token = ''
      this.user = null
      localStorage.removeItem('token')
      localStorage.removeItem('user')
    }
  }
})
