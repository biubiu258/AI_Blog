import request from './request'

export const authApi = {
  register: (data) => request.post('/auth/register', data),
  login: (data) => request.post('/auth/login', data),
  logout: () => request.post('/auth/logout'),
  captcha: () => request.get('/auth/captcha'),
  sendEmailCode: () => request.post('/auth/email/send-code'),
  verifyEmail: (data) => request.post('/auth/email/verify', data),
  sendResetCode: (data) => request.post('/auth/password/reset-code', data),
  resetPassword: (data) => request.post('/auth/password/reset', data)
}

export const userApi = {
  me: () => request.get('/user/me'),
  updateMe: (data) => request.put('/user/me', data),
  getPreference: () => request.get('/user/preference'),
  updatePreference: (data) => request.put('/user/preference', data),
  profile: (id) => request.get(`/users/${id}`),
  follow: (id) => request.post(`/users/${id}/follow`),
  followers: (id) => request.get(`/users/${id}/followers`),
  following: (id) => request.get(`/users/${id}/following`),
  articles: (id, params) => request.get(`/users/${id}/articles`, { params }),
  favorites: (id, params) => request.get(`/users/${id}/favorites`, { params })
}

export const articleApi = {
  list: (params) => request.get('/articles', { params }),
  mine: (params) => request.get('/articles/mine', { params }),
  hot: (params) => request.get('/articles/hot', { params }),
  detail: (id) => request.get(`/articles/${id}`),
  create: (data) => request.post('/articles', data),
  update: (id, data) => request.put(`/articles/${id}`, data),
  remove: (id) => request.delete(`/articles/${id}`),
  like: (id) => request.post(`/articles/${id}/like`),
  favorite: (id) => request.post(`/articles/${id}/favorite`),
  view: (id) => request.get(`/articles/${id}/view`),
  versions: (id) => request.get(`/articles/${id}/versions`),
  version: (id, versionId) => request.get(`/articles/${id}/versions/${versionId}`)
}

export const tagApi = {
  list: () => request.get('/tags'),
  articles: (id, params) => request.get(`/tags/${id}/articles`, { params })
}

export const notificationApi = {
  list: (params) => request.get('/notifications', { params }),
  unreadCount: () => request.get('/notifications/unread-count'),
  markRead: (ids) => request.post('/notifications/read', { ids })
}

export const statsApi = {
  author: (id) => request.get(`/stats/author/${id}`)
}

export const reportApi = {
  create: (data) => request.post('/reports', data)
}

export const searchApi = {
  search: (data) => request.post('/ai/search', data)
}

export const categoryApi = {
  list: () => request.get('/categories'),
  create: (data) => request.post('/categories', data),
  rename: (id, data) => request.put(`/categories/${id}`, data),
  remove: (id) => request.delete(`/categories/${id}`)
}

export const commentApi = {
  listByArticle: (articleId) => request.get(`/articles/${articleId}/comments`),
  create: (articleId, data) => request.post(`/articles/${articleId}/comments`, data),
  remove: (id) => request.delete(`/comments/${id}`)
}

export const fileApi = {
  upload: (formData) =>
    request.post('/files/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
}

export const adminApi = {
  listUsers: (params) => request.get('/admin/users', { params }),
  setUserStatus: (id, data) => request.put(`/admin/users/${id}/status`, data),
  setUserRole: (id, role) => request.put(`/admin/users/${id}/role`, { role }),
  listArticles: (params) => request.get('/admin/articles', { params }),
  batchUnpublishArticles: (ids) =>
    request.put('/admin/articles/batch-status', { ids, status: 'draft' }),
  batchDeleteArticles: (ids) =>
    request.delete('/admin/articles/batch', { params: { ids: ids.join(',') } }),
  listComments: (params) => request.get('/admin/comments', { params }),
  batchDeleteComments: (ids) =>
    request.delete('/admin/comments/batch', { params: { ids: ids.join(',') } }),
  listReports: (params) => request.get('/admin/reports', { params }),
  pendingReports: () => request.get('/admin/reports/pending-count'),
  handleReport: (id, action) => request.put(`/admin/reports/${id}/handle`, { action }),
  aiUsage: (params) => request.get('/admin/ai/usage', { params }),
  // AI 输出审计：列表 / 详情 / 待审数 / 人工审核
  aiOutputs: (params) => request.get('/admin/ai/outputs', { params }),
  aiOutput: (id) => request.get(`/admin/ai/outputs/${id}`),
  pendingAiOutputs: () => request.get('/admin/ai/outputs/pending-count'),
  reviewAiOutput: (id, data) => request.put(`/admin/ai/outputs/${id}/review`, data),
  listLogs: (params) => request.get('/admin/logs', { params })
}
