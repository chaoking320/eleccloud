package com.retry.platform.client.callback;

import com.retry.platform.client.dto.Result;
import com.retry.platform.client.hook.QueryResult;
import com.retry.platform.client.hook.RetryContext;
import com.retry.platform.client.hook.RetryHook;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;

/**
 * SDK端回调控制器，供重试服务端远程调用
 */
@Slf4j
@RestController
@RequestMapping("/api/retry/callback")
public class RetryCallbackController {

    @Autowired
    private ApplicationContext applicationContext;
    
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 1. 远程驱动：状态检查 (checkStatus)
     */
    @PostMapping("/check-status")
    public Result<String> checkStatus(@RequestBody RetryContext context, @RequestParam String hookClass) {
        try {
            log.info("Received remote checkStatus request: taskId={}, hookClass={}", context.getTaskId(), hookClass);
            RetryHook hook = getHookBean(hookClass);
            String status = hook.checkStatus(context);
            return Result.success(status);
        } catch (Exception e) {
            log.error("Failed to execute checkStatus remotely", e);
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 2. 远程驱动：主动向第三方状态回查 (doQuery)
     */
    @PostMapping("/query")
    public Result<QueryResult> doQuery(@RequestBody RetryContext context, @RequestParam String hookClass) {
        try {
            log.info("Received remote doQuery request: taskId={}, hookClass={}", context.getTaskId(), hookClass);
            RetryHook hook = getHookBean(hookClass);
            QueryResult queryResult = hook.doQuery(context);
            return Result.success(queryResult);
        } catch (Exception e) {
            log.error("Failed to execute doQuery remotely", e);
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 3. 远程驱动：执行成功后的业务后续逻辑 (doCallback)
     */
    @PostMapping("/do-callback")
    public Result<Void> doCallback(@RequestBody CallbackRequest request) {
        try {
            log.info("Received remote doCallback request: taskId={}, hookClass={}", 
                    request.getContext().getTaskId(), request.getHookClass());
            RetryHook hook = getHookBean(request.getHookClass());
            hook.doCallback(request.getContext(), request.getQueryResult());
            return Result.success(null);
        } catch (Exception e) {
            log.error("Failed to execute doCallback remotely", e);
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 4. 远程驱动：重新发起初始动作 (等同于重试 INIT 任务)
     */
    @PostMapping("/execute-method")
    public Result<Object> executeMethod(@RequestBody RetryContext context) {
        try {
            log.info("Received remote executeMethod request: taskId={}, methodClass={}, methodName={}", 
                    context.getTaskId(), context.getMethodClass(), context.getMethodName());
            
            // 从本地 Spring 容器获取对应的 Service 实例！
            Class<?> clazz = Class.forName(context.getMethodClass());
            Object targetBean = applicationContext.getBean(clazz);
            
            // 解析参数JSON
            Map<String, Object> paramsMap = parseParamsJson(context.getMethodParamsJson());
            
            // 查找匹配的方法
            Method targetMethod = findMethod(clazz, context.getMethodName(), paramsMap);
            if (targetMethod == null) {
                return Result.fail("Method not found in local bean: " + context.getMethodName());
            }
            
            // 准备方法参数
            Object[] args = prepareMethodArgs(targetMethod, paramsMap, context.getIdempotentKey());
            
            // 调用方法
            targetMethod.setAccessible(true);
            Object result = targetMethod.invoke(targetBean, args);
            
            return Result.success(result);
        } catch (Exception e) {
            log.error("Failed to execute method remotely", e);
            return Result.fail(e.getMessage());
        }
    }

    private RetryHook getHookBean(String hookClass) throws Exception {
        Class<?> clazz = Class.forName(hookClass);
        return (RetryHook) applicationContext.getBean(clazz);
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseParamsJson(String paramsJson) throws Exception {
        if (paramsJson == null || paramsJson.trim().isEmpty()) {
            return new HashMap<>();
        }
        return objectMapper.readValue(paramsJson, Map.class);
    }
    
    private Method findMethod(Class<?> clazz, String methodName, Map<String, Object> paramsMap) {
        Method[] methods = clazz.getDeclaredMethods();
        for (Method method : methods) {
            if (method.getName().equals(methodName)) {
                if (method.getParameterCount() == paramsMap.size()) {
                    return method;
                }
            }
        }
        return null;
    }
    
    private Object[] prepareMethodArgs(Method method, Map<String, Object> paramsMap, String idempotentKey) throws Exception {
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];
        
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            String paramName = parameter.getName();
            Class<?> paramType = parameter.getType();
            
            Object paramValue = paramsMap.get(paramName);
            
            // 如果参数值是null，且有幂等键，尝试匹配
            if (paramValue == null && idempotentKey != null) {
                if (paramName.toLowerCase().contains("id") || paramName.toLowerCase().contains("key")) {
                    paramValue = idempotentKey;
                }
            }
            
            args[i] = convertType(paramValue, paramType);
        }
        
        return args;
    }
    
    private Object convertType(Object value, Class<?> targetType) throws Exception {
        if (value == null) {
            return null;
        }
        if (targetType.isInstance(value)) {
            return value;
        }
        if (targetType == String.class) {
            return value.toString();
        } else if (targetType == Integer.class || targetType == int.class) {
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            return Integer.parseInt(value.toString());
        } else if (targetType == Long.class || targetType == long.class) {
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            return Long.parseLong(value.toString());
        } else if (targetType == Double.class || targetType == double.class) {
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
            return Double.parseDouble(value.toString());
        } else if (targetType == Boolean.class || targetType == boolean.class) {
            if (value instanceof Boolean) {
                return value;
            }
            return Boolean.parseBoolean(value.toString());
        } else {
            String json = objectMapper.writeValueAsString(value);
            return objectMapper.readValue(json, targetType);
        }
    }

    /**
     * 回调请求参数体
     */
    @Data
    public static class CallbackRequest {
        private RetryContext context;
        private QueryResult queryResult;
        private String hookClass;
    }
}
