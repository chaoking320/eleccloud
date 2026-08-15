package com.retry.platform.client.executor;

import com.retry.platform.client.api.RetryClient;
import com.retry.platform.client.dto.RetryTaskDTO;
import com.retry.platform.client.hook.QueryResult;
import com.retry.platform.client.hook.RetryContext;
import com.retry.platform.client.hook.RetryHook;
import com.retry.platform.client.mq.RetryMessageProducer;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;

/**
 * SDK 本地重试与回查状态机执行引擎
 * 代替旧平台方案中的 Server HTTP 回调调度，完全在业务进程本地线程中安全解析并驱动重试。
 */
@Slf4j
@Component
public class LocalRetryExecutor {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private RetryClient retryClient;

    @Autowired
    private RetryMessageProducer retryMessageProducer;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 核心调度消费处理入口
     *
     * @param taskId 待处理的任务ID
     */
    public void execute(String taskId) {
        log.info("[LocalRetryExecutor] Processing retry task: taskId={}", taskId);
        try {
            // 1. 从 server 获取任务最新状态（Server 此时只作为任务数据存储库）
            RetryTaskDTO task = retryClient.queryTask(taskId);
            if (task == null) {
                log.warn("[LocalRetryExecutor] Task not found or deleted on server. taskId={}", taskId);
                return;
            }

            // 防御拦截：如果任务已经是成功或最终失败状态，直接 Ack 退出
            if ("SUCCESS".equals(task.getTaskStatus()) || "FAILED".equals(task.getTaskStatus())) {
                log.info("[LocalRetryExecutor] Task is already in terminal status: taskId={}, status={}", taskId, task.getTaskStatus());
                return;
            }

            // 2. 状态互斥锁：使用乐观锁 CAS 抢占任务执行权
            // 这里使用 Server 提供的 markExecuting 接口或者本地数据库来实现抢占。
            // 为了最简化，我们通过向 Server 查询和更新其 EXECUTING 状态来实现分布式互斥。
            if (!markExecutingSafely(taskId)) {
                log.info("[LocalRetryExecutor] Failed to lock task. taskId={} may be executing by other node.", taskId);
                return;
            }

            // 3. 构建重试上下文
            RetryContext context = buildContext(task);

            // 4. 加载本地 hook 实现
            String hookClassName = task.getHookClass();
            RetryHook hook = null;
            if (hookClassName != null && !hookClassName.trim().isEmpty()) {
                hook = getHookBean(hookClassName);
            }

            if (hook == null) {
                log.error("[LocalRetryExecutor] Hook class not found: taskId={}, hookClass={}", taskId, hookClassName);
                markFailed(taskId, "Hook class not found: " + hookClassName);
                return;
            }

            // 5. 核心状态机驱动
            String status = hook.checkStatus(context);
            log.info("[LocalRetryExecutor] checkStatus returned: taskId={}, status={}", taskId, status);

            switch (status) {
                case "SUCCESS":
                    handleSuccess(taskId, context, hook);
                    break;
                case "WAIT":
                    handleWait(taskId, context, hook, task);
                    break;
                case "INIT":
                default:
                    handleInit(taskId, context, hook, task);
                    break;
            }

        } catch (Exception e) {
            log.error("[LocalRetryExecutor] Exception executing task: taskId={}", taskId, e);
            // 异常时退回待重试状态
            rollbackToPending(taskId, e.getMessage());
        }
    }

    private void handleSuccess(String taskId, RetryContext context, RetryHook hook) {
        log.info("[LocalRetryExecutor] Task already SUCCESS. taskId={}", taskId);
        hook.doCallback(context, QueryResult.success("Already confirmed SUCCESS by checkStatus"));
        retryClient.markSuccess(taskId);
    }

    private void handleWait(String taskId, RetryContext context, RetryHook hook, RetryTaskDTO task) {
        log.info("[LocalRetryExecutor] Task in WAIT state. Triggering doQuery. taskId={}", taskId);
        try {
            QueryResult queryResult = hook.doQuery(context);
            if (queryResult.isSuccess()) {
                log.info("[LocalRetryExecutor] Query confirmed SUCCESS. Triggering doCallback. taskId={}", taskId);
                hook.doCallback(context, queryResult);
                retryClient.markSuccess(taskId);
            } else {
                log.info("[LocalRetryExecutor] Query returned failure/pending. Rescheduling. taskId={}", taskId);
                scheduleNext(task);
            }
        } catch (Exception e) {
            log.error("[LocalRetryExecutor] Query failed. Rescheduling. taskId={}", taskId, e);
            scheduleNext(task);
        }
    }

    private void handleInit(String taskId, RetryContext context, RetryHook hook, RetryTaskDTO task) {
        log.info("[LocalRetryExecutor] Task in INIT state. Invoking local method. taskId={}", taskId);
        try {
            // 反射从本地 Spring 容器获取对应的 Service 实例执行方法
            Class<?> clazz = Class.forName(context.getMethodClass());
            Object targetBean = applicationContext.getBean(clazz);

            Map<String, Object> paramsMap = parseParamsJson(context.getMethodParamsJson());
            // 传入 methodParamTypes 以支持按类型签名精确定位重载方法
            Method targetMethod = findMethod(clazz, context.getMethodName(), paramsMap,
                    context.getMethodParamTypes());
            if (targetMethod == null) {
                throw new NoSuchMethodException("Method not found: " + context.getMethodName());
            }

            // 仅按参数名从 Map 还原入参，不再用启发式内容填充
            Object[] args = prepareMethodArgs(targetMethod, paramsMap);
            targetMethod.setAccessible(true);
            targetMethod.invoke(targetBean, args);

            log.info("[LocalRetryExecutor] Local method execution completed. Downgrading to WAIT for querying. taskId={}", taskId);
            // 本地方法执行完（此时并没有出错），我们将状态标记为 WAIT 状态进行轮询状态检查
            updateTaskStatus(taskId, "WAIT");
            scheduleNext(task);

        } catch (Exception e) {
            log.warn("[LocalRetryExecutor] Local method invocation failed: taskId={}, error={}", taskId, e.getMessage());
            scheduleNext(task);
        }
    }

    private void scheduleNext(RetryTaskDTO task) {
        String taskId = task.getTaskId();
        int newRetryCount = task.getRetryCount() + 1;

        // 次数上限检查
        if (newRetryCount > task.getMaxRetryCount()) {
            log.warn("[LocalRetryExecutor] Exceeded max retry count limit. Marking task FAILED. taskId={}", taskId);
            markFailed(taskId, "Exceeded max retry count limit");
            return;
        }

        // 计算下次延迟时间并投递 MQ
        long delayMs = calculateDelayMs(task, newRetryCount);
        
        // 更新 Server 上的重试信息 (增加重试次数并重新标记为 INIT 供下次重试抢占)
        updateRetryCountAndStatus(taskId, newRetryCount, "INIT");

        // 投递延时消息
        retryMessageProducer.sendDelayMessage(taskId, delayMs, task.getSceneType());
    }

    private long calculateDelayMs(RetryTaskDTO task, int retryCount) {
        // 自定义或回退支持
        String strategy = task.getBackoffStrategy() != null ? task.getBackoffStrategy() : "CUSTOM";
        int baseMins = task.getBackoffBase() != null ? task.getBackoffBase() : 1;
        
        if ("FIXED".equalsIgnoreCase(strategy)) {
            return baseMins * 60L * 1000L;
        } else if ("LINEAR".equalsIgnoreCase(strategy)) {
            return (long) retryCount * baseMins * 60L * 1000L;
        } else if ("EXPONENTIAL".equalsIgnoreCase(strategy)) {
            int exp = Math.min(retryCount - 1, 30);
            return baseMins * (1L << exp) * 60L * 1000L;
        } else {
            // CUSTOM 自定义列表间隔解析，默认 1 分钟
            String intervals = task.getRetryIntervals();
            if (intervals == null || intervals.trim().isEmpty()) {
                return 60L * 1000L;
            }
            String[] split = intervals.split(",");
            int idx = Math.min(retryCount - 1, split.length - 1);
            try {
                return Integer.parseInt(split[idx].trim()) * 60L * 1000L;
            } catch (Exception e) {
                return 60L * 1000L;
            }
        }
    }

    // ==================== REST & DB API 联动封装 ====================

    private boolean markExecutingSafely(String taskId) {
        try {
            // 我们通过重试平台的 REST 接口通知它将任务状态原子修改为 EXECUTING (支持 CAS)
            return retryClient.markExecuting(taskId);
        } catch (Exception e) {
            return false;
        }
    }

    private void updateTaskStatus(String taskId, String status) {
        try {
            retryClient.updateStatus(taskId, status);
        } catch (Exception e) {
            log.error("Failed to update status: {}", taskId, e);
        }
    }

    private void updateRetryCountAndStatus(String taskId, int count, String status) {
        try {
            retryClient.updateRetryCountAndStatus(taskId, count, status);
        } catch (Exception e) {
            log.error("Failed to update retry info: {}", taskId, e);
        }
    }

    private void rollbackToPending(String taskId, String errorMsg) {
        try {
            retryClient.rollbackToPending(taskId, errorMsg);
        } catch (Exception e) {
            log.error("Failed to rollback: {}", taskId, e);
        }
    }

    private void markFailed(String taskId, String reason) {
        try {
            retryClient.markFailed(taskId, reason);
        } catch (Exception e) {
            log.error("Failed to mark failed: {}", taskId, e);
        }
    }

    // ==================== 反射和类型转换工具方法 ====================

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

