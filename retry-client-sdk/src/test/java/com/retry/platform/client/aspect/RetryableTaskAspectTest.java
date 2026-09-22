package com.retry.platform.client.aspect;

import com.retry.platform.client.annotation.RetryableTask;
import com.retry.platform.client.api.RetryClient;
import com.retry.platform.client.dto.RetryTaskRequest;
import com.retry.platform.client.hook.QueryResult;
import com.retry.platform.client.hook.RetryContext;
import com.retry.platform.client.hook.RetryHook;
import com.retry.platform.client.hook.RetryStatus;
import com.retry.platform.client.mq.RetryMessageProducer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RetryableTaskAspect} 单元测试
 * 覆盖 POST_FAIL / PRE_SUBMIT 两种模式的核心契约：
 * - 成功路径不产生任务
 * - 失败路径提交任务并按 retryIntervals 首值计算首次延时
 * - PRE_SUBMIT 无 Hook 成功即 markSuccess；有 Hook 则保留任务交异步闭环
 * - 提交失败时原始业务异常不被吞掉
 */
class RetryableTaskAspectTest {

    private RetryableTaskAspect aspect;
    private RetryClient retryClient;
    private RetryMessageProducer producer;

    /** 被拦截的业务方法宿主（注解直接声明，测试通过反射取真实注解实例） */
    static class DemoService {
        @RetryableTask(sceneType = 1001, idempotentKey = "#orderId", retryIntervals = "2,5,10")
        public void postFail(String orderId) { }

        @RetryableTask(sceneType = 1002, idempotentKey = "#orderId", retryIntervals = "2,5,10", throwException = true)
        public void postFailThrow(String orderId) { }

        @RetryableTask(sceneType = 1003, idempotentKey = "#orderId", preSubmit = true, retryIntervals = "3,7")
        public void preSubmitNoHook(String orderId) { }

        @RetryableTask(sceneType = 1004, idempotentKey = "#orderId", preSubmit = true, hookClass = DemoHook.class)
        public void preSubmitWithHook(String orderId) { }
    }

    static class DemoHook implements RetryHook {
        @Override
        public RetryStatus checkStatus(RetryContext context) { return RetryStatus.INIT; }
        @Override
        public QueryResult doQuery(RetryContext context) { return QueryResult.success("ok"); }
        @Override
        public void doCallback(RetryContext context, QueryResult result) { }
    }

    @BeforeEach
    void setUp() {
        aspect = new RetryableTaskAspect();
        retryClient = mock(RetryClient.class);
        producer = mock(RetryMessageProducer.class);
        ReflectionTestUtils.setField(aspect, "retryClient", retryClient);
        ReflectionTestUtils.setField(aspect, "retryMessageProducer", producer);
    }

    @AfterEach
    void tearDown() {
        RetryableTaskAspect.IN_RETRY_CONTEXT.remove();
    }

    // ==================== 测试脚手架 ====================

    private RetryableTask annotationOf(String methodName) throws Exception {
        Method m = DemoService.class.getDeclaredMethod(methodName, String.class);
        return m.getAnnotation(RetryableTask.class);
    }

    private ProceedingJoinPoint pjpOf(String methodName, Object[] args, Throwable proceedThrows) throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature sig = mock(MethodSignature.class);
        Method target = DemoService.class.getDeclaredMethod(methodName, String.class);
        when(pjp.getSignature()).thenReturn(sig);
        when(pjp.getArgs()).thenReturn(args);
        when(sig.getName()).thenReturn(methodName);
        when(sig.getMethod()).thenReturn(target);
        when(sig.getParameterNames()).thenReturn(new String[]{"orderId"});
        when(sig.getParameterTypes()).thenReturn(new Class<?>[]{String.class});
        when(sig.getReturnType()).thenReturn(void.class);
        when(sig.getDeclaringType()).thenReturn((Class) DemoService.class);
        if (proceedThrows != null) {
            when(pjp.proceed()).thenThrow(proceedThrows);
        } else {
            when(pjp.proceed()).thenReturn(null);
        }
        return pjp;
    }

    // ==================== POST_FAIL 模式 ====================

    @Test
    void postFailSuccessDoesNotSubmitTask() throws Throwable {
        RetryableTask anno = annotationOf("postFail");
        ProceedingJoinPoint pjp = pjpOf("postFail", new Object[]{"ORD-1"}, null);

        assertNull(aspect.around(pjp, anno));

        verify(retryClient, never()).submit(any());
        verify(producer, never()).sendDelayMessage(anyString(), anyLong(), anyInt());
    }

    @Test
    void postFailFailureSubmitsTaskWithFirstIntervalDelay() throws Throwable {
        RetryableTask anno = annotationOf("postFail");
        ProceedingJoinPoint pjp = pjpOf("postFail", new Object[]{"ORD-2"},
                new IllegalStateException("downstream timeout"));
        when(retryClient.submit(any(RetryTaskRequest.class))).thenReturn("T-100");

        // throwException=false：异常被吞，返回 void 默认值 null
        assertNull(aspect.around(pjp, anno));

        ArgumentCaptor<RetryTaskRequest> captor = ArgumentCaptor.forClass(RetryTaskRequest.class);
        verify(retryClient).submit(captor.capture());
        RetryTaskRequest req = captor.getValue();
        assertEquals("POST_FAIL", req.getSubmitMode());
        assertEquals(1001, req.getSceneType());
        assertEquals("ORD-2", req.getIdempotentKey());
        assertEquals("2,5,10", req.getRetryIntervals());
        assertEquals("java.lang.String", req.getMethodParamTypes());

        // 首次延时 = retryIntervals 首值 2 分钟 = 120000ms
        verify(producer).sendDelayMessage(eq("T-100"), eq(120_000L), eq(1001));
    }

    @Test
    void postFailSubmitFailureRethrowsOriginalException() throws Throwable {
        RetryableTask anno = annotationOf("postFail");
        IllegalStateException original = new IllegalStateException("biz fail");
        ProceedingJoinPoint pjp = pjpOf("postFail", new Object[]{"ORD-3"}, original);
        when(retryClient.submit(any(RetryTaskRequest.class))).thenThrow(new RuntimeException("server down"));

        // 重试任务提交失败时，必须把原始业务异常抛回调用方，不允许静默丢任务
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> aspect.around(pjp, anno));
        assertEquals("biz fail", thrown.getMessage());
        verify(producer, never()).sendDelayMessage(anyString(), anyLong(), anyInt());
    }

    @Test
    void postFailThrowExceptionRethrowsAfterSubmitting() throws Throwable {
        RetryableTask anno = annotationOf("postFailThrow");
        RuntimeException original = new RuntimeException("boom");
        ProceedingJoinPoint pjp = pjpOf("postFailThrow", new Object[]{"ORD-4"}, original);
        when(retryClient.submit(any(RetryTaskRequest.class))).thenReturn("T-104");

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> aspect.around(pjp, anno));
        assertEquals("boom", thrown.getMessage());
        // 抛异常前任务已提交，重试链路不受影响
        verify(retryClient).submit(any(RetryTaskRequest.class));
        verify(producer).sendDelayMessage(eq("T-104"), anyLong(), eq(1002));
    }

    // ==================== PRE_SUBMIT 模式 ====================

    @Test
    void preSubmitRegistersBeforeExecutionAndMarksSuccessAfter() throws Throwable {
        RetryableTask anno = annotationOf("preSubmitNoHook");
        ProceedingJoinPoint pjp = pjpOf("preSubmitNoHook", new Object[]{"ORD-5"}, null);
        when(retryClient.submit(any(RetryTaskRequest.class))).thenReturn("T-105");

        aspect.around(pjp, anno);

        // 严格时序：先注册 → 再执行业务 → 后标记成功
        org.mockito.InOrder ordered = inOrder(retryClient, pjp);
        ordered.verify(retryClient).submit(any(RetryTaskRequest.class));
        ordered.verify(pjp).proceed();
        ordered.verify(retryClient).markSuccess("T-105");

        // 首次延时 = 首值 3 分钟
        verify(producer).sendDelayMessage(eq("T-105"), eq(180_000L), eq(1003));
    }

    @Test
    void preSubmitFailureKeepsTaskInitWithoutMarkSuccess() throws Throwable {
        RetryableTask anno = annotationOf("preSubmitNoHook");
        ProceedingJoinPoint pjp = pjpOf("preSubmitNoHook", new Object[]{"ORD-6"},
                new IllegalStateException("crash before done"));
        when(retryClient.submit(any(RetryTaskRequest.class))).thenReturn("T-106");

        // throwException=false：静默返回，任务保持 INIT 等平台重试
        assertNull(aspect.around(pjp, anno));
        verify(retryClient, never()).markSuccess(anyString());
    }

    @Test
    void preSubmitWithHookDoesNotMarkSuccessOnMethodReturn() throws Throwable {
        RetryableTask anno = annotationOf("preSubmitWithHook");
        ProceedingJoinPoint pjp = pjpOf("preSubmitWithHook", new Object[]{"ORD-7"}, null);
        when(retryClient.submit(any(RetryTaskRequest.class))).thenReturn("T-107");

        aspect.around(pjp, anno);

        // 配置了 Hook：方法返回只代表动作发起，成功与否由 Hook 异步反查闭环，不能提前 markSuccess
        verify(retryClient, never()).markSuccess(anyString());
        ArgumentCaptor<RetryTaskRequest> captor = ArgumentCaptor.forClass(RetryTaskRequest.class);
        verify(retryClient).submit(captor.capture());
        assertTrue(captor.getValue().getHookClass().endsWith("DemoHook"));
    }

    @Test
    void preSubmitRegistrationFailureDoesNotBlockBusinessMethod() throws Throwable {
        RetryableTask anno = annotationOf("preSubmitWithHook");
        ProceedingJoinPoint pjp = pjpOf("preSubmitWithHook", new Object[]{"ORD-8"}, null);
        when(retryClient.submit(any(RetryTaskRequest.class))).thenThrow(new RuntimeException("server unreachable"));

        // 预注册失败只降级打日志，业务方法本身必须正常执行
        assertNull(aspect.around(pjp, anno));
        verify(pjp).proceed();
        verify(retryClient, never()).markSuccess(anyString());
    }

    // ==================== 重试上下文防递归 ====================

    @Test
    void skipsInterceptionWhenAlreadyInRetryContext() throws Throwable {
        RetryableTaskAspect.IN_RETRY_CONTEXT.set(Boolean.TRUE);
        RetryableTask anno = annotationOf("postFail");
        ProceedingJoinPoint pjp = pjpOf("postFail", new Object[]{"ORD-9"}, null);

        aspect.around(pjp, anno);

        // LocalRetryExecutor 反射重跑时设置的 ThreadLocal 标志必须让切面直接放行，防止重复提单
        verify(retryClient, never()).submit(any());
        verify(pjp).proceed();
    }
}
