<template>
  <div class="scene-config">
    <div class="header">
      <div class="header-left">
        <h2>场景配置管理</h2>
        <div class="stats" v-if="sceneList.length > 0">
          <el-tag type="info" size="small">
            总计: {{ sceneList.length }}个场景
          </el-tag>
          <el-tag type="success" size="small">
            启用: {{ enabledCount }}个
          </el-tag>
          <el-tag type="warning" size="small">
            禁用: {{ disabledCount }}个
          </el-tag>
        </div>
      </div>
      <el-button type="primary" @click="handleAdd">
        <el-icon><Plus /></el-icon>
        新增场景
      </el-button>
    </div>

    <!-- 场景列表 -->
    <el-card>
      <el-table :data="sceneList" v-loading="loading" stripe>
        <el-table-column prop="sceneType" label="场景类型" width="120" />
        <el-table-column prop="sceneName" label="场景名称" width="200" />
        <el-table-column prop="retryIntervals" label="重试间隔" width="300">
          <template #default="{ row }">
            <el-tag v-for="(interval, index) in parseIntervals(row.retryIntervals)" 
                    :key="index" 
                    size="small" 
                    style="margin-right: 5px;">
              {{ interval }}分钟
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="maxRetryCount" label="最大重试次数" width="120" />
        <el-table-column prop="hookClass" label="钩子类" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.hookClass" class="hook-class">{{ row.hookClass }}</span>
            <el-tag v-else type="info" size="small">默认实现</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="enabled" label="状态" width="100">
          <template #default="{ row }">
            <el-switch 
              v-model="row.enabled" 
              :active-value="1"
              :inactive-value="0"
              :before-change="() => handleBeforeChange(row)"
              :loading="row.switching"
            />
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog 
      :title="dialogTitle" 
      v-model="dialogVisible" 
      width="600px"
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
            <el-option label="自定义间隔 (CUSTOM)" value="CUSTOM" />
            <el-option label="固定间隔 (FIXED)" value="FIXED" />
            <el-option label="线性递增 (LINEAR)" value="LINEAR" />
            <el-option label="指数递增 (EXPONENTIAL)" value="EXPONENTIAL" />
          </el-select>
        </el-form-item>

        <el-form-item label="退避基数" prop="backoffBase" v-if="form.backoffStrategy !== 'CUSTOM'">
          <el-input-number 
            v-model="form.backoffBase" 
            :min="1" 
            placeholder="单位：分钟"
            style="width: 100%"
          />
          <div class="form-tip" style="width: 100%; margin-top: 5px;">单位：分钟。用于计算退避间隔。</div>
        </el-form-item>
        
        <el-form-item label="最大重试次数" prop="maxRetryCount" v-if="form.backoffStrategy !== 'CUSTOM'">
          <el-input-number 
            v-model="form.maxRetryCount" 
            :min="1" 
            :max="100"
            placeholder="最大重试次数"
            style="width: 100%"
          />
          <div class="form-tip" style="width: 100%; margin-top: 5px;">非自定义策略下必须指定最大重试次数。</div>
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
            钩子类需要实现 RetryHook 接口，用于自定义状态检查和业务逻辑。留空则使用默认实现。
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { sceneApi } from '@/api'
import RetryIntervalConfig from './components/RetryIntervalConfig.vue'

// 响应式数据
const loading = ref(false)
const sceneList = ref([])
const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref()

// 表单数据
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

// 表单验证规则
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
  ],
  hookClass: [
    { 
      pattern: /^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)*$/, 
      message: '钩子类名格式不正确，应为Java类全限定名', 
      trigger: 'blur' 
    }
  ]
}

// 计算属性
const isEdit = computed(() => !!form.id)
const dialogTitle = computed(() => isEdit.value ? '编辑场景配置' : '新增场景配置')
const enabledCount = computed(() => sceneList.value.filter(scene => scene.enabled === 1 || scene.enabled === true).length)
const disabledCount = computed(() => sceneList.value.filter(scene => scene.enabled === 0 || scene.enabled === false).length)

// 解析重试间隔字符串
const parseIntervals = (intervals) => {
  if (!intervals) return []
  return intervals.split(',').map(item => item.trim()).filter(item => item)
}

// 加载场景列表
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

// 处理新增
const handleAdd = () => {
  resetForm()
  dialogVisible.value = true
}

// 处理编辑
const handleEdit = (row) => {
  Object.assign(form, { ...row })
  form.enabled = (row.enabled === 1 || row.enabled === true) ? 1 : 0
  dialogVisible.value = true
}

// 处理删除
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

// 处理状态切换（使用 before-change 机制，彻底避免加载时误触发）
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

// 处理表单提交
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
      ElMessage.error(isEdit.value ? '更新失败：' + error.response.data.message : '创建失败：' + error.response.data.message)
    } else if (error.message) {
      ElMessage.error(isEdit.value ? '更新失败：' + error.message : '创建失败：' + error.message)
    }
  } finally {
    submitting.value = false
  }
}

// 重置表单
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
  
  if (formRef.value) {
    formRef.value.clearValidate()
  }
}

// 组件挂载时加载数据
onMounted(() => {
  loadSceneList()
})
</script>

<style scoped>
.scene-config {
  padding: 20px;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.header-left {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.header h2 {
  margin: 0;
  color: #303133;
}

.stats {
  display: flex;
  gap: 8px;
}

.form-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 5px;
  line-height: 1.4;
}

:deep(.el-table) {
  font-size: 14px;
}

:deep(.el-table .el-table__cell) {
  padding: 12px 0;
}

:deep(.el-tag) {
  margin-bottom: 2px;
}

.hook-class {
  font-family: 'Courier New', monospace;
  font-size: 12px;
  color: #606266;
}
</style>