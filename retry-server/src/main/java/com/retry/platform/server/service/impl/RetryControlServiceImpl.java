package com.retry.platform.server.service.impl;

import com.retry.platform.server.entity.RetryTask;
import com.retry.platform.server.entity.SceneConfig;
import com.retry.platform.server.service.RetryControlService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 重试控制服务实现类
 * 负责重试次数控制、退避策略计算和时间阈值判断
 */
@Slf4j
@Service
public class RetryControlServiceImpl implements RetryControlService {

    @Override
    public Long calculateNextRetryTime(SceneConfig sceneConfig, int currentRetryCount) {
        // 1. 检查是否还有剩余重试次数
        if (currentRetryCount >= sceneConfig.getMaxRetryCount()) {
            log.debug("No more retries: currentRetryCount={}, maxRetryCount={}",
                    currentRetryCount, sceneConfig.getMaxRetryCount());
            return null;
        }

        // 2. 使用退避策略计算下次执行时间
        long nextRetryTime = sceneConfig.getBackoffStrategyEnum().calculateNextRetryTime(
                currentRetryCount,
                sceneConfig.getBackoffBaseOrDefault(),
                sceneConfig.getRetryIntervalList()
        );

        log.debug("Calculated next retry time: strategy={}, retryCount={}, baseMins={}, nextRetryTime={}",
                sceneConfig.getBackoffStrategy(), currentRetryCount,
                sceneConfig.getBackoffBaseOrDefault(), nextRetryTime);

        return nextRetryTime;
    }

    @Override
    public boolean shouldContinueRetry(RetryTask retryTask) {
        return shouldContinueRetry(retryTask, null);
    }

    @Override
    public boolean shouldContinueRetry(RetryTask retryTask, SceneConfig sceneConfig) {
        if (retryTask == null) {
            return false;
        }

        // 终止条件1：任务已成功
        if ("SUCCESS".equals(retryTask.getTaskStatus())) {
            return false;
        }

        // 终止条件2：任务已标记失败
        if ("FAILED".equals(retryTask.getTaskStatus())) {
            return false;
        }

        // 终止条件3：次数阈值 —— 重试次数达到上限
        if (retryTask.getRetryCount() >= retryTask.getMaxRetryCount()) {
            log.info("Task reached max retry count: taskId={}, retryCount={}, maxRetryCount={}",
                    retryTask.getTaskId(), retryTask.getRetryCount(), retryTask.getMaxRetryCount());
            return false;
        }

        // 终止条件4：时间阈值 —— 超过最大重试总时长（需要 SceneConfig 提供时间阈值）
        if (sceneConfig != null && sceneConfig.getMaxRetryDurationOrZero() > 0
                && retryTask.getCreateTime() != null) {
            long elapsedSeconds = (System.currentTimeMillis()
                    - java.sql.Timestamp.valueOf(retryTask.getCreateTime()).getTime()) / 1000L;
            long maxDurationSeconds = sceneConfig.getMaxRetryDurationOrZero();

            if (elapsedSeconds >= maxDurationSeconds) {
                log.warn("Task exceeded max retry duration: taskId={}, elapsedSeconds={}, maxDurationSeconds={}",
                        retryTask.getTaskId(), elapsedSeconds, maxDurationSeconds);
                return false;
            }
        }

        return true;
    }

    @Override
    public int incrementRetryCount(RetryTask retryTask) {
        int newRetryCount = retryTask.getRetryCount() + 1;
        retryTask.setRetryCount(newRetryCount);
        return newRetryCount;
    }
}
