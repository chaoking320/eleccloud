package com.retry.platform.client.aspect;

import com.retry.platform.client.annotation.RetryableTask;
import com.retry.platform.client.api.RetryClient;
import com.retry.platform.client.dto.RetryTaskRequest;
import com.retry.platform.client.hook.RetryHook;
import com.retry.platform.client.strategy.BackoffType;
import com.retry.platform.client.util.JsonUtil;
import com.retry.platform.client.util.ParameterExtractor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 可重试任务切面
 * 拦截 @RetryableTask 注解的方法，支持两种模式：
 *
 * <ul>
 *   <li><b>POST_FAIL 模式（默认）</b>：方法执行失败后，自动向重试平台提交任务</li>
 *   <li><b>PRE_SUBMIT 模式</b>：方法执行前先注册 INIT 任务，执行成功后标记 SUCCESS；
 *       若中途崩溃，平台将在下次调度时主动触发重试</li>
 * </ul>
 */
@Slf4j
@Aspect
public class RetryableTaskAspect {

    public static final ThreadLocal<Boolean> IN_RETRY_CONTEXT = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Autowired
    private RetryClient retryClient;

    @Autowired(required = false)
    private com.retry.platform.client.mq.RetryMessageProducer retryMessageProducer;

    @Around("@annotation(retryableTask)")
    public Object around(ProceedingJoinPoint pjp, RetryableTask retryableTask) throws Throwable {
        if (Boolean.TRUE.equals(IN_RETRY_CONTEXT.get())) {
            // 正在处于 LocalRetryExecutor 本地重试反射调用中，直接放行，不进行递归 AOP 拦截与重复提单
            return pjp.proceed();
        }
        if (retryableTask.preSubmit()) {
            return handlePreSubmitMode(pjp, retryableTask);
        } else {
            return handlePostFailMode(pjp, retryableTask);
        }
    }

    // ==================== PRE_SUBMIT 模式 ====================

    /**
     * 预提交模式：先注册任务 → 执行方法 → 成功后标记 SUCCESS
     */
    private Object handlePreSubmitMode(ProceedingJoinPoint pjp, RetryableTask retryableTask) throws Throwable {
        RetryTaskRequest request = buildRetryTaskRequest(pjp, retryableTask, "PRE_SUBMIT");
        String taskId = null;

        // Step 1: 方法执行前，向平台注册 INIT 状态的任务（事前保护）
        try {
            taskId = retryClient.submit(request);
            log.info("[PRE_SUBMIT] Pre-registered retry task before method execution. taskId={}, sceneType={}, idempotentKey={}",
                    taskId, retryableTask.sceneType(), request.getIdempotentKey());

            // 预提交模式：首次延时从注解/request 配置的间隔第一个值读取，而非硬编码 60s
            if (taskId != null && retryMessageProducer != null) {
                long initialDelayMs = resolveInitialDelayMs(request);
                retryMessageProducer.sendDelayMessage(taskId, initialDelayMs, retryableTask.sceneType());
            }
        } catch (Exception e) {
            log.error("[PRE_SUBMIT] Failed to pre-register retry task. sceneType={}, idempotentKey={}, error={}",
                    retryableTask.sceneType(), request.getIdempotentKey(), e.getMessage());
        }

        // Step 2: 执行原方法
        Throwable lastThrowable = null;
        boolean executedSuccessfully = false;
        Object result = null;
        try {
            result = pjp.proceed();
            executedSuccessfully = true;
        } catch (Throwable e) {
            lastThrowable = e;
        }

        // 两级重试第 1 级：本地快重试
        if (!executedSuccessfully) {
            int localTimes = retryableTask.localRetryTimes();
            long localInterval = retryableTask.localIntervalMs();
            if (localTimes > 0) {
                log.info("[PRE_SUBMIT][TwoTierRetry] Starting local fast retry (up to {} times, interval {}ms)...",
                        localTimes, localInterval);
                for (int attempt = 1; attempt <= localTimes; attempt++) {
                    if (localInterval > 0) {
                        try {
                            Thread.sleep(localInterval);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            log.warn("[PRE_SUBMIT][TwoTierRetry] Local retry interrupted at attempt {}/{}", attempt, localTimes);
                            break;
                        }
                    }
                    try {
                        result = pjp.proceed();
                        executedSuccessfully = true;
                        log.info("[PRE_SUBMIT][TwoTierRetry] Local fast retry succeeded at attempt {}/{}", attempt, localTimes);
                        break;
                    } catch (Throwable retryEx) {
                        lastThrowable = retryEx;
                        log.warn("[PRE_SUBMIT][TwoTierRetry] Local fast retry attempt {}/{} failed: {}",
                                attempt, localTimes, retryEx.getMessage());
                    }
                }
            }
        }

        if (executedSuccessfully) {
            // Step 3: 方法执行成功
            if (taskId != null) {
                if (retryableTask.hookClass() == RetryHook.class) {
                    // 无自定义 Hook：方法执行成功即代表业务成功，标记 SUCCESS
                    try {
                        boolean marked = retryClient.markSuccess(taskId);
                        if (marked) {
                            log.info("[PRE_SUBMIT] Method succeeded, marked task as SUCCESS. taskId={}", taskId);
                        } else {
                            log.warn("[PRE_SUBMIT] Method succeeded but failed to mark task SUCCESS. taskId={}", taskId);
                        }
                    } catch (Exception e) {
                        log.error("[PRE_SUBMIT] Exception marking task SUCCESS. taskId={}, error={}", taskId, e.getMessage());
                    }
                } else {
                    // 配置了 Hook：方法执行成功仅代表异步动作已发起，任务保留供 Hook 异步反查闭环
                    log.info("[PRE_SUBMIT] Method executed. Task has Hook [{}], retained for async lifecycle verification. taskId={}",
                            retryableTask.hookClass().getSimpleName(), taskId);
                }
            }
            return result;

        } else {
            log.warn("[PRE_SUBMIT] Method execution failed after local retries. taskId={} will remain INIT for platform retry. Error: {}",
                    taskId, lastThrowable != null ? lastThrowable.getMessage() : "unknown");

            if (retryableTask.throwException()) {
                throw lastThrowable;
            }
            return getDefaultReturnValue((MethodSignature) pjp.getSignature());
        }
    }

