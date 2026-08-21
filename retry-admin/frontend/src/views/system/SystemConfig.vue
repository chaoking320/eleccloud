<template>
  <div class="system-config">
    <el-card class="box-card">
      <template #header>
        <div class="card-header">
          <span>全局系统配置</span>
        </div>
      </template>
      <el-form :model="configForm" label-width="150px" style="max-width: 600px;">
        <el-form-item label="全局报警 Webhook">
          <el-input v-model="configForm.webhookUrl" placeholder="请输入钉钉/企微机器人 Webhook 地址" />
        </el-form-item>
        <el-form-item label="历史记录保留天数">
          <el-input-number v-model="configForm.historyRetentionDays" :min="1" :max="365" />
        </el-form-item>
        <el-form-item label="控制台自动刷新">
          <el-switch v-model="configForm.autoRefresh" active-text="开启" inactive-text="关闭" />
        </el-form-item>
        <el-form-item label="刷新间隔 (秒)" v-if="configForm.autoRefresh">
          <el-slider v-model="configForm.refreshInterval" :min="5" :max="60" show-input />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="saveConfig" :loading="saving">保存配置</el-button>
          <el-button @click="resetConfig">恢复默认</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'

const defaultConfig = {
  webhookUrl: '',
  historyRetentionDays: 30,
  autoRefresh: false,
  refreshInterval: 10
}

const configForm = ref({ ...defaultConfig })
const saving = ref(false)

const loadConfig = () => {
  const saved = localStorage.getItem('retry_system_config')
  if (saved) {
    try {
      Object.assign(configForm.value, JSON.parse(saved))
    } catch (e) {
      console.error('Failed to parse saved config', e)
    }
  }
}

const saveConfig = () => {
  saving.value = true
  setTimeout(() => {
    localStorage.setItem('retry_system_config', JSON.stringify(configForm.value))
    ElMessage.success('系统配置保存成功（本地存储）')
    saving.value = false
  }, 500)
}

const resetConfig = () => {
  Object.assign(configForm.value, defaultConfig)
  saveConfig()
}

onMounted(() => {
  loadConfig()
})
</script>

<style scoped>
.system-config {
  padding: 20px;
}
.card-header {
  font-weight: bold;
}
</style>