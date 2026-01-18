import request from '@/utils/request'

// 场景配置相关API
export const sceneApi = {
  // 获取场景列表
  getSceneList() {
    return request({
      url: '/scene/list',
      method: 'get'
    })
  },
  
  // 获取场景详情
  getScene(id) {
    return request({
      url: `/scene/${id}`,
      method: 'get'
    })
  },
  
  // 创建场景
  createScene(data) {
    return request({
      url: '/scene',
      method: 'post',
      data
    })
  },
  
  // 更新场景
  updateScene(id, data) {
    return request({
      url: `/scene/${id}`,
      method: 'put',
      data
    })
  },
  
  // 删除场景
  deleteScene(id) {
    return request({
      url: `/scene/${id}`,
      method: 'delete'
    })
  }
}

// 任务监控相关API
export const taskApi = {
  // 获取任务统计
  getStatistics(sceneType) {
    return request({
      url: '/task/statistics',
      method: 'get',
      params: { sceneType }
    })
  },
  
  // 获取任务列表
  getTaskList(params) {
    return request({
      url: '/task/list',
      method: 'get',
      params
    })
  },
  
  // 获取任务详情
  getTaskDetail(taskId) {
    return request({
      url: `/task/${taskId}`,
      method: 'get'
    })
  },
  
  // 手动重试任务
  retryTask(taskId) {
    return request({
      url: `/task/${taskId}/retry`,
      method: 'post'
    })
  },
  
  // 获取失败任务列表
  getFailedTasks(params) {
    return request({
      url: '/task/failed',
      method: 'get',
      params
    })
  }
}

// 系统配置相关API
export const systemApi = {
  // 获取系统配置
  getSystemConfig() {
    return request({
      url: '/system/config',
      method: 'get'
    })
  },
  
  // 更新系统配置
  updateSystemConfig(data) {
    return request({
      url: '/system/config',
      method: 'put',
      data
    })
  }
}