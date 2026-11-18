<template>
  <el-card>
    <div class="toolbar">
      <el-select v-model="courseId" placeholder="选择课程" style="width: 220px" @change="load">
        <el-option v-for="c in courses" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-button type="success" :disabled="!courseId" @click="openCreate">新增考试</el-button>
    </div>

    <el-table :data="list" stripe v-loading="loading">
      <el-table-column prop="name" label="考试名称" min-width="160" />
      <el-table-column prop="examDate" label="考试日期" width="140" />
      <el-table-column prop="fullScore" label="满分" width="100" />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>

  <el-dialog v-model="dialogVisible" :title="form.id ? '编辑考试' : '新增考试'" width="480px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="考试名称" prop="name"><el-input v-model="form.name" /></el-form-item>
      <el-form-item label="考试日期">
        <el-date-picker v-model="form.examDate" type="date" value-format="YYYY-MM-DD"
                        style="width: 100%" />
      </el-form-item>
      <el-form-item label="满分" prop="fullScore">
        <el-input-number v-model="form.fullScore" :min="1" :max="200" />
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
import { courseApi, examApi } from '../api'

const courses = ref([])
const courseId = ref(null)
const list = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const formRef = ref()
const form = reactive({ id: null, courseId: null, name: '', examDate: '', fullScore: 100 })
const rules = {
  name: [{ required: true, message: '请输入考试名称', trigger: 'blur' }],
  fullScore: [{ required: true, message: '请输入满分', trigger: 'change' }]
}

async function loadCourses() {
  courses.value = await courseApi.list()
  if (courses.value.length) {
    courseId.value = courses.value[0].id
    await load()
  }
}

async function load() {
  if (!courseId.value) {
    list.value = []
    return
  }
  loading.value = true
  try {
    list.value = await examApi.list(courseId.value)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, {
    id: null, courseId: courseId.value, name: '', examDate: '', fullScore: 100
  })
  dialogVisible.value = true
}

function openEdit(row) {
  Object.assign(form, {
    id: row.id, courseId: courseId.value, name: row.name,
    examDate: row.examDate, fullScore: row.fullScore
  })
  dialogVisible.value = true
}

async function save() {
  await formRef.value.validate()
  if (form.id) {
    await examApi.update(form.id, form)
  } else {
    await examApi.create(form)
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  await load()
}

async function remove(row) {
  await ElMessageBox.confirm(`删除考试「${row.name}」将同时删除其成绩，确定继续吗？`, '警告',
    { type: 'warning' })
  await examApi.remove(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(loadCourses)
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}
</style>
