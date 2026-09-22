<template>
  <div class="app-wrapper" :class="{ hideSidebar: isCollapse }">
    <div class="sidebar-container">
      <Sidebar />
    </div>
    <div class="main-container">
      <div class="navbar-wrapper">
        <Navbar />
      </div>
      <div class="app-main">
        <router-view />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useAppStore } from '@/stores'
import Sidebar from './components/Sidebar.vue'
import Navbar from './components/Navbar.vue'

const appStore = useAppStore()
const isCollapse = computed(() => !appStore.sidebar.opened)
</script>

<style scoped>
.app-wrapper {
  position: relative;
  height: 100vh;
  width: 100%;
  display: flex;
}

.sidebar-container {
  width: 220px;
  height: 100%;
  position: fixed;
  font-size: 0px;
  top: 0;
  bottom: 0;
  left: 0;
  z-index: 1001;
  overflow: hidden;
  background-color: #0f172a;
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.2);
  transition: width 0.28s cubic-bezier(0.4, 0, 0.2, 1);
}

.main-container {
  min-height: 100vh;
  width: calc(100% - 220px);
  margin-left: 220px;
  position: relative;
  display: flex;
  flex-direction: column;
  transition: all 0.28s cubic-bezier(0.4, 0, 0.2, 1);
  background-color: #f8fafc;
}

.hideSidebar .sidebar-container {
  width: 64px;
}

.hideSidebar .main-container {
  width: calc(100% - 64px);
  margin-left: 64px;
}

.navbar-wrapper {
  height: 56px;
  flex-shrink: 0;
}

.app-main {
  height: calc(100vh - 56px);
  width: 100%;
  position: relative;
  overflow-y: auto;
  overflow-x: hidden;
  background-color: #f8fafc;
  box-sizing: border-box;
}
</style>