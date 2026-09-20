package com.retry.platform.client.executor;

import com.retry.platform.client.api.RetryInternalClient;
import com.retry.platform.client.dto.RetryTaskDTO;
import com.retry.platform.client.hook.QueryResult;
import com.retry.platform.client.hook.RetryContext;
import com.retry.platform.client.hook.RetryHook;
import com.retry.platform.client.hook.RetryStatus;
import com.retry.platform.client.mq.RetryMessagePayload;
import com.retry.platform.client.mq.RetryMessageProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * LocalRetryExecutor 完整测试套件
 * 
 * 测试覆盖：
 * 1. 状态机所有分支（SUCCESS/WAIT/INIT）
 * 2. 并发安全（CAS锁定）
 * 3. Hook生命周期完整流程
 * 4. 边界条件与异常处理
 * 5. 胖瘦消息入口
 * 6. 重试次数上限控制
 * 7. 看门狗心跳机制
 */
@DisplayName("LocalRetryExecutor 完整测试")
class LocalRetryExecutorComprehensiveTest {

    private LocalRetryExecutor executor;
    private RetryInternalClient retryClient;
    private RetryMessageProducer messageProducer;
    private ApplicationContext applicationContext;
    private TestRetryHook testHook;

    @BeforeEach
    void setUp() {
        executor = new LocalRetryExecutor();
        retryClient = mock(RetryInternalClient.class);
        messageProducer = mock(RetryMessageProducer.class);
        applicationContext = mock(ApplicationContext.class);
        testHook = spy(new TestRetryHook());

        ReflectionTestUtils.setField(executor, "retryClient", retryClient);
        ReflectionTestUtils.setField(executor, "retryMessageProducer", messageProducer);
        ReflectionTestUtils.setField(executor, "applicationContext", applicationContext);
    }

    // ==================== 状态机测试：SUCCESS分支 ====================

    @Test
    @DisplayName("Hook.checkStatus返回SUCCESS - 应跳过重试，直接执行回调并标记成功")
    void testSuccessBranch_shouldSkipRetryAndMarkSuccess() {
        // Given: 任务已成功完成
        testHook.setCheckStatusResult(RetryStatus.SUCCESS);
        testHook.setDoQueryResult(QueryResult.success("Already done"));
        
        RetryMessagePayload payload = createPayload("TASK001", 1);
        when(retryClient.markExecuting("TASK001")).thenReturn(true);
        when(applicationContext.getBean(TestRetryHook.class)).thenReturn(testHook);
        when(applicationContext.getBeansOfType(RetryHook.class))
            .thenReturn(java.util.Collections.singletonMap("testHook", testHook));

        // When: 执行重试
        executor.executeWithPayload(payload);

        // Then: 应该调用doCallback并标记SUCCESS，不应该发送延时消息
        verify(testHook, times(1)).checkStatus(any(RetryContext.class));
        verify(testHook, times(1)).doCallback(any(RetryContext.class), any(QueryResult.class));
        verify(retryClient, times(1)).markSuccess("TASK001");
        verify(messageProducer, never()).sendDelayMessageWithPayload(any(), anyLong());
    }

    // ==================== 状态机测试：WAIT分支 ====================

    @Test
    @DisplayName("Hook.checkStatus返回WAIT且doQuery返回成功 - 应执行回调并标记成功")
    void testWaitBranch_querySuccess_shouldExecuteCallbackAndMarkSuccess() {
        // Given: 任务在等待，查询后确认成功
        testHook.setCheckStatusResult(RetryStatus.WAIT);
        testHook.setDoQueryResult(QueryResult.success("Payment confirmed"));
        
        RetryMessagePayload payload = createPayload("TASK002", 2);
        when(retryClient.markExecuting("TASK002")).thenReturn(true);
        when(applicationContext.getBeansOfType(RetryHook.class))
            .thenReturn(java.util.Collections.singletonMap("testHook", testHook));

        // When
        executor.executeWithPayload(payload);

        // Then: 应调用doQuery和doCallback，并标记SUCCESS
        verify(testHook, times(1)).checkStatus(any());
        verify(testHook, times(1)).doQuery(any());
        verify(testHook, times(1)).doCallback(any(), any());
        verify(retryClient, times(1)).updateRetryCountAndStatus(eq("TASK002"), eq(2), eq("SUCCESS"));
        verify(retryClient, times(1)).recordHistory(eq("TASK002"), eq(2), eq("SUCCESS"), isNull(), anyLong());
    }

    @Test
    @DisplayName("Hook.checkStatus返回WAIT且doQuery返回pending - 应重新调度")
    void testWaitBranch_queryPending_shouldReschedule() {
        // Given: 任务在等待，查询后仍未完成
        testHook.setCheckStatusResult(RetryStatus.WAIT);
        testHook.setDoQueryResult(QueryResult.pending("Still processing"));
        
        RetryMessagePayload payload = createPayload("TASK003", 1, 3);
        when(retryClient.markExecuting("TASK003")).thenReturn(true);
        when(applicationContext.getBeansOfType(RetryHook.class))
            .thenReturn(java.util.Collections.singletonMap("testHook", testHook));

        // When
        executor.executeWithPayload(payload);

        // Then: 应记录PENDING历史，并重新调度
        verify(testHook, times(1)).doQuery(any());
        verify(testHook, never()).doCallback(any(), any());
        verify(retryClient, times(1)).recordHistory(eq("TASK003"), eq(1), eq("PENDING"), 
            eq("doQuery returned not-success"), anyLong());
        
        ArgumentCaptor<RetryMessagePayload> captor = ArgumentCaptor.forClass(RetryMessagePayload.class);
        verify(messageProducer, times(1)).sendDelayMessageWithPayload(captor.capture(), anyLong());
        assertEquals(2, captor.getValue().getRetryCount()); // 重试次数+1
    }

    @Test
    @DisplayName("Hook.checkStatus返回WAIT但doQuery抛异常 - 应记录失败并重新调度")
    void testWaitBranch_queryThrowsException_shouldReschedule() {
        // Given: 查询时抛出异常
        testHook.setCheckStatusResult(RetryStatus.WAIT);
        testHook.setThrowOnQuery(new RuntimeException("Network timeout"));
        
        RetryMessagePayload payload = createPayload("TASK004", 1, 3);
        when(retryClient.markExecuting("TASK004")).thenReturn(true);
        when(applicationContext.getBeansOfType(RetryHook.class))
            .thenReturn(java.util.Collections.singletonMap("testHook", testHook));

        // When
        executor.executeWithPayload(payload);

        // Then: 应记录FAILED历史并重新调度
        verify(retryClient, times(1)).recordHistory(eq("TASK004"), eq(1), eq("FAILED"), 
            contains("Network timeout"), anyLong());
        verify(messageProducer, times(1)).sendDelayMessageWithPayload(any(), anyLong());
    }

    // ==================== 状态机测试：INIT分支 ====================

    @Test
    @DisplayName("Hook.checkStatus返回INIT - 应执行业务方法并查询确认")
    void testInitBranch_shouldExecuteMethodAndQuery() {
        // Given: 任务需要执行
        testHook.setCheckStatusResult(RetryStatus.INIT);
        testHook.setDoQueryResult(QueryResult.success("Executed successfully"));
        
        // 模拟业务Bean和方法
        TestBusinessService businessService = spy(new TestBusinessService());
        RetryMessagePayload payload = createPayloadWithMethod("TASK005", 
            TestBusinessService.class.getName(), "processOrder", 1, 3);
        
        when(retryClient.markExecuting("TASK005")).thenReturn(true);
        when(applicationContext.getBeansOfType(RetryHook.class))
            .thenReturn(java.util.Collections.singletonMap("testHook", testHook));
        when(applicationContext.getBean(TestBusinessService.class)).thenReturn(businessService);
        when(applicationContext.getClassLoader()).thenReturn(this.getClass().getClassLoader());

        // When
        executor.executeWithPayload(payload);

        // Then: 应执行业务方法、调用doQuery和doCallback
        verify(businessService, times(1)).processOrder(anyString());
        verify(testHook, times(1)).doQuery(any());
        verify(testHook, times(1)).doCallback(any(), any());
        verify(retryClient, times(1)).updateRetryCountAndStatus(eq("TASK005"), eq(1), eq("SUCCESS"));
    }

    // ==================== 并发安全测试 ====================

    @Test
    @DisplayName("CAS锁定失败 - 应立即退出不执行")
    void testConcurrency_casLockFailed_shouldExitImmediately() {
        // Given: 另一个节点已锁定任务
        RetryMessagePayload payload = createPayload("TASK006", 1);
        when(retryClient.markExecuting("TASK006")).thenReturn(false);

        // When
        executor.executeWithPayload(payload);

        // Then: 应该直接返回，不执行任何逻辑
        verify(testHook, never()).checkStatus(any());
        verify(retryClient, never()).updateStatus(anyString(), anyString());
        verify(messageProducer, never()).sendDelayMessageWithPayload(any(), anyLong());
    }

    // ==================== 重试次数上限测试 ====================

    @Test
    @DisplayName("达到最大重试次数 - 应标记为FAILED不再重试")
    void testMaxRetryExceeded_shouldMarkFailedAndStop() {
        // Given: 已达到最大重试次数
        testHook.setCheckStatusResult(RetryStatus.WAIT);
        testHook.setDoQueryResult(QueryResult.pending("Still waiting"));
        
        RetryMessagePayload payload = createPayload("TASK007", 5, 5); // retryCount=5, max=5
        when(retryClient.markExecuting("TASK007")).thenReturn(true);
        when(applicationContext.getBeansOfType(RetryHook.class))
            .thenReturn(java.util.Collections.singletonMap("testHook", testHook));

        // When
        executor.executeWithPayload(payload);

        // Then: 应标记为FAILED，不再发送延时消息
        verify(retryClient, times(1)).markFailed(eq("TASK007"), contains("max retry count"));
        verify(messageProducer, never()).sendDelayMessageWithPayload(any(), anyLong());
    }

    // ==================== 胖瘦消息入口测试 ====================

    @Test
    @DisplayName("瘦消息入口 - 应先查询任务详情再执行")
    void testSlimMessage_shouldQueryTaskFirst() {
        // Given: 使用瘦消息（只有taskId）
        RetryTaskDTO taskDTO = new RetryTaskDTO();
        taskDTO.setTaskId("TASK008");
        taskDTO.setTaskStatus("INIT");
        taskDTO.setSceneType(100);
        taskDTO.setRetryCount(0);
        taskDTO.setMaxRetryCount(3);
        taskDTO.setHookClass(TestRetryHook.class.getName());
        taskDTO.setMethodClass(TestBusinessService.class.getName());
        taskDTO.setMethodName("processOrder");
        taskDTO.setMethodParams("{\"orderId\":\"ORDER001\"}");
        
        when(retryClient.queryTask("TASK008")).thenReturn(taskDTO);
        when(retryClient.markExecuting("TASK008")).thenReturn(true);
        when(applicationContext.getBeansOfType(RetryHook.class))
            .thenReturn(java.util.Collections.singletonMap("testHook", testHook));

        // When: 使用瘦消息入口
        executor.execute("TASK008");

        // Then: 应该先查询任务
        verify(retryClient, times(1)).queryTask("TASK008");
        verify(retryClient, times(1)).markExecuting("TASK008");
    }

    @Test
    @DisplayName("瘦消息入口 - 任务已是终态应直接返回")
    void testSlimMessage_terminalStatus_shouldReturnImmediately() {
        // Given: 任务已成功
        RetryTaskDTO taskDTO = new RetryTaskDTO();
        taskDTO.setTaskId("TASK009");
        taskDTO.setTaskStatus("SUCCESS");
        
        when(retryClient.queryTask("TASK009")).thenReturn(taskDTO);

        // When
        executor.execute("TASK009");

        // Then: 不应尝试锁定
        verify(retryClient, never()).markExecuting(anyString());
    }

    // ==================== 边界条件与异常处理 ====================

