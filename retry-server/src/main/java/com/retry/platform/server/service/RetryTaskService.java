package com.retry.platform.server.service;

import com.retry.platform.client.dto.RetryTaskDTO;
import com.retry.platform.client.dto.RetryTaskRequest;
import com.retry.platform.server.entity.RetryHistory;

import java.util.List;

/**
 * 重试任务服务接口
 */
public interface RetryTaskService {
    
    /**
     * 创建重试任务
     * @param request 重试任务请求
     * @return 任务ID
     */
    String createTask(RetryTaskRequest request);
    
    /**
     * 根据任务ID查询任务详情
     * @param taskId 任务ID
     * @return 任务DTO
     */
    RetryTaskDTO getTask(String taskId);
    
    /**
     * 更新任务状态
     * @param taskId 任务ID
     * @param taskStatus 任务状态
     */
    void updateTaskStatus(String taskId, String taskStatus);

    /**
     * 原子 CAS 尝试将任务状态从 INIT 更新为 EXECUTING
     * <p>使用单条 SQL WHERE task_status='INIT' 实现，返回影响行数：
     * 1 → 抢占成功，0 → 已被其他节点抢占
     *
     * @param taskId 任务ID
     * @return affected rows
     */
    int casMarkExecuting(String taskId);
    
    /**
     * 更新任务状态和重试信息
     * @param taskId 任务ID
     * @param taskStatus 任务状态
     * @param retryCount 重试次数
     * @param nextRetryTime 下次重试时间
     */
    void updateTaskStatusAndRetryInfo(String taskId, String taskStatus, 
                                      Integer retryCount, Long nextRetryTime);
    
    /**
     * 查询待执行任务（数据库兜底）
     * @param currentTime 当前时间戳
     * @param limit 限制数量
     * @return 任务ID列表
     */
    List<String> queryPendingTasks(long currentTime, int limit);
    
    /**
     * 记录任务执行历史
     * @param taskId 任务ID
     * @param retryCount 重试次数
     * @param executeResult 执行结果
     * @param errorMessage 错误信息
     * @param costTime 耗时(ms)
     */
    void recordHistory(String taskId, Integer retryCount, String executeResult, 
                      String errorMessage, Integer costTime);
    
    /**
     * 查询任务的重试历史
     * @param taskId 任务ID
     * @return 重试历史列表
     */
    List<RetryHistory> getTaskHistory(String taskId);
    
    /**
     * 手动触发重试任务
     * @param taskId 任务ID
     */
    void triggerRetry(String taskId);
}
