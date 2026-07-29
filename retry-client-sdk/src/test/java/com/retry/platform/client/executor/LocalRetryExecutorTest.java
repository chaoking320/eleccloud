package com.retry.platform.client.executor;

import com.retry.platform.client.api.RetryClient;
import com.retry.platform.client.dto.RetryTaskDTO;
import com.retry.platform.client.mq.RetryMessageProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.contains;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link LocalRetryExecutor} 单元测试
 * 重点覆盖 execute() 的状态机守卫逻辑（任务不存在 / 终态提前返回 /
 * 抢锁失败 / 无 Hook / Hook 不存在回滚），避免重复执行与任务丢失。
 * 通过反射注入 mock 依赖，不依赖外部中间件。
 */
class LocalRetryExecutorTest {

    private LocalRetryExecutor executor;
    private RetryClient retryClient;
    private RetryMessageProducer producer;
    private ApplicationContext applicationContext;

    @BeforeEach
    void setUp() {
        executor = new LocalRetryExecutor();
        retryClient = mock(RetryClient.class);
        producer = mock(RetryMessageProducer.class);
        applicationContext = mock(ApplicationContext.class);
        ReflectionTestUtils.setField(executor, "retryClient", retryClient);
        ReflectionTestUtils.setField(executor, "retryMessageProducer", producer);
        ReflectionTestUtils.setField(executor, "applicationContext", applicationContext);
    }

    private RetryTaskDTO baseTask(String status) {
        RetryTaskDTO task = new RetryTaskDTO();
        task.setTaskId("T1");
        task.setTaskStatus(status);
        task.setRetryCount(0);
        task.setMaxRetryCount(5);
        task.setSceneType(1);
        return task;
    }

    @Test
    void returnsEarlyWhenTaskNotFoundOnServer() {
        when(retryClient.queryTask("T1")).thenReturn(null);
        executor.execute("T1");
        verify(retryClient, never()).markExecuting(anyString());
    }

    @Test
    void returnsEarlyWhenTaskAlreadySuccess() {
        when(retryClient.queryTask("T1")).thenReturn(baseTask("SUCCESS"));
        executor.execute("T1");
        verify(retryClient, never()).markExecuting(anyString());
    }

    @Test
    void returnsEarlyWhenTaskAlreadyFailed() {
        when(retryClient.queryTask("T1")).thenReturn(baseTask("FAILED"));
        executor.execute("T1");
        verify(retryClient, never()).markExecuting(anyString());
    }

    @Test
    void returnsWithoutRetryWhenLockNotAcquired() {
        when(retryClient.queryTask("T1")).thenReturn(baseTask("INIT"));
        when(retryClient.markExecuting("T1")).thenReturn(false);
        executor.execute("T1");
        verify(retryClient, never()).markFailed(anyString(), anyString());
        verify(producer, never()).sendDelayMessage(anyString(), anyLong(), anyInt());
    }

    @Test
    void marksFailedWhenNoHookClassConfigured() {
        RetryTaskDTO task = baseTask("INIT");
        task.setHookClass(null);
        when(retryClient.queryTask("T1")).thenReturn(task);
        when(retryClient.markExecuting("T1")).thenReturn(true);
        executor.execute("T1");
        verify(retryClient).markFailed(eq("T1"), contains("Hook class not found"));
    }

    @Test
    void rollsBackToPendingWhenHookBeanMissing() {
        RetryTaskDTO task = baseTask("INIT");
        task.setHookClass("java.lang.String");
        when(retryClient.queryTask("T1")).thenReturn(task);
        when(retryClient.markExecuting("T1")).thenReturn(true);
        when(applicationContext.getBean(any(Class.class))).thenThrow(new RuntimeException("no such bean"));
        executor.execute("T1");
        verify(retryClient).rollbackToPending(eq("T1"), anyString());
    }
}
