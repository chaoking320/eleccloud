<template>
  <div class="navbar">
    <div class="left-menu">
      <el-icon class="hamburger" @click="toggleSidebar">
        <Fold v-if="sidebar.opened" />
        <Expand v-else />
      </el-icon>
      <el-breadcrumb class="app-breadcrumb" separator="/">
        <el-breadcrumb-item>
          <router-link to="/dashboard">控制台</router-link>
        </el-breadcrumb-item>
        <el-breadcrumb-item v-if="$route.meta.title">
          {{ $route.meta.title }}
        </el-breadcrumb-item>
      </el-breadcrumb>
    </div>
    
    <div class="right-menu">
      <div class="env-tag">
        <span class="status-indicator"></span>
        <span class="env-text">集群: SHARED</span>
      </div>

      <el-dropdown trigger="click" @command="handleCommand">
        <div class="user-profile">
          <el-avatar
            :size="30"
            src="https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png"
            class="user-avatar"
          />
          <span class="user-name">{{ userName }}</span>
          <el-icon class="el-icon--right"><ArrowDown /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu class="user-dropdown-menu">
            <el-dropdown-item disabled>
              <div class="dropdown-header">
                <strong>{{ userName }}</strong>
                <span class="role-badge">Super Admin</span>
              </div>
            </el-dropdown-item>
            <el-dropdown-item divided command="demo">
              <el-icon><Monitor /></el-icon>
              <span>跳转交互演示页 (8082)</span>
            </el-dropdown-item>
            <el-dropdown-item divided command="logout">
              <el-icon><SwitchButton /></el-icon>
              <span style="color: #f56c6c;">退出登录</span>
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAppStore } from '@/stores'
import { useAuthStore } from '@/stores/auth'
import { ElMessageBox, ElMessage } from 'element-plus'
import { Expand, Fold, ArrowDown, Monitor, SwitchButton } from '@element-plus/icons-vue'

const router = useRouter()
const appStore = useAppStore()
const authStore = useAuthStore()

const sidebar = computed(() => appStore.sidebar)
const userName = computed(() => authStore.userName)

const toggleSidebar = () => {
  appStore.toggleSidebar()
}

const handleCommand = (command) => {
  if (command === 'logout') {
    ElMessageBox.confirm('确定要退出 ElecCloud 控制台吗？', '提示', {
      confirmButtonText: '确定退出',
      cancelButtonText: '取消',
      type: 'warning'
    }).then(async () => {
      await authStore.logout()
      ElMessage.success('已安全退出')
      router.push('/login')
    }).catch(() => {})
  } else if (command === 'demo') {
    window.open('http://localhost:8082/demo.html', '_blank')
  }
}
</script>

<style scoped>
.navbar {
  height: 56px;
  position: relative;
  background: #ffffff;
  border-bottom: 1px solid #e2e8f0;
  box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.05);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  z-index: 100;
}

.left-menu {
  display: flex;
  align-items: center;
}

.hamburger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 6px;
  cursor: pointer;
  margin-right: 16px;
  color: #64748b;
  transition: all 0.2s;
}

.hamburger:hover {
  background-color: #f1f5f9;
  color: #1e293b;
}

.app-breadcrumb {
  font-size: 14px;
}

.app-breadcrumb :deep(.el-breadcrumb__inner) {
  color: #64748b;
  font-weight: 500;
}

.app-breadcrumb :deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
  color: #0f172a;
  font-weight: 600;
}

.right-menu {
  display: flex;
  align-items: center;
  gap: 16px;
}

.env-tag {
  display: flex;
  align-items: center;
  gap: 6px;
  background: #ecfdf5;
  border: 1px solid #a7f3d0;
  padding: 4px 10px;
  border-radius: 20px;
}

.status-indicator {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #10b981;
  box-shadow: 0 0 6px #10b981;
}

.env-text {
  font-size: 11px;
  font-weight: 600;
  color: #065f46;
}

.user-profile {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 8px;
  transition: background-color 0.2s;
}

.user-profile:hover {
  background-color: #f8fafc;
}

.user-avatar {
  border: 1px solid #cbd5e1;
}

.user-name {
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
}

.dropdown-header {
  display: flex;
  flex-direction: column;
  padding: 4px 0;
}

.role-badge {
  font-size: 11px;
  color: #3b82f6;
  margin-top: 2px;
}
</style>