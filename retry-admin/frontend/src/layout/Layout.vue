<template>
  <div class="app-wrapper" :class="{ hideSidebar: isCollapse }">
    <div class="sidebar-container">
      <Sidebar />
    </div>
    <div class="main-container">
      <div class="navbar">
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
  width: 210px;
  height: 100%;
  position: fixed;
  font-size: 0px;
  top: 0;
  bottom: 0;
  left: 0;
  z-index: 1001;
  overflow: hidden;
  background-color: #304156;
  box-shadow: 2px 0 6px rgba(0, 21, 41, 0.35);
  transition: width 0.28s cubic-bezier(0.4, 0, 0.2, 1);
}

.main-container {
  min-height: 100vh;
  width: calc(100% - 210px);
  margin-left: 210px;
  position: relative;
  display: flex;
  flex-direction: column;
  transition: all 0.28s cubic-bezier(0.4, 0, 0.2, 1);
}

.hideSidebar .sidebar-container {
  width: 64px;
}

.hideSidebar .main-container {
  width: calc(100% - 64px);
  margin-left: 64px;
}

.navbar {
  height: 50px;
  overflow: hidden;
  position: relative;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  flex-shrink: 0;
}

.app-main {
  height: calc(100vh - 50px);
  width: 100%;
  position: relative;
  overflow-y: auto;
  overflow-x: hidden;
  background-color: #f0f2f5;
  box-sizing: border-box;
}
</style>