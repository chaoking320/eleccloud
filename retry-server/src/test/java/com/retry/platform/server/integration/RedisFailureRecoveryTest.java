package com.retry.platform.server.integration;

import com.retry.platform.server.entity.RetryTask;
import com.retry.platform.server.mapper.RetryTaskMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Redis故障恢复测试
 * 测试Redis故障时系统的降级和恢复能力
 */
class RedisFailureRecoveryTest extends BaseIntegrationTest {

    @Autowired
    private RetryTaskMapper retryTaskMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void testSystemWorksWhenRedisDown() {
        // 创建任务（Redis正常）
        RetryTask task = createTestTask("REDIS-DOWN-" + System.currentTimeMillis());
        retryTaskMapper.insert(task);

        // 验证任务创建成功
        RetryTask savedTask = retryTaskMapper.selectByTaskId(task.getTaskId());
        assertNotNull(savedTask);

        // 模拟Redis故障（停止容器）
        REDIS_CONTAINER.stop();

        try {
            // 即使Redis down，仍然可以查询数据库
            RetryTask taskAfterRedisDown = retryTaskMapper.selectByTaskId(task.getTaskId());
            assertNotNull(taskAfterRedisDown);
            assertEquals(task.getTaskId(), taskAfterRedisDown.getTaskId());

            // 更新任务状态（应该成功，因为数据库仍然可用）
            taskAfterRedisDown.setTaskStatus("EXECUTING");
            int updated = retryTaskMapper.updateTaskStatus(
                    taskAfterRedisDown.getTaskId(),
                    "EXECUTING",
                    taskAfterRedisDown.getRetryCount()
            );
            assertTrue(updated > 0);

        } finally {
            // 恢复Redis
            REDIS_CONTAINER.start();
        }

        // 验证Redis恢复后系统正常
        try {
            redisTemplate.opsForValue().set("test-key", "test-value");
            String value = redisTemplate.opsForValue().get("test-key");
            assertEquals("test-value", value);
        } catch (Exception e) {
            fail("Redis should be recovered but got exception: " + e.getMessage());
        }
    }

    @Test
    void testRedisConnectionRecovery() {
        // Redis正常时写入
        String key = "recovery-test-" + System.currentTimeMillis();
        redisTemplate.opsForValue().set(key, "value1");
        assertEquals("value1", redisTemplate.opsForValue().get(key));

        // 停止Redis
        REDIS_CONTAINER.stop();

        // Redis down时操作会失败
        assertThrows(Exception.class, () -> {
            redisTemplate.opsForValue().set("should-fail", "value");
        });

        // 重启Redis
        REDIS_CONTAINER.start();

        // 等待连接恢复（最多5秒）
        boolean recovered = false;
        for (int i = 0; i < 10; i++) {
            try {
                Thread.sleep(500);
                redisTemplate.opsForValue().set(key, "value2");
                recovered = true;
                break;
            } catch (Exception e) {
                // 继续等待
            }
        }

        assertTrue(recovered, "Redis connection should recover after restart");
        assertEquals("value2", redisTemplate.opsForValue().get(key));
    }

    private RetryTask createTestTask(String taskId) {
        RetryTask task = new RetryTask();
        task.setTaskId(taskId);
        task.setSceneType(999);
        task.setIdempotentKey("KEY-" + taskId);
        task.setMethodClass("com.test.TestService");
        task.setMethodName("testMethod");
        task.setMethodParams("{}");
        task.setTaskStatus("INIT");
        task.setSubmitMode("POST_FAIL");
        task.setRetryCount(0);
        task.setMaxRetryCount(3);
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        return task;
    }
}
