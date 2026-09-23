<template>
  <div class="failed-task">
    <!-- 顶栏状态提示 -->
    <el-alert
      :title="$t('failed.alertTitle')"
      type="error"
      :description="$t('failed.alertDesc')"
      show-icon
      :closable="false"
      class="warning-alert"
    />

    <!-- 搜索筛选区 Filters -->
    <el-card class="filter-card">
      <el-form :inline="true" :model="filters" class="demo-form-inline">
        <el-form-item :label="$t('task.filterScene')">
          <el-select v-model="filters.sceneType" :placeholder="$t('task.filterScenePlaceholder')" clearable style="width: 180px">
            <el-option v-for="item in sceneOptions" :key="item.sceneType" :label="`${item.sceneName} (${item.sceneType})`" :value="item.sceneType" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('task.filterKey')">
          <el-input v-model="filters.idempotentKey" :placeholder="$t('task.filterKeyPlaceholder')" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="Fail Time">
          <el-date-picker
            v-model="timeRange"
            type="datetimerange"
            range-separator="~"
            start-placeholder="Start"
            end-placeholder="End"
            value-format="x"
            style="width: 360px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>&nbsp;{{ $t('common.query') }}
          </el-button>
          <el-button @click="resetFilters">{{ $t('common.reset') }}</el-button>
          <el-button type="success" @click="loadTableData" :loading="loading">
            <el-icon><RefreshRight /></el-icon>&nbsp;{{ $t('common.refresh') }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 失败任务数据列表 Table -->
    <el-card class="table-card">
      <template #header>
        <div class="card-header-title">
          <span class="title-text">{{ $t('failed.title') }}</span>
        </div>
      </template>

      <el-table :data="tableData" v-loading="loading" stripe style="width: 100%" class="custom-table">
        <el-table-column prop="taskId" :label="$t('failed.colTaskId')" min-width="190">
          <template #default="{ row }">
            <span class="task-id-mono" :title="row.taskId">{{ row.taskId }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="sceneType" :label="$t('failed.colScene')" min-width="200">
          <template #default="{ row }">
            <div class="scene-cell">
              <span class="scene-id-chip">#{{ row.sceneType }}</span>
              <span class="scene-name-text" :title="getSceneName(row.sceneType)">{{ cleanSceneName(row.sceneType) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="idempotentKey" :label="$t('failed.colIdempotentKey')" min-width="160">
          <template #default="{ row }">
            <span class="idempotent-code" :title="row.idempotentKey">{{ row.idempotentKey }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="failReason" :label="$t('failed.colReason')" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="fail-reason-text">{{ row.failReason }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="retryCount" :label="$t('failed.colRetryCount')" width="110" align="center">
          <template #default="{ row }">
            <span class="retry-badge danger">
              <span class="retry-badge-dot"></span>
              {{ row.retryCount }}
            </span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('failed.colFailTime')" width="170">
          <template #default="{ row }">
            <span class="time-cell">{{ formatTime(row.failTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('common.operation')" width="220" fixed="right">
          <template #default="{ row }">
            <div class="action-buttons">
              <button class="action-btn detail" @click="viewDetail(row)" :title="$t('failed.actionDetail')">
                <el-icon><View /></el-icon> {{ $t('failed.actionDetail') }}
              </button>
              <button class="action-btn recover" @click="recoverTask(row)" :title="$t('failed.actionRecover')">
                <el-icon><Refresh /></el-icon> {{ $t('failed.actionRecover') }}
              </button>
              <button class="action-btn delete" @click="deleteTask(row)" title="删除归档记录">
                <el-icon><Delete /></el-icon>
              </button>
            </div>
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

    <!-- 详情展示 Dialog -->
    <el-dialog
      v-model="detailVisible"
      title="失败任务归档详情"
      width="780px"
      class="detail-dialog"
      destroy-on-close>
      <div v-if="selectedTask" class="dialog-content">
        <el-descriptions :column="2" border class="descriptions-box">
          <el-descriptions-item label="任务ID" :span="2">{{ selectedTask.taskId }}</el-descriptions-item>
          <el-descriptions-item label="场景">{{ getSceneName(selectedTask.sceneType) }} (Type: {{ selectedTask.sceneType }})</el-descriptions-item>
          <el-descriptions-item label="幂等键">{{ selectedTask.idempotentKey }}</el-descriptions-item>
          <el-descriptions-item label="触发方法类" :span="2">{{ selectedTask.methodClass }}</el-descriptions-item>
          <el-descriptions-item label="触发方法名">{{ selectedTask.methodName }}</el-descriptions-item>
          <el-descriptions-item label="重试次数">{{ selectedTask.retryCount }} 次</el-descriptions-item>
          <el-descriptions-item label="任务创建时间">{{ formatTime(selectedTask.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="失败归档时间">{{ formatTime(selectedTask.failTime) }}</el-descriptions-item>
        </el-descriptions>

        <!-- 详细失败原因 -->
        <div class="fail-detail-section">
          <div class="section-title error-title">超限失败原因描述</div>
          <div class="fail-detail-box">
            {{ selectedTask.failReason || '没有记录具体的错误堆栈信息' }}
          </div>
        </div>

        <!-- 参数展示 -->
        <div class="params-section">
          <div class="section-title">方法调用参数 (JSON Payload)</div>
          <pre class="json-code"><code>{{ formatJson(selectedTask.methodParams) }}</code></pre>
        </div>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="detailVisible = false">关闭</el-button>
          <el-button type="danger" plain @click="deleteTask(selectedTask)">彻底删除</el-button>
          <el-button type="success" @click="recoverTask(selectedTask)">一键恢复并重新执行</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, RefreshRight, View, Refresh, Delete } from '@element-plus/icons-vue'
import request from '@/utils/request'

// API URLs
const getFailedList = (params) => request({ url: '/failed/list', method: 'get', params })
const getFailedDetail = (taskId) => request({ url: `/failed/${taskId}`, method: 'get' })
const recoverFailedTask = (taskId) => request({ url: `/failed/${taskId}/recover`, method: 'post' })
const deleteFailedTask = (taskId) => request({ url: `/failed/${taskId}`, method: 'delete' })
const getSceneList = () => request({ url: '/scene/list', method: 'get' })

// State Variables
const loading = ref(false)
const tableData = ref([])
const selectedTask = ref(null)
const detailVisible = ref(false)
const timeRange = ref([])
const sceneOptions = ref([])
const sceneMap = ref({})

const filters = reactive({
  sceneType: '',
  idempotentKey: ''
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

// Formatting and Helper Methods
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

const getSceneName = (sceneType) => {
  return sceneMap.value[sceneType] ? `${sceneMap.value[sceneType]} (${sceneType})` : `场景 (${sceneType})`
}

const cleanSceneName = (sceneType) => {
  const full = sceneMap.value[sceneType]
  if (!full) return `场景 ${sceneType}`
  return full.replace(/^\[Demo\]\s*/, '')
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

// Load failed tasks
const loadTableData = async () => {
  loading.value = true
  try {
    const params = {
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      ...filters
    }
    if (timeRange.value && timeRange.value.length === 2) {
      params.startTime = timeRange.value[0]
      params.endTime = timeRange.value[1]
    }
    const res = await getFailedList(params)
    if (res && res.data) {
      tableData.value = res.data.list || []
      pagination.total = res.data.total || 0
    }
  } catch (e) {
    console.error(e)
    ElMessage.error('加载失败列表数据异常')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.pageNum = 1
  loadTableData()
}

const resetFilters = () => {
  filters.sceneType = ''
  filters.idempotentKey = ''
  timeRange.value = []
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
    const res = await getFailedDetail(row.taskId)
    if (res && res.data) {
      selectedTask.value = res.data
    } else {
      selectedTask.value = row
    }
    detailVisible.value = true
  } catch (e) {
    ElMessage.error('获取归档详情失败')
  }
}

const recoverTask = (row) => {
  ElMessageBox.confirm(
    `确定要一键恢复任务 ${row.taskId} 吗？恢复后将把它重置为 INIT 并放回重试队列。`,
    '恢复任务确认',
    {
      confirmButtonText: '立即恢复',
      cancelButtonText: '取消',
      type: 'success'
    }
  ).then(async () => {
    try {
      const res = await recoverFailedTask(row.taskId)
      if (res && res.success) {
        ElMessage.success('任务成功恢复！已加入队列并重新就绪。')
        detailVisible.value = false
        loadTableData()
      } else {
        ElMessage.error(res.message || '恢复失败')
      }
    } catch (e) {
      console.error(e)
    }
  }).catch(() => {})
}

const deleteTask = (row) => {
  ElMessageBox.confirm(
    `确定要永久删除任务 ${row.taskId} 吗？删除后不可恢复。`,
    '彻底删除警告',
    {
      confirmButtonText: '彻底删除',
      cancelButtonText: '取消',
      type: 'danger'
    }
  ).then(async () => {
    try {
      const res = await deleteFailedTask(row.taskId)
      if (res && res.success) {
        ElMessage.success('失败归档已永久物理删除')
        detailVisible.value = false
        loadTableData()
      } else {
        ElMessage.error(res.message || '删除失败')
      }
    } catch (e) {
      console.error(e)
    }
  }).catch(() => {})
}

onMounted(() => {
  loadScenes()
  loadTableData()
})
</script>

<style scoped>
.failed-task {
  padding: 24px;
  background-color: #f7f8fa;
  min-height: 100vh;
}

.warning-alert {
  margin-bottom: 24px;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(245, 108, 108, 0.08);
}

/* Filters and cards styling */
.filter-card {
  border-radius: 12px;
  margin-bottom: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.03);
}

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

.task-id-mono {
  font-family: 'SF Mono', Consolas, Monaco, monospace;
  font-size: 12.5px;
  color: #1e293b;
  font-weight: 600;
  letter-spacing: -0.2px;
}

.scene-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  max-width: 100%;
}

.scene-id-chip {
  font-family: 'SF Mono', Consolas, monospace;
  font-size: 11px;
  font-weight: 700;
  color: #6366f1;
  background: rgba(99, 102, 241, 0.08);
  border: 1px solid rgba(99, 102, 241, 0.2);
  padding: 1px 5px;
  border-radius: 4px;
  flex-shrink: 0;
}

.scene-name-text {
  font-size: 12.5px;
  color: #334155;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.idempotent-code {
  font-family: 'SF Mono', Consolas, monospace;
  font-size: 11.5px;
  color: #475569;
  background: #f1f5f9;
  padding: 2px 6px;
  border-radius: 4px;
  max-width: 150px;
  display: inline-block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
}

.fail-reason-text {
  color: #ef4444;
  font-weight: 500;
  font-family: 'SF Mono', Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
}

.retry-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 8px;
  border-radius: 20px;
  font-size: 11.5px;
  font-weight: 600;
}

.retry-badge.danger {
  background: #fef2f2;
  color: #ef4444;
  border: 1px solid rgba(239, 68, 68, 0.2);
}

.retry-badge-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
}

.time-cell {
  font-size: 12px;
  color: #64748b;
  font-variant-numeric: tabular-nums;
}

.action-buttons {
  display: flex;
  align-items: center;
  gap: 4px;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 3px 8px;
  border-radius: 5px;
  font-size: 11.5px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  border: 1px solid transparent;
  background: #f8fafc;
  color: #475569;
}

.action-btn:hover {
  transform: translateY(-1px);
}

.action-btn.detail {
  color: #6366f1;
  background: rgba(99, 102, 241, 0.06);
  border-color: rgba(99, 102, 241, 0.2);
}

.action-btn.detail:hover {
  background: #6366f1;
  color: #ffffff;
  border-color: #6366f1;
  box-shadow: 0 2px 6px rgba(99, 102, 241, 0.3);
}

.action-btn.recover {
  color: #10b981;
  background: rgba(16, 185, 129, 0.06);
  border-color: rgba(16, 185, 129, 0.2);
}

.action-btn.recover:hover {
  background: #10b981;
  color: #ffffff;
  border-color: #10b981;
  box-shadow: 0 2px 6px rgba(16, 185, 129, 0.3);
}

.action-btn.delete {
  color: #ef4444;
  background: rgba(239, 68, 68, 0.06);
  border-color: rgba(239, 68, 68, 0.2);
  padding: 3px 6px;
}

.action-btn.delete:hover {
  background: #ef4444;
  color: #ffffff;
  border-color: #ef4444;
  box-shadow: 0 2px 6px rgba(239, 68, 68, 0.3);
}

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

/* Detail dialog styling */
.descriptions-box {
  margin-bottom: 24px;
}

.fail-detail-section {
  margin-bottom: 24px;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 12px;
  color: #303133;
  padding-left: 8px;
}

.error-title {
  border-left: 4px solid #f56c6c;
}

.params-section .section-title {
  border-left: 4px solid #409eff;
}

.fail-detail-box {
  background-color: #fff5f5;
  color: #c0392b;
  border: 1px solid #fcd3d3;
  padding: 16px;
  border-radius: 8px;
  font-size: 13.5px;
  line-height: 1.6;
  font-family: Consolas, Monaco, monospace;
  white-space: pre-wrap;
  word-break: break-all;
  box-shadow: inset 0 1px 4px rgba(245, 108, 108, 0.03);
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
</style>