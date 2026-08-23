<template>
  <div class="sidebar" :class="{ 'is-collapse': isCollapse }">
    <div class="logo">
      <h2 v-if="!isCollapse">重试平台</h2>
      <h2 v-else class="logo-mini">RT</h2>
    </div>
    <el-menu
      :default-active="$route.path"
      :collapse="isCollapse"
      :collapse-transition="false"
      class="el-menu-vertical"
      background-color="#304156"
      text-color="#bfcbd9"
      active-text-color="#409EFF"
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
  background-color: #304156;
}

.logo {
  height: 50px;
  line-height: 50px;
  text-align: center;
  background-color: #2b2f3a;
  color: #fff;
  font-size: 16px;
  font-weight: bold;
  overflow: hidden;
  white-space: nowrap;
}

.logo-mini {
  font-size: 18px;
  color: #409EFF;
}

.el-menu-vertical {
  border-right: none;
  height: calc(100vh - 50px);
}

.el-menu-vertical:not(.el-menu--collapse) {
  width: 210px;
}

.el-menu-item {
  height: 50px;
  line-height: 50px;
}

.el-menu-item:hover {
  background-color: #263445 !important;
}

.el-menu-item.is-active {
  background-color: #409EFF !important;
}
</style>