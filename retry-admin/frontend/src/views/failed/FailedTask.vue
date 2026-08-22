<template>
  <div class="failed-task">
    <!-- 顶栏状态提示 -->
    <el-alert
      title="失败超限任务说明"
      type="error"
      description="当一个重试任务尝试了配置的最大次数（例如 4 次）依然返回失败、或者超时无法恢复时，重试平台会将其移入本失败任务表，停止自动重试。您可以排查底层业务或下游接口后，手动点击【一键恢复】将其重新放回重试队列。"
      show-icon
      :closable="false"
      class="warning-alert"
    />

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
        <el-form-item label="幂等键 (Idempotent Key)">
          <el-input v-model="filters.idempotentKey" placeholder="请输入幂等键" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="失败时间">
          <el-date-picker
            v-model="timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="x"
            style="width: 360px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>&nbsp;查询
          </el-button>
          <el-button @click="resetFilters">重置</el-button>
          <el-button type="success" @click="loadTableData" :loading="loading">
            <el-icon><RefreshRight /></el-icon>&nbsp;刷新
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 失败任务数据列表 Table -->
    <el-card class="table-card">
      <template #header>
        <div class="card-header-title">
          <span class="title-text">超限失败任务管理 (停止自动重试)</span>
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
        <el-table-column prop="failReason" label="核心失败原因" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="fail-reason-text">{{ row.failReason }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="retryCount" label="重试次数" width="100">
          <template #default="{ row }">
            <el-tag type="danger" size="small" effect="dark" round>
              {{ row.retryCount }} 次
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="归档时间" width="180">
          <template #default="{ row }">
            {{ formatTime(row.failTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="viewDetail(row)">
              <el-icon><View /></el-icon>&nbsp;详情
            </el-button>
            <el-button link type="success" size="small" @click="recoverTask(row)">
              <el-icon><Refresh /></el-icon>&nbsp;一键恢复
            </el-button>
            <el-button link type="danger" size="small" @click="deleteTask(row)">
              <el-icon><Delete /></el-icon>&nbsp;删除
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

// State Variables
const loading = ref(false)
const tableData = ref([])
const selectedTask = ref(null)
const detailVisible = ref(false)
const timeRange = ref([])

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

.fail-reason-text {
  color: #f56c6c;
  font-weight: 500;
  font-family: Menlo, Monaco, Consolas, monospace;
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