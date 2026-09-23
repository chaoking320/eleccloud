<template>
  <div class="dashboard">
    <!-- Top Welcome Banner -->
    <div class="welcome-banner">
      <div class="banner-content">
        <div class="banner-badge-row">
          <span class="banner-badge">{{ $t('dashboard.enterpriseBadge') }}</span>
          <span class="cluster-status-pill">
            <span class="pulse-indicator"></span>
            <span>{{ $t('dashboard.engineOnline') }}</span>
          </span>
        </div>
        <h2 class="banner-title">{{ $t('dashboard.title') }}</h2>
        <p class="banner-desc">
          {{ $t('dashboard.subtitle') }}
        </p>
      </div>
      <div class="banner-actions">
        <el-button type="primary" :icon="Refresh" :loading="loading" @click="loadStatistics">
          {{ $t('dashboard.refreshMetrics') }}
        </el-button>
        <el-button class="btn-demo-link" :icon="Monitor" @click="goToDemo">
          {{ $t('dashboard.interactiveDemo') }}
        </el-button>
      </div>
    </div>

    <!-- Cluster Nodes Live Topology Panel (COOL HIGH-TECH FEATURE) -->
    <el-card shadow="never" class="cluster-topology-card">
      <div class="topology-header">
        <div class="topology-title">
          <el-icon class="topology-icon"><Connection /></el-icon>
          <span>{{ $t('dashboard.topologyTitle') }}</span>
        </div>
        <div class="topology-legend">
          <span class="legend-item"><span class="legend-dot green"></span> {{ $t('dashboard.statusNormal') }}</span>
          <span class="legend-item"><span class="legend-dot blue"></span> {{ $t('dashboard.statusSyncing') }}</span>
          <span class="legend-item"><span class="legend-dot purple"></span> {{ $t('dashboard.statusLocked') }}</span>
        </div>
      </div>
      <div class="nodes-grid">
        <div class="node-card">
          <div class="node-top">
            <div class="node-icon bg-blue">🚀</div>
            <div class="node-title-group">
              <div class="node-name">{{ $t('dashboard.serverCluster') }}</div>
              <div class="node-sub">{{ $t('dashboard.serverClusterDesc') }}</div>
            </div>
            <span class="node-status-badge online">ACTIVE</span>
          </div>
          <div class="node-metrics">
            <div class="metric-item">
              <span class="metric-key">{{ $t('dashboard.nodeStatus') }}</span>
              <span class="metric-val text-success">UP (8080)</span>
            </div>
            <div class="metric-item">
              <span class="metric-key">{{ $t('dashboard.concurrencyControl') }}</span>
              <span class="metric-val">ShedLock PT5M</span>
            </div>
          </div>
        </div>

        <div class="node-card">
          <div class="node-top">
            <div class="node-icon bg-emerald">⚡</div>
            <div class="node-title-group">
              <div class="node-name">{{ $t('dashboard.consumerCluster') }}</div>
              <div class="node-sub">{{ $t('dashboard.consumerClusterDesc') }}</div>
            </div>
            <span class="node-status-badge online">RUNNING</span>
          </div>
          <div class="node-metrics">
            <div class="metric-item">
              <span class="metric-key">{{ $t('dashboard.consumerMode') }}</span>
              <span class="metric-val text-primary">Atomic Lua</span>
            </div>
            <div class="metric-item">
              <span class="metric-key">{{ $t('dashboard.twoTierHybrid') }}</span>
              <span class="metric-val text-success">200ms In-Memory</span>
            </div>
          </div>
        </div>

        <div class="node-card">
          <div class="node-top">
            <div class="node-icon bg-amber">⏱️</div>
            <div class="node-title-group">
              <div class="node-name">{{ $t('dashboard.redisEngine') }}</div>
              <div class="node-sub">{{ $t('dashboard.redisEngineDesc') }}</div>
            </div>
            <span class="node-status-badge online">READY</span>
          </div>
          <div class="node-metrics">
            <div class="metric-item">
              <span class="metric-key">{{ $t('dashboard.queueHealth') }}</span>
              <span class="metric-val text-success">Healthy (&lt;1ms)</span>
            </div>
            <div class="metric-item">
              <span class="metric-key">{{ $t('dashboard.fallbackGuarantee') }}</span>
              <span class="metric-val">15s Fallback</span>
            </div>
          </div>
        </div>

        <div class="node-card">
          <div class="node-top">
            <div class="node-icon bg-purple">🛡️</div>
            <div class="node-title-group">
              <div class="node-name">{{ $t('dashboard.mysqlStore') }}</div>
              <div class="node-sub">{{ $t('dashboard.mysqlStoreDesc') }}</div>
            </div>
            <span class="node-status-badge online">SYNCED</span>
          </div>
          <div class="node-metrics">
            <div class="metric-item">
              <span class="metric-key">{{ $t('dashboard.primaryConstraint') }}</span>
              <span class="metric-val">uk_task_id</span>
            </div>
            <div class="metric-item">
              <span class="metric-key">{{ $t('dashboard.dataArchive') }}</span>
              <span class="metric-val text-success">Auto-Clean ON</span>
            </div>
          </div>
        </div>
      </div>
    </el-card>

    <!-- Stat Cards -->
    <el-row :gutter="20" class="stat-row">
      <el-col :xs="24" :sm="12" :md="6">
        <div class="stat-card total-card">
          <div class="stat-info">
            <span class="stat-label">{{ $t('dashboard.totalTasks') }}</span>
            <div class="stat-val">{{ statistics.totalTasks }}</div>
            <div class="stat-meta">{{ $t('dashboard.totalTasksSub') }}</div>
          </div>
          <div class="stat-icon-wrapper total-icon">
            <el-icon><Document /></el-icon>
          </div>
        </div>
      </el-col>
      
      <el-col :xs="24" :sm="12" :md="6">
        <div class="stat-card success-card">
          <div class="stat-info">
            <span class="stat-label">{{ $t('dashboard.successTasks') }}</span>
            <div class="stat-val text-success">{{ statistics.successTasks }}</div>
            <div class="stat-meta">
              {{ $t('dashboard.overallRate') }} <strong class="rate-highlight">{{ successRate }}%</strong>
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
            <span class="stat-label">{{ $t('dashboard.failedTasks') }}</span>
            <div class="stat-val text-danger">{{ statistics.failedTasks }}</div>
            <div class="stat-meta">{{ $t('dashboard.failedTasksSub') }}</div>
          </div>
          <div class="stat-icon-wrapper error-icon">
            <el-icon><CircleCloseFilled /></el-icon>
          </div>
        </div>
      </el-col>
      
      <el-col :xs="24" :sm="12" :md="6">
        <div class="stat-card warning-card">
          <div class="stat-info">
            <span class="stat-label">{{ $t('dashboard.runningTasks') }}</span>
            <div class="stat-val text-warning">{{ statistics.runningTasks }}</div>
            <div class="stat-meta">{{ $t('dashboard.runningTasksSub') }}</div>
          </div>
          <div class="stat-icon-wrapper warning-icon">
            <el-icon><Loading /></el-icon>
          </div>
        </div>
      </el-col>
    </el-row>
    
    <!-- Chart & Architecture Info -->
    <el-row :gutter="20">
      <el-col :xs="24" :lg="16">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="card-header">
              <div class="card-title-group">
                <span class="card-title">{{ $t('dashboard.chartTitle') }}</span>
                <span class="card-subtitle">{{ $t('dashboard.chartSub') }}</span>
              </div>
              <el-tag size="small" type="success" effect="light">{{ $t('dashboard.chartLiveBadge') }}</el-tag>
            </div>
          </template>
          <div style="height: 360px;">
            <v-chart class="chart" :option="chartOption" autoresize />
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="8">
        <el-card shadow="never" class="info-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">{{ $t('dashboard.governanceTitle') }}</span>
              <span class="status-dot-active"></span>
            </div>
          </template>
          
          <div class="feature-list">
            <div class="feature-item">
              <div class="feature-icon bg-blue">⚡</div>
              <div class="feature-body">
                <div class="feature-name">{{ $t('dashboard.featTwoTier') }}</div>
                <div class="feature-desc">{{ $t('dashboard.featTwoTierDesc') }}</div>
              </div>
            </div>

            <div class="feature-item">
              <div class="feature-icon bg-emerald">🔄</div>
              <div class="feature-body">
                <div class="feature-name">{{ $t('dashboard.featConsumer') }}</div>
                <div class="feature-desc">{{ $t('dashboard.featConsumerDesc') }}</div>
              </div>
            </div>

            <div class="feature-item">
              <div class="feature-icon bg-purple">🛡️</div>
              <div class="feature-body">
                <div class="feature-name">{{ $t('dashboard.featShedLock') }}</div>
                <div class="feature-desc">{{ $t('dashboard.featShedLockDesc') }}</div>
              </div>
            </div>

            <div class="feature-item">
              <div class="feature-icon bg-amber">💾</div>
              <div class="feature-body">
                <div class="feature-name">{{ $t('dashboard.featFallback') }}</div>
                <div class="feature-desc">{{ $t('dashboard.featFallbackDesc') }}</div>
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
import { Document, SuccessFilled, CircleCloseFilled, Loading, Refresh, Monitor, Connection } from '@element-plus/icons-vue'
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
    backgroundColor: '#0f172a',
    borderColor: '#334155',
    textStyle: { color: '#f8fafc', fontSize: 12 },
    borderRadius: 8
  },
  legend: {
    data: ['成功任务', '失败任务', '进行中'],
    top: 0,
    icon: 'roundRect',
    textStyle: { color: '#64748b' }
  },
  grid: {
    left: '3%',
    right: '4%',
    bottom: '3%',
    top: '12%',
    containLabel: true
  },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: ['00:00', '04:00', '08:00', '12:00', '16:00', '20:00', '实时'],
    axisLine: { lineStyle: { color: '#e2e8f0' } },
    axisLabel: { color: '#64748b', fontSize: 11 }
  },
  yAxis: {
    type: 'value',
    splitLine: { lineStyle: { color: '#f1f5f9', type: 'dashed' } },
    axisLabel: { color: '#64748b', fontSize: 11 }
  },
  series: [
    {
      name: '成功任务',
      type: 'line',
      smooth: 0.35,
      showSymbol: false,
      lineStyle: { width: 3, color: '#10b981' },
      areaStyle: {
        color: {
          type: 'linear',
          x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: 'rgba(16, 185, 129, 0.3)' },
            { offset: 1, color: 'rgba(16, 185, 129, 0.01)' }
          ]
        }
      },
      data: [35, 42, 68, 95, 140, 180, 215]
    },
    {
      name: '进行中',
      type: 'line',
      smooth: 0.35,
      showSymbol: false,
      lineStyle: { width: 3, color: '#3b82f6' },
      areaStyle: {
        color: {
          type: 'linear',
          x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: 'rgba(59, 130, 246, 0.25)' },
            { offset: 1, color: 'rgba(59, 130, 246, 0.01)' }
          ]
        }
      },
      data: [12, 18, 15, 24, 20, 28, 30]
    },
    {
      name: '失败任务',
      type: 'line',
      smooth: 0.35,
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
  background: linear-gradient(135deg, #090d16 0%, #1e293b 100%);
  color: #fff;
  padding: 28px 32px;
  border-radius: 16px;
  margin-bottom: 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.15);
  border: 1px solid rgba(255, 255, 255, 0.08);
}

