package com.retry.platform.server.service;

import com.retry.platform.server.entity.RetryTask;
import com.retry.platform.server.entity.SceneConfig;

/**
 * 重试控制服务接口
 * 负责重试次数控制、退避策略计算和时间阈值判断
 */
public interface RetryControlService {

    /**
     * 计算下次重试时间（使用场景配置的退避策略）
     *
     * @param sceneConfig        场景配置（含退避策略、基数、自定义间隔列表）
     * @param currentRetryCount  当前重试次数（从0开始）
     * @return 下次重试时间戳（毫秒），超过最大次数返回 null
     */
    Long calculateNextRetryTime(SceneConfig sceneConfig, int currentRetryCount);

    /**
     * 判断是否应该继续重试（仅检查次数阈值）
     *
     * @param retryTask 重试任务
     * @return true-继续重试, false-停止重试
     */
    boolean shouldContinueRetry(RetryTask retryTask);

    /**
     * 判断是否应该继续重试（检查次数阈值 + 时间阈值）
     *
     * @param retryTask   重试任务
     * @param sceneConfig 场景配置（提供 maxRetryDuration 时间阈值），可为 null
     * @return true-继续重试, false-停止重试
     */
    boolean shouldContinueRetry(RetryTask retryTask, SceneConfig sceneConfig);

    /**
     * 递增重试次数
     *
     * @param retryTask 重试任务
     * @return 递增后的重试次数
     */
    int incrementRetryCount(RetryTask retryTask);
}
