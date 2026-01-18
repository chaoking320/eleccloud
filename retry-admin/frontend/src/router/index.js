import { createRouter, createWebHistory } from 'vue-router'
import Layout from '@/layout/Layout.vue'

const routes = [
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: { title: '仪表盘', icon: 'DataBoard' }
      }
    ]
  },
  {
    path: '/scene',
    component: Layout,
    children: [
      {
        path: '',
        name: 'SceneConfig',
        component: () => import('@/views/scene/SceneConfig.vue'),
        meta: { title: '场景配置', icon: 'Setting' }
      }
    ]
  },
  {
    path: '/task',
    component: Layout,
    children: [
      {
        path: '',
        name: 'TaskMonitor',
        component: () => import('@/views/task/TaskMonitor.vue'),
        meta: { title: '任务监控', icon: 'Monitor' }
      }
    ]
  },
  {
    path: '/failed',
    component: Layout,
    children: [
      {
        path: '',
        name: 'FailedTask',
        component: () => import('@/views/failed/FailedTask.vue'),
        meta: { title: '失败任务', icon: 'Warning' }
      }
    ]
  },
  {
    path: '/system',
    component: Layout,
    children: [
      {
        path: '',
        name: 'SystemConfig',
        component: () => import('@/views/system/SystemConfig.vue'),
        meta: { title: '系统配置', icon: 'Tools' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router