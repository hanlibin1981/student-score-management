import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { canAccess } from '../utils/permission'
import Login from '../views/Login.vue'
import MainLayout from '../layouts/MainLayout.vue'
import Dashboard from '../views/Dashboard.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: Login },
    {
      path: '/',
      component: MainLayout,
      redirect: '/dashboard',
      children: [
        { path: 'dashboard', component: Dashboard },
        { path: 'students', component: () => import('../views/Students.vue'), meta: { roles: ['ADMIN', 'TEACHER'] } },
        { path: 'courses', component: () => import('../views/Courses.vue'), meta: { roles: ['ADMIN'] } },
        { path: 'users', component: () => import('../views/Users.vue'), meta: { roles: ['ADMIN'] } },
        { path: 'exams', component: () => import('../views/Exams.vue'), meta: { roles: ['ADMIN', 'TEACHER'] } },
        { path: 'scores', component: () => import('../views/Scores.vue'), meta: { roles: ['ADMIN', 'TEACHER'] } },
        { path: 'analysis', component: () => import('../views/Analysis.vue'), meta: { roles: ['ADMIN', 'TEACHER'] } },
        { path: 'my-scores', component: () => import('../views/MyScores.vue'), meta: { roles: ['STUDENT'] } }
      ]
    },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
  ]
})

router.beforeEach(to => {
  const token = localStorage.getItem('token')
  const user = JSON.parse(localStorage.getItem('user') || 'null')
  if (to.path === '/login') {
    return token ? '/dashboard' : true
  }
  if (!token || !user) {
    return '/login'
  }
  if (!canAccess(user.role, to.meta.roles)) {
    ElMessage.warning('无权访问该页面')
    return '/dashboard'
  }
  return true
})

export default router
