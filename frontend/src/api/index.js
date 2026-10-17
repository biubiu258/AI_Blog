import request from './request'

export const authApi = {
  register: (data) => request.post('/auth/register', data),
  login: (data) => request.post('/auth/login', data),
  logout: () => request.post('/auth/logout')
}

export const userApi = {
  me: () => request.get('/user/me'),
  updateMe: (data) => request.put('/user/me', data),
  getPreference: () => request.get('/user/preference'),
  updatePreference: (data) => request.put('/user/preference', data)
}

export const articleApi = {
  list: (params) => request.get('/articles', { params }),
  mine: (params) => request.get('/articles/mine', { params }),
  detail: (id) => request.get(`/articles/${id}`),
  create: (data) => request.post('/articles', data),
  update: (id, data) => request.put(`/articles/${id}`, data),
  remove: (id) => request.delete(`/articles/${id}`)
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
  setUserStatus: (id, status) => request.put(`/admin/users/${id}/status`, { status })
}