    // ==================== POST_FAIL 模式 ====================

    /**
     * 失败后提交模式：执行方法 → 失败后优先本地快重试 → 用尽后升级提交分布式重试任务
     */
    private Object handlePostFailMode(ProceedingJoinPoint pjp, RetryableTask retryableTask) throws Throwable {
        Throwable lastThrowable = null;
        try {
            return pjp.proceed();
        } catch (Throwable e) {
            lastThrowable = e;
            log.warn("[POST_FAIL] Initial method execution failed. Method: {}, Error: {}",
                    pjp.getSignature().toLongString(), e.getMessage());
        }

        // 两级重试第 1 级：本地轻量级快速重试（针对毫秒级瞬时微抖动，零 DB/MQ 开销）
        int localTimes = retryableTask.localRetryTimes();
        long localInterval = retryableTask.localIntervalMs();
        if (localTimes > 0) {
            log.info("[TwoTierRetry] Starting local fast retry (up to {} times, interval {}ms) for method: {}",
                    localTimes, localInterval, pjp.getSignature().toShortString());
            for (int attempt = 1; attempt <= localTimes; attempt++) {
                if (localInterval > 0) {
                    try {
                        Thread.sleep(localInterval);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.warn("[TwoTierRetry] Local fast retry sleep interrupted at attempt {}/{}", attempt, localTimes);
                        break;
                    }
                }
                try {
                    Object result = pjp.proceed();
                    log.info("[TwoTierRetry] Local fast retry succeeded at attempt {}/{}. Avoided distributed submission! Method: {}",
                            attempt, localTimes, pjp.getSignature().toShortString());
                    return result;
                } catch (Throwable retryEx) {
                    lastThrowable = retryEx;
                    log.warn("[TwoTierRetry] Local fast retry attempt {}/{} failed: {}",
                            attempt, localTimes, retryEx.getMessage());
                }
            }
            log.warn("[TwoTierRetry] Local fast retries exhausted ({}/{}). Escalating to distributed retry platform...",
                    localTimes, localTimes);
        }

        // 两级重试第 2 级：升级为分布式持久化延时重试
        RetryTaskRequest request = buildRetryTaskRequest(pjp, retryableTask, "POST_FAIL");

        try {
            String taskId = retryClient.submit(request);
            log.info("[POST_FAIL] Escalated to distributed retry. taskId={}, sceneType={}, idempotentKey={}",
                    taskId, retryableTask.sceneType(), request.getIdempotentKey());

            if (taskId != null && retryMessageProducer != null) {
                // 首次延时从注解/request 配置的间隔第一个值读取，而非硬编码 60s
                long initialDelayMs = resolveInitialDelayMs(request);
                retryMessageProducer.sendDelayMessage(taskId, initialDelayMs, retryableTask.sceneType());
            }
        } catch (Exception ex) {
            log.error("[POST_FAIL] Failed to submit retry task", ex);
            log.warn("[POST_FAIL] Retry submission failed. Rethrowing original business exception.");
            throw lastThrowable;
        }

        if (retryableTask.throwException()) {
            throw lastThrowable;
        }
        return getDefaultReturnValue((MethodSignature) pjp.getSignature());
    }

