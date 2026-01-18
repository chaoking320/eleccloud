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
 * 拦截@RetryableTask注解的方法，在方法执行失败时自动创建重试任务
 */
@Slf4j
@Aspect
@Component
public class RetryableTaskAspect {
    
    @Autowired
    private RetryClient retryClient;
    
    @Around("@annotation(retryableTask)")
    public Object around(ProceedingJoinPoint pjp, RetryableTask retryableTask) throws Throwable {
        try {
            // 执行原方法
            return pjp.proceed();
        } catch (Throwable e) {
            log.warn("Method execution failed, submitting retry task. Method: {}, Error: {}", 
                    pjp.getSignature().toLongString(), e.getMessage());
            
            // 构建重试任务请求
            RetryTaskRequest request = buildRetryTaskRequest(pjp, retryableTask);
            
            // 提交重试任务
            try {
                String taskId = retryClient.submit(request);
                log.info("Retry task submitted successfully. TaskId: {}, SceneType: {}, IdempotentKey: {}", 
                        taskId, retryableTask.sceneType(), request.getIdempotentKey());
            } catch (Exception ex) {
                log.error("Failed to submit retry task", ex);
            }
            
            // 根据配置决定是否抛出异常
            if (retryableTask.throwException()) {
                throw e;
            }
            
            return null;
        }
    }
    
    /**
     * 构建重试任务请求
     */
    private RetryTaskRequest buildRetryTaskRequest(ProceedingJoinPoint pjp, RetryableTask retryableTask) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Object[] args = pjp.getArgs();
        
        RetryTaskRequest request = new RetryTaskRequest();
        
        // 设置场景类型
        request.setSceneType(retryableTask.sceneType());
        
        // 提取幂等键
        String idempotentKey = ParameterExtractor.extractIdempotentKey(
                signature, args, retryableTask.idempotentKey());
        request.setIdempotentKey(idempotentKey);
        
        // 设置方法信息
        request.setMethodClass(signature.getDeclaringType().getName());
        request.setMethodName(signature.getName());
        
        // 提取并序列化方法参数
        Map<String, Object> paramMap = ParameterExtractor.extractParameters(signature, args);
        String paramsJson = JsonUtil.toJson(paramMap);
        request.setMethodParams(paramsJson);
        
        // 设置异步标志
        request.setAsync(retryableTask.async());
        
        return request;
    }
}
