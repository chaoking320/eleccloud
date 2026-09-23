<template>
  <div class="task-monitor">
    <!-- 顶部数据看板 Dashboard Metric Cards -->
    <el-row :gutter="20" class="stat-dashboard">
      <el-col :xs="24" :sm="12" :md="6">
        <div class="stat-box total-box">
          <div class="stat-left">
            <span class="stat-title">{{ $t('task.metricTotal') }}</span>
            <div class="stat-num">{{ stats.total }}</div>
            <div class="stat-sub">{{ $t('task.metricTotalSub') }}</div>
          </div>
          <div class="stat-badge-icon total-icon">
            <el-icon><Tickets /></el-icon>
          </div>
        </div>
      </el-col>

      <el-col :xs="24" :sm="12" :md="6">
        <div class="stat-box init-box">
          <div class="stat-left">
            <span class="stat-title">{{ $t('task.metricInit') }}</span>
            <div class="stat-num text-primary">{{ stats.init }}</div>
            <div class="stat-sub">{{ $t('task.metricInitSub') }}</div>
          </div>
          <div class="stat-badge-icon init-icon">
            <el-icon><Timer /></el-icon>
          </div>
        </div>
      </el-col>

      <el-col :xs="24" :sm="12" :md="6">
        <div class="stat-box wait-box">
          <div class="stat-left">
            <span class="stat-title">{{ $t('task.metricWait') }}</span>
            <div class="stat-num text-warning">{{ stats.wait }}</div>
            <div class="stat-sub">{{ $t('task.metricWaitSub') }}</div>
          </div>
          <div class="stat-badge-icon wait-icon">
            <el-icon><Refresh /></el-icon>
          </div>
        </div>
      </el-col>

      <el-col :xs="24" :sm="12" :md="6">
        <div class="stat-box failed-box">
          <div class="stat-left">
            <span class="stat-title">{{ $t('task.metricFailed') }}</span>
            <div class="stat-num text-danger">{{ stats.failed }}</div>
            <div class="stat-sub">{{ $t('task.metricFailedSub') }}</div>
          </div>
          <div class="stat-badge-icon failed-icon">
            <el-icon><Warning /></el-icon>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 搜索筛选区 Filters -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="filters" class="demo-form-inline">
        <el-form-item :label="$t('task.filterScene')">
          <el-select v-model="filters.sceneType" :placeholder="$t('task.filterScenePlaceholder')" clearable style="width: 180px">
            <el-option v-for="item in sceneOptions" :key="item.sceneType" :label="`${item.sceneName} (#${item.sceneType})`" :value="item.sceneType" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('task.filterStatus')">
          <el-select v-model="filters.taskStatus" :placeholder="$t('task.filterStatusPlaceholder')" clearable style="width: 140px">
            <el-option label="INIT" value="INIT" />
            <el-option label="WAIT" value="WAIT" />
            <el-option label="SUCCESS" value="SUCCESS" />
            <el-option label="FAILED" value="FAILED" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('task.filterKey')">
          <el-input v-model="filters.idempotentKey" :placeholder="$t('task.filterKeyPlaceholder')" clearable style="width: 220px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">
            {{ $t('common.query') }}
          </el-button>
          <el-button @click="resetFilters">{{ $t('common.reset') }}</el-button>
          <el-button :icon="RefreshRight" @click="loadAllData" :loading="loading">
            {{ $t('common.refresh') }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 任务数据列表 Table -->
    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-header-title">
          <div class="header-left-title">
            <span class="title-text">{{ $t('task.title') }}</span>
            <span class="title-sub">{{ $t('task.subtitle') }}</span>
          </div>
        </div>
      </template>

      <!-- 核心修复：扩大列宽，消解叠字与换行问题，去除透明穿透 -->
      <el-table :data="tableData" v-loading="loading" stripe style="width: 100%" class="custom-monitor-table">
        <el-table-column prop="taskId" :label="$t('task.colTaskId')" width="190" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="task-id-text">{{ row.taskId }}</span>
          </template>
        </el-table-column>

        <!-- 场景类型与名称 -->
        <el-table-column :label="$t('task.colScene')" min-width="210">
          <template #default="{ row }">
            <div class="scene-item-cell">
              <span class="scene-chip">#{{ row.sceneType }}</span>
              <span class="scene-title-text">{{ cleanSceneName(row.sceneType) }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="idempotentKey" :label="$t('task.colIdempotentKey')" width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="idempotent-text">{{ row.idempotentKey }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="methodName" :label="$t('task.colMethod')" width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="method-badge">{{ row.methodName }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="taskStatus" :label="$t('task.colStatus')" width="120">
          <template #default="{ row }">
            <span class="status-pill" :class="getStatusClass(row.taskStatus)">
              <span class="status-bullet"></span>
              <span>{{ row.taskStatus }}</span>
            </span>
          </template>
        </el-table-column>

        <el-table-column :label="$t('task.colRetryCount')" width="110">
          <template #default="{ row }">
            <span class="retry-counter-pill">{{ row.retryCount }} / {{ row.maxRetryCount }}</span>
          </template>
        </el-table-column>

        <el-table-column :label="$t('task.colNextTime')" width="170">
          <template #default="{ row }">
            <span class="time-text">{{ formatTime(row.nextRetryTime) }}</span>
          </template>
        </el-table-column>

        <el-table-column :label="$t('common.operation')" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" :icon="View" @click="viewDetail(row)">
              {{ $t('task.actionTrace') }}
            </el-button>
            <el-button 
              link 
              type="warning" 
              size="small" 
              :icon="Refresh"
              @click="triggerRetry(row)"
              :disabled="row.taskStatus === 'SUCCESS'">
              {{ $t('task.actionTrigger') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 Pagination -->
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="pagination.pageNum"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="pagination.total"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <!-- 详情弹窗 Dialog -->
    <el-dialog
      v-model="detailVisible"
      title="重试任务详细全息档案与执行轨迹"
      width="820px"
      class="detail-dialog"
      destroy-on-close>
      <div v-if="selectedTask" class="dialog-content">
        <el-tabs type="border-card">
          <!-- 任务基本信息 -->
          <el-tab-pane label="任务基本属性">
            <el-descriptions :column="2" border class="descriptions-box">
              <el-descriptions-item label="任务全局ID" :span="2">
                <span class="mono-code">{{ selectedTask.taskId }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="所属场景">
                {{ cleanSceneName(selectedTask.sceneType) }} (编码: #{{ selectedTask.sceneType }})
              </el-descriptions-item>
              <el-descriptions-item label="幂等流水号">
                <span class="mono-code">{{ selectedTask.idempotentKey }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="业务方法类" :span="2">
                <span class="mono-code">{{ selectedTask.methodClass }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="业务方法名">
                <span class="method-badge">{{ selectedTask.methodName }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="当前状态">
                <span class="status-pill" :class="getStatusClass(selectedTask.taskStatus)">
                  <span class="status-bullet"></span>
                  <span>{{ selectedTask.taskStatus }}</span>
                </span>
              </el-descriptions-item>
              <el-descriptions-item label="已执行次数">{{ selectedTask.retryCount }} 次</el-descriptions-item>
              <el-descriptions-item label="允许最大次数">{{ selectedTask.maxRetryCount }} 次</el-descriptions-item>
              <el-descriptions-item label="登记时间">{{ formatTime(selectedTask.createTime) }}</el-descriptions-item>
              <el-descriptions-item label="最后更新">{{ formatTime(selectedTask.updateTime) }}</el-descriptions-item>
            </el-descriptions>

            <!-- 参数展示 -->
            <div class="params-section">
              <div class="section-title">业务方法入参反序列化快照 (JSON)</div>
              <pre class="json-code"><code>{{ formatJson(selectedTask.methodParams) }}</code></pre>
            </div>
          </el-tab-pane>

          <!-- 任务重试时间轴 History Timeline -->
          <el-tab-pane label="重试执行轨迹 (Timeline)">
            <div class="timeline-wrapper">
              <el-empty v-if="historyList.length === 0" description="暂无重试历史轨迹记录" />
              <el-timeline v-else>
                <el-timeline-item
                  v-for="(history, index) in historyList"
                  :key="history.id"
                  :type="getHistoryType(history.executeResult)"
                  :color="getHistoryColor(history.executeResult)"
                  :timestamp="formatTime(history.executeTime)"
                  placement="top">
                  <el-card class="timeline-card">
                    <div class="timeline-card-header">
                      <span class="retry-count-tag">第 {{ history.retryCount }} 次调度执行</span>
                      <el-tag :type="getHistoryType(history.executeResult)" size="small" effect="dark">
                        {{ history.executeResult }}
                      </el-tag>
                    </div>
                    <div class="timeline-card-body">
                      <p v-if="history.errorMessage && history.executeResult !== 'SUCCESS'" class="error-msg">
                        <strong>异常原因：</strong>{{ formatErrorMessage(history.errorMessage) }}
                      </p>
                      <p v-else-if="history.errorMessage && history.executeResult === 'SUCCESS'" class="success-msg">
                        <strong>执行反馈：</strong>{{ formatErrorMessage(history.errorMessage) }}
                      </p>
                      <p class="cost-time"><strong>执行耗时：</strong>{{ history.costTime }} ms</p>
                    </div>
                  </el-card>
                </el-timeline-item>
              </el-timeline>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="detailVisible = false">关闭</el-button>
          <el-button 
            type="warning" 
            v-if="selectedTask && selectedTask.taskStatus !== 'SUCCESS'" 
            @click="triggerRetry(selectedTask)">
            手动触发重试
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Tickets, Timer, Refresh, Warning, Search, RefreshRight, View } from '@element-plus/icons-vue'
import request from '@/utils/request'

const getTaskList = (params) => request({ url: '/task/list', method: 'get', params })
const getTaskDetail = (taskId) => request({ url: `/task/${taskId}`, method: 'get' })
const getTaskHistory = (taskId) => request({ url: `/task/${taskId}/history`, method: 'get' })
const getDashboardStats = () => request({ url: '/task/stats', method: 'get' })
const triggerManualRetry = (taskId) => request({ url: `/task/${taskId}/retry`, method: 'post' })
const getSceneList = () => request({ url: '/scene/list', method: 'get' })

const loading = ref(false)
const tableData = ref([])
const historyList = ref([])
const selectedTask = ref(null)
const detailVisible = ref(false)
const sceneOptions = ref([])
const sceneMap = ref({})

const stats = reactive({
  total: 0,
  init: 0,
  wait: 0,
  failed: 0
})

const filters = reactive({
  sceneType: '',
  taskStatus: '',
  idempotentKey: ''
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const loadScenes = async () => {
  try {
    const res = await getSceneList()
    if (res && res.data) {
      sceneOptions.value = res.data
      const map = {}
      res.data.forEach(scene => {
        map[scene.sceneType] = scene.sceneName
      })
      sceneMap.value = map
    }
  } catch (e) {
    console.error('Failed to load scenes', e)
  }
}

const cleanSceneName = (sceneType) => {
  const name = sceneMap.value[sceneType]
  if (!name) return `场景 #${sceneType}`
  return name.replace(/\s*\(\d+\)$/, '')
}

const getStatusClass = (status) => {
  switch (status) {
    case 'SUCCESS': return 'status-success'
    case 'WAIT': return 'status-wait'
    case 'INIT': return 'status-init'
    case 'FAILED': return 'status-failed'
    default: return 'status-init'
  }
}

const getHistoryType = (result) => {
  switch (result) {
    case 'SUCCESS': return 'success'
    case 'FAILED': return 'danger'
    case 'RETRY': return 'warning'
    default: return 'info'
  }
}

const getHistoryColor = (result) => {
  switch (result) {
    case 'SUCCESS': return '#10b981'
    case 'FAILED': return '#ef4444'
    case 'RETRY': return '#f59e0b'
    default: return '#64748b'
  }
}

const formatTime = (timeVal) => {
  if (!timeVal) return '-'
  const date = new Date(timeVal)
  return date.toLocaleString()
}

const formatJson = (jsonStr) => {
  if (!jsonStr) return '{}'
  try {
    const obj = JSON.parse(jsonStr)
    return JSON.stringify(obj, null, 2)
  } catch (e) {
    return jsonStr
  }
}

const formatErrorMessage = (msg) => {
  if (!msg) return ''
  try {
    return decodeURIComponent(msg.replace(/\+/g, ' '))
  } catch (e) {
    return msg
  }
}

const loadStats = async () => {
  try {
    const res = await getDashboardStats()
    if (res && res.data) {
      stats.init = res.data.init || 0
      stats.wait = res.data.wait || 0
      stats.failed = res.data.failed || 0
      stats.total = res.data.total !== undefined ? res.data.total : (stats.init + stats.wait + (res.data.success || 0) + stats.failed)
    }
  } catch (e) {
    console.error('Failed to load dashboard stats', e)
  }
}

const loadTableData = async () => {
  loading.value = true
  try {
    const params = {
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      ...filters
    }
    const res = await getTaskList(params)
    if (res && res.data) {
      tableData.value = res.data.list || []
      pagination.total = res.data.total || 0
    }
  } catch (e) {
    console.error('Failed to load table data', e)
    ElMessage.error('加载列表数据失败')
  } finally {
    loading.value = false
  }
}

const loadAllData = () => {
  loadStats()
  loadTableData()
}

const handleSearch = () => {
  pagination.pageNum = 1
  loadTableData()
}

const resetFilters = () => {
  filters.sceneType = ''
  filters.taskStatus = ''
  filters.idempotentKey = ''
  handleSearch()
}

const handleSizeChange = (val) => {
  pagination.pageSize = val
  loadTableData()
}

const handleCurrentChange = (val) => {
  pagination.pageNum = val
  loadTableData()
}

const viewDetail = async (row) => {
  try {
    const resDetail = await getTaskDetail(row.taskId)
    if (resDetail && resDetail.data) {
      selectedTask.value = resDetail.data
    } else {
      selectedTask.value = row
    }
    
    const resHistory = await getTaskHistory(row.taskId)
    if (resHistory && resHistory.data) {
      historyList.value = resHistory.data
    } else {
      historyList.value = []
    }
    
    detailVisible.value = true
  } catch (e) {
    ElMessage.error('获取详情或历史失败')
  }
}

const triggerRetry = (row) => {
  ElMessageBox.confirm(
    `确定要立即手动触发任务 ${row.taskId} 的重试吗？`,
    '手动重试确认',
    {
      confirmButtonText: '确定触发',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(async () => {
    try {
      const res = await triggerManualRetry(row.taskId)
      if (res && res.success) {
        ElMessage.success('手动触发重试已提交，正在调度执行...')
        loadAllData()
        if (detailVisible.value) {
          viewDetail(row)
        }
      } else {
        ElMessage.error(res.message || '重试提交失败')
      }
    } catch (e) {
      console.error(e)
    }
  }).catch(() => {})
}

onMounted(() => {
  loadScenes()
  loadAllData()
})
</script>

<style scoped>
.task-monitor {
  padding: 24px;
}

/* Modern Metric Cards (Replaced harsh gradient with refined cards) */
.stat-dashboard {
  margin-bottom: 24px;
}

.stat-box {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 18px 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  transition: all 0.25s ease;
}

.stat-box:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 16px rgba(0, 0, 0, 0.06);
  border-color: #cbd5e1;
}

.stat-title {
  font-size: 13px;
  color: #64748b;
  font-weight: 500;
}

.stat-num {
  font-size: 28px;
  font-weight: 800;
  color: #0f172a;
  margin: 4px 0 2px;
}

.stat-sub {
  font-size: 11px;
  color: #94a3b8;
}

.stat-badge-icon {
  width: 46px;
  height: 46px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
}

.total-icon  { background: #eff6ff; color: #2563eb; }
.init-icon   { background: #eef2ff; color: #4f46e5; }
.wait-icon   { background: #fffbeb; color: #d97706; }
.failed-icon { background: #fef2f2; color: #ef4444; }

.text-primary { color: #2563eb; }
.text-warning { color: #d97706; }
.text-danger  { color: #ef4444; }

/* Filter area */
.filter-card {
  border-radius: 12px;
  margin-bottom: 20px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
}

.table-card {
  border-radius: 12px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
}

.card-header-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-left-title {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.title-text {
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
}

.title-sub {
  font-size: 12px;
  color: #94a3b8;
}

/* Custom Table Components */
.task-id-text {
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  color: #2563eb;
  font-weight: 600;
}

.scene-item-cell {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.scene-chip {
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
  font-weight: 700;
  background: #f1f5f9;
  color: #475569;
  padding: 1px 6px;
  border-radius: 4px;
}

.scene-title-text {
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
}

.idempotent-text {
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  color: #475569;
}

.method-badge {
  font-family: 'JetBrains Mono', monospace;
  font-size: 11.5px;
  color: #0f172a;
  background-color: #f1f5f9;
  padding: 2px 6px;
  border-radius: 4px;
  border: 1px solid #e2e8f0;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 8px;
  border-radius: 20px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.2px;
}

.status-bullet {
  width: 5px;
  height: 5px;
  border-radius: 50%;
}

.status-success { background: #ecfdf5; color: #059669; }
.status-success .status-bullet { background: #10b981; }

.status-wait { background: #fffbeb; color: #d97706; }
.status-wait .status-bullet { background: #f59e0b; }

.status-init { background: #eff6ff; color: #2563eb; }
.status-init .status-bullet { background: #3b82f6; }

.status-failed { background: #fef2f2; color: #ef4444; }
.status-failed .status-bullet { background: #ef4444; }

.retry-counter-pill {
  font-family: 'JetBrains Mono', monospace;
  font-weight: 700;
  color: #2563eb;
  background-color: #eff6ff;
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 11.5px;
}

.time-text {
  font-size: 12px;
  color: #64748b;
}

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.mono-code {
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
}

.params-section {
  margin-top: 20px;
}

.section-title {
  font-size: 13px;
  font-weight: 700;
  margin-bottom: 8px;
  color: #0f172a;
}

.json-code {
  background-color: #0f172a;
  color: #e2e8f0;
  padding: 16px;
  border-radius: 8px;
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  overflow-x: auto;
  line-height: 1.5;
}

.timeline-wrapper {
  max-height: 480px;
  overflow-y: auto;
  padding: 10px 10px 10px 0;
}

.timeline-card {
  border-radius: 8px;
}

.timeline-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.retry-count-tag {
  font-weight: 700;
  color: #0f172a;
  font-size: 13px;
}

.error-msg {
  color: #ef4444;
  background-color: #fef2f2;
  padding: 8px 12px;
  border-radius: 6px;
  font-size: 12px;
  border-left: 3px solid #ef4444;
}

.success-msg {
  color: #059669;
  background-color: #ecfdf5;
  padding: 8px 12px;
  border-radius: 6px;
  font-size: 12px;
  border-left: 3px solid #10b981;
}

.cost-time {
  color: #64748b;
  font-size: 12px;
  margin-top: 6px;
}
</style>