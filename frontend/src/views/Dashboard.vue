<template>
  <div v-if="auth.role === 'STUDENT'">
    <el-row :gutter="16">
      <el-col :span="8">
        <el-card>
          <div class="stat"><span>已修课程</span><strong>{{ myCourses.length }}</strong></div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card>
          <div class="stat"><span>平均总评</span><strong>{{ averageScore }}</strong></div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="block">
      <template #header>我的课程总评</template>
      <el-table :data="myCourses" stripe>
        <el-table-column prop="courseName" label="课程" />
        <el-table-column prop="totalScore" label="总评" width="120" />
        <el-table-column label="排名" width="160">
          <template #default="{ row }">{{ row.rank }} / {{ row.studentCount }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>

  <div v-else>
    <el-row :gutter="16">
      <el-col v-for="card in cards" :key="card.label" :span="6">
        <el-card>
          <div class="stat"><span>{{ card.label }}</span><strong>{{ card.value }}</strong></div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="block">
      <template #header>
        <div class="card-header">
          <span>分数段分布</span>
          <div class="selects">
            <el-select v-model="courseId" placeholder="课程" style="width: 180px" @change="loadExams">
              <el-option v-for="c in courses" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
            <el-select v-model="examId" placeholder="考试" style="width: 180px" @change="loadDistribution">
              <el-option v-for="e in exams" :key="e.id" :label="e.name" :value="e.id" />
            </el-select>
          </div>
        </div>
      </template>
      <BaseChart :option="distributionOption" height="320px" />
    </el-card>

    <el-card class="block">
      <template #header>课程平均分 / 及格率对比</template>
      <BaseChart :option="comparisonOption" height="340px" />
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { analysisApi, courseApi, examApi, scoreApi } from '../api'
import { useAuthStore } from '../stores/auth'
import BaseChart from '../components/BaseChart.vue'

const auth = useAuthStore()
const overview = ref({ studentCount: 0, courseCount: 0, avgScore: 0, passRate: 0 })
const comparison = ref([])
const courses = ref([])
const exams = ref([])
const courseId = ref(null)
const examId = ref(null)
const distribution = ref([])
const myCourses = ref([])

const cards = computed(() => [
  { label: '学生总数', value: overview.value.studentCount },
  { label: '课程总数', value: overview.value.courseCount },
  { label: '全校平均分', value: overview.value.avgScore },
  { label: '及格率', value: `${overview.value.passRate}%` }
])

const averageScore = computed(() => {
  if (!myCourses.value.length) return '0.0'
  const sum = myCourses.value.reduce((acc, course) => acc + Number(course.totalScore || 0), 0)
  return (sum / myCourses.value.length).toFixed(1)
})

const distributionOption = computed(() => ({
  tooltip: { trigger: 'item' },
  legend: { bottom: 0 },
  series: [{
    type: 'pie',
    radius: ['40%', '65%'],
    data: distribution.value.map(item => ({ name: item.name, value: item.count }))
  }]
}))

const comparisonOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['平均分', '及格率'] },
  xAxis: { type: 'category', data: comparison.value.map(item => item.courseName) },
  yAxis: [
    { type: 'value', name: '平均分', max: 100 },
    { type: 'value', name: '及格率(%)', max: 100 }
  ],
  series: [
    { name: '平均分', type: 'bar', data: comparison.value.map(item => item.avgScore) },
    { name: '及格率', type: 'line', yAxisIndex: 1, data: comparison.value.map(item => item.passRate) }
  ]
}))

async function loadExams() {
  exams.value = await examApi.list(courseId.value)
  examId.value = exams.value[0]?.id ?? null
  await loadDistribution()
}

async function loadDistribution() {
  distribution.value = examId.value ? await analysisApi.distribution(examId.value) : []
}

onMounted(async () => {
  if (auth.role === 'STUDENT') {
    myCourses.value = await scoreApi.myCourses()
    return
  }
  overview.value = await analysisApi.overview()
  comparison.value = await analysisApi.courseComparison()
  courses.value = await courseApi.list()
  if (courses.value.length) {
    courseId.value = courses.value[0].id
    await loadExams()
  }
})
</script>

<style scoped>
.stat {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.stat span {
  color: #909399;
  font-size: 14px;
}
.stat strong {
  font-size: 28px;
  color: #303133;
}
.block {
  margin-top: 16px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.selects {
  display: flex;
  gap: 8px;
}
</style>
