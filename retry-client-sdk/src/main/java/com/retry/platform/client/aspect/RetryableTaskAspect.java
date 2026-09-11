package com.retry.platform.client.aspect;

import com.retry.platform.client.annotation.RetryableTask;
import com.retry.platform.client.api.RetryClient;
import com.retry.platform.client.dto.RetryTaskRequest;
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
@Component
public class RetryableTaskAspect {

    public static final ThreadLocal<Boolean> IN_RETRY_CONTEXT = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Autowired
    private RetryClient retryClient;

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
            
            // 预提交模式也需要防进程瞬间崩溃，先往本地 MQ 丢一条延时消息
            // 延时可以设定得长一些（例如 5 分钟），即使真的崩溃了也可以在此时间后触发本地 doQuery 重试
            if (taskId != null) {
                // 读取首次重试延时（从 1 分钟开始，或者是 30 秒）
                long initialDelayMs = 60 * 1000L; 
                retryMessageProducer.sendDelayMessage(taskId, initialDelayMs, retryableTask.sceneType());
            }
        } catch (Exception e) {
            log.error("[PRE_SUBMIT] Failed to pre-register retry task. sceneType={}, idempotentKey={}, error={}",
                    retryableTask.sceneType(), request.getIdempotentKey(), e.getMessage());
        }

        // Step 2: 执行原方法
        try {
            Object result = pjp.proceed();

            // Step 3: 方法执行成功，通知平台标记 SUCCESS
            if (taskId != null) {
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
            }
            return result;

        } catch (Throwable e) {
            log.warn("[PRE_SUBMIT] Method execution failed. taskId={} will remain INIT for platform retry. Error: {}",
                    taskId, e.getMessage());

            if (retryableTask.throwException()) {
                throw e;
            }
            return getDefaultReturnValue((MethodSignature) pjp.getSignature());
        }
    }

    // ==================== POST_FAIL 模式 ====================

    @Autowired
    private com.retry.platform.client.mq.RetryMessageProducer retryMessageProducer;

    /**
     * 失败后提交模式：执行方法 → 失败后提交重试任务
     */
    private Object handlePostFailMode(ProceedingJoinPoint pjp, RetryableTask retryableTask) throws Throwable {
        try {
            return pjp.proceed();
        } catch (Throwable e) {
            log.warn("[POST_FAIL] Method execution failed, submitting retry task. Method: {}, Error: {}",
                    pjp.getSignature().toLongString(), e.getMessage());

            RetryTaskRequest request = buildRetryTaskRequest(pjp, retryableTask, "POST_FAIL");

            try {
                String taskId = retryClient.submit(request);
                log.info("[POST_FAIL] Retry task submitted. taskId={}, sceneType={}, idempotentKey={}",
                        taskId, retryableTask.sceneType(), request.getIdempotentKey());
                
                if (taskId != null) {
                    // 方法失败后，立刻开始第一轮投递，延时默认为 1 分钟（后续由场景策略精确推进）
                    long initialDelayMs = 60 * 1000L; 
                    retryMessageProducer.sendDelayMessage(taskId, initialDelayMs, retryableTask.sceneType());
                }
            } catch (Exception ex) {
                log.error("[POST_FAIL] Failed to submit retry task", ex);
                log.warn("[POST_FAIL] Retry submission failed. Rethrowing original business exception.");
                throw e;
            }

            if (retryableTask.throwException()) {
                throw e;
            }
            return getDefaultReturnValue((MethodSignature) pjp.getSignature());
        }
    }

    // ==================== 公共工具方法 ====================

    /**
     * 构建重试任务请求
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

        // 提取参数类型列表，供 LocalRetryExecutor 精确定位重载方法（修复: 原来仅按参数数量匹配导致重载歧义）
        Class<?>[] paramTypes = signature.getParameterTypes();
        if (paramTypes != null && paramTypes.length > 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < paramTypes.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(paramTypes[i].getName());
            }
            request.setMethodParamTypes(sb.toString());
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
