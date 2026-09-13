<template>
  <div class="dashboard">
    <el-row :gutter="20">
      <el-col :span="6">
        <el-card class="box-card">
          <div class="card-header">
            <span>总任务数</span>
            <el-icon><Document /></el-icon>
          </div>
          <div class="card-content">
            <div class="number">{{ statistics.totalTasks }}</div>
            <div class="desc">累计创建任务</div>
          </div>
        </el-card>
      </el-col>
      
      <el-col :span="6">
        <el-card class="box-card">
          <div class="card-header">
            <span>成功任务</span>
            <el-icon><SuccessFilled /></el-icon>
          </div>
          <div class="card-content">
            <div class="number success">{{ statistics.successTasks }}</div>
            <div class="desc">执行成功任务</div>
          </div>
        </el-card>
      </el-col>
      
      <el-col :span="6">
        <el-card class="box-card">
          <div class="card-header">
            <span>失败任务</span>
            <el-icon><CircleCloseFilled /></el-icon>
          </div>
          <div class="card-content">
            <div class="number error">{{ statistics.failedTasks }}</div>
            <div class="desc">执行失败任务</div>
          </div>
        </el-card>
      </el-col>
      
      <el-col :span="6">
        <el-card class="box-card">
          <div class="card-header">
            <span>进行中任务</span>
            <el-icon><Loading /></el-icon>
          </div>
          <div class="card-content">
            <div class="number warning">{{ statistics.runningTasks }}</div>
            <div class="desc">正在重试任务</div>
          </div>
        </el-card>
      </el-col>
    </el-row>
    
    <el-row :gutter="20" style="margin-top: 20px;">
      <el-col :span="24">
        <el-card>
          <div class="card-header">
            <span>任务趋势图</span>
          </div>
          <div style="height: 400px;">
            <v-chart class="chart" :option="chartOption" />
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart } from 'echarts/charts'
import {
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent
} from 'echarts/components'
import VChart from 'vue-echarts'
import { Document, SuccessFilled, CircleCloseFilled, Loading } from '@element-plus/icons-vue'
import { taskApi } from '@/api'

use([
  CanvasRenderer,
  LineChart,
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent
])

const statistics = ref({
  totalTasks: 0,
  successTasks: 0,
  failedTasks: 0,
  runningTasks: 0
})

const chartOption = ref({
  title: {
    text: '任务执行趋势'
  },
  tooltip: {
    trigger: 'axis'
  },
  legend: {
    data: ['成功任务', '失败任务', '进行中任务']
  },
  grid: {
    left: '3%',
    right: '4%',
    bottom: '3%',
    containLabel: true
  },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
  },
  yAxis: {
    type: 'value'
  },
  series: [
    {
      name: '成功任务',
      type: 'line',
      stack: 'Total',
      data: [120, 132, 101, 134, 90, 230, 210]
    },
    {
      name: '失败任务',
      type: 'line',
      stack: 'Total',
      data: [20, 18, 15, 23, 12, 18, 25]
    },
    {
      name: '进行中任务',
      type: 'line',
      stack: 'Total',
      data: [30, 25, 28, 32, 20, 35, 40]
    }
  ]
})

const loadStatistics = async () => {
  try {
    const res = await taskApi.getStatistics()
    if (res && res.data) {
      const data = res.data
      statistics.value = {
        totalTasks: data.total || 0,
        successTasks: data.success || 0,
        failedTasks: data.failed || 0,
        runningTasks: (data.init || 0) + (data.wait || 0)
      }
    }
  } catch (e) {
    console.error('Failed to load dashboard stats', e)
  }
}

onMounted(() => {
  loadStatistics()
})
</script>

<style scoped>
.dashboard {
  padding: 20px;
}

.box-card {
  height: 120px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 14px;
  color: #666;
  margin-bottom: 10px;
}

.card-content {
  text-align: center;
}

.number {
  font-size: 32px;
  font-weight: bold;
  color: #409EFF;
  margin-bottom: 5px;
}

.number.success {
  color: #67C23A;
}

.number.error {
  color: #F56C6C;
}

.number.warning {
  color: #E6A23C;
}

.desc {
  font-size: 12px;
  color: #999;
}

.chart {
  height: 400px;
}
</style>