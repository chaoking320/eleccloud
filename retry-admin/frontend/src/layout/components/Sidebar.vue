<template>
  <div class="sidebar">
    <div class="logo">
      <h2>重试平台</h2>
    </div>
    <el-menu
      :default-active="$route.path"
      class="el-menu-vertical"
      background-color="#304156"
      text-color="#bfcbd9"
      active-text-color="#409EFF"
      router
    >
      <el-menu-item index="/dashboard">
        <el-icon><DataBoard /></el-icon>
        <span>仪表盘</span>
      </el-menu-item>
      
      <el-menu-item index="/scene">
        <el-icon><Setting /></el-icon>
        <span>场景配置</span>
      </el-menu-item>
      
      <el-menu-item index="/task">
        <el-icon><Monitor /></el-icon>
        <span>任务监控</span>
      </el-menu-item>
      
      <el-menu-item index="/failed">
        <el-icon><Warning /></el-icon>
        <span>失败任务</span>
      </el-menu-item>
      
      <el-menu-item index="/system">
        <el-icon><Tools /></el-icon>
        <span>系统配置</span>
      </el-menu-item>
    </el-menu>
  </div>
</template>

<script setup>
import { DataBoard, Setting, Monitor, Warning, Tools } from '@element-plus/icons-vue'
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
}

.el-menu-vertical {
  border-right: none;
  height: calc(100vh - 50px);
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