<template>
  <el-card>
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="课程名称" clearable style="width: 220px"
                @keyup.enter="load" />
      <el-button type="primary" @click="load">查询</el-button>
      <el-button type="success" @click="openCreate">新增课程</el-button>
    </div>

    <el-table :data="list" stripe v-loading="loading">
      <el-table-column prop="courseCode" label="课程编号" width="120" />
      <el-table-column prop="name" label="课程名称" min-width="160" />
      <el-table-column prop="credit" label="学分" width="80" />
      <el-table-column prop="semester" label="学期" width="140" />
      <el-table-column prop="teacherName" label="任课教师" width="120" />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>

  <el-dialog v-model="dialogVisible" :title="form.id ? '编辑课程' : '新增课程'" width="520px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="课程编号" prop="courseCode"><el-input v-model="form.courseCode" /></el-form-item>
      <el-form-item label="课程名称" prop="name"><el-input v-model="form.name" /></el-form-item>
      <el-form-item label="学分">
        <el-input-number v-model="form.credit" :min="0" :max="20" :step="0.5" />
      </el-form-item>
      <el-form-item label="学期">
        <el-input v-model="form.semester" placeholder="如 2026-2027-1" />
      </el-form-item>
      <el-form-item label="任课教师">
        <el-select v-model="form.teacherId" placeholder="请选择教师" clearable style="width: 100%">
          <el-option v-for="t in teachers" :key="t.id" :label="t.realName" :value="t.id" />
        </el-select>
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
import { courseApi, userApi } from '../api'

const list = ref([])
const teachers = ref([])
const keyword = ref('')
const loading = ref(false)
const dialogVisible = ref(false)
const formRef = ref()
const form = reactive({
  id: null, courseCode: '', name: '', credit: 3, semester: '', teacherId: null
})
const rules = {
  courseCode: [{ required: true, message: '请输入课程编号', trigger: 'blur' }],
  name: [{ required: true, message: '请输入课程名称', trigger: 'blur' }]
}

async function load() {
  loading.value = true
  try {
    list.value = await courseApi.list({ keyword: keyword.value })
  } finally {
    loading.value = false
  }
}

async function loadTeachers() {
  const data = await userApi.page({ role: 'TEACHER', size: 100 })
  teachers.value = data.list
}

function resetForm() {
  Object.assign(form, {
    id: null, courseCode: '', name: '', credit: 3, semester: '', teacherId: null
  })
}

function openCreate() {
  resetForm()
  dialogVisible.value = true
}

function openEdit(row) {
  resetForm()
  Object.assign(form, {
    id: row.id, courseCode: row.courseCode, name: row.name,
    credit: row.credit, semester: row.semester, teacherId: row.teacherId
  })
  dialogVisible.value = true
}

async function save() {
  await formRef.value.validate()
  if (form.id) {
    await courseApi.update(form.id, form)
  } else {
    await courseApi.create(form)
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  await load()
}

async function remove(row) {
  await ElMessageBox.confirm(`确定删除课程「${row.name}」吗？`, '提示', { type: 'warning' })
  await courseApi.remove(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(async () => {
  await Promise.all([load(), loadTeachers()])
})
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}
</style>
