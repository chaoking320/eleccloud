<template>
  <div class="sidebar" :class="{ 'is-collapse': isCollapse }">
    <div class="logo">
      <div class="logo-icon">⚡</div>
      <div v-if="!isCollapse" class="logo-text">
        <span class="brand-name">ElecCloud</span>
        <span class="brand-badge">PRO</span>
      </div>
    </div>
    <el-menu
      :default-active="$route.path"
      :collapse="isCollapse"
      :collapse-transition="false"
      class="el-menu-vertical"
      background-color="#0f172a"
      text-color="#94a3b8"
      active-text-color="#ffffff"
      router
    >
      <el-menu-item index="/dashboard">
        <el-icon><DataBoard /></el-icon>
        <template #title>仪表盘</template>
      </el-menu-item>
      
      <el-menu-item index="/scene">
        <el-icon><Setting /></el-icon>
        <template #title>场景配置</template>
      </el-menu-item>
      
      <el-menu-item index="/task">
        <el-icon><Monitor /></el-icon>
        <template #title>任务监控</template>
      </el-menu-item>
      
      <el-menu-item index="/failed">
        <el-icon><Warning /></el-icon>
        <template #title>失败任务</template>
      </el-menu-item>
      
      <el-menu-item index="/system">
        <el-icon><Tools /></el-icon>
        <template #title>系统配置</template>
      </el-menu-item>
    </el-menu>

    <div v-if="!isCollapse" class="sidebar-footer">
      <div class="version-info">
        <span class="version-label">Core v1.0.0</span>
        <span class="version-status">Cluster Normal</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useAppStore } from '@/stores'
import { DataBoard, Setting, Monitor, Warning, Tools } from '@element-plus/icons-vue'

const appStore = useAppStore()
const isCollapse = computed(() => !appStore.sidebar.opened)
</script>

<style scoped>
.sidebar {
  height: 100%;
  background-color: #0f172a;
  display: flex;
  flex-direction: column;
  border-right: 1px solid #1e293b;
  transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.logo {
  height: 56px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 16px;
  background-color: #090d16;
  border-bottom: 1px solid #1e293b;
  overflow: hidden;
  white-space: nowrap;
}

.logo-icon {
  width: 32px;
  height: 32px;
  min-width: 32px;
  background: linear-gradient(135deg, #2563eb, #38bdf8);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 18px;
  box-shadow: 0 2px 8px rgba(37, 99, 235, 0.35);
}

.logo-text {
  display: flex;
  align-items: center;
  gap: 6px;
}

.brand-name {
  color: #f8fafc;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0.5px;
}

.brand-badge {
  font-size: 10px;
  font-weight: 700;
  padding: 1px 5px;
  border-radius: 4px;
  background: rgba(56, 189, 248, 0.2);
  color: #38bdf8;
  border: 1px solid rgba(56, 189, 248, 0.3);
}

.el-menu-vertical {
  border-right: none;
  flex: 1;
  padding-top: 8px;
}

.el-menu-vertical:not(.el-menu--collapse) {
  width: 220px;
}

.el-menu-item {
  height: 44px;
  line-height: 44px;
  margin: 4px 10px;
  border-radius: 8px;
  font-weight: 500;
  transition: all 0.2s ease;
}

.el-menu-item:hover {
  background-color: #1e293b !important;
  color: #f1f5f9 !important;
}

/* 核心修复：Active 状态确保字体与图标为纯白并使用现代蓝底渐变高亮 */
.el-menu-item.is-active {
  background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%) !important;
  color: #ffffff !important;
  font-weight: 600;
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.35);
}

.el-menu-item.is-active :deep(.el-icon) {
  color: #ffffff !important;
}

.sidebar-footer {
  padding: 14px 16px;
  border-top: 1px solid #1e293b;
  background: #090d16;
}

.version-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.version-label {
  font-size: 11px;
  color: #64748b;
  font-weight: 500;
}

.version-status {
  font-size: 11px;
  color: #10b981;
  font-weight: 600;
}
</style>