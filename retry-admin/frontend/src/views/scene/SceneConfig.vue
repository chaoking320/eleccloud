<template>
  <div class="scene-config">
    <!-- Top Action & Statistics Header -->
    <div class="header">
      <div class="header-left">
        <div class="title-row">
          <h2 class="page-title">{{ $t('scene.title') }}</h2>
          <span class="page-subtitle">{{ $t('scene.subtitle') }}</span>
        </div>
        <div class="stats-pills" v-if="sceneList.length > 0">
          <span class="pill-item total">{{ $t('scene.totalScenes', { total: sceneList.length }) }}</span>
          <span class="pill-item enabled">{{ $t('scene.enabledCount', { count: enabledCount }) }}</span>
          <span class="pill-item disabled" v-if="disabledCount > 0">{{ $t('scene.disabledCount', { count: disabledCount }) }}</span>
        </div>
      </div>
      <el-button type="primary" :icon="Plus" class="btn-create" @click="handleAdd">
        {{ $t('scene.addScene') }}
      </el-button>
    </div>

    <!-- 场景数据表格 -->
    <el-card shadow="never" class="table-card">
      <el-table :data="sceneList" v-loading="loading" stripe style="width: 100%" class="custom-scene-table">
        <!-- 场景类型 -->
        <el-table-column prop="sceneType" :label="$t('scene.colSceneType')" width="120">
          <template #default="{ row }">
            <span class="scene-code-badge">ID: {{ row.sceneType }}</span>
          </template>
        </el-table-column>

        <!-- 场景名称 -->
        <el-table-column prop="sceneName" :label="$t('scene.colSceneName')" min-width="160">
          <template #default="{ row }">
            <span class="scene-name-text">{{ row.sceneName }}</span>
          </template>
        </el-table-column>

        <!-- 重试策略与间隔流水线 -->
        <el-table-column :label="$t('scene.colRetryPipeline')" min-width="340">
          <template #default="{ row }">
            <div class="strategy-pipeline-cell">
              <!-- 策略类型标签 -->
              <span class="strategy-pill" :class="getStrategyClass(row.backoffStrategy)">
                {{ getStrategyLabel(row.backoffStrategy) }}
              </span>

              <!-- 间隔流水线序列 -->
              <div class="pipeline-flow" v-if="parseIntervals(row.retryIntervals).length > 0">
                <template v-for="(interval, idx) in parseIntervals(row.retryIntervals)" :key="idx">
                  <span class="flow-step-pill">
                    <span class="step-num">{{ idx + 1 }}</span>
                    <span class="step-val">{{ formatIntervalText(interval) }}</span>
                  </span>
                  <span v-if="idx < parseIntervals(row.retryIntervals).length - 1" class="flow-connector">➔</span>
                </template>
              </div>

              <!-- 非自定义退避公式提示 -->
              <div v-else class="strategy-formula">
                <span v-if="row.backoffStrategy === 'LINEAR'" class="formula-text">
                  Interval = n × {{ row.backoffBase || 1 }}m
                </span>
                <span v-else-if="row.backoffStrategy === 'EXPONENTIAL'" class="formula-text">
                  Interval = 2^(n-1) × {{ row.backoffBase || 1 }}m
                </span>
                <span v-else-if="row.backoffStrategy === 'FIXED'" class="formula-text">
                  Fixed {{ row.backoffBase || 1 }}m
                </span>
                <span v-else class="formula-text text-muted">Default strategy</span>
              </div>
            </div>
          </template>
        </el-table-column>

        <!-- 最大重试限制 -->
        <el-table-column :label="$t('scene.colRetryLimit')" width="140">
          <template #default="{ row }">
            <div class="limit-cell">
              <span class="count-badge">{{ $t('scene.maxRetries', { count: row.maxRetryCount || 3 }) }}</span>
              <span v-if="row.maxRetryDuration" class="duration-text">
                {{ $t('scene.timeoutSec', { sec: row.maxRetryDuration }) }}
              </span>
            </div>
          </template>
        </el-table-column>

        <!-- 钩子类 -->
        <el-table-column prop="hookClass" :label="$t('scene.colHookClass')" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.hookClass" class="hook-class-pill" :title="row.hookClass">
              {{ getSimpleHookName(row.hookClass) }}
            </span>
            <span v-else class="hook-default-pill">{{ locale === 'en' ? 'Default Reflection (No Hook)' : '系统默认反射 (无Hook)' }}</span>
          </template>
        </el-table-column>

        <!-- 状态切换 -->
        <el-table-column prop="enabled" :label="$t('scene.colStatus')" width="100">
          <template #default="{ row }">
            <el-switch 
              v-model="row.enabled" 
              :active-value="1" 
              :inactive-value="0"
              active-color="#10b981"
              inactive-color="#cbd5e1"
              :before-change="() => handleBeforeChange(row)"
              :loading="row.switching"
            />
          </template>
        </el-table-column>

        <!-- 操作 -->
        <el-table-column :label="$t('scene.colAction')" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" :icon="Edit" @click="handleEdit(row)">
              {{ $t('common.edit') }}
            </el-button>
            <el-button link type="danger" size="small" :icon="Delete" @click="handleDelete(row)">
              {{ $t('common.delete') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog 
      :title="dialogTitle" 
      v-model="dialogVisible" 
      width="640px"
      @close="resetForm"
    >
      <el-form 
        ref="formRef" 
        :model="form" 
        :rules="rules" 
        label-width="120px"
      >
        <el-form-item label="场景类型" prop="sceneType">
          <el-input-number 
            v-model="form.sceneType" 
            :min="1" 
            :max="999999"
            :disabled="isEdit"
            placeholder="请输入场景类型编码"
            style="width: 100%"
          />
        </el-form-item>
        
        <el-form-item label="场景名称" prop="sceneName">
          <el-input 
            v-model="form.sceneName" 
            placeholder="请输入场景名称"
            maxlength="50"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="退避策略" prop="backoffStrategy">
          <el-select v-model="form.backoffStrategy" placeholder="请选择退避策略" style="width: 100%">
            <el-option label="自定义间隔 (CUSTOM - 支持秒级/分钟级精细序列)" value="CUSTOM" />
            <el-option label="固定间隔 (FIXED - 每次等待固定时间)" value="FIXED" />
            <el-option label="线性递增 (LINEAR - 随次数线性递增)" value="LINEAR" />
            <el-option label="指数递增 (EXPONENTIAL - 2^n 倍数指数退避)" value="EXPONENTIAL" />
          </el-select>
        </el-form-item>

        <el-form-item label="退避基数" prop="backoffBase" v-if="form.backoffStrategy !== 'CUSTOM'">
          <el-input-number 
            v-model="form.backoffBase" 
            :min="1" 
            placeholder="单位：分钟"
            style="width: 100%"
          />
          <div class="form-tip">单位：分钟。用于计算退避间隔。</div>
        </el-form-item>
        
        <el-form-item label="最大重试次数" prop="maxRetryCount" v-if="form.backoffStrategy !== 'CUSTOM'">
          <el-input-number 
            v-model="form.maxRetryCount" 
            :min="1" 
            :max="100"
            placeholder="最大重试次数"
            style="width: 100%"
          />
          <div class="form-tip">非自定义策略下必须指定最大重试次数。</div>
        </el-form-item>
        
        <el-form-item label="重试间隔" prop="retryIntervals" v-if="form.backoffStrategy === 'CUSTOM'">
          <retry-interval-config v-model="form.retryIntervals" />
        </el-form-item>
        
        <el-form-item label="钩子类" prop="hookClass">
          <el-input 
            v-model="form.hookClass" 
            placeholder="请输入钩子类全限定名，如：com.example.MyRetryHook"
            maxlength="256"
          />
          <div class="form-tip">
            钩子类需实现 RetryHook 接口，用于三步幂等状态检查 (checkStatus ➜ doQuery ➜ doCallback)。
          </div>
        </el-form-item>
        
        <el-form-item label="启用状态" prop="enabled">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">
          {{ isEdit ? '更新' : '创建' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete } from '@element-plus/icons-vue'
import { sceneApi } from '@/api'
import RetryIntervalConfig from './components/RetryIntervalConfig.vue'

const { t, locale } = useI18n()
const loading = ref(false)
const sceneList = ref([])
const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref()

const form = reactive({
  id: null,
  sceneType: null,
  sceneName: '',
  retryIntervals: '',
  hookClass: '',
  enabled: true,
  backoffStrategy: 'CUSTOM',
  backoffBase: 1
})

const rules = {
  sceneType: [
    { required: true, message: '请输入场景类型', trigger: 'blur' },
    { type: 'number', min: 1, message: '场景类型必须大于0', trigger: 'blur' }
  ],
  sceneName: [
    { required: true, message: '请输入场景名称', trigger: 'blur' },
    { min: 2, max: 50, message: '场景名称长度在2-50个字符', trigger: 'blur' }
  ],
  retryIntervals: [
    { 
      validator: (rule, value, callback) => {
        if (form.backoffStrategy === 'CUSTOM' && !value) {
          callback(new Error('请配置重试间隔'))
        } else {
          callback()
        }
      }, 
      trigger: 'blur' 
    }
  ]
}

const isEdit = computed(() => !!form.id)
const dialogTitle = computed(() => isEdit.value ? (locale.value === 'en' ? 'Edit Scene' : '编辑场景配置') : (locale.value === 'en' ? 'Add Scene' : '新增场景配置'))
const enabledCount = computed(() => sceneList.value.filter(scene => scene.enabled === 1 || scene.enabled === true).length)
const disabledCount = computed(() => sceneList.value.filter(scene => scene.enabled === 0 || scene.enabled === false).length)

const parseIntervals = (intervals) => {
  if (!intervals) return []
  return intervals.split(',').map(item => item.trim()).filter(item => item)
}

const formatIntervalText = (val) => {
  if (val === '0') return '<5s'
  return `${val}m`
}

const getStrategyLabel = (strategy) => {
  switch (strategy) {
    case 'FIXED': return t('scene.strategyFixed')
    case 'LINEAR': return t('scene.strategyLinear')
    case 'EXPONENTIAL': return t('scene.strategyExponential')
    case 'CUSTOM': default: return t('scene.strategyCustom')
  }
}

const getStrategyClass = (strategy) => {
  switch (strategy) {
    case 'FIXED': return 'strat-fixed'
    case 'LINEAR': return 'strat-linear'
    case 'EXPONENTIAL': return 'strat-exp'
    case 'CUSTOM': default: return 'strat-custom'
  }
}

const getSimpleHookName = (fullClass) => {
  if (!fullClass) return ''
  const parts = fullClass.split('.')
  return parts[parts.length - 1]
}

const loadSceneList = async () => {
  try {
    loading.value = true
    const response = await sceneApi.getSceneList()
    sceneList.value = response.data || []
  } catch (error) {
    ElMessage.error('加载场景列表失败：' + (error.response?.data?.message || error.message))
  } finally {
    loading.value = false
  }
}

const handleAdd = () => {
  resetForm()
  dialogVisible.value = true
}

const handleEdit = (row) => {
  Object.assign(form, { ...row })
  form.enabled = (row.enabled === 1 || row.enabled === true) ? 1 : 0
  dialogVisible.value = true
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除场景"${row.sceneName}"吗？删除后不可恢复。`,
      '确认删除',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    await sceneApi.deleteScene(row.id)
    ElMessage.success('删除成功')
    loadSceneList()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败：' + (error.response?.data?.message || error.message))
    }
  }
}

const handleBeforeChange = (row) => {
  const currentEnabled = (row.enabled === 1 || row.enabled === true) ? 1 : 0
  const nextStatus = currentEnabled === 1 ? 0 : 1
  const action = nextStatus === 1 ? '启用' : '禁用'
  
  return new Promise((resolve, reject) => {
    ElMessageBox.confirm(
      `确定要${action}场景"${row.sceneName}"吗？`,
      `确认${action}`,
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    ).then(async () => {
      try {
        row.switching = true
        await sceneApi.updateScene(row.id, { enabled: nextStatus })
        ElMessage.success(`${action}成功`)
        await loadSceneList()
        resolve(true)
      } catch (error) {
        ElMessage.error(`${action}失败：` + (error.response?.data?.message || error.message))
        reject(false)
      } finally {
        row.switching = false
      }
    }).catch(() => {
      reject(false)
    })
  })
}

const handleSubmit = async () => {
  try {
    await formRef.value.validate()
    submitting.value = true
    const data = { ...form }
    delete data.id
    
    if (isEdit.value) {
      await sceneApi.updateScene(form.id, data)
      ElMessage.success('更新成功')
    } else {
      await sceneApi.createScene(data)
      ElMessage.success('创建成功')
    }
    
    dialogVisible.value = false
    loadSceneList()
  } catch (error) {
    if (error.response?.data?.message) {
      ElMessage.error(error.response.data.message)
    } else if (error.message) {
      ElMessage.error(error.message)
    }
  } finally {
    submitting.value = false
  }
}

const resetForm = () => {
  Object.assign(form, {
    id: null,
    sceneType: null,
    sceneName: '',
    retryIntervals: '',
    hookClass: '',
    enabled: true,
    backoffStrategy: 'CUSTOM',
    backoffBase: 1
  })
  if (formRef.value) formRef.value.clearValidate()
}

onMounted(() => {
  loadSceneList()
})
</script>

<style scoped>
.scene-config {
  padding: 24px;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
  flex-wrap: wrap;
  gap: 16px;
}

.header-left {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.title-row {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.page-title {
  margin: 0;
  font-size: 22px;
  font-weight: 800;
  color: #0f172a;
  letter-spacing: -0.3px;
}

.page-subtitle {
  font-size: 13px;
  color: #64748b;
}

.stats-pills {
  display: flex;
  gap: 8px;
  margin-top: 4px;
}

.pill-item {
  font-size: 12px;
  padding: 2px 10px;
  border-radius: 20px;
  font-weight: 500;
}

.pill-item.total {
  background: #f1f5f9;
  color: #475569;
}

.pill-item.enabled {
  background: #ecfdf5;
  color: #059669;
}

.pill-item.disabled {
  background: #fffbeb;
  color: #d97706;
}

.btn-create {
  height: 38px;
  padding: 0 18px;
  font-weight: 600;
}

.table-card {
  background: #ffffff;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
}

.scene-code-badge {
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
  font-weight: 700;
  background: #eff6ff;
  color: #2563eb;
  padding: 3px 8px;
  border-radius: 6px;
  border: 1px solid #dbeafe;
}

.scene-name-text {
  font-weight: 600;
  color: #1e293b;
  font-size: 13.5px;
}

/* Strategy & Pipeline Visualization */
.strategy-pipeline-cell {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.strategy-pill {
  font-size: 11px;
  font-weight: 700;
  padding: 3px 8px;
  border-radius: 4px;
  letter-spacing: 0.2px;
}

.strat-custom { background: #f5f3ff; color: #7c3aed; border: 1px solid #ede9fe; }
.strat-fixed  { background: #ecfdf5; color: #059669; border: 1px solid #d1fae5; }
.strat-linear { background: #eff6ff; color: #2563eb; border: 1px solid #dbeafe; }
.strat-exp    { background: #fffbeb; color: #d97706; border: 1px solid #fef3c7; }

.pipeline-flow {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  background: #f8fafc;
  padding: 3px 8px;
  border-radius: 6px;
  border: 1px solid #e2e8f0;
}

.flow-step-pill {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 11px;
  background: #ffffff;
  border: 1px solid #cbd5e1;
  padding: 1px 6px;
  border-radius: 4px;
}

.step-num {
  color: #94a3b8;
  font-size: 9px;
  font-weight: 700;
}

.step-val {
  color: #0f172a;
  font-weight: 600;
  font-family: 'JetBrains Mono', monospace;
}

.flow-connector {
  font-size: 9px;
  color: #94a3b8;
}

.strategy-formula {
  font-size: 12px;
  color: #475569;
  font-family: 'JetBrains Mono', monospace;
}

.limit-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.count-badge {
  font-size: 12px;
  font-weight: 600;
  color: #0f172a;
}

.duration-text {
  font-size: 11px;
  color: #94a3b8;
}

.hook-class-pill {
  font-family: 'JetBrains Mono', monospace;
  font-size: 11.5px;
  color: #0f172a;
  background: #f1f5f9;
  padding: 2px 8px;
  border-radius: 4px;
  border: 1px solid #e2e8f0;
}

.hook-default-pill {
  font-size: 11px;
  color: #94a3b8;
}

.form-tip {
  font-size: 12px;
  color: #94a3b8;
  margin-top: 4px;
}
</style>