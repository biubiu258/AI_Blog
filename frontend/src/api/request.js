import axios from 'axios'
import { ElMessage } from 'element-plus'

/**
 * 统一 axios 实例：
 * - 请求自动携带 Authorization: Bearer <jwt>
 * - 响应按 { code, message, data } 解包，code !== 0 视为业务失败
 * - code 1002（未登录/令牌失效）自动清理并跳转登录页
 */
const request = axios.create({
  baseURL: '/api',
  timeout: 30000
})

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code !== 0) {
        ElMessage.error(body.message || '请求失败')
        return Promise.reject(body)
      }
      return body.data
    }
    return body
  },
  (error) => {
    const status = error.response?.status
    const body = error.response?.data
    const code = body?.code
    const message = body?.message

    if (status === 401 || code === 1002) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      ElMessage.error(message || '登录状态已失效，请重新登录')
      if (!location.pathname.startsWith('/login')) {
        setTimeout(() => {
          location.href = '/login?redirect=' + encodeURIComponent(location.pathname)
        }, 400)
      }
    } else if (message) {
      ElMessage.error(message)
    } else {
      ElMessage.error(status ? `请求失败（${status}）` : '网络异常，请稍后重试')
    }
    return Promise.reject(error)
  }
)

export default request
