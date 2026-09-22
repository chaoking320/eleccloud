<template>
  <div id="app">
    <router-view />
  </div>
</template>

<script>
export default {
  name: 'App'
}
</script>

<style>
:root {
  --el-color-primary: #2563eb;
  --el-color-primary-light-3: #60a5fa;
  --el-color-primary-light-5: #93c5fd;
  --el-color-primary-light-7: #bfdbfe;
  --el-color-primary-light-8: #dbeafe;
  --el-color-primary-light-9: #eff6ff;
  --el-color-primary-dark-2: #1d4ed8;
}

* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body, #app {
  height: 100%;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  background-color: #f8fafc;
  color: #0f172a;
}

/* Custom Scrollbar */
::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}
::-webkit-scrollbar-track {
  background: transparent;
}
::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 4px;
}
::-webkit-scrollbar-thumb:hover {
  background: #94a3b8;
}

/* Modern Card Overrides */
.el-card {
  border-radius: 12px !important;
  border: 1px solid #e2e8f0 !important;
  box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.04), 0 1px 2px -1px rgba(0, 0, 0, 0.04) !important;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1) !important;
}

.el-card:hover {
  box-shadow: 0 4px 12px 0 rgba(0, 0, 0, 0.06), 0 2px 4px -2px rgba(0, 0, 0, 0.06) !important;
}

.el-card__header {
  border-bottom: 1px solid #f1f5f9 !important;
  padding: 16px 20px !important;
}

/* 核心修复：彻底解决 Element Plus fixed 列造成的文本叠层/重影问题 */
.el-table {
  border-radius: 8px !important;
  --el-table-header-bg-color: #f8fafc !important;
  --el-table-header-text-color: #475569 !important;
  background-color: #ffffff !important;
}

.el-table th.el-table__cell {
  font-weight: 600 !important;
  font-size: 13px !important;
  letter-spacing: 0.2px !important;
  background-color: #f8fafc !important;
  padding: 12px 0 !important;
}

.el-table td.el-table__cell {
  padding: 12px 0 !important;
  font-size: 13px !important;
  background-color: #ffffff; /* 确保背景不透明，防止多层叠影 */
}

.el-table--striped .el-table__body tr.el-table__row--striped td.el-table__cell {
  background-color: #f8fafc;
}

.el-table .el-table__row:hover > td.el-table__cell {
  background-color: #f1f5f9 !important;
}

/* 修复 fixed-right 列透明穿透导致叠字 */
.el-table__fixed-right, .el-table__fixed {
  background-color: #ffffff !important;
  box-shadow: -4px 0 12px rgba(0, 0, 0, 0.05) !important;
}
.el-table__fixed-right td.el-table__cell, .el-table__fixed td.el-table__cell {
  background-color: #ffffff !important;
}

/* 核心修复：确保 link 类型的按钮不被 primary 渐变背景污染成蓝块 */
.el-button.is-link {
  background: transparent !important;
  border: none !important;
  box-shadow: none !important;
  padding: 4px 6px !important;
  height: auto !important;
  line-height: 1.5 !important;
}
.el-button.is-link.el-button--primary {
  color: #2563eb !important;
}
.el-button.is-link.el-button--primary:hover {
  color: #3b82f6 !important;
  text-decoration: underline;
}
.el-button.is-link.el-button--warning {
  color: #d97706 !important;
}
.el-button.is-link.el-button--warning:hover {
  color: #f59e0b !important;
  text-decoration: underline;
}
.el-button.is-link.el-button--danger {
  color: #dc2626 !important;
}
.el-button.is-link.el-button--danger:hover {
  color: #ef4444 !important;
  text-decoration: underline;
}
.el-button.is-link.el-button--success {
  color: #059669 !important;
}
.el-button.is-link.el-button--success:hover {
  color: #10b981 !important;
  text-decoration: underline;
}

/* 实体按钮样式 */
.el-button:not(.is-link) {
  border-radius: 8px !important;
  font-weight: 500 !important;
  transition: all 0.2s ease !important;
}

.el-button--primary:not(.is-link) {
  background: linear-gradient(135deg, #2563eb, #3b82f6) !important;
  border: none !important;
  box-shadow: 0 2px 6px rgba(37, 99, 235, 0.25) !important;
}

.el-button--primary:not(.is-link):hover {
  background: linear-gradient(135deg, #3b82f6, #60a5fa) !important;
  transform: translateY(-1px) !important;
  box-shadow: 0 4px 10px rgba(37, 99, 235, 0.35) !important;
}

/* Modern Input & Select Overrides */
.el-input__wrapper, .el-select .el-input__wrapper {
  border-radius: 8px !important;
  box-shadow: 0 0 0 1px #cbd5e1 inset !important;
  transition: all 0.2s !important;
}

.el-input__wrapper.is-focus, .el-select .el-input__wrapper.is-focus {
  box-shadow: 0 0 0 2px #3b82f6 inset, 0 0 8px rgba(59, 130, 246, 0.2) !important;
}

/* Dialog Modernization */
.el-dialog {
  border-radius: 16px !important;
  overflow: hidden !important;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1) !important;
}

.el-dialog__header {
  border-bottom: 1px solid #f1f5f9 !important;
  margin-right: 0 !important;
  padding: 18px 24px !important;
}

.el-dialog__title {
  font-weight: 700 !important;
  font-size: 16px !important;
  color: #0f172a !important;
}

.el-dialog__body {
  padding: 24px !important;
}
</style>