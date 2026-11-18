<template>
  <el-container class="layout">
    <el-aside width="220px">
      <div class="brand">成绩管理系统</div>
      <el-menu :default-active="route.path" router background-color="#1f2d3d"
               text-color="#bfcbd9" active-text-color="#409EFF">
        <el-menu-item v-for="item in menus" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span class="welcome">{{ auth.user?.realName || auth.user?.username }}</span>
        <el-tag size="small">{{ roleLabel }}</el-tag>
        <el-button link type="primary" @click="logout">退出登录</el-button>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const allMenus = [
  { path: '/dashboard', title: '仪表盘', icon: 'Odometer', roles: ['ADMIN', 'TEACHER', 'STUDENT'] },
  { path: '/students', title: '学生管理', icon: 'User', roles: ['ADMIN', 'TEACHER'] },
  { path: '/courses', title: '课程管理', icon: 'Reading', roles: ['ADMIN'] },
  { path: '/users', title: '用户管理', icon: 'Avatar', roles: ['ADMIN'] },
  { path: '/exams', title: '考试管理', icon: 'Calendar', roles: ['ADMIN', 'TEACHER'] },
  { path: '/scores', title: '成绩录入', icon: 'EditPen', roles: ['ADMIN', 'TEACHER'] },
  { path: '/analysis', title: '成绩分析', icon: 'DataAnalysis', roles: ['ADMIN', 'TEACHER'] },
  { path: '/my-scores', title: '我的成绩', icon: 'Tickets', roles: ['STUDENT'] }
]

const menus = computed(() => allMenus.filter(item => item.roles.includes(auth.role)))
const roleLabel = computed(
  () => ({ ADMIN: '管理员', TEACHER: '教师', STUDENT: '学生' }[auth.role] || '')
)

function logout() {
  auth.logout()
  router.push('/login')
}
</script>

<style scoped>
.layout {
  min-height: 100vh;
}
.brand {
  height: 60px;
  line-height: 60px;
  text-align: center;
  color: #fff;
  font-weight: 600;
  background: #17222f;
}
.el-aside {
  background: #1f2d3d;
}
.el-menu {
  border-right: none;
}
.header {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
}
.welcome {
  color: #303133;
}
</style>
