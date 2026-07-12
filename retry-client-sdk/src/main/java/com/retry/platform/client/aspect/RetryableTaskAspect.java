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

    @Autowired
    private RetryClient retryClient;

    @Around("@annotation(retryableTask)")
    public Object around(ProceedingJoinPoint pjp, RetryableTask retryableTask) throws Throwable {
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
        } catch (Exception e) {
            // 注册任务失败时，视配置决定是否阻断业务执行
            log.error("[PRE_SUBMIT] Failed to pre-register retry task. sceneType={}, idempotentKey={}, error={}",
                    retryableTask.sceneType(), request.getIdempotentKey(), e.getMessage());
            // 注册失败不阻断业务，继续执行（可根据业务需要改为抛出异常）
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
                    // 标记失败不影响业务结果，平台会在下次调度时通过 checkStatus 发现已成功
                    log.error("[PRE_SUBMIT] Exception marking task SUCCESS. taskId={}, error={}", taskId, e.getMessage());
                }
            }
            return result;

        } catch (Throwable e) {
            // 方法执行失败：任务保持 INIT 状态，平台将在定时调度时触发重试
            log.warn("[PRE_SUBMIT] Method execution failed. taskId={} will remain INIT for platform retry. Error: {}",
                    taskId, e.getMessage());

            if (retryableTask.throwException()) {
                throw e;
            }
            return null;
        }
    }

    // ==================== POST_FAIL 模式 ====================

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
            } catch (Exception ex) {
                log.error("[POST_FAIL] Failed to submit retry task", ex);
            }

            if (retryableTask.throwException()) {
                throw e;
            }
            return null;
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

        return request;
    }
}
