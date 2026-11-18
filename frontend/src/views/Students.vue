<template>
  <el-card>
    <div class="toolbar">
      <el-input v-model="query.keyword" placeholder="学号 / 姓名" clearable style="width: 200px"
                @keyup.enter="search" />
      <el-input v-model="query.className" placeholder="班级" clearable style="width: 200px"
                @keyup.enter="search" />
      <el-button type="primary" @click="search">查询</el-button>
      <el-button v-if="canEdit" type="success" @click="openCreate">新增学生</el-button>
    </div>

    <el-table :data="list" stripe v-loading="loading">
      <el-table-column prop="studentNo" label="学号" width="120" />
      <el-table-column prop="name" label="姓名" width="110" />
      <el-table-column prop="gender" label="性别" width="80" />
      <el-table-column prop="className" label="班级" min-width="140" />
      <el-table-column prop="phone" label="电话" width="140" />
      <el-table-column prop="email" label="邮箱" min-width="180" />
      <el-table-column label="账号" width="100">
        <template #default="{ row }">
          <el-tag :type="row.hasAccount ? 'success' : 'info'">
            {{ row.hasAccount ? '已开通' : '未开通' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="canEdit" label="操作" width="230" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="warning" :disabled="row.hasAccount" @click="openAccount(row)">
            开通账号
          </el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination class="pager" layout="total, prev, pager, next" :total="total"
                   :page-size="query.size" :current-page="query.page"
                   @current-change="onPageChange" />
  </el-card>

  <el-dialog v-model="dialogVisible" :title="form.id ? '编辑学生' : '新增学生'" width="520px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
      <el-form-item label="学号" prop="studentNo"><el-input v-model="form.studentNo" /></el-form-item>
      <el-form-item label="姓名" prop="name"><el-input v-model="form.name" /></el-form-item>
      <el-form-item label="性别">
        <el-radio-group v-model="form.gender">
          <el-radio value="男">男</el-radio>
          <el-radio value="女">女</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="班级"><el-input v-model="form.className" /></el-form-item>
      <el-form-item label="电话"><el-input v-model="form.phone" /></el-form-item>
      <el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { studentApi } from '../api'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const canEdit = computed(() => auth.role === 'ADMIN')

const query = reactive({ keyword: '', className: '', page: 1, size: 10 })
const list = ref([])
const total = ref(0)
const loading = ref(false)
const dialogVisible = ref(false)
const formRef = ref()
const form = reactive({
  id: null, studentNo: '', name: '', gender: '男', className: '', phone: '', email: ''
})
const rules = {
  studentNo: [{ required: true, message: '请输入学号', trigger: 'blur' }],
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }]
}

async function load() {
  loading.value = true
  try {
    const data = await studentApi.page(query)
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

function resetForm() {
  Object.assign(form, {
    id: null, studentNo: '', name: '', gender: '男', className: '', phone: '', email: ''
  })
}

function openCreate() {
  resetForm()
  dialogVisible.value = true
}

function openEdit(row) {
  resetForm()
  Object.assign(form, {
    id: row.id, studentNo: row.studentNo, name: row.name, gender: row.gender,
    className: row.className, phone: row.phone, email: row.email
  })
  dialogVisible.value = true
}

async function save() {
  await formRef.value.validate()
  if (form.id) {
    await studentApi.update(form.id, form)
  } else {
    await studentApi.create(form)
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  await load()
}

async function openAccount(row) {
  await ElMessageBox.confirm(
    `确定为「${row.name}」开通登录账号吗？用户名与初始密码均为学号。`, '提示', { type: 'warning' }
  )
  await studentApi.openAccount(row.id)
  ElMessage.success('账号已开通，初始密码 123456')
  await load()
}

async function remove(row) {
  await ElMessageBox.confirm(
    `删除学生「${row.name}」将同时删除其成绩与账号，确定继续吗？`, '警告', { type: 'warning' }
  )
  await studentApi.remove(row.id)
  ElMessage.success('已删除')
  await load()
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
