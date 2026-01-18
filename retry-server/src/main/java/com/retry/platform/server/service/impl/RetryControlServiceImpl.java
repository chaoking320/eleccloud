package com.retry.platform.server.service.impl;

import com.retry.platform.server.entity.RetryTask;
import com.retry.platform.server.entity.SceneConfig;
import com.retry.platform.server.service.RetryControlService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 重试控制服务实现类
 */
@Slf4j
@Service
public class RetryControlServiceImpl implements RetryControlService {
    
    @Override
    public Long calculateNextRetryTime(SceneConfig sceneConfig, int currentRetryCount) {
        List<Integer> retryIntervals = sceneConfig.getRetryIntervalList();
        
        // 如果重试次数超过配置的间隔数量，返回null表示不再重试
        if (currentRetryCount >= retryIntervals.size()) {
            return null;
        }
        
        // 获取对应的重试间隔（分钟）
        int intervalMinutes = retryIntervals.get(currentRetryCount);
        
        // 计算下次执行时间（当前时间 + 间隔）
        long nextRetryTime = System.currentTimeMillis() + intervalMinutes * 60 * 1000L;
        
        log.debug("Calculated next retry time: currentRetryCount={}, intervalMinutes={}, nextRetryTime={}", 
                currentRetryCount, intervalMinutes, nextRetryTime);
        
        return nextRetryTime;
    }
    
    @Override
    public boolean shouldContinueRetry(RetryTask retryTask) {
        if (retryTask == null) {
            return false;
        }
        
        // 如果已经成功，不需要重试
        if ("SUCCESS".equals(retryTask.getTaskStatus())) {
            return false;
        }
        
        // 如果已经失败，不需要重试
        if ("FAILED".equals(retryTask.getTaskStatus())) {
            return false;
        }
        
        // 检查重试次数是否超过最大值
        if (retryTask.getRetryCount() >= retryTask.getMaxRetryCount()) {
            log.info("Task reached max retry count: taskId={}, retryCount={}, maxRetryCount={}", 
                    retryTask.getTaskId(), retryTask.getRetryCount(), retryTask.getMaxRetryCount());
            return false;
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