    // ==================== 公共工具方法 ====================

    /**
     * 从 request 的 retryIntervals 中解析首次延时（分钟，取第一个值转毫秒）
     * 若未配置则回退 SDK 默认值 1 分钟（60000ms）
     */
    private long resolveInitialDelayMs(RetryTaskRequest request) {
        String intervals = request.getRetryIntervals();
        if (intervals != null && !intervals.trim().isEmpty()) {
            String[] parts = intervals.split(",");
            try {
                int firstMinutes = Integer.parseInt(parts[0].trim());
                if (firstMinutes > 0) {
                    return firstMinutes * 60_000L;
                }
                // 0 分钟间隔（Demo 用）：至少等 200ms，防止 Redis ZSET 立即触发风暴
                return 200L;
            } catch (NumberFormatException ignore) {
                // 解析失败降级
            }
        }
        return 60_000L; // 默认 1 分钟
    }

    /**
     * 构建重试任务请求（含注解策略字段）
     */
    private RetryTaskRequest buildRetryTaskRequest(ProceedingJoinPoint pjp,
                                                    RetryableTask retryableTask,
                                                    String submitMode) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Object[] args = pjp.getArgs();

        RetryTaskRequest request = new RetryTaskRequest();
        request.setSceneType(retryableTask.sceneType());
        request.setIdempotentKey(
                ParameterExtractor.extractIdempotentKey(signature, args, retryableTask.idempotentKey()));
        request.setMethodClass(signature.getDeclaringType().getName());
        request.setMethodName(signature.getName());

        Map<String, Object> paramMap = ParameterExtractor.extractParameters(signature, args);
        request.setMethodParams(JsonUtil.toJson(paramMap));
        request.setAsync(retryableTask.async());
        request.setSubmitMode(submitMode);

        // 提取参数类型列表，供 LocalRetryExecutor 精确定位重载方法
        Class<?>[] paramTypes = signature.getParameterTypes();
        if (paramTypes != null && paramTypes.length > 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < paramTypes.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(paramTypes[i].getName());
            }
            request.setMethodParamTypes(sb.toString());
        }

        // ===== 注解策略字段写入 Request（三级优先级第一层：注解直接声明）=====

        // maxRetryCount：注解 > 0 时取注解值，否则留 null 给 Standalone 走 YAML/默认值
        if (retryableTask.maxRetryCount() > 0) {
            request.setMaxRetryCount(retryableTask.maxRetryCount());
        }

        // retryIntervals：注解非空时取注解值
        if (!retryableTask.retryIntervals().isEmpty()) {
            request.setRetryIntervals(retryableTask.retryIntervals());
        }

        // backoffStrategy：仅当注解非 CUSTOM 时显式设置（CUSTOM 是注解默认，和 SDK 默认相同，留 null 由下层决定）
        if (retryableTask.backoffStrategy() != BackoffType.CUSTOM) {
            request.setBackoffStrategy(retryableTask.backoffStrategy().name());
        }

        // backoffBase：注解 > 0 时取注解值
        if (retryableTask.backoffBase() > 0) {
            request.setBackoffBase(retryableTask.backoffBase());
        }

        // hookClass：注解不是 RetryHook.class 哨兵时，转为全限定名写入
        if (retryableTask.hookClass() != RetryHook.class) {
            request.setHookClass(retryableTask.hookClass().getName());
        }

        return request;
    }

    /**
     * 当方法返回基本类型时，AOP 通知不能返回 null（否则 Spring 抛出
     * "Null return value does not match primitive return type"）。
     * 根据返回类型返回对应的零值；对象类型 / void 返回 null。
     */
    private Object getDefaultReturnValue(MethodSignature signature) {
        Class<?> returnType = signature.getReturnType();
        if (returnType == void.class) {
            return null;
        }
        if (returnType == boolean.class) return false;
        if (returnType == char.class) return '\0';
        if (returnType == byte.class) return (byte) 0;
        if (returnType == short.class) return (short) 0;
        if (returnType == int.class) return 0;
        if (returnType == long.class) return 0L;
        if (returnType == float.class) return 0.0f;
        if (returnType == double.class) return 0.0d;
        return null;
    }
}
