package com.retry.platform.client.executor;

import com.retry.platform.client.api.RetryClient;
import com.retry.platform.client.mq.RetryMessagePayload;
import com.retry.platform.client.mq.RetryMessageProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * {@link LocalRetryExecutor} 退避计算与重调度逻辑单元测试
 * 覆盖：
 * - 四种退避策略（CUSTOM/FIXED/LINEAR/EXPONENTIAL）的延时换算
 * - 间隔列表越界取尾值、非法值降级默认、最小 200ms 防风暴保护
 * - scheduleNext 达到上限标记 FAILED、未达上限重投胖消息
 */
class LocalRetryExecutorSchedulingTest {

    private LocalRetryExecutor executor;
    private RetryClient retryClient;
    private RetryMessageProducer producer;

    @BeforeEach
    void setUp() {
        executor = new LocalRetryExecutor();
        retryClient = mock(RetryClient.class);
        producer = mock(RetryMessageProducer.class);
        ApplicationContext ctx = mock(ApplicationContext.class);
        ReflectionTestUtils.setField(executor, "retryClient", retryClient);
        ReflectionTestUtils.setField(executor, "retryMessageProducer", producer);
        ReflectionTestUtils.setField(executor, "applicationContext", ctx);
    }

    private long calcDelay(RetryMessagePayload payload, int retryCount) {
        return (long) ReflectionTestUtils.invokeMethod(executor, "calculateDelayMsFromPayload", payload, retryCount);
    }

    private RetryMessagePayload.RetryMessagePayloadBuilder base() {
        return RetryMessagePayload.builder().taskId("T1").sceneType(1);
    }

    // ==================== 退避策略换算 ====================

    @Test
    void customStrategyPicksIntervalByIndex() {
        RetryMessagePayload p = base().backoffStrategy("CUSTOM").retryIntervals("1,3,5").build();
        assertEquals(60_000L, calcDelay(p, 1));   // 第1次 → 1分钟
        assertEquals(180_000L, calcDelay(p, 2));  // 第2次 → 3分钟
        assertEquals(300_000L, calcDelay(p, 3));  // 第3次 → 5分钟
        assertEquals(300_000L, calcDelay(p, 10)); // 越界取最后一个值
    }

    @Test
    void customStrategyFallsBackToOneMinuteWhenIntervalsMissingOrInvalid() {
        assertEquals(60_000L, calcDelay(base().backoffStrategy("CUSTOM").build(), 1));
        assertEquals(60_000L, calcDelay(base().backoffStrategy("CUSTOM").retryIntervals("abc,2").build(), 1));
    }

    @Test
    void fixedStrategyUsesBaseMinutes() {
        RetryMessagePayload p = base().backoffStrategy("FIXED").backoffBase(2).build();
        assertEquals(120_000L, calcDelay(p, 1));
        assertEquals(120_000L, calcDelay(p, 5));
    }

    @Test
    void linearStrategyScalesWithRetryCount() {
        RetryMessagePayload p = base().backoffStrategy("LINEAR").backoffBase(2).build();
        assertEquals(120_000L, calcDelay(p, 1));  // 1×2min
        assertEquals(480_000L, calcDelay(p, 4));  // 4×2min
    }

    @Test
    void exponentialStrategyDoublesAndCapsExponent() {
        RetryMessagePayload p = base().backoffStrategy("EXPONENTIAL").backoffBase(1).build();
        assertEquals(60_000L, calcDelay(p, 1));    // 2^0
        assertEquals(480_000L, calcDelay(p, 4));   // 2^3
        // 指数封顶 30，不溢出为负数
        long huge = calcDelay(p, 40);
        assertEquals(60_000L << 30, huge);
        org.junit.jupiter.api.Assertions.assertTrue(huge > 0);
    }

    @Test
    void unknownStrategyFallsBackToCustom() {
        RetryMessagePayload p = base().backoffStrategy("NOT_A_STRATEGY").retryIntervals("7").build();
        assertEquals(420_000L, calcDelay(p, 1));
    }

    @Test
    void zeroIntervalIsFlooredTo200msAntiStorm() {
        RetryMessagePayload p = base().backoffStrategy("CUSTOM").retryIntervals("0,0").build();
        assertEquals(200L, calcDelay(p, 1));
        RetryMessagePayload fixed0 = base().backoffStrategy("FIXED").backoffBase(0).build();
        // base=0 分钟 → 0ms → 触发 200ms 下限保护
        assertEquals(200L, calcDelay(fixed0, 3));
    }

    // ==================== scheduleNext 重调度 ====================

    @Test
    void scheduleNextMarksFailedWhenMaxRetryReached() {
        RetryMessagePayload p = base().retryCount(4).maxRetryCount(4)
                .backoffStrategy("CUSTOM").retryIntervals("1,2,3").build();

        ReflectionTestUtils.invokeMethod(executor, "scheduleNext", p, "INIT");

        verify(retryClient).markFailed(eq("T1"), anyString());
        verify(producer, never()).sendDelayMessageWithPayload(any(), anyLong());
    }

    @Test
    void scheduleNextReschedulesWithIncrementedCountWhenBudgetRemains() {
        RetryMessagePayload p = base().retryCount(1).maxRetryCount(4)
                .backoffStrategy("CUSTOM").retryIntervals("1,2,3").build();

        ReflectionTestUtils.invokeMethod(executor, "scheduleNext", p, "WAIT");

        // 持久层记录当前次数与目标状态
        verify(retryClient).updateRetryCountAndStatus("T1", 1, "WAIT");
        // 重投的胖消息 retryCount 已递增为 2，第2轮间隔 = 2 分钟
        org.mockito.ArgumentCaptor<RetryMessagePayload> captor =
                org.mockito.ArgumentCaptor.forClass(RetryMessagePayload.class);
        verify(producer).sendDelayMessageWithPayload(captor.capture(), eq(120_000L));
        assertEquals(2, captor.getValue().getRetryCount());
        assertEquals("T1", captor.getValue().getTaskId());
    }

    @Test
    void scheduleNextSkipsEnqueueWhenProducerAbsent() {
        ReflectionTestUtils.setField(executor, "retryMessageProducer", null);
        RetryMessagePayload p = base().retryCount(1).maxRetryCount(4)
                .backoffStrategy("FIXED").backoffBase(1).build();

        ReflectionTestUtils.invokeMethod(executor, "scheduleNext", p, null);

        // 无 producer 时仍更新状态，不抛异常（Standalone 降级路径）
        verify(retryClient).updateRetryCountAndStatus("T1", 1, "INIT");
        verify(retryClient, never()).markFailed(anyString(), anyString());
    }

    @Test
    void nonPositiveRetryCountNormalizedToOne() {
        // retryCount=0 或负数属于脏数据，必须归一为 1，否则指数策略位移数为负
        RetryMessagePayload p = base().retryCount(0).maxRetryCount(4)
                .backoffStrategy("EXPONENTIAL").backoffBase(1).build();
        assertEquals(60_000L, calcDelay(p, Math.max(1, p.getRetryCount())));

        ReflectionTestUtils.invokeMethod(executor, "scheduleNext", p, "INIT");
        org.mockito.ArgumentCaptor<RetryMessagePayload> captor =
                org.mockito.ArgumentCaptor.forClass(RetryMessagePayload.class);
        verify(producer).sendDelayMessageWithPayload(captor.capture(), anyLong());
        // 0 → 归一为1 → 下一轮 2
        assertEquals(2, captor.getValue().getRetryCount());
    }
}