.banner-badge-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.banner-badge {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 1px;
  background: rgba(56, 189, 248, 0.15);
  color: #38bdf8;
  padding: 3px 8px;
  border-radius: 6px;
  border: 1px solid rgba(56, 189, 248, 0.3);
}

.cluster-status-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: rgba(16, 185, 129, 0.15);
  color: #10b981;
  padding: 3px 10px;
  border-radius: 20px;
  font-size: 11px;
  font-weight: 600;
  border: 1px solid rgba(16, 185, 129, 0.3);
}

.pulse-indicator {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #10b981;
  box-shadow: 0 0 6px #10b981;
  animation: pulse 1.8s infinite;
}

.banner-title {
  font-size: 24px;
  font-weight: 800;
  margin-bottom: 6px;
  letter-spacing: -0.5px;
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

.btn-demo-link {
  background: rgba(255, 255, 255, 0.08) !important;
  color: #f8fafc !important;
  border: 1px solid rgba(255, 255, 255, 0.15) !important;
}

.btn-demo-link:hover {
  background: rgba(255, 255, 255, 0.15) !important;
}

/* Cluster Topology Card */
.cluster-topology-card {
  margin-bottom: 24px;
  background: #ffffff;
  padding: 20px 24px;
}

.topology-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.topology-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
}

