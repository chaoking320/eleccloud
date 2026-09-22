<template>
  <div class="dashboard">
    <!-- Top Welcome Banner -->
    <div class="welcome-banner">
      <div class="banner-content">
        <h2 class="banner-title">ElecCloud 重试调度控制台</h2>
        <p class="banner-desc">
          高可用去中心化分布式重试中台 · 实时监控集群任务状态与重试流水线
        </p>
      </div>
      <div class="banner-actions">
        <el-button type="primary" :icon="Refresh" :loading="loading" @click="loadStatistics">
          刷新数据
        </el-button>
        <el-button :icon="Monitor" @click="goToDemo">
          打开交互演示
        </el-button>
      </div>
    </div>

    <!-- Stat Cards -->
    <el-row :gutter="20" class="stat-row">
      <el-col :xs="24" :sm="12" :md="6">
        <div class="stat-card total-card">
          <div class="stat-info">
            <span class="stat-label">总任务数</span>
            <div class="stat-val">{{ statistics.totalTasks }}</div>
            <div class="stat-meta">累计注册重试任务</div>
          </div>
          <div class="stat-icon-wrapper total-icon">
            <el-icon><Document /></el-icon>
          </div>
        </div>
      </el-col>
      
      <el-col :xs="24" :sm="12" :md="6">
        <div class="stat-card success-card">
          <div class="stat-info">
            <span class="stat-label">成功任务</span>
            <div class="stat-val text-success">{{ statistics.successTasks }}</div>
            <div class="stat-meta">
              成功率: {{ successRate }}%
            </div>
          </div>
          <div class="stat-icon-wrapper success-icon">
            <el-icon><SuccessFilled /></el-icon>
          </div>
        </div>
      </el-col>
      
      <el-col :xs="24" :sm="12" :md="6">
        <div class="stat-card error-card">
          <div class="stat-info">
            <span class="stat-label">失败任务</span>
            <div class="stat-val text-danger">{{ statistics.failedTasks }}</div>
            <div class="stat-meta">需人工介入或重试枯竭</div>
          </div>
          <div class="stat-icon-wrapper error-icon">
            <el-icon><CircleCloseFilled /></el-icon>
          </div>
        </div>
      </el-col>
      
      <el-col :xs="24" :sm="12" :md="6">
        <div class="stat-card warning-card">
          <div class="stat-info">
            <span class="stat-label">进行中任务</span>
            <div class="stat-val text-warning">{{ statistics.runningTasks }}</div>
            <div class="stat-meta">处于 INIT 或 WAIT 队列</div>
          </div>
          <div class="stat-icon-wrapper warning-icon">
            <el-icon><Loading /></el-icon>
          </div>
        </div>
      </el-col>
    </el-row>
    
    <!-- Chart & Architecture Info -->
    <el-row :gutter="20" style="margin-top: 20px;">
      <el-col :xs="24" :lg="16">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">重试任务流转趋势</span>
              <el-tag size="small" type="success" effect="light">实时心跳同步</el-tag>
            </div>
          </template>
          <div style="height: 380px;">
            <v-chart class="chart" :option="chartOption" autoresize />
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="8">
        <el-card shadow="never" class="info-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">平台核心特性状态</span>
              <span class="status-dot-active"></span>
            </div>
          </template>
          
          <div class="feature-list">
            <div class="feature-item">
              <div class="feature-icon bg-blue">⚡</div>
              <div class="feature-body">
                <div class="feature-name">双层混合重试 (Two-Tier)</div>
                <div class="feature-desc">本地内存 200ms 快速消解抖动，失败平滑升级至服务端调度</div>
              </div>
            </div>

            <div class="feature-item">
              <div class="feature-icon bg-emerald">🔄</div>
              <div class="feature-body">
                <div class="feature-name">去中心化自驱动 MQ</div>
                <div class="feature-desc">服务端轻量调度延迟与通知，SDK 节点本地通过反射自驱动重试</div>
              </div>
            </div>

            <div class="feature-item">
              <div class="feature-icon bg-purple">🛡️</div>
              <div class="feature-body">
                <div class="feature-name">预提交与故障降级补偿</div>
                <div class="feature-desc">支持 PRE_SUBMIT 防崩溃，并在服务端宕机时本地队列补偿同步</div>
              </div>
            </div>

            <div class="feature-item">
              <div class="feature-icon bg-amber">⏱️</div>
              <div class="feature-body">
                <div class="feature-name">动态退避与兜底扫描</div>
                <div class="feature-desc">支持 FIXED, LINEAR, EXPONENTIAL 策略及高效兜底补漏扫描</div>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
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
import { Document, SuccessFilled, CircleCloseFilled, Loading, Refresh, Monitor } from '@element-plus/icons-vue'
import { taskApi } from '@/api'

use([
  CanvasRenderer,
  LineChart,
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent
])

