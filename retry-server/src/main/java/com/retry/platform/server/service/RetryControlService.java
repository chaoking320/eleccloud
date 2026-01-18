package com.retry.platform.server.service;

import com.retry.platform.server.entity.RetryTask;
import com.retry.platform.server.entity.SceneConfig;

/**
 * 重试控制服务接口
 * 负责重试次数控制和下次执行时间计算
 */
public interface RetryControlService {
    
    /**
     * 计算下次重试时间
     * 
     * @param sceneConfig 场景配置
     * @param currentRetryCount 当前重试次数
     * @return 下次重试时间戳（毫秒），如果超过最大重试次数返回null
     */
    Long calculateNextRetryTime(SceneConfig sceneConfig, int currentRetryCount);
    
    /**
     * 判断是否应该继续重试
     * 
     * @param retryTask 重试任务
     * @return true-继续重试, false-停止重试
     */
    boolean shouldContinueRetry(RetryTask retryTask);
    
    /**
     * 递增重试次数
     * 
     * @param retryTask 重试任务
     * @return 递增后的重试次数
     */
    int incrementRetryCount(RetryTask retryTask);
}
