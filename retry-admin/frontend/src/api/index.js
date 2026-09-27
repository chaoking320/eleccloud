import request from '@/utils/request'

// 认证相关 API
export const authApi = {
  login(data) {
    return request({
      url: '/auth/login',
      method: 'post',
      data
    })
  },
  logout() {
    return request({
      url: '/auth/logout',
      method: 'post'
    })
  },
  getUserInfo() {
    return request({
      url: '/auth/user-info',
      method: 'get'
    })
  }
}

// 场景配置相关 API
export const sceneApi = {
  getSceneList() {
    return request({
      url: '/scene/list',
      method: 'get'
    })
  },
  getScene(id) {
    return request({
      url: `/scene/${id}`,
      method: 'get'
    })
  },
  createScene(data) {
    return request({
      url: '/scene',
      method: 'post',
      data
    })
  },
  updateScene(id, data) {
    return request({
      url: `/scene/${id}`,
      method: 'put',
      data
    })
  },
  deleteScene(id) {
    return request({
      url: `/scene/${id}`,
      method: 'delete'
    })
  }
}

// 任务监控相关 API
export const taskApi = {
  getStatistics(sceneType) {
    return request({
      url: '/task/stats',
      method: 'get',
      params: { sceneType }
    })
  },
  getTaskList(params) {
    return request({
      url: '/task/list',
      method: 'get',
      params
    })
  },
  getTaskDetail(taskId) {
    return request({
      url: `/task/${taskId}`,
      method: 'get'
    })
  },
  getTaskHistory(taskId) {
    return request({
      url: `/task/${taskId}/history`,
      method: 'get'
    })
  },
  retryTask(taskId) {
    return request({
      url: `/task/${taskId}/retry`,
      method: 'post'
    })
  }
}

// 失败死信任务相关 API
export const failedApi = {
  getFailedList(params) {
    return request({
      url: '/failed/list',
      method: 'get',
      params
    })
  },
  getFailedDetail(taskId) {
    return request({
      url: `/failed/${taskId}`,
      method: 'get'
    })
  },
  recoverTask(taskId) {
    return request({
      url: `/failed/${taskId}/recover`,
      method: 'post'
    })
  },
  deleteTask(taskId) {
    return request({
      url: `/failed/${taskId}`,
      method: 'delete'
    })
  }
}

// 系统配置相关 API
export const systemApi = {
  getSystemConfig() {
    return request({
      url: '/system/config',
      method: 'get'
    })
  },
  updateSystemConfig(data) {
    return request({
      url: '/system/config',
      method: 'put',
      data
    })
  }
}