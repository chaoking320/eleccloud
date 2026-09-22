package com.retry.platform.client.standalone;

import com.retry.platform.client.config.RetryClientProperties;
import com.retry.platform.client.config.StandaloneSceneConfig;
import com.retry.platform.client.dto.RetryTaskDTO;
import com.retry.platform.client.dto.RetryTaskRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link StandaloneRetryClientImpl} 单元测试
 * 覆盖 Standalone 模式两大核心契约：
 * - 三级策略优先级合并：Request(注解) > YAML scenes > SDK 内置默认
 * - 幂等保护：活跃任务复用 taskId，终态任务重置复用行（防唯一索引冲突）
 */
class StandaloneRetryClientImplTest {

    private StandaloneRetryTaskMapper taskMapper;
    private StandaloneRetryHistoryMapper historyMapper;
    private RetryClientProperties properties;
    private StandaloneRetryClientImpl client;

    @BeforeEach
    void setUp() {
        taskMapper = mock(StandaloneRetryTaskMapper.class);
        historyMapper = mock(StandaloneRetryHistoryMapper.class);
        properties = new RetryClientProperties();
        properties.setMode("standalone");
        client = new StandaloneRetryClientImpl(taskMapper, historyMapper, properties);
    }

    private RetryTaskRequest baseRequest() {
        RetryTaskRequest req = new RetryTaskRequest();
        req.setSceneType(1001);
        req.setIdempotentKey("KEY-1");
        req.setMethodClass("com.example.DemoService");
        req.setMethodName("sync");
        req.setMethodParams("{\"orderId\":\"KEY-1\"}");
        req.setSubmitMode("POST_FAIL");
        return req;
    }

    private StandaloneSceneConfig ymlScene(Integer sceneType) {
        StandaloneSceneConfig s = new StandaloneSceneConfig();
        s.setSceneType(sceneType);
        s.setMaxRetryCount(9);
        s.setRetryIntervals("11,22");
        s.setBackoffStrategy("LINEAR");
        s.setBackoffBase(7);
        s.setHookClass("com.example.YmlHook");
        return s;
    }

    private RetryTaskDTO captureInserted() {
        ArgumentCaptor<RetryTaskDTO> captor = ArgumentCaptor.forClass(RetryTaskDTO.class);
        verify(taskMapper).insert(captor.capture());
        return captor.getValue();
    }

    // ==================== 三级策略优先级 ====================

    @Test
    void sdkDefaultsApplyWhenNothingConfigured() {
        when(taskMapper.selectBySceneAndIdempotentKey(anyInt(), anyString())).thenReturn(null);

        String taskId = client.submit(baseRequest());

        assertNotNull(taskId);
        assertTrue(taskId.startsWith("RT"));
        RetryTaskDTO task = captureInserted();
        assertEquals("INIT", task.getTaskStatus());
        assertEquals(3, task.getMaxRetryCount());          // SDK 默认
        assertEquals("1,3,5", task.getRetryIntervals());   // SDK 默认
        assertEquals("CUSTOM", task.getBackoffStrategy()); // SDK 默认
        assertEquals(1, task.getBackoffBase());            // SDK 默认
        assertEquals("POST_FAIL", task.getSubmitMode());
    }

    @Test
    void requestFieldsOverrideYamlAndDefaults() {
        when(taskMapper.selectBySceneAndIdempotentKey(anyInt(), anyString())).thenReturn(null);
        properties.setScenes(Collections.singletonList(ymlScene(1001)));

        RetryTaskRequest req = baseRequest();
        req.setMaxRetryCount(5);
        req.setRetryIntervals("1,2");
        req.setBackoffStrategy("EXPONENTIAL");
        req.setBackoffBase(3);
        req.setHookClass("com.example.AnnoHook");

        client.submit(req);

        RetryTaskDTO task = captureInserted();
        // 第一级：Request 全胜
        assertEquals(5, task.getMaxRetryCount());
        assertEquals("1,2", task.getRetryIntervals());
        assertEquals("EXPONENTIAL", task.getBackoffStrategy());
        assertEquals(3, task.getBackoffBase());
        assertEquals("com.example.AnnoHook", task.getHookClass());
    }

