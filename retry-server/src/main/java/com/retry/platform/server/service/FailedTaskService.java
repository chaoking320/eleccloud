package com.retry.platform.server.service;

import com.retry.platform.server.entity.RetryTask;

/**
 * 失败任务服务接口
 */
public interface FailedTaskService {
    
    /**
     * 将重试任务移动到失败任务表
     * 
     * @param retryTask 重试任务
     * @param failReason 失败原因
     */
    void moveToFailedTask(RetryTask retryTask, String failReason);
}