.topology-icon {
  font-size: 18px;
  color: #2563eb;
}

.topology-legend {
  display: flex;
  gap: 14px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #64748b;
}

.legend-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}
.legend-dot.green  { background: #10b981; }
.legend-dot.blue   { background: #3b82f6; }
.legend-dot.purple { background: #a855f7; }

.nodes-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 16px;
}

.node-card {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 14px 16px;
  transition: all 0.25s;
}

.node-card:hover {
  border-color: #cbd5e1;
  background: #ffffff;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
}

.node-top {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.node-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
}

.node-title-group {
  flex: 1;
}

.node-name {
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
}

.node-sub {
  font-size: 11px;
  color: #64748b;
}

.node-status-badge {
  font-size: 10px;
  font-weight: 700;
  padding: 2px 6px;
  border-radius: 4px;
}

.node-status-badge.online {
  background: #ecfdf5;
  color: #059669;
  border: 1px solid #a7f3d0;
}

.node-metrics {
  border-top: 1px dashed #e2e8f0;
  padding-top: 10px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.metric-item {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
}

.metric-key { color: #64748b; }
.metric-val { font-weight: 600; color: #1e293b; }

/* Stat Cards */
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
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.06);
  border-color: #cbd5e1;
}

.stat-label {
  font-size: 13px;
  color: #64748b;
  font-weight: 500;
}

.stat-val {
  font-size: 30px;
  font-weight: 800;
  color: #0f172a;
  margin: 4px 0 2px;
}

.stat-meta {
  font-size: 12px;
  color: #94a3b8;
}

.rate-highlight {
  color: #10b981;
}

.text-success { color: #10b981; }
.text-danger  { color: #ef4444; }
.text-warning { color: #f59e0b; }
.text-primary { color: #2563eb; }

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

.card-title-group {
  display: flex;
  flex-direction: column;
}

.card-title {
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
}

.card-subtitle {
  font-size: 11px;
  color: #94a3b8;
  margin-top: 2px;
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
  font-size: 13px;
  font-weight: 700;
  color: #1e293b;
  margin-bottom: 2px;
}

.feature-desc {
  font-size: 11px;
  color: #64748b;
  line-height: 1.5;
}

.chart {
  height: 360px;
}

@keyframes pulse {
  0% { transform: scale(0.95); opacity: 0.7; }
  50% { transform: scale(1.2); opacity: 1; }
  100% { transform: scale(0.95); opacity: 0.7; }
}
</style>