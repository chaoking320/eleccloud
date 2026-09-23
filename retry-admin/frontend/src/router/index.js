import { createRouter, createWebHistory } from 'vue-router'
import Layout from '@/layout/Layout.vue'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '用户登录', hidden: true }
  },
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
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/dashboard'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 全局路由守卫
router.beforeEach((to, from, next) => {
  if (to.query && (to.query.token || to.query.demo === '1')) {
    localStorage.setItem('eleccloud_token', to.query.token || 'admin-token-default')
  }
  const token = localStorage.getItem('eleccloud_token')
  if (to.path === '/login') {
    if (token) {
      next('/dashboard')
    } else {
      next()
    }
  } else {
    if (token) {
      next()
    } else {
      next(`/login?redirect=${encodeURIComponent(to.fullPath)}`)
    }
  }
})

export default router