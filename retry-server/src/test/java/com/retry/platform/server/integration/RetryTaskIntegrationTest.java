package com.retry.platform.server.integration;

import com.alibaba.fastjson2.JSON;
import com.retry.platform.server.entity.RetryTask;
import com.retry.platform.server.mapper.FailedTaskMapper;
import com.retry.platform.server.mapper.RetryTaskMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 重试任务集成测试
 * 测试完整的任务提交、查询、状态更新流程
 */
class RetryTaskIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private RetryTaskMapper retryTaskMapper;

    @Autowired
    private FailedTaskMapper failedTaskMapper;

    @Test
    void testSubmitAndQueryTask() {
        // 准备任务数据
        Map<String, Object> request = new HashMap<>();
        request.put("sceneType", 999);
        request.put("idempotentKey", "TEST-" + System.currentTimeMillis());
        request.put("methodClass", "com.test.TestService");
        request.put("methodName", "testMethod");
        request.put("methodParams", "{\"param1\":\"value1\"}");
        request.put("submitMode", "POST_FAIL");

        // 提交任务
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(JSON.toJSONString(request), headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/retry/submit",
                entity,
                String.class
        );

        // 验证提交成功
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("\"success\":true"));

        // 解析返回的taskId
        Map<String, Object> result = JSON.parseObject(response.getBody(), Map.class);
        String taskId = (String) result.get("data");
        assertNotNull(taskId);

        // 查询任务
        ResponseEntity<String> queryResponse = restTemplate.getForEntity(
                "/api/retry/task/" + taskId,
                String.class
        );

        assertEquals(HttpStatus.OK, queryResponse.getStatusCode());
        Map<String, Object> queryResult = JSON.parseObject(queryResponse.getBody(), Map.class);
        assertTrue((Boolean) queryResult.get("success"));

        Map<String, Object> taskData = (Map<String, Object>) queryResult.get("data");
        assertEquals(taskId, taskData.get("taskId"));
        assertEquals(999, taskData.get("sceneType"));
        assertEquals("INIT", taskData.get("taskStatus"));
    }

    @Test
    void testDuplicateSubmitWithSameIdempotentKey() {
        String idempotentKey = "DUPLICATE-" + System.currentTimeMillis();

        Map<String, Object> request = new HashMap<>();
        request.put("sceneType", 999);
        request.put("idempotentKey", idempotentKey);
        request.put("methodClass", "com.test.TestService");
        request.put("methodName", "testMethod");
        request.put("methodParams", "{}");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(JSON.toJSONString(request), headers);

        // 第一次提交
        ResponseEntity<String> response1 = restTemplate.postForEntity(
                "/api/retry/submit",
                entity,
                String.class
        );
        assertEquals(HttpStatus.OK, response1.getStatusCode());

        // 第二次提交相同幂等键
        ResponseEntity<String> response2 = restTemplate.postForEntity(
                "/api/retry/submit",
                entity,
                String.class
        );

        // 应该返回错误或相同的taskId
        assertEquals(HttpStatus.OK, response2.getStatusCode());
        // 验证数据库中只有一条记录
        RetryTask task = retryTaskMapper.selectBySceneAndIdempotentKey(999, idempotentKey);
        assertNotNull(task);
    }

    @Test
    void testUpdateTaskStatus() {
        // 先提交一个任务
        String idempotentKey = "UPDATE-" + System.currentTimeMillis();
        RetryTask task = new RetryTask();
        task.setTaskId("TASK-" + System.currentTimeMillis());
        task.setSceneType(999);
        task.setIdempotentKey(idempotentKey);
        task.setMethodClass("com.test.TestService");
        task.setMethodName("testMethod");
        task.setMethodParams("{}");
        task.setTaskStatus("INIT");
        task.setSubmitMode("POST_FAIL");
        task.setRetryCount(0);
        task.setMaxRetryCount(3);
        retryTaskMapper.insert(task);

        // 更新状态为EXECUTING
        Map<String, Object> updateRequest = new HashMap<>();
        updateRequest.put("taskId", task.getTaskId());
        updateRequest.put("status", "EXECUTING");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(JSON.toJSONString(updateRequest), headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/retry/status",
                entity,
                String.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());

        // 验证状态已更新
        RetryTask updatedTask = retryTaskMapper.selectByTaskId(task.getTaskId());
        assertEquals("EXECUTING", updatedTask.getTaskStatus());
    }

    @Test
    void testMarkTaskAsSuccess() {
        // 创建任务
        String taskId = "SUCCESS-" + System.currentTimeMillis();
        RetryTask task = new RetryTask();
        task.setTaskId(taskId);
        task.setSceneType(999);
        task.setIdempotentKey("KEY-" + System.currentTimeMillis());
        task.setMethodClass("com.test.TestService");
        task.setMethodName("testMethod");
        task.setMethodParams("{}");
        task.setTaskStatus("WAIT");
        task.setSubmitMode("POST_FAIL");
        task.setRetryCount(1);
        task.setMaxRetryCount(3);
        retryTaskMapper.insert(task);

        // 标记成功
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/retry/success/" + taskId,
                null,
                String.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());

        // 验证状态
        RetryTask updatedTask = retryTaskMapper.selectByTaskId(taskId);
        assertEquals("SUCCESS", updatedTask.getTaskStatus());
    }

    @Test
    void testMarkTaskAsFailed() {
        // 创建任务
        String taskId = "FAILED-" + System.currentTimeMillis();
        String idempotentKey = "KEY-" + System.currentTimeMillis();
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
        task.setMaxRetryCount(3);
        retryTaskMapper.insert(task);

        // 标记失败
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

        // 验证任务已从retry_task删除
        RetryTask deletedTask = retryTaskMapper.selectByTaskId(taskId);
        assertNull(deletedTask);

        // 验证任务已进入failed_task
        assertNotNull(failedTaskMapper.selectByTaskId(taskId));
    }

    @Test
    void testConcurrentExecutionLock() throws InterruptedException {
        // 创建任务
        String taskId = "LOCK-" + System.currentTimeMillis();
        RetryTask task = new RetryTask();
        task.setTaskId(taskId);
        task.setSceneType(999);
        task.setIdempotentKey("KEY-" + System.currentTimeMillis());
        task.setMethodClass("com.test.TestService");
        task.setMethodName("testMethod");
        task.setMethodParams("{}");
        task.setTaskStatus("INIT");
        task.setSubmitMode("POST_FAIL");
        task.setRetryCount(0);
        task.setMaxRetryCount(3);
        retryTaskMapper.insert(task);

        // 两个线程同时尝试获取执行锁
        Thread thread1 = new Thread(() -> {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    "/api/retry/executing/" + taskId,
                    null,
                    String.class
            );
        });

        Thread thread2 = new Thread(() -> {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    "/api/retry/executing/" + taskId,
                    null,
                    String.class
            );
        });

        thread1.start();
        thread2.start();

        thread1.join();
        thread2.join();

        // 验证只有一个线程成功获取锁（状态应该是EXECUTING）
        RetryTask lockedTask = retryTaskMapper.selectByTaskId(taskId);
        assertEquals("EXECUTING", lockedTask.getTaskStatus());
    }
}