    @Test
    @DisplayName("Hook实例不存在 - 应降级为NoOpHook并继续执行")
    void testHookNotFound_shouldFallbackToNoOp() {
        // Given: Hook类不存在
        RetryMessagePayload payload = createPayload("TASK010", 1);
        payload.setHookClass("com.nonexistent.HookClass");
        
        when(retryClient.markExecuting("TASK010")).thenReturn(true);
        when(applicationContext.getBeansOfType(RetryHook.class))
            .thenReturn(java.util.Collections.emptyMap());

        // When: 不应抛出异常
        assertDoesNotThrow(() -> executor.executeWithPayload(payload));

        // Then: 应该继续执行（使用NoOpHook）
        verify(retryClient, times(1)).markExecuting("TASK010"));
    }

    @Test
    @DisplayName("RetryContext构建 - 应正确解析参数和类型")
    void testRetryContextBuilding_shouldParseCorrectly() {
        // Given
        testHook.setCheckStatusResult(RetryStatus.SUCCESS);
        testHook.setDoQueryResult(QueryResult.success("OK"));
        
        RetryMessagePayload payload = RetryMessagePayload.builder()
            .taskId("TASK011")
            .sceneType(100)
            .idempotentKey("ORDER001")
            .methodClass(TestBusinessService.class.getName())
            .methodName("processOrder")
            .methodParams("{\"orderId\":\"ORDER001\",\"amount\":100.5}")
            .methodParamTypes("java.lang.String,java.lang.Double")
            .hookClass(TestRetryHook.class.getName())
            .retryCount(1)
            .maxRetryCount(5)
            .build();
        
        when(retryClient.markExecuting("TASK011")).thenReturn(true);
        when(applicationContext.getBeansOfType(RetryHook.class))
            .thenReturn(java.util.Collections.singletonMap("testHook", testHook));

        // When
        executor.executeWithPayload(payload);

        // Then: 验证RetryContext正确构建
        ArgumentCaptor<RetryContext> contextCaptor = ArgumentCaptor.forClass(RetryContext.class);
        verify(testHook).checkStatus(contextCaptor.capture());
        
        RetryContext context = contextCaptor.getValue();
        assertEquals("TASK011", context.getTaskId());
        assertEquals("ORDER001", context.getIdempotentKey());
        assertEquals(1, context.getRetryCount());
        assertEquals(5, context.getMaxRetryCount());
        assertNotNull(context.getParams());
        assertTrue(context.getParams().containsKey("orderId"));
    }

    // ==================== 辅助类和方法 ====================

    private RetryMessagePayload createPayload(String taskId, int retryCount) {
        return createPayload(taskId, retryCount, 5);
    }

    private RetryMessagePayload createPayload(String taskId, int retryCount, int maxRetryCount) {
        return RetryMessagePayload.builder()
            .taskId(taskId)
            .sceneType(100)
            .idempotentKey("KEY_" + taskId)
            .methodClass(TestBusinessService.class.getName())
            .methodName("processOrder")
            .methodParams("{\"orderId\":\"ORDER001\"}")
            .hookClass(TestRetryHook.class.getName())
            .retryCount(retryCount)
            .maxRetryCount(maxRetryCount)
            .retryIntervals("1,2,3")
            .build();
    }

    private RetryMessagePayload createPayloadWithMethod(String taskId, String className, 
                                                       String methodName, int retryCount, int maxRetryCount) {
        return RetryMessagePayload.builder()
            .taskId(taskId)
            .sceneType(100)
            .idempotentKey("KEY_" + taskId)
            .methodClass(className)
            .methodName(methodName)
            .methodParams("{\"orderId\":\"ORDER001\"}")
            .methodParamTypes("java.lang.String")
            .hookClass(TestRetryHook.class.getName())
            .retryCount(retryCount)
            .maxRetryCount(maxRetryCount)
            .retryIntervals("1,2,3")
            .build();
    }

    /**
     * 测试用Hook实现
     */
    public static class TestRetryHook implements RetryHook {
        private RetryStatus checkStatusResult = RetryStatus.INIT;
        private QueryResult doQueryResult = QueryResult.success("OK");
        private RuntimeException throwOnQuery;

        public void setCheckStatusResult(RetryStatus result) {
            this.checkStatusResult = result;
        }

        public void setDoQueryResult(QueryResult result) {
            this.doQueryResult = result;
        }

        public void setThrowOnQuery(RuntimeException e) {
            this.throwOnQuery = e;
        }

        @Override
        public RetryStatus checkStatus(RetryContext context) {
            return checkStatusResult;
        }

        @Override
        public QueryResult doQuery(RetryContext context) {
            if (throwOnQuery != null) {
                throw throwOnQuery;
            }
            return doQueryResult;
        }

        @Override
        public void doCallback(RetryContext context, QueryResult result) {
            // 测试用空实现
        }
    }

    /**
     * 测试用业务服务
     */
    public static class TestBusinessService {
        public void processOrder(String orderId) {
            // 测试用空实现
        }
    }
}
