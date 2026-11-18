<template>
  <el-card>
    <el-tabs v-model="tab">
      <el-tab-pane label="分数段分布" name="distribution">
        <div class="toolbar">
          <el-select v-model="courseId" placeholder="课程" style="width: 200px" @change="loadExams">
            <el-option v-for="c in courses" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
          <el-select v-model="examId" placeholder="考试" style="width: 200px" @change="loadDistribution">
            <el-option v-for="e in exams" :key="e.id" :label="e.name" :value="e.id" />
          </el-select>
        </div>
        <BaseChart :option="distributionOption" height="360px" />
      </el-tab-pane>

      <el-tab-pane label="课程对比" name="comparison">
        <BaseChart :option="comparisonOption" height="360px" />
        <el-table :data="comparison" stripe class="table">
          <el-table-column prop="courseName" label="课程" />
          <el-table-column prop="avgScore" label="平均分" width="140" />
          <el-table-column prop="passRate" label="及格率(%)" width="140" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="不及格预警" name="failing">
        <div class="toolbar">
          <el-select v-model="courseId" placeholder="课程" style="width: 200px" @change="loadExams">
            <el-option v-for="c in courses" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
          <el-select v-model="examId" placeholder="考试" style="width: 200px">
            <el-option v-for="e in exams" :key="e.id" :label="e.name" :value="e.id" />
          </el-select>
          <el-input-number v-model="threshold" :min="0" :max="100" :step="5" />
          <el-button type="primary" @click="loadFailing">查询</el-button>
        </div>
        <el-table :data="failing" stripe>
          <el-table-column prop="studentNo" label="学号" width="140" />
          <el-table-column prop="name" label="姓名" width="140" />
          <el-table-column prop="score" label="原始分" width="120" />
          <el-table-column prop="fullScore" label="满分" width="100" />
          <el-table-column label="折算分" width="120">
            <template #default="{ row }">
              {{ (Number(row.score) * 100 / Number(row.fullScore)).toFixed(1) }}
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </el-card>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { analysisApi, courseApi, examApi } from '../api'
import BaseChart from '../components/BaseChart.vue'

const tab = ref('distribution')
const courses = ref([])
const exams = ref([])
const courseId = ref(null)
const examId = ref(null)
const distribution = ref([])
const comparison = ref([])
const failing = ref([])
const threshold = ref(60)

const distributionOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  xAxis: { type: 'category', data: distribution.value.map(item => item.name) },
  yAxis: { type: 'value', name: '人数', minInterval: 1 },
  series: [{
    type: 'bar',
    barWidth: '50%',
    data: distribution.value.map(item => item.count)
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

async function loadFailing() {
  if (!examId.value) return
  failing.value = await analysisApi.failing(examId.value, threshold.value)
}

watch(tab, async value => {
  if (value === 'failing') {
    await loadFailing()
  } else if (value === 'distribution') {
    await loadDistribution()
  }
})

onMounted(async () => {
  comparison.value = await analysisApi.courseComparison()
  courses.value = await courseApi.list()
  if (courses.value.length) {
    courseId.value = courses.value[0].id
    await loadExams()
  }
})
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.table {
  margin-top: 16px;
}
</style>
