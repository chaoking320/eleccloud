<template>
  <div class="task-monitor">
    <!-- 顶部数据看板 Dashboard Cards -->
    <el-row :gutter="20" class="stat-dashboard">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card total-card">
          <div class="stat-content">
            <div class="stat-label">总任务数</div>
            <div class="stat-value">{{ stats.total }}</div>
          </div>
          <div class="stat-icon">
            <el-icon><Tickets /></el-icon>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card init-card">
          <div class="stat-content">
            <div class="stat-label">就绪状态 (INIT)</div>
            <div class="stat-value">{{ stats.init }}</div>
          </div>
          <div class="stat-icon">
            <el-icon><Timer /></el-icon>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card wait-card">
          <div class="stat-content">
            <div class="stat-label">等待回调 (WAIT)</div>
            <div class="stat-value">{{ stats.wait }}</div>
          </div>
          <div class="stat-icon">
            <el-icon><Refresh /></el-icon>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card failed-card">
          <div class="stat-content">
            <div class="stat-label">已转失败 (FAILED)</div>
            <div class="stat-value">{{ stats.failed }}</div>
          </div>
          <div class="stat-icon">
            <el-icon><Warning /></el-icon>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 搜索筛选区 Filters -->
    <el-card class="filter-card">
      <el-form :inline="true" :model="filters" class="demo-form-inline">
        <el-form-item label="场景类型">
          <el-select v-model="filters.sceneType" placeholder="选择场景" clearable style="width: 180px">
            <el-option label="[Demo] 电商退款 (10)" :value="10" />
            <el-option label="[Demo] 酒店结算 (11)" :value="11" />
            <el-option label="[Demo] 库存同步 (12)" :value="12" />
            <el-option label="退款场景 (1)" :value="1" />
            <el-option label="结算场景 (2)" :value="2" />
            <el-option label="库存场景 (3)" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="任务状态">
          <el-select v-model="filters.taskStatus" placeholder="选择状态" clearable style="width: 160px">
            <el-option label="INIT" value="INIT" />
            <el-option label="WAIT" value="WAIT" />
            <el-option label="SUCCESS" value="SUCCESS" />
          </el-select>
        </el-form-item>
        <el-form-item label="幂等键 (Idempotent Key)">
          <el-input v-model="filters.idempotentKey" placeholder="请输入幂等键" clearable style="width: 240px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>&nbsp;查询
          </el-button>
          <el-button @click="resetFilters">重置</el-button>
          <el-button type="success" @click="loadAllData" :loading="loading">
            <el-icon><RefreshRight /></el-icon>&nbsp;刷新
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 任务数据列表 Table -->
    <el-card class="table-card">
      <template #header>
        <div class="card-header-title">
          <span class="title-text">重试任务监控列表</span>
        </div>
      </template>

      <el-table :data="tableData" v-loading="loading" stripe style="width: 100%" class="custom-table">
        <el-table-column prop="taskId" label="任务ID" width="180" show-overflow-tooltip />
        <el-table-column prop="sceneType" label="场景" width="160">
          <template #default="{ row }">
            <el-tag :type="getSceneTagType(row.sceneType)" effect="plain">
              {{ getSceneName(row.sceneType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="idempotentKey" label="幂等键" width="180" show-overflow-tooltip />
        <el-table-column prop="methodName" label="触发方法" width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="method-name">{{ row.methodName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="taskStatus" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.taskStatus)" effect="dark" class="status-tag">
              {{ row.taskStatus }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="重试次数" width="120">
          <template #default="{ row }">
            <span class="retry-badge">{{ row.retryCount }} / {{ row.maxRetryCount }}</span>
          </template>
        </el-table-column>
        <el-table-column label="下次执行时间" width="180">
          <template #default="{ row }">
            {{ formatTime(row.nextRetryTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="viewDetail(row)">
              <el-icon><View /></el-icon>&nbsp;详情
            </el-button>
            <el-button 
              link 
              type="warning" 
              size="small" 
              @click="triggerRetry(row)"
              :disabled="row.taskStatus === 'SUCCESS'">
              <el-icon><Refresh /></el-icon>&nbsp;重试
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
      title="重试任务详细信息及历史轨迹"
      width="800px"
      class="detail-dialog"
      destroy-on-close>
      <div v-if="selectedTask" class="dialog-content">
        <el-tabs type="border-card">
          <!-- 任务基本信息 -->
          <el-tab-pane label="基本属性">
            <el-descriptions :column="2" border class="descriptions-box">
              <el-descriptions-item label="任务ID" :span="2">{{ selectedTask.taskId }}</el-descriptions-item>
              <el-descriptions-item label="场景名称">
                {{ getSceneName(selectedTask.sceneType) }} (Type: {{ selectedTask.sceneType }})
              </el-descriptions-item>
              <el-descriptions-item label="幂等键">{{ selectedTask.idempotentKey }}</el-descriptions-item>
              <el-descriptions-item label="业务方法类" :span="2">{{ selectedTask.methodClass }}</el-descriptions-item>
              <el-descriptions-item label="业务方法名">{{ selectedTask.methodName }}</el-descriptions-item>
              <el-descriptions-item label="当前状态">
                <el-tag :type="getStatusType(selectedTask.taskStatus)" effect="dark">{{ selectedTask.taskStatus }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="已重试次数">{{ selectedTask.retryCount }} 次</el-descriptions-item>
              <el-descriptions-item label="最大限制">{{ selectedTask.maxRetryCount }} 次</el-descriptions-item>
              <el-descriptions-item label="创建时间">{{ formatTime(selectedTask.createTime) }}</el-descriptions-item>
              <el-descriptions-item label="更新时间">{{ formatTime(selectedTask.updateTime) }}</el-descriptions-item>
            </el-descriptions>

            <!-- 参数展示 -->
            <div class="params-section">
              <div class="section-title">业务方法调用参数 (JSON)</div>
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
                      <span class="retry-count-tag">第 {{ history.retryCount }} 次重试</span>
                      <el-tag :type="getHistoryType(history.executeResult)" size="small" effect="dark">
                        {{ history.executeResult }}
                      </el-tag>
                    </div>
                    <div class="timeline-card-body">
                      <p v-if="history.errorMessage && history.executeResult !== 'SUCCESS'" class="error-msg">
                        <strong>异常信息：</strong>{{ formatErrorMessage(history.errorMessage) }}
                      </p>
                      <p v-else-if="history.errorMessage && history.executeResult === 'SUCCESS'" class="success-msg">
                        <strong>执行反馈：</strong>{{ formatErrorMessage(history.errorMessage) }}
                      </p>
                      <p class="cost-time"><strong>耗时：</strong>{{ history.costTime }} ms</p>
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

// API URLs
const getTaskList = (params) => request({ url: '/task/list', method: 'get', params })
const getTaskDetail = (taskId) => request({ url: `/task/${taskId}`, method: 'get' })
const getTaskHistory = (taskId) => request({ url: `/task/${taskId}/history`, method: 'get' })
const getDashboardStats = () => request({ url: '/task/stats', method: 'get' })
const triggerManualRetry = (taskId) => request({ url: `/task/${taskId}/retry`, method: 'post' })

// State Variables
const loading = ref(false)
const tableData = ref([])
const historyList = ref([])
const selectedTask = ref(null)
const detailVisible = ref(false)

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

// Methods
const getSceneName = (sceneType) => {
  const map = {
    1: '退款场景 (1)',
    2: '结算场景 (2)',
    3: '库存场景 (3)',
    10: '电商退款 (10)',
    11: '酒店结算 (11)',
    12: '库存同步 (12)'
  }
  return map[sceneType] || `场景 (${sceneType})`
}

const getSceneTagType = (sceneType) => {
  const map = {
    1: 'primary',
    2: 'success',
    3: 'warning',
    10: 'primary',
    11: 'success',
    12: 'warning'
  }
  return map[sceneType] || 'info'
}

const getStatusType = (status) => {
  switch (status) {
    case 'SUCCESS': return 'success'
    case 'WAIT': return 'warning'
    case 'INIT': return 'info'
    case 'FAILED': return 'danger'
    default: return 'info'
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
    case 'SUCCESS': return '#67C23A'
    case 'FAILED': return '#F56C6C'
    case 'RETRY': return '#E6A23C'
    default: return '#909399'
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

// Load Stats and Table
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

// Actions
const viewDetail = async (row) => {
  try {
    const resDetail = await getTaskDetail(row.taskId)
    if (resDetail && resDetail.data) {
      selectedTask.value = resDetail.data
    } else {
      selectedTask.value = row
    }
    
    // Load history timeline
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
        ElMessage.success('手动触发重试已提交，正在远程执行...')
        loadAllData()
        if (detailVisible.value) {
          // Refresh details if visible
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
  loadAllData()
})
</script>

<style scoped>
.task-monitor {
  padding: 20px;
  background-color: #f0f2f5;
  box-sizing: border-box;
}

/* Dashboard cards styling with beautiful linear-gradients */
.stat-dashboard {
  margin-bottom: 20px;
}

.stat-card {
  height: 96px;
  border: none;
  border-radius: 10px;
  color: #fff;
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  overflow: hidden;
}

.stat-card :deep(.el-card__body) {
  padding: 16px 20px;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-sizing: border-box;
  overflow: hidden;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 16px rgba(0, 0, 0, 0.12);
}

.total-card {
  background: linear-gradient(135deg, #1890ff 0%, #36cfc9 100%);
}

.init-card {
  background: linear-gradient(135deg, #597ef7 0%, #722ed1 100%);
}

.wait-card {
  background: linear-gradient(135deg, #fa8c16 0%, #ffc069 100%);
}

.failed-card {
  background: linear-gradient(135deg, #ff4d4f 0%, #ff7875 100%);
}

.stat-content {
  flex: 1;
}

.stat-label {
  font-size: 13px;
  opacity: 0.9;
  margin-bottom: 6px;
  font-weight: 500;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  line-height: 1;
}

.stat-icon {
  font-size: 36px;
  opacity: 0.35;
  display: flex;
  align-items: center;
}

/* Filter area styling */
.filter-card {
  border-radius: 12px;
  margin-bottom: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.03);
}

/* Table area styling */
.table-card {
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.03);
  margin-bottom: 24px;
}

.card-header-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.title-text {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.custom-table {
  border-radius: 8px;
  overflow: hidden;
}

.method-name {
  font-family: Menlo, Monaco, Consolas, "Courier New", monospace;
  font-size: 13px;
  color: #606266;
  background-color: #f4f4f5;
  padding: 2px 6px;
  border-radius: 4px;
}

.status-tag {
  font-weight: 600;
  letter-spacing: 0.5px;
}

.retry-badge {
  font-weight: bold;
  color: #409eff;
  background-color: #ecf5ff;
  padding: 4px 8px;
  border-radius: 20px;
  font-size: 12px;
}

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

/* Timeline and details dialog styling */
.descriptions-box {
  margin-bottom: 20px;
}

.params-section {
  margin-top: 20px;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 10px;
  color: #303133;
  border-left: 4px solid #409eff;
  padding-left: 8px;
}

.json-code {
  background-color: #2d3748;
  color: #a3b1c6;
  padding: 16px;
  border-radius: 8px;
  font-family: Consolas, Monaco, monospace;
  font-size: 13px;
  overflow-x: auto;
  line-height: 1.5;
  box-shadow: inset 0 2px 8px rgba(0, 0, 0, 0.15);
}

.timeline-wrapper {
  max-height: 480px;
  overflow-y: auto;
  padding: 10px 10px 10px 0;
}

.timeline-card {
  border-radius: 8px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.04);
}

.timeline-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.retry-count-tag {
  font-weight: 600;
  color: #303133;
}

.timeline-card-body p {
  margin: 6px 0;
  font-size: 13px;
}

.error-msg {
  color: #f56c6c;
  background-color: #fef0f0;
  padding: 8px 12px;
  border-radius: 4px;
  word-break: break-all;
  border-left: 3px solid #f56c6c;
}

.success-msg {
  color: #67c23a;
  background-color: #f0f9eb;
  padding: 8px 12px;
  border-radius: 4px;
  word-break: break-all;
  border-left: 3px solid #67c23a;
}

.cost-time {
  color: #909399;
}
</style>