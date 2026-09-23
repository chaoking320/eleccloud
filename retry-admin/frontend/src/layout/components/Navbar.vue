<template>
  <div class="navbar">
    <div class="left-menu">
      <el-icon class="hamburger" @click="toggleSidebar">
        <Fold v-if="sidebar.opened" />
        <Expand v-else />
      </el-icon>
      <el-breadcrumb class="app-breadcrumb" separator="/">
        <el-breadcrumb-item>
          <router-link to="/dashboard">{{ $t('common.console') }}</router-link>
        </el-breadcrumb-item>
        <el-breadcrumb-item v-if="currentRouteTitle">
          {{ currentRouteTitle }}
        </el-breadcrumb-item>
      </el-breadcrumb>
    </div>
    
    <div class="right-menu">
      <!-- Language Selector -->
      <el-dropdown trigger="click" @command="handleLangChange">
        <div class="lang-selector">
          <span class="lang-icon">🌐</span>
          <span class="lang-text">{{ currentLangLabel }}</span>
          <el-icon class="el-icon--right"><ArrowDown /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu class="lang-dropdown-menu">
            <el-dropdown-item command="zh" :disabled="locale === 'zh'">
              <span>🇨🇳 简体中文</span>
            </el-dropdown-item>
            <el-dropdown-item command="en" :disabled="locale === 'en'">
              <span>🇺🇸 English</span>
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>

      <div class="env-tag">
        <span class="status-indicator"></span>
        <span class="env-text">{{ $t('common.environment') }}</span>
      </div>

      <el-dropdown trigger="click" @command="handleCommand">
        <div class="user-profile">
          <el-avatar
            :size="30"
            src="https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png"
            class="user-avatar"
          />
          <span class="user-name">{{ displayUserName }}</span>
          <el-icon class="el-icon--right"><ArrowDown /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu class="user-dropdown-menu">
            <el-dropdown-item disabled>
              <div class="dropdown-header">
                <strong>{{ userName }}</strong>
                <span class="role-badge">{{ $t('common.superAdmin') }}</span>
              </div>
            </el-dropdown-item>
            <el-dropdown-item divided command="demo">
              <el-icon><Monitor /></el-icon>
              <span>{{ $t('common.demoCenter') }}</span>
            </el-dropdown-item>
            <el-dropdown-item divided command="logout">
              <el-icon><SwitchButton /></el-icon>
              <span style="color: #f56c6c;">{{ $t('common.logout') }}</span>
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAppStore } from '@/stores'
import { useAuthStore } from '@/stores/auth'
import { ElMessageBox, ElMessage } from 'element-plus'
import { Expand, Fold, ArrowDown, Monitor, SwitchButton } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const appStore = useAppStore()
const authStore = useAuthStore()
const { locale, t } = useI18n()

const sidebar = computed(() => appStore.sidebar)
const userName = computed(() => authStore.userName)
const displayUserName = computed(() => locale.value === 'en' ? (authStore.userName === '系统管理员' ? 'Admin' : (authStore.userName || 'Admin')) : (authStore.userName || '系统管理员'))

const currentLangLabel = computed(() => {
  return locale.value === 'en' ? 'English' : '简体中文'
})

const currentRouteTitle = computed(() => {
  const routeName = route.name
  if (routeName === 'Dashboard') return t('menu.dashboard')
  if (routeName === 'SceneConfig') return t('menu.sceneConfig')
  if (routeName === 'TaskMonitor') return t('menu.taskMonitor')
  if (routeName === 'FailedTask') return t('menu.failedTask')
  if (routeName === 'SystemConfig') return t('menu.systemConfig')
  return route.meta?.title || ''
})

const handleLangChange = (lang) => {
  locale.value = lang
  localStorage.setItem('eleccloud_lang', lang)
  ElMessage.success(lang === 'en' ? 'Switched to English' : '已切换至简体中文')
}

const toggleSidebar = () => {
  appStore.toggleSidebar()
}

const handleCommand = (command) => {
  if (command === 'logout') {
    ElMessageBox.confirm(
      locale.value === 'en' ? 'Are you sure you want to sign out?' : '确定要退出 ElecCloud 控制台吗？',
      locale.value === 'en' ? 'Notice' : '提示',
      {
        confirmButtonText: locale.value === 'en' ? 'Sign Out' : '确定退出',
        cancelButtonText: locale.value === 'en' ? 'Cancel' : '取消',
        type: 'warning'
      }
    ).then(async () => {
      await authStore.logout()
      ElMessage.success(locale.value === 'en' ? 'Signed out safely' : '已安全退出')
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

.lang-selector {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  padding: 4px 10px;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  background-color: #f8fafc;
  transition: all 0.2s;
}

.lang-selector:hover {
  background-color: #f1f5f9;
  border-color: #cbd5e1;
}

.lang-icon {
  font-size: 14px;
}

.lang-text {
  font-size: 12px;
  font-weight: 600;
  color: #334155;
}
</style>