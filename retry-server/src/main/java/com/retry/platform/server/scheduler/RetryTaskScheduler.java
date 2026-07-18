package com.retry.platform.server.scheduler;

import com.retry.platform.server.service.DelayQueueService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * 重试任务调度器
 * 定时扫描Redis延时队列，获取到期任务并提交到线程池执行
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "retry.scheduler", name = "enabled", havingValue = "false", matchIfMissing = false)
public class RetryTaskScheduler {
    
    private static final String SCHEDULER_LOCK_KEY = "retry:scheduler:lock";
    private static final long LOCK_WAIT_TIME = 0; // 不等待，获取不到锁直接返回
    private static final long LOCK_LEASE_TIME = 8; // 锁持有时间8秒（小于扫描间隔10秒）
    
    @Autowired
    private DelayQueueService delayQueueService;
    
    @Autowired
    private RedissonClient redissonClient;
    
    @Autowired(required = false)
    private Executor taskExecutor;
    
    @Value("${retry.scheduler.batch-size:100}")
    private int batchSize;
    
    /**
     * 定时扫描延时队列，已禁用
     */
    @Scheduled(fixedDelayString = "${retry.scheduler.scan-interval:10000}")
    public void scanAndDispatchTasks() {
        // 定时扫描已废除
    }
    
    /**
     * 执行扫描和分发逻辑
     */
    private void doScanAndDispatch() {
        // 已废除
    }
    
    /**
     * 执行任务
     */
    private void executeTaskWithLock(String taskId) {
        // 已废除
    }
}
