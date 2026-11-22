<template>
  <el-card>
    <div class="toolbar">
      <el-select v-model="examName" placeholder="选择考试" style="width: 240px" @change="loadRows">
        <el-option v-for="option in sessions" :key="option.name" :label="sessionLabel(option)"
                   :value="option.name" />
      </el-select>
      <el-input v-model="className" placeholder="按班级筛选（可选）" clearable style="width: 200px"
                @keyup.enter="loadRows" />
      <el-button type="primary" @click="loadRows">刷新</el-button>
      <el-button type="success" :loading="saving" :disabled="!examName || !rows.length" @click="save">
        保存成绩
      </el-button>
    </div>

    <el-empty v-if="!loading && !sessions.length" description="暂无可录入的考试" />

    <el-table v-else :data="rows" stripe v-loading="loading" empty-text="暂无学生或成绩数据">
      <el-table-column prop="studentNo" label="学号" width="120" fixed />
      <el-table-column prop="studentName" label="姓名" width="120" fixed />
      <el-table-column prop="className" label="班级" width="150" fixed />
      <el-table-column v-for="exam in exams" :key="exam.examId" :label="exam.courseName"
                       min-width="160">
        <template #header>
          <div class="subject-header">
            <span>{{ exam.courseName }}</span>
            <small>{{ exam.examDate || '未设日期' }} · {{ exam.fullScore }}分</small>
          </div>
        </template>
        <template #default="{ row }">
          <el-input-number v-model="row.scores[exam.examId]" :min="0" :max="exam.fullScore"
                           :step="0.5" controls-position="right" style="width: 130px" />
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { examApi, scoreApi } from '../api'
import { toScoreItems } from '../utils/scoreSession'

const sessions = ref([])
const examName = ref(null)
const session = ref({ examName: null, exams: [], rows: [] })
const className = ref('')
const loading = ref(false)
const saving = ref(false)

const exams = computed(() => session.value.exams)
const rows = computed(() => session.value.rows)

function sessionLabel(option) {
  return option.examDate ? `${option.name}（${option.examDate}）` : option.name
}

async function loadSessions() {
  sessions.value = await examApi.sessions()
  if (!sessions.value.length) {
    session.value = { examName: null, exams: [], rows: [] }
    return
  }
  examName.value = sessions.value[0].name
  await loadRows()
}

async function loadRows() {
  if (!examName.value) {
    session.value = { examName: null, exams: [], rows: [] }
    return
  }
  loading.value = true
  try {
    const data = await scoreApi.session(examName.value, className.value || undefined)
    session.value = {
      examName: data.examName,
      exams: data.exams ?? [],
      rows: (data.rows ?? []).map(row => ({ ...row, scores: row.scores ?? {} }))
    }
  } finally {
    loading.value = false
  }
}

async function save() {
  const items = toScoreItems(rows.value, exams.value)
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

onMounted(loadSessions)
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.subject-header {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}
.subject-header small {
  color: #909399;
  font-weight: 400;
}
</style>
