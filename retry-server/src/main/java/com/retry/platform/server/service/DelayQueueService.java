package com.retry.platform.server.service;

import java.util.List;

/**
 * 延时队列服务接口
 * 基于Redis ZSET实现延时任务调度
 */
public interface DelayQueueService {
    
    /**
     * 添加延时任务到队列
     * @param taskId 任务ID
     * @param executeTime 执行时间戳(毫秒)
     */
    void addTask(String taskId, long executeTime);
    
    /**
     * 获取到期的任务
     * @param currentTime 当前时间戳(毫秒)
     * @param limit 限制数量
     * @return 任务ID列表
     */
    List<String> pollExpiredTasks(long currentTime, int limit);
    
    /**
     * 移除任务
     * @param taskId 任务ID
     */
    void removeTask(String taskId);
    
    /**
     * 更新任务执行时间
     * @param taskId 任务ID
     * @param newExecuteTime 新的执行时间戳(毫秒)
     */
    void updateTaskTime(String taskId, long newExecuteTime);
}
