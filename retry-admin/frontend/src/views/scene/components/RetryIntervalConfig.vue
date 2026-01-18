<template>
  <div class="retry-interval-config">
    <div class="interval-list">
      <div 
        v-for="(interval, index) in intervals" 
        :key="index" 
        class="interval-item"
      >
        <el-input-number
          v-model="interval.value"
          :min="1"
          :max="10080"
          placeholder="间隔时间"
          @change="updateIntervals"
        />
        <span class="unit">分钟</span>
        <el-button 
          type="danger" 
          size="small" 
          :icon="Delete"
          circle
          @click="removeInterval(index)"
          :disabled="intervals.length <= 1"
        />
      </div>
    </div>
    
    <div class="actions">
      <el-button 
        type="primary" 
        size="small" 
        :icon="Plus"
        @click="addInterval"
        :disabled="intervals.length >= 10"
      >
        添加间隔
      </el-button>
      
      <div class="tips">
        <el-icon><InfoFilled /></el-icon>
        <span>重试间隔按顺序执行，建议设置为递增序列（如：1,5,10,30分钟）。最多支持10个间隔。</span>
      </div>
    </div>
    
    <div class="preview" v-if="intervals.length > 0">
      <div class="preview-title">重试时间预览：</div>
      <div class="preview-content">
        <el-tag 
          v-for="(time, idx) in previewTimes" 
          :key="idx"
          size="small"
          type="info"
          style="margin-right: 8px; margin-bottom: 4px;"
        >
          第{{ idx + 1 }}次：{{ time }}
        </el-tag>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { Plus, Delete, InfoFilled } from '@element-plus/icons-vue'

// Props
const props = defineProps({
  modelValue: {
    type: String,
    default: ''
  }
})

// Emits
const emit = defineEmits(['update:modelValue'])

// 响应式数据
const intervals = ref([])

// 计算属性 - 预览重试时间
const previewTimes = computed(() => {
  const times = []
  let currentTime = new Date()
  
  intervals.value.forEach((interval, index) => {
    if (interval.value && interval.value > 0) {
      currentTime = new Date(currentTime.getTime() + interval.value * 60 * 1000)
      times.push(formatTime(currentTime))
    }
  })
  
  return times
})

// 格式化时间显示
const formatTime = (date) => {
  const now = new Date()
  const diff = date.getTime() - now.getTime()
  const minutes = Math.floor(diff / (1000 * 60))
  const hours = Math.floor(minutes / 60)
  const days = Math.floor(hours / 24)
  
  if (days > 0) {
    return `${days}天${hours % 24}小时后`
  } else if (hours > 0) {
    return `${hours}小时${minutes % 60}分钟后`
  } else {
    return `${minutes}分钟后`
  }
}

// 添加间隔
const addInterval = () => {
  intervals.value.push({ value: null })
}

// 删除间隔
const removeInterval = (index) => {
  intervals.value.splice(index, 1)
  updateIntervals()
}

// 更新间隔值
const updateIntervals = () => {
  const validIntervals = intervals.value
    .filter(item => item.value && item.value > 0)
    .map(item => item.value)
  
  const intervalString = validIntervals.join(',')
  emit('update:modelValue', intervalString)
}

// 解析初始值
const parseInitialValue = (value) => {
  if (!value) {
    intervals.value = [{ value: null }]
    return
  }
  
  const values = value.split(',').map(item => {
    const num = parseInt(item.trim())
    return isNaN(num) ? null : num
  }).filter(item => item !== null)
  
  if (values.length === 0) {
    intervals.value = [{ value: null }]
  } else {
    intervals.value = values.map(value => ({ value }))
  }
}

// 监听外部值变化
watch(() => props.modelValue, (newValue) => {
  parseInitialValue(newValue)
}, { immediate: true })

// 组件挂载时初始化
onMounted(() => {
  if (intervals.value.length === 0) {
    intervals.value = [{ value: null }]
  }
})
</script>

<style scoped>
.retry-interval-config {
  width: 100%;
}

.interval-list {
  margin-bottom: 15px;
}

.interval-item {
  display: flex;
  align-items: center;
  margin-bottom: 10px;
  gap: 10px;
}

.interval-item:last-child {
  margin-bottom: 0;
}

.unit {
  font-size: 14px;
  color: #606266;
  min-width: 30px;
}

.actions {
  margin-bottom: 15px;
}

.tips {
  display: flex;
  align-items: center;
  margin-top: 10px;
  padding: 8px 12px;
  background-color: #f4f4f5;
  border-radius: 4px;
  font-size: 12px;
  color: #606266;
  gap: 5px;
}

.preview {
  padding: 12px;
  background-color: #fafafa;
  border-radius: 4px;
  border: 1px solid #e4e7ed;
}

.preview-title {
  font-size: 13px;
  color: #303133;
  margin-bottom: 8px;
  font-weight: 500;
}

.preview-content {
  line-height: 1.6;
}

:deep(.el-input-number) {
  width: 120px;
}

:deep(.el-input-number .el-input__inner) {
  text-align: left;
}
</style>