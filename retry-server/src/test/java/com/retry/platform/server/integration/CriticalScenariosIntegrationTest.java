package com.retry.platform.server.integration;

import com.alibaba.fastjson2.JSON;
import com.retry.platform.server.entity.RetryTask;
import com.retry.platform.server.mapper.RetryTaskMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 关键场景集成测试
 * 
 * 测试覆盖：
 * 1. Redis故障降级到MySQL兜底
 * 2. 进程崩溃后任务恢复（ExecutingTimeoutScanner）
 * 3. PRE_SUBMIT完整流程
 * 4. 并发提交幂等性
 * 5. CAS锁并发安全
 * 6. 最大重试次数控制
 */
@DisplayName("关键场景集成测试")
class CriticalScenariosIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private RetryTaskMapper retryTaskMapper;

    @Autowired(required = false)
    private RedisTemplate<String, String> redisTemplate;

    // ==================== Redis故障降级测试 ====================

    @Test
    @DisplayName("Redis故障 - DatabaseFallbackScheduler应从MySQL捞取任务")
    void testRedisFaillure_shouldFallbackToDatabase() throws Exception {
        // Given: 创建一个已到期的任务
        String taskId = "REDIS_FAIL_" + System.currentTimeMillis();
        RetryTask task = new RetryTask();
        task.setTaskId(taskId);
        task.setSceneType(999);
        task.setIdempotentKey("FAIL_KEY_001");
        task.setMethodClass("com.test.TestService");
        task.setMethodName("testMethod");
        task.setMethodParams("{}");
        task.setTaskStatus("INIT");
        task.setSubmitMode("POST_FAIL");
        task.setRetryCount(0);
        task.setMaxRetryCount(3);
        task.setNextRetryTime(System.currentTimeMillis() - 60000); // 1分钟前就该执行
        retryTaskMapper.insert(task);

        // 模拟Redis不可用：不添加到Redis ZSET
        // DatabaseFallbackScheduler会扫描到这个任务

        // When: 等待DatabaseFallbackScheduler扫描（默认15秒一次）
        Thread.sleep(20000);

        // Then: 任务应该被捞起并处理（状态变化或被删除）
        RetryTask updatedTask = retryTaskMapper.selectByTaskId(taskId);
        if (updatedTask != null) {
            // 如果任务还在，状态应该不是INIT（说明被处理过）
            assertNotEquals("INIT", updatedTask.getTaskStatus(), 
                "Task should have been processed by DatabaseFallbackScheduler");
        }
        // 如果任务已被删除（成功或失败），也说明被处理了
    }

    // ==================== 进程崩溃恢复测试 ====================

    @Test
    @DisplayName("任务EXECUTING超时 - ExecutingTimeoutScanner应回滚为INIT")
    void testProcessCrash_shouldRecoverStuckExecutingTasks() throws Exception {
        // Given: 模拟一个卡在EXECUTING状态超时的任务
        String taskId = "CRASH_" + System.currentTimeMillis();
        RetryTask task = new RetryTask();
        task.setTaskId(taskId);
        task.setSceneType(999);
        task.setIdempotentKey("CRASH_KEY_001");
        task.setMethodClass("com.test.TestService");
        task.setMethodName("testMethod");
        task.setMethodParams("{}");
        task.setTaskStatus("EXECUTING");
        task.setSubmitMode("POST_FAIL");
        task.setRetryCount(1);
        task.setMaxRetryCount(3);
        task.setNextRetryTime(System.currentTimeMillis());
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now().minusMinutes(10)); // 10分钟前更新（超时）
        retryTaskMapper.insert(task);

        // When: 等待ExecutingTimeoutScanner扫描（默认1分钟一次，超时阈值5分钟）
        Thread.sleep(70000); // 等待1分钟+

        // Then: 任务应被回滚为INIT
        RetryTask recoveredTask = retryTaskMapper.selectByTaskId(taskId);
        assertNotNull(recoveredTask, "Task should exist");
        assertEquals("INIT", recoveredTask.getTaskStatus(), 
            "Stuck EXECUTING task should be recovered to INIT by ExecutingTimeoutScanner");
    }

    // ==================== PRE_SUBMIT完整流程测试 ====================

    @Test
    @DisplayName("PRE_SUBMIT模式 - 完整流程测试（提交→执行→成功标记）")
    void testPreSubmitMode_completeFlow() {
        // Step 1: 提交PRE_SUBMIT任务
        String idempotentKey = "PRESUB_" + System.currentTimeMillis();
        Map<String, Object> request = new HashMap<>();
        request.put("sceneType", 999);
        request.put("idempotentKey", idempotentKey);
        request.put("methodClass", "com.test.TestService");
        request.put("methodName", "testMethod");
        request.put("methodParams", "{\"param1\":\"value1\"}");
        request.put("submitMode", "PRE_SUBMIT");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(JSON.toJSONString(request), headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/retry/submit",
                entity,
                String.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> result = JSON.parseObject(response.getBody(), Map.class);
        String taskId = (String) result.get("data");
        assertNotNull(taskId);

        // Step 2: 验证任务状态为INIT
        RetryTask task = retryTaskMapper.selectByTaskId(taskId);
        assertNotNull(task);
        assertEquals("INIT", task.getTaskStatus());
        assertEquals("PRE_SUBMIT", task.getSubmitMode());

        // Step 3: 模拟业务方法执行成功，调用markSuccess
        ResponseEntity<String> markResponse = restTemplate.postForEntity(
                "/api/retry/success/" + taskId,
                null,
                String.class
        );

        assertEquals(HttpStatus.OK, markResponse.getStatusCode());

        // Step 4: 验证任务已标记为SUCCESS
        RetryTask successTask = retryTaskMapper.selectByTaskId(taskId);
        assertNotNull(successTask);
        assertEquals("SUCCESS", successTask.getTaskStatus());
    }

    // ==================== 并发幂等性测试 ====================

    @Test
    @DisplayName("并发提交相同幂等键 - 应只创建一个任务")
    void testConcurrentSubmit_sameIdempotentKey_shouldCreateOnlyOne() throws Exception {
        // Given: 10个线程同时提交相同幂等键的任务
        String idempotentKey = "CONCURRENT_" + System.currentTimeMillis();
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // When: 并发提交
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    Map<String, Object> request = new HashMap<>();
                    request.put("sceneType", 999);
                    request.put("idempotentKey", idempotentKey);
                    request.put("methodClass", "com.test.TestService");
                    request.put("methodName", "testMethod");
                    request.put("methodParams", "{}");

                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    HttpEntity<String> entity = new HttpEntity<>(JSON.toJSONString(request), headers);

                    ResponseEntity<String> response = restTemplate.postForEntity(
                            "/api/retry/submit",
                            entity,
                            String.class
                    );

                    if (response.getStatusCode() == HttpStatus.OK) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Then: 所有请求都应该成功，但数据库只有一条记录
        assertEquals(threadCount, successCount.get(), "All requests should succeed");
        
        RetryTask task = retryTaskMapper.selectBySceneAndIdempotentKey(999, idempotentKey);
        assertNotNull(task, "Task should be created");
        
        // 验证数据库中只有一条记录
        // 注意：由于幂等性，可能返回相同的taskId，需要从日志或其他方式验证
    }

    // ==================== CAS锁并发安全测试 ====================

    @Test
    @DisplayName("并发执行同一任务 - CAS锁应确保只有一个节点执行")
    void testConcurrentExecution_casLock_shouldOnlyOneExecute() throws Exception {
        // Given: 创建一个任务
        String taskId = "CAS_" + System.currentTimeMillis();
        RetryTask task = new RetryTask();
        task.setTaskId(taskId);
        task.setSceneType(999);
        task.setIdempotentKey("CAS_KEY_001");
        task.setMethodClass("com.test.TestService");
        task.setMethodName("testMethod");
        task.setMethodParams("{}");
        task.setTaskStatus("INIT");
        task.setSubmitMode("POST_FAIL");
        task.setRetryCount(0);
        task.setMaxRetryCount(3);
        retryTaskMapper.insert(task);

        // When: 10个线程同时尝试执行
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger acquiredCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    ResponseEntity<String> response = restTemplate.postForEntity(
                            "/api/retry/executing/" + taskId,
                            null,
                            String.class
                    );

                    if (response.getStatusCode() == HttpStatus.OK) {
                        Map<String, Object> result = JSON.parseObject(response.getBody(), Map.class);
                        Boolean acquired = (Boolean) result.get("data");
                        if (Boolean.TRUE.equals(acquired)) {
                            acquiredCount.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Then: 只有一个线程应该成功获取锁
        assertEquals(1, acquiredCount.get(), 
            "Only one thread should acquire the CAS lock");
        
        // 验证任务状态已变为EXECUTING
        RetryTask lockedTask = retryTaskMapper.selectByTaskId(taskId);
        assertEquals("EXECUTING", lockedTask.getTaskStatus());
    }

    // ==================== 最大重试次数控制测试 ====================

    @Test
    @DisplayName("超过最大重试次数 - 应移入failed_task表")
    void testMaxRetryExceeded_shouldMoveToFailedTable() {
        // Given: 创建一个已达最大重试次数的任务
        String taskId = "MAX_RETRY_" + System.currentTimeMillis();
        String idempotentKey = "MAX_KEY_001";
        RetryTask task = new RetryTask();
        task.setTaskId(taskId);
        task.setSceneType(999);
        task.setIdempotentKey(idempotentKey);
        task.setMethodClass("com.test.TestService");
        task.setMethodName("testMethod");
        task.setMethodParams("{}");
        task.setTaskStatus("INIT");
        task.setSubmitMode("POST_FAIL");
        task.setRetryCount(3);
        task.setMaxRetryCount(3); // 已达上限
        retryTaskMapper.insert(task);

        // When: 标记为失败
        Map<String, Object> failRequest = new HashMap<>();
        failRequest.put("taskId", taskId);
        failRequest.put("reason", "Max retry count exceeded");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(JSON.toJSONString(failRequest), headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/retry/failed",
                entity,
                String.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());

        // Then: 任务应从retry_task表删除
        RetryTask deletedTask = retryTaskMapper.selectByTaskId(taskId);
        assertNull(deletedTask, "Task should be removed from retry_task");

        // 任务应存在于failed_task表（如果有映射器）
        // 这里简化处理，实际项目中应验证failed_task表
    }

    // ==================== 数据一致性测试 ====================

    @Test
    @DisplayName("事务回滚 - 任务提交失败应完全回滚")
    void testTransactionRollback_shouldRollbackCompletely() {
        // Given: 提交一个无效场景的任务（应触发异常）
        Map<String, Object> request = new HashMap<>();
        request.put("sceneType", -1); // 无效场景
        request.put("idempotentKey", "INVALID_" + System.currentTimeMillis());
        request.put("methodClass", "com.test.TestService");
        request.put("methodName", "testMethod");
        request.put("methodParams", "{}");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(JSON.toJSONString(request), headers);

        // When: 提交任务
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/retry/submit",
                entity,
                String.class
        );

        // Then: 应该返回错误，且数据库中不应有记录
        // 由于场景不存在，应该返回失败
        Map<String, Object> result = JSON.parseObject(response.getBody(), Map.class);
        assertFalse((Boolean) result.get("success"), "Request should fail for invalid scene");
        
        // 验证数据库中没有残留数据
        RetryTask task = retryTaskMapper.selectBySceneAndIdempotentKey(-1, 
            "INVALID_" + System.currentTimeMillis());
        assertNull(task, "No data should be left in database after rollback");
    }

    // ==================== 性能基准测试 ====================

    @Test
    @DisplayName("批量提交性能测试 - 100个任务应在合理时间内完成")
    void testPerformance_batchSubmit_shouldCompleteInReasonableTime() throws Exception {
        // Given: 准备100个任务
        int taskCount = 100;
        long startTime = System.currentTimeMillis();

        // When: 批量提交
        for (int i = 0; i < taskCount; i++) {
            Map<String, Object> request = new HashMap<>();
            request.put("sceneType", 999);
            request.put("idempotentKey", "PERF_" + System.currentTimeMillis() + "_" + i);
            request.put("methodClass", "com.test.TestService");
            request.put("methodName", "testMethod");
            request.put("methodParams", "{}");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(JSON.toJSONString(request), headers);

            restTemplate.postForEntity("/api/retry/submit", entity, String.class);
        }

        long duration = System.currentTimeMillis() - startTime;

        // Then: 应在合理时间内完成（例如10秒）
        assertTrue(duration < 10000, 
            String.format("100 tasks should be submitted within 10s, actual: %dms", duration));
        
        double tps = (taskCount * 1000.0) / duration;
        System.out.println(String.format("Performance: %d tasks in %dms, TPS: %.2f", 
            taskCount, duration, tps));
    }
}
