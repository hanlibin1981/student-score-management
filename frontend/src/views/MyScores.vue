<template>
  <div>
    <el-row :gutter="16" justify="center">
      <el-col :span="8">
        <el-card>
          <div class="stat"><span>已修课程</span><strong>{{ totals.length }}</strong></div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card>
          <div class="stat"><span>平均总评</span><strong>{{ average }}</strong></div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="block">
      <template #header>课程总评与排名</template>
      <el-table :data="totals" stripe>
        <el-table-column prop="courseName" label="课程" />
        <el-table-column prop="totalScore" label="总评" width="120" />
        <el-table-column label="排名" width="160">
          <template #default="{ row }">{{ row.rank }} / {{ row.studentCount }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card class="block">
      <template #header>
        <div class="card-header">
          <span>历次考试趋势</span>
          <el-select v-model="courseId" style="width: 200px" @change="loadTrend">
            <el-option v-for="c in totals" :key="c.courseId" :label="c.courseName"
                       :value="c.courseId" />
          </el-select>
        </div>
      </template>
      <BaseChart :option="trendOption" height="340px" />
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { analysisApi, scoreApi } from '../api'
import BaseChart from '../components/BaseChart.vue'

const totals = ref([])
const courseId = ref(null)
const trend = ref([])

const average = computed(() => {
  if (!totals.value.length) return '0.0'
  const sum = totals.value.reduce((acc, item) => acc + Number(item.totalScore || 0), 0)
  return (sum / totals.value.length).toFixed(1)
})

const trendOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  xAxis: { type: 'category', data: trend.value.map(item => item.examName) },
  yAxis: { type: 'value', name: '折算分', min: 0, max: 100 },
  series: [{
    type: 'line',
    smooth: true,
    areaStyle: {},
    data: trend.value.map(item => item.percent)
  }]
}))

async function loadTrend() {
  trend.value = courseId.value ? await analysisApi.trend(undefined, courseId.value) : []
}

onMounted(async () => {
  totals.value = await scoreApi.myCourses()
  if (totals.value.length) {
    courseId.value = totals.value[0].courseId
    await loadTrend()
  }
})
</script>

<style scoped>
.stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  text-align: center;
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
</style>
