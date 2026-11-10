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
  version: (id, versionId) => request.get(`/articles/${id}/versions/${versionId}`),
  aiDisclosureSuggest: (id) => request.get(`/articles/${id}/ai-disclosure/suggest`),
  updateAiDisclosure: (id, data) => request.patch(`/articles/${id}/ai-disclosure`, data),
  exportMarkdown: (id) => request.get(`/articles/${id}/export`, { responseType: 'text' }),
  trace: (id) => request.get(`/articles/${id}/trace`),
  readComplete: (id) => request.post(`/articles/${id}/read-complete`)
}

export const curatorApi = {
  me: () => request.get('/curator/me'),
  slots: (params) => request.get('/curator/slots', { params }),
  candidates: () => request.get('/curator/candidates'),
  recommend: (data) => request.post('/curator/slots', data),
  removeSlot: (id) => request.delete(`/curator/slots/${id}`)
}

export const seriesApi = {
  mine: () => request.get('/series/mine'),
  subscribed: () => request.get('/series/subscribed'),
  detail: (id) => request.get(`/series/${id}`),
  create: (data) => request.post('/series', data),
  update: (id, data) => request.put(`/series/${id}`, data),
  remove: (id) => request.delete(`/series/${id}`),
  addArticle: (id, data) => request.post(`/series/${id}/articles`, data),
  removeArticle: (id, articleId) => request.delete(`/series/${id}/articles/${articleId}`),
  reorder: (id, articleIds) => request.put(`/series/${id}/order`, { articleIds }),
  subscribe: (id) => request.post(`/series/${id}/subscribe`),
  unsubscribe: (id) => request.delete(`/series/${id}/subscribe`)
}

export const streakApi = {
  summary: () => request.get('/streaks/summary'),
  makeup: () => request.post('/streaks/makeup')
}

export const campaignApi = {
  list: () => request.get('/campaigns')
}

export const techApi = {
  articles: (params) => request.get('/tech/articles', { params }),
  terms: (params) => request.get('/tech/terms', { params }),
  checkTerms: (text) => request.post('/tech/terms/check', { text }),
  wording: (data) => request.post('/tech/assist/wording', data)
}

export const techAdminApi = {
  docs: (params) => request.get('/admin/tech/docs', { params }),
  createDoc: (data) => request.post('/admin/tech/docs', data),
  uploadDoc: (formData) =>
    request.post('/admin/tech/docs/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    }),
  updateDoc: (id, data) => request.put(`/admin/tech/docs/${id}`, data),
  removeDoc: (id) => request.delete(`/admin/tech/docs/${id}`),
  indexDoc: (id) => request.post(`/admin/tech/docs/${id}/index`),
  chunks: (id) => request.get(`/admin/tech/docs/${id}/chunks`),
  terms: (params) => request.get('/admin/tech/terms', { params }),
  createTerm: (data) => request.post('/admin/tech/terms', data),
  updateTerm: (id, data) => request.put(`/admin/tech/terms/${id}`, data),
  removeTerm: (id) => request.delete(`/admin/tech/terms/${id}`),
  importTerms: (items) => request.post('/admin/tech/terms/import', items),
  stats: () => request.get('/admin/tech/stats')
}

export const insightApi = {
  generate: () => request.post('/ai/insight')
}

export const badgeApi = {
  apply: () => request.post('/badges/apply'),
  mine: () => request.get('/badges/mine')
}

export const styleProfileApi = {
  list: () => request.get('/style-profiles'),
  create: (data) => request.post('/style-profiles', data),
  setDefault: (id) => request.put(`/style-profiles/${id}/default`),
  remove: (id) => request.delete(`/style-profiles/${id}`)
}

export const companionApi = {
  get: () => request.get('/ai/companion')
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
  contentLabels: (params) => request.get('/admin/content-labels', { params }),
  updateContentLabel: (articleId, data) =>
    request.put(`/admin/content-labels/${articleId}`, data),
  contentLabelLogs: (articleId) => request.get(`/admin/content-labels/${articleId}/logs`),
  badges: (params) => request.get('/admin/badges', { params }),
  reviewBadge: (id, data) => request.put(`/admin/badges/${id}`, data),
  listLogs: (params) => request.get('/admin/logs', { params }),
  curators: () => request.get('/admin/curators'),
  addCurator: (data) => request.post('/admin/curators', data),
  removeCurator: (id) => request.delete(`/admin/curators/${id}`),
  curationCandidates: () => request.get('/admin/curation/candidates'),
  campaigns: (params) => request.get('/admin/campaigns', { params }),
  createCampaign: (data) => request.post('/admin/campaigns', data),
  updateCampaign: (id, data) => request.put(`/admin/campaigns/${id}`, data),
  deleteCampaign: (id) => request.delete(`/admin/campaigns/${id}`),
  publishCampaign: (id) => request.post(`/admin/campaigns/${id}/publish`)
}
