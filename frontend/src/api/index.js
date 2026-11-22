import request from './request'

export const authApi = {
  login: data => request.post('/auth/login', data),
  me: () => request.get('/auth/me'),
  changePassword: data => request.put('/auth/password', data)
}

export const studentApi = {
  page: params => request.get('/students', { params }),
  detail: id => request.get(`/students/${id}`),
  create: data => request.post('/students', data),
  update: (id, data) => request.put(`/students/${id}`, data),
  remove: id => request.delete(`/students/${id}`),
  openAccount: id => request.post(`/students/${id}/account`)
}

export const courseApi = {
  list: params => request.get('/courses', { params }),
  create: data => request.post('/courses', data),
  update: (id, data) => request.put(`/courses/${id}`, data),
  remove: id => request.delete(`/courses/${id}`)
}

export const examApi = {
  list: courseId => request.get('/exams', { params: { courseId } }),
  sessions: () => request.get('/exams/sessions'),
  create: data => request.post('/exams', data),
  update: (id, data) => request.put(`/exams/${id}`, data),
  remove: id => request.delete(`/exams/${id}`)
}

export const scoreApi = {
  byExam: (examId, className) => request.get('/scores', { params: { examId, className } }),
  session: (examName, className) => request.get('/scores/session', { params: { examName, className } }),
  saveBatch: items => request.post('/scores/batch', { items }),
  remove: id => request.delete(`/scores/${id}`),
  myCourses: () => request.get('/scores/my')
}

export const analysisApi = {
  overview: () => request.get('/analysis/overview'),
  distribution: examId => request.get('/analysis/distribution', { params: { examId } }),
  failing: (examId, threshold) => request.get('/analysis/failing', { params: { examId, threshold } }),
  courseComparison: () => request.get('/analysis/course-comparison'),
  trend: (studentId, courseId) => request.get('/analysis/trend', { params: { studentId, courseId } })
}

export const userApi = {
  page: params => request.get('/users', { params }),
  create: data => request.post('/users', data),
  setStatus: (id, enabled) => request.put(`/users/${id}/status`, { enabled }),
  resetPassword: id => request.put(`/users/${id}/password`)
}
