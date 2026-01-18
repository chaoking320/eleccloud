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
@ConditionalOnProperty(prefix = "retry.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RetryTaskScheduler {
    
    private static final String SCHEDULER_LOCK_KEY = "retry:scheduler:lock";
    private static final long LOCK_WAIT_TIME = 0; // 不等待，获取不到锁直接返回
    private static final long LOCK_LEASE_TIME = 8; // 锁持有时间8秒（小于扫描间隔10秒）
    
    @Autowired
    private DelayQueueService delayQueueService;
    
    @Autowired
    private RedissonClient redissonClient;
    
    @Autowired
    @Qualifier("retryTaskThreadPoolExecutor")
    private Executor taskExecutor;
    
    @Autowired
    private RetryTaskExecutorService executorService;
    
    @Value("${retry.scheduler.batch-size:100}")
    private int batchSize;
    
    /**
     * 定时扫描延时队列，每10秒执行一次
     * 使用fixedDelay确保上次执行完成后再等待指定时间
     */
    @Scheduled(fixedDelayString = "${retry.scheduler.scan-interval:10000}")
    public void scanAndDispatchTasks() {
        RLock lock = redissonClient.getLock(SCHEDULER_LOCK_KEY);
        
        try {
            // 尝试获取分布式锁，防止多节点重复扫描
            boolean locked = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);
            
            if (!locked) {
                log.debug("Failed to acquire scheduler lock, another node may be processing");
                return;
            }
            
            try {
                doScanAndDispatch();
            } finally {
                // 释放锁
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Scheduler lock interrupted", e);
        } catch (Exception e) {
            log.error("Scheduler scan failed", e);
        }
    }
    
    /**
     * 执行扫描和分发逻辑
     */
    private void doScanAndDispatch() {
        long startTime = System.currentTimeMillis();
        long currentTime = System.currentTimeMillis();
        
        try {
            // 从延时队列获取到期任务
            List<String> expiredTaskIds = delayQueueService.pollExpiredTasks(currentTime, batchSize);
            
            if (expiredTaskIds.isEmpty()) {
                log.debug("No expired tasks found in delay queue");
                return;
            }
            
            log.info("Found {} expired tasks, dispatching to executor", expiredTaskIds.size());
            
            // 提交任务到线程池执行
            int successCount = 0;
            for (String taskId : expiredTaskIds) {
                try {
                    taskExecutor.execute(() -> executeTaskWithLock(taskId));
                    successCount++;
                } catch (Exception e) {
                    log.error("Failed to submit task to executor: taskId={}", taskId, e);
                }
            }
            
            long costTime = System.currentTimeMillis() - startTime;
            log.info("Scheduler scan completed: found={}, dispatched={}, cost={}ms", 
                    expiredTaskIds.size(), successCount, costTime);
            
        } catch (Exception e) {
            log.error("Failed to scan and dispatch tasks", e);
        }
    }
    
    /**
     * 使用分布式锁执行任务，防止同一任务被多个节点重复执行
     */
    private void executeTaskWithLock(String taskId) {
        String lockKey = "retry:task:lock:" + taskId;
        RLock taskLock = redissonClient.getLock(lockKey);
        
        try {
            // 尝试获取任务锁，等待时间1秒，锁持有时间30秒
            boolean locked = taskLock.tryLock(1, 30, TimeUnit.SECONDS);

            
            if (!locked) {
                log.debug("Failed to acquire task lock, task may be processing by another node: taskId={}", taskId);
                return;
            }
            
            try {
                // 执行任务
                executorService.executeTask(taskId);
            } finally {
                // 释放锁
                if (taskLock.isHeldByCurrentThread()) {
                    taskLock.unlock();
                }
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Task lock interrupted: taskId={}", taskId, e);
        } catch (Exception e) {
            log.error("Failed to execute task: taskId={}", taskId, e);
        }
    }
}