    @Test
    void yamlScenesOverrideSdkDefaultsWhenRequestSilent() {
        when(taskMapper.selectBySceneAndIdempotentKey(anyInt(), anyString())).thenReturn(null);
        properties.setScenes(Collections.singletonList(ymlScene(1001)));

        client.submit(baseRequest());

        RetryTaskDTO task = captureInserted();
        // 第二级：YAML 覆盖 SDK 默认
        assertEquals(9, task.getMaxRetryCount());
        assertEquals("11,22", task.getRetryIntervals());
        assertEquals("LINEAR", task.getBackoffStrategy());
        assertEquals(7, task.getBackoffBase());
        assertEquals("com.example.YmlHook", task.getHookClass());
    }

    @Test
    void yamlOfDifferentSceneTypeIsIgnored() {
        when(taskMapper.selectBySceneAndIdempotentKey(anyInt(), anyString())).thenReturn(null);
        properties.setScenes(Collections.singletonList(ymlScene(9999))); // 场景不匹配

        client.submit(baseRequest());

        RetryTaskDTO task = captureInserted();
        assertEquals(3, task.getMaxRetryCount());        // 落回 SDK 默认
        assertEquals(null, task.getHookClass());
    }

    // ==================== 幂等与状态保护 ====================

    @Test
    void activeTaskIsReusedWithoutNewInsert() {
        RetryTaskDTO existing = new RetryTaskDTO();
        existing.setTaskId("RT-EXISTING");
        existing.setTaskStatus("WAIT");
        when(taskMapper.selectBySceneAndIdempotentKey(1001, "KEY-1")).thenReturn(existing);

        String taskId = client.submit(baseRequest());

        assertEquals("RT-EXISTING", taskId);
        verify(taskMapper, never()).insert(any());
        verify(taskMapper, never()).updateStatusAndRetryInfo(anyString(), anyString(), anyInt(), anyLong());
    }

    @Test
    void terminatedTaskIsResetToInitReusingRow() {
        RetryTaskDTO existing = new RetryTaskDTO();
        existing.setTaskId("RT-OLD");
        existing.setTaskStatus("SUCCESS");
        when(taskMapper.selectBySceneAndIdempotentKey(1001, "KEY-1")).thenReturn(existing);

        String taskId = client.submit(baseRequest());

        // 保留原 taskId 防止 (scene_type, idempotent_key) 唯一索引冲突，行重置为 INIT
        assertEquals("RT-OLD", taskId);
        verify(taskMapper, never()).insert(any());
        verify(taskMapper).updateStatusAndRetryInfo(eq("RT-OLD"), eq("INIT"), eq(0), anyLong());
    }

    // ==================== 状态操作 ====================

    @Test
    void markExecutingIsCasBasedOnAffectedRows() {
        when(taskMapper.casUpdateToExecuting("T1")).thenReturn(1).thenReturn(0);
        assertTrue(client.markExecuting("T1"));   // 抢占成功
        assertEquals(false, client.markExecuting("T1")); // 已被他人抢占
    }

    @Test
    void historyFailureDoesNotBreakMainFlow() {
        when(historyMapper.insert(anyString(), anyInt(), anyString(), any(), anyLong()))
                .thenThrow(new RuntimeException("db down"));
        // recordHistory 内部吞异常：审计是弱依赖，绝不阻断重试主流程
        client.recordHistory("T1", 1, "SUCCESS", null, 10L);
    }

    @Test
    void queryTaskSwallowsMapperExceptionReturnsNull() {
        when(taskMapper.selectByTaskId("T1")).thenThrow(new RuntimeException("db down"));
        assertEquals(null, client.queryTask("T1"));
    }
}
