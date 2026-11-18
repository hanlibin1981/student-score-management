<template>
  <el-card>
    <div class="toolbar">
      <el-select v-model="courseId" placeholder="选择课程" style="width: 200px" @change="loadExams">
        <el-option v-for="c in courses" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-select v-model="examId" placeholder="选择考试" style="width: 200px" @change="loadRows">
        <el-option v-for="e in exams" :key="e.id" :label="e.name" :value="e.id" />
      </el-select>
      <el-input v-model="className" placeholder="按班级筛选（可选）" clearable style="width: 200px"
                @keyup.enter="loadRows" />
      <el-button type="primary" @click="loadRows">刷新</el-button>
      <el-button type="success" :loading="saving" :disabled="!examId" @click="save">保存成绩</el-button>
    </div>

    <el-table :data="rows" stripe v-loading="loading">
      <el-table-column prop="studentNo" label="学号" width="120" />
      <el-table-column prop="studentName" label="姓名" width="120" />
      <el-table-column prop="className" label="班级" min-width="150" />
      <el-table-column label="分数" width="200">
        <template #default="{ row }">
          <el-input-number v-model="row.score" :min="0" :max="fullScore" :step="0.5"
                           controls-position="right" style="width: 140px" />
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { courseApi, examApi, scoreApi } from '../api'

const courses = ref([])
const exams = ref([])
const courseId = ref(null)
const examId = ref(null)
const rows = ref([])
const className = ref('')
const loading = ref(false)
const saving = ref(false)

const fullScore = computed(
  () => exams.value.find(exam => exam.id === examId.value)?.fullScore ?? 100
)

async function loadCourses() {
  courses.value = await courseApi.list()
  if (courses.value.length) {
    courseId.value = courses.value[0].id
    await loadExams()
  }
}

async function loadExams() {
  exams.value = await examApi.list(courseId.value)
  examId.value = exams.value[0]?.id ?? null
  await loadRows()
}

async function loadRows() {
  if (!examId.value) {
    rows.value = []
    return
  }
  loading.value = true
  try {
    rows.value = await scoreApi.byExam(examId.value, className.value || undefined)
  } finally {
    loading.value = false
  }
}

async function save() {
  const items = rows.value
    .filter(row => row.score !== null && row.score !== undefined && row.score !== '')
    .map(row => ({ studentId: row.studentId, examId: examId.value, score: row.score }))
  if (!items.length) {
    ElMessage.warning('没有可保存的成绩')
    return
  }
  saving.value = true
  try {
    await scoreApi.saveBatch(items)
    ElMessage.success(`已保存 ${items.length} 条成绩`)
    await loadRows()
  } finally {
    saving.value = false
  }
}

onMounted(loadCourses)
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
</style>