const loading = ref(false)
const statistics = ref({
  totalTasks: 0,
  successTasks: 0,
  failedTasks: 0,
  runningTasks: 0
})

const successRate = computed(() => {
  if (!statistics.value.totalTasks) return '100.0'
  const rate = (statistics.value.successTasks / statistics.value.totalTasks) * 100
  return rate.toFixed(1)
})

const chartOption = ref({
  tooltip: {
    trigger: 'axis',
    backgroundColor: '#1e293b',
    borderColor: '#334155',
    textStyle: { color: '#f8fafc' }
  },
  legend: {
    data: ['成功任务', '失败任务', '进行中'],
    top: 5,
    icon: 'roundRect'
  },
  grid: {
    left: '3%',
    right: '4%',
    bottom: '3%',
    top: '14%',
    containLabel: true
  },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: ['00:00', '04:00', '08:00', '12:00', '16:00', '20:00', '当前'],
    axisLine: { lineStyle: { color: '#cbd5e1' } },
    axisLabel: { color: '#64748b' }
  },
  yAxis: {
    type: 'value',
    splitLine: { lineStyle: { color: '#f1f5f9' } },
    axisLabel: { color: '#64748b' }
  },
  series: [
    {
      name: '成功任务',
      type: 'line',
      smooth: true,
      showSymbol: false,
      lineStyle: { width: 3, color: '#10b981' },
      areaStyle: {
        color: {
          type: 'linear',
          x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: 'rgba(16, 185, 129, 0.28)' },
            { offset: 1, color: 'rgba(16, 185, 129, 0.02)' }
          ]
        }
      },
      data: [35, 42, 68, 95, 140, 180, 215]
    },
    {
      name: '进行中',
      type: 'line',
      smooth: true,
      showSymbol: false,
      lineStyle: { width: 3, color: '#3b82f6' },
      areaStyle: {
        color: {
          type: 'linear',
          x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: 'rgba(59, 130, 246, 0.25)' },
            { offset: 1, color: 'rgba(59, 130, 246, 0.02)' }
          ]
        }
      },
      data: [12, 18, 15, 24, 20, 28, 30]
    },
    {
      name: '失败任务',
      type: 'line',
      smooth: true,
      showSymbol: false,
      lineStyle: { width: 2, color: '#ef4444' },
      data: [2, 1, 3, 2, 4, 3, 5]
    }
  ]
})

const loadStatistics = async () => {
  loading.value = true
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
  } finally {
    loading.value = false
  }
}

const goToDemo = () => {
  window.open('http://localhost:8082/demo.html', '_blank')
}

onMounted(() => {
  loadStatistics()
})
</script>

<style scoped>
.dashboard {
  padding: 24px;
}

.welcome-banner {
  background: linear-gradient(135deg, #1e293b 0%, #0f172a 100%);
  color: #fff;
  padding: 24px 28px;
  border-radius: 12px;
  margin-bottom: 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
}

.banner-title {
  font-size: 20px;
  font-weight: 700;
  margin-bottom: 6px;
  color: #f8fafc;
}

.banner-desc {
  font-size: 13px;
  color: #94a3b8;
  margin: 0;
}

.banner-actions {
  display: flex;
  gap: 12px;
}

.stat-row {
  margin-bottom: 8px;
}

.stat-card {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 20px 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  transition: all 0.25s ease;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.08);
  border-color: #cbd5e1;
}

.stat-label {
  font-size: 13px;
  color: #64748b;
  font-weight: 500;
}

.stat-val {
  font-size: 30px;
  font-weight: 700;
  color: #0f172a;
  margin: 4px 0 2px;
}

.stat-meta {
  font-size: 12px;
  color: #94a3b8;
}

.text-success { color: #10b981; }
.text-danger  { color: #ef4444; }
.text-warning { color: #f59e0b; }

.stat-icon-wrapper {
  width: 52px;
  height: 52px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
}

.total-icon   { background: #eff6ff; color: #3b82f6; }
.success-icon { background: #ecfdf5; color: #10b981; }
.error-icon   { background: #fef2f2; color: #ef4444; }
.warning-icon { background: #fffbeb; color: #f59e0b; }

.chart-card, .info-card {
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #ffffff;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: #0f172a;
}

.status-dot-active {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #10b981;
  box-shadow: 0 0 6px #10b981;
}

.feature-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 6px 0;
}

.feature-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.feature-icon {
  width: 36px;
  height: 36px;
  min-width: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
}

.bg-blue    { background: #eff6ff; }
.bg-emerald { background: #ecfdf5; }
.bg-purple  { background: #f5f3ff; }
.bg-amber   { background: #fffbeb; }

.feature-name {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 2px;
}

.feature-desc {
  font-size: 12px;
  color: #64748b;
  line-height: 1.5;
}

.chart {
  height: 380px;
}
</style>