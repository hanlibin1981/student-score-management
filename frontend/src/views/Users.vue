<template>
  <el-card>
    <div class="toolbar">
      <el-select v-model="query.role" placeholder="角色" clearable style="width: 140px" @change="search">
        <el-option label="管理员" value="ADMIN" />
        <el-option label="教师" value="TEACHER" />
        <el-option label="学生" value="STUDENT" />
      </el-select>
      <el-input v-model="query.keyword" placeholder="用户名 / 姓名" clearable style="width: 220px"
                @keyup.enter="search" />
      <el-button type="primary" @click="search">查询</el-button>
      <el-button type="success" @click="openCreate">新增教师</el-button>
    </div>

    <el-table :data="list" stripe v-loading="loading">
      <el-table-column prop="username" label="用户名" width="150" />
      <el-table-column prop="realName" label="姓名" width="150" />
      <el-table-column label="角色" width="100">
        <template #default="{ row }">{{ roleLabel(row.role) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button link :type="row.enabled ? 'warning' : 'success'" @click="toggle(row)">
            {{ row.enabled ? '禁用' : '启用' }}
          </el-button>
          <el-button link type="primary" @click="resetPassword(row)">重置密码</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination class="pager" layout="total, prev, pager, next" :total="total"
                   :page-size="query.size" :current-page="query.page"
                   @current-change="onPageChange" />
  </el-card>

  <el-dialog v-model="dialogVisible" title="新增教师" width="480px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="用户名" prop="username"><el-input v-model="form.username" /></el-form-item>
      <el-form-item label="姓名" prop="realName"><el-input v-model="form.realName" /></el-form-item>
      <el-form-item label="初始密码">
        <el-input v-model="form.password" placeholder="留空则使用 123456" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { userApi } from '../api'

const query = reactive({ role: '', keyword: '', page: 1, size: 10 })
const list = ref([])
const total = ref(0)
const loading = ref(false)
const dialogVisible = ref(false)
const formRef = ref()
const form = reactive({ username: '', realName: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }]
}

const roleLabel = role => ({ ADMIN: '管理员', TEACHER: '教师', STUDENT: '学生' }[role] || role)

async function load() {
  loading.value = true
  try {
    const data = await userApi.page(query)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  load()
}

function onPageChange(page) {
  query.page = page
  load()
}

function openCreate() {
  Object.assign(form, { username: '', realName: '', password: '' })
  dialogVisible.value = true
}

async function save() {
  await formRef.value.validate()
  await userApi.create(form)
  ElMessage.success('创建成功')
  dialogVisible.value = false
  await load()
}

async function toggle(row) {
  await userApi.setStatus(row.id, !row.enabled)
  ElMessage.success('状态已更新')
  await load()
}

async function resetPassword(row) {
  await ElMessageBox.confirm(`确定将「${row.realName}」的密码重置为 123456 吗？`, '提示',
    { type: 'warning' })
  await userApi.resetPassword(row.id)
  ElMessage.success('密码已重置')
}

onMounted(load)
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
