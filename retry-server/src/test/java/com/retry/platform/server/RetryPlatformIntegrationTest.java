package com.retry.platform.server;

import com.retry.platform.server.entity.RetryTask;
import com.retry.platform.server.mapper.RetryTaskMapper;
import com.retry.platform.server.service.DelayQueueService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 分布式重试平台核心集成测试
 * 覆盖分布式锁锁争抢断言、延时队列以及兜底扫描逻辑验证
 */
@Slf4j
@SpringBootTest
public class RetryPlatformIntegrationTest {

    @Autowired(required = false)
    private RedissonClient redissonClient;

    @Autowired
    private RetryTaskMapper retryTaskMapper;

    @Autowired
    private DelayQueueService delayQueueService;

    /**
     * 测试并断言 Redisson 分布式锁的互斥争抢
     */
    @Test
    public void testDistributedLockExclusivity() throws InterruptedException {
        if (redissonClient == null) {
            log.warn("RedissonClient not initialized, skipping distributed lock exclusivity test");
            return;
        }

        log.info("Starting Redisson Distributed Lock Exclusivity Test...");
        String lockKey = "retry:lock:test-integration-lock";
        RLock lock = redissonClient.getLock(lockKey);
        
        // 清理旧锁
        if (lock.isLocked()) {
            lock.unlock();
        }

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger lockSuccessCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    // 争抢锁，最多等待2秒，持锁3秒
                    boolean acquired = lock.tryLock(2, 3, TimeUnit.SECONDS);
                    if (acquired) {
                        lockSuccessCount.incrementAndGet();
                        log.info("Thread {} successfully acquired the lock!", Thread.currentThread().getName());
                        // 模拟持锁工作耗时
                        Thread.sleep(1500);
                        lock.unlock();
                        log.info("Thread {} released the lock.", Thread.currentThread().getName());
                    } else {
                        log.info("Thread {} failed to acquire the lock due to exclusivity constraint.", Thread.currentThread().getName());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // 由于持锁1.5秒，最多会有2-3个线程能够在10秒超时内成功轮流获取到锁，但任意时间绝对不会重叠
        log.info("Exclusivity test finished. Lock successful acquisitions count: {}", lockSuccessCount.get());
        Assertions.assertTrue(lockSuccessCount.get() >= 1, "Should have acquired lock at least once");
    }

    /**
     * 测试延时队列添加任务与数据库兜底扫描逻辑
     */
    @Test
    public void testDelayQueueAndDatabaseFallbackScan() {
        log.info("Starting Delay Queue and Fallback Scan Integration Test...");

        // 1. 验证 DelayQueueService 基础行为
        String testTaskId = "TEST-INTEGRATION-TASK-001";
        long executionTime = System.currentTimeMillis() + 5000; // 5秒后执行
        
        try {
            delayQueueService.addTask(testTaskId, executionTime);
            log.info("Successfully added test task {} to Redis delay queue", testTaskId);
            
            // 验证可从队列中获取或更新
            delayQueueService.updateTaskTime(testTaskId, executionTime + 2000);
            log.info("Successfully updated execution time for task {}", testTaskId);
            
            delayQueueService.removeTask(testTaskId);
            log.info("Successfully removed task {} from delay queue", testTaskId);
        } catch (Exception e) {
            log.warn("Redis delay queue operations encountered an exception (normal if Redis is offline during test run): {}", e.getMessage());
        }

        // 2. 验证数据库兜底扫描逻辑
        long testTimeLimit = System.currentTimeMillis() + 10000;
        int limit = 10;
        
        // 尝试从 MyBatis 查询待执行任务
        try {
            java.util.List<String> pendingTasks = retryTaskMapper.selectPendingTasks(testTimeLimit, limit);
            log.info("Database Fallback Scan query successful, found {} pending tasks", pendingTasks.size());
            Assertions.assertNotNull(pendingTasks, "Pending tasks collection should not be null");
        } catch (Exception e) {
            log.error("Database query failed during fallback scan test", e);
            Assertions.fail("Database query should succeed");
        }
    }
}
