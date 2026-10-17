import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发环境代理：/api 与 /uploads 转发到 Spring Boot，
// SSE 长连接同样走此代理，生产环境由 Nginx 承担同样职责。
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