    /**
     * 定位方法。
     * <p>
     * 修复：原实现仅按参数数量匹配，无法处理重载方法（同名不同参数类型）。
     * 修复后：优先按参数类型签名精确匹配；仅当 methodParamTypes 缺失时才降级按参数数量匹配。
     *
     * @param clazz          目标类
     * @param methodName     方法名
     * @param paramsMap      参数名⇒参数值映射（用于降级匹配时按数量匹配）
     * @param methodParamTypes 逻号分隔的全限定参数类型，可为 null
     * @return 匹配的 Method，找不到返回 null
     */
    private Method findMethod(Class<?> clazz, String methodName,
                              Map<String, Object> paramsMap, String methodParamTypes) {
        // ① 优先：按参数类型签名精确匹配（解决重载歧义）
        if (methodParamTypes != null && !methodParamTypes.trim().isEmpty()) {
            try {
                String[] typeNames = methodParamTypes.split(",");
                Class<?>[] paramTypes = new Class<?>[typeNames.length];
                for (int i = 0; i < typeNames.length; i++) {
                    paramTypes[i] = resolveClass(typeNames[i].trim());
                }
                return clazz.getDeclaredMethod(methodName, paramTypes);
            } catch (NoSuchMethodException e) {
                log.warn("[findMethod] Precise match failed for {}#{} with types=[{}], falling back to count-match.",
                        clazz.getName(), methodName, methodParamTypes);
            } catch (ClassNotFoundException e) {
                log.warn("[findMethod] Failed to resolve param type class: {}", e.getMessage());
            }
        }

        // ② 降级：按参数数量匹配（兼容旧数据 / methodParamTypes 为空的情况）
        Method matched = null;
        int matchCount = 0;
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(methodName)
                    && method.getParameterCount() == paramsMap.size()) {
                matched = method;
                matchCount++;
            }
        }
        if (matchCount > 1) {
            log.warn("[findMethod] Ambiguous overload: found {} methods named '{}' with {} params in {}. "
                    + "Consider re-submitting to persist methodParamTypes for precise matching.",
                    matchCount, methodName, paramsMap.size(), clazz.getName());
        }
        return matched;
    }

    /**
     * 将基本类型名 / 全限定类名解析为 Class。
     */
    private Class<?> resolveClass(String typeName) throws ClassNotFoundException {
        switch (typeName) {
            case "boolean": return boolean.class;
            case "byte":    return byte.class;
            case "char":    return char.class;
            case "short":   return short.class;
            case "int":     return int.class;
            case "long":    return long.class;
            case "float":   return float.class;
            case "double":  return double.class;
            case "void":    return void.class;
            default:        return Class.forName(typeName);
        }
    }

    /**
     * 按参数名从 Map 中还原方法入参。
     * <p>
     * 修复：移除了原来的‘参数名含 id/key 就用幂等键填充’的启发式规则——
     * 该规则将任意含“id”/“key”字符串的参数（如 buildingId、apiKey）误填为幂等键。
     * 修复后：仅按参数名从 paramsMap 中取对应属性，找不到则置 null。
     */
    private Object[] prepareMethodArgs(Method method, Map<String, Object> paramsMap) throws Exception {
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            String paramName = parameters[i].getName();
            Class<?> paramType = parameters[i].getType();
            Object paramValue = paramsMap.get(paramName);
            if (paramValue == null && log.isDebugEnabled()) {
                log.debug("[prepareMethodArgs] No value found for param '{}' in paramsMap, will use null/zero.", paramName);
            }
            args[i] = convertType(paramValue, paramType);
        }
        return args;
    }

    private Object convertType(Object value, Class<?> targetType) throws Exception {
        if (value == null) return null;
        if (targetType.isInstance(value)) return value;
        if (targetType == String.class) return value.toString();
        if (targetType == Integer.class || targetType == int.class) {
            if (value instanceof Number) return ((Number) value).intValue();
            return Integer.parseInt(value.toString());
        }
        if (targetType == Long.class || targetType == long.class) {
            if (value instanceof Number) return ((Number) value).longValue();
            return Long.parseLong(value.toString());
        }
        if (targetType == Double.class || targetType == double.class) {
            if (value instanceof Number) return ((Number) value).doubleValue();
            return Double.parseDouble(value.toString());
        }
        if (targetType == Boolean.class || targetType == boolean.class) {
            if (value instanceof Boolean) return value;
            return Boolean.parseBoolean(value.toString());
        }
        String json = objectMapper.writeValueAsString(value);
        return objectMapper.readValue(json, targetType);
    }

    private RetryContext buildContext(RetryTaskDTO task) {
        Map<String, Object> paramsMap = null;
        try {
            if (task.getMethodParams() != null && !task.getMethodParams().trim().isEmpty()) {
                paramsMap = objectMapper.readValue(task.getMethodParams(), Map.class);
            }
        } catch (Exception e) {
            log.error("Failed to parse method params: taskId={}", task.getTaskId(), e);
        }
        return RetryContext.builder()
                .taskId(task.getTaskId())
                .sceneType(task.getSceneType())
                .idempotentKey(task.getIdempotentKey())
                .params(paramsMap)
                .retryCount(task.getRetryCount())
                .maxRetryCount(task.getMaxRetryCount())
                .methodClass(task.getMethodClass())
                .methodName(task.getMethodName())
                .methodParamsJson(task.getMethodParams())
                .methodParamTypes(task.getMethodParamTypes())
                .build();
    }
}
