package com.retry.platform.client.executor;

import com.retry.platform.client.api.RetryClient;
import com.retry.platform.client.dto.RetryTaskDTO;
import com.retry.platform.client.hook.QueryResult;
import com.retry.platform.client.hook.RetryContext;
import com.retry.platform.client.hook.RetryHook;
import com.retry.platform.client.mq.RetryMessagePayload;
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
     * 核心调度消费处理入口（瘦消息路径：需要先 HTTP 查询任务详情）
     *
     * @param taskId 待处理的任务ID
     */
    public void execute(String taskId) {
        log.info("[LocalRetryExecutor] Processing slim message: taskId={}", taskId);
        try {
            // 1. 从 server 获取任务最新状态（瘦消息路径的唯一一次 HTTP 查询）
            RetryTaskDTO task = retryClient.queryTask(taskId);
            if (task == null) {
                log.warn("[LocalRetryExecutor] Task not found or deleted on server. taskId={}", taskId);
                return;
            }
            // 防御拦截：终态直接退出
            if ("SUCCESS".equals(task.getTaskStatus()) || "FAILED".equals(task.getTaskStatus())) {
                log.info("[LocalRetryExecutor] Task already terminal: taskId={}, status={}", taskId, task.getTaskStatus());
                return;
            }
            // 转换为 payload 后走统一执行逻辑
            executeInternal(buildPayloadFromDTO(task));
        } catch (Exception e) {
            log.error("[LocalRetryExecutor] Exception in slim message path: taskId={}", taskId, e);
            rollbackToPending(taskId, e.getMessage());
        }
    }

    /**
     * 胖消息执行入口（直接携带执行上下文，无需先 HTTP 查询任务详情）
     * HTTP 调用减少：queryTask 调用被省掉，只剩 CAS + 状态更新 = 2~3 次。
     *
     * @param payload 完整的任务执行上下文（从 MQ 消息中反序列化而来）
     */
    public void executeWithPayload(RetryMessagePayload payload) {
        log.info("[LocalRetryExecutor] Processing fat message: taskId={}, retryCount={}",
                payload.getTaskId(), payload.getRetryCount());
        try {
            executeInternal(payload);
        } catch (Exception e) {
            log.error("[LocalRetryExecutor] Exception in fat message path: taskId={}", payload.getTaskId(), e);
            rollbackToPending(payload.getTaskId(), e.getMessage());
        }
    }

    /**
     * 统一执行逻辑（胖消息和瘦消息共用）
     */
    private void executeInternal(RetryMessagePayload payload) {
        String taskId = payload.getTaskId();

        // CAS 抢占：此次 HTTP 调用无法省略，需要数据库行锁保证多节点互斥
        if (!markExecutingSafely(taskId)) {
            log.info("[LocalRetryExecutor] Failed to CAS lock task: taskId={}", taskId);
            return;
        }

        // 构建重试上下文
        RetryContext context = buildContextFromPayload(payload);

        // 加载 Hook
        String hookClassName = payload.getHookClass();
        RetryHook hook = null;
        if (hookClassName != null && !hookClassName.trim().isEmpty()) {
            try {
                hook = getHookBean(hookClassName);
            } catch (Exception e) {
                log.error("[LocalRetryExecutor] Failed to load hook: {}", hookClassName, e);
            }
        }

        if (hook == null) {
            // hook 未配置或找不到 → 降级：直接反射调用原始业务方法重试（无幂等保护）
            log.warn("[LocalRetryExecutor] Hook not found or not configured for taskId={}, hookClass={}. " +
                    "Falling back to direct method retry.", taskId, hookClassName);
            handleInit(taskId, context, new NoOpRetryHook(), payload);
            return;
        }

        // 核心状态机驱动
        String status = hook.checkStatus(context);
        log.info("[LocalRetryExecutor] checkStatus: taskId={}, status={}", taskId, status);

        switch (status) {
            case "SUCCESS":
                handleSuccess(taskId, context, hook);
                break;
            case "WAIT":
                handleWait(taskId, context, hook, payload);
                break;
            case "INIT":
            default:
                handleInit(taskId, context, hook, payload);
                break;
        }
    }


    private void handleSuccess(String taskId, RetryContext context, RetryHook hook) {
        log.info("[LocalRetryExecutor] Task already SUCCESS. taskId={}", taskId);
        hook.doCallback(context, QueryResult.success("Already confirmed SUCCESS by checkStatus"));
        retryClient.markSuccess(taskId);
    }

    private void handleWait(String taskId, RetryContext context, RetryHook hook, RetryMessagePayload payload) {
        log.info("[LocalRetryExecutor] Task in WAIT state. Triggering doQuery. taskId={}", taskId);
        long startTime = System.currentTimeMillis();
        try {
            QueryResult queryResult = hook.doQuery(context);
            long costMs = System.currentTimeMillis() - startTime;
            if (queryResult.isSuccess()) {
                log.info("[LocalRetryExecutor] Query confirmed SUCCESS. Triggering doCallback. taskId={}", taskId);
                hook.doCallback(context, queryResult);
                retryClient.markSuccess(taskId);
                // ★ M1 修复：记录 WAIT 查询成功历史
                safeRecordHistory(taskId, context.getRetryCount(), "SUCCESS", null, costMs);
            } else {
                log.info("[LocalRetryExecutor] Query returned failure/pending. Rescheduling. taskId={}", taskId);
                // ★ M1 修复：记录 WAIT 查询未完成历史
                safeRecordHistory(taskId, context.getRetryCount(), "PENDING", "doQuery returned not-success", costMs);
                scheduleNext(payload);
            }
        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - startTime;
            log.error("[LocalRetryExecutor] Query failed. Rescheduling. taskId={}", taskId, e);
            // ★ M1 修复：记录 WAIT 查询异常历史
            safeRecordHistory(taskId, context.getRetryCount(), "FAILED", e.getMessage(), costMs);
            scheduleNext(payload);
        }
    }

    private void handleInit(String taskId, RetryContext context, RetryHook hook, RetryMessagePayload payload) {
        log.info("[LocalRetryExecutor] Task in INIT state. Invoking local method. taskId={}", taskId);
        long startTime = System.currentTimeMillis();
        try {
            // 反射从本地 Spring 容器获取对应的 Service 实例执行方法
            // 必须用线程上下文 ClassLoader，否则 SDK jar 里的 ClassLoader 无法加载业务方的类
            ClassLoader contextCl = Thread.currentThread().getContextClassLoader();
            Class<?> clazz = Class.forName(context.getMethodClass(), true, contextCl);
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

            long costMs = System.currentTimeMillis() - startTime;
            log.info("[LocalRetryExecutor] Local method execution completed → WAIT. taskId={}", taskId);
            // ★ M1 修复：记录方法执行成功历史（进入 WAIT 等待状态确认）
            safeRecordHistory(taskId, context.getRetryCount(), "SUCCESS", null, costMs);
            // 执行完毕，切换为 WAIT 状态等待 Hook 确认
            updateTaskStatus(taskId, "WAIT");
            scheduleNext(payload);

        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - startTime;
            log.warn("[LocalRetryExecutor] Local method invocation failed: taskId={}, error={}", taskId, e.getMessage());
            // ★ M1 修复：记录方法执行失败历史
            safeRecordHistory(taskId, context.getRetryCount(), "FAILED",
                    e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), costMs);
            scheduleNext(payload);
        }
    }

    private void scheduleNext(RetryMessagePayload payload) {
        String taskId = payload.getTaskId();
        int newRetryCount = (payload.getRetryCount() != null ? payload.getRetryCount() : 0) + 1;

        // 次数上限检查
        if (payload.getMaxRetryCount() != null && newRetryCount > payload.getMaxRetryCount()) {
            log.warn("[LocalRetryExecutor] Exceeded max retry count. Marking FAILED. taskId={}", taskId);
            markFailed(taskId, "Exceeded max retry count limit");
            return;
        }

        // 计算下次延迟时间
        long delayMs = calculateDelayMsFromPayload(payload, newRetryCount);

        // 更新 Server 上的重试信息（增加重试次数并重新标记为 INIT）
        updateRetryCountAndStatus(taskId, newRetryCount, "INIT");

        // 投递胖消息（更新 retryCount 后重新投递）
        RetryMessagePayload nextPayload = clonePayloadWithNewCount(payload, newRetryCount);
        retryMessageProducer.sendDelayMessageWithPayload(nextPayload, delayMs);
        log.info("[LocalRetryExecutor] Scheduled next retry: taskId={}, retryCount={}, delayMs={}", taskId, newRetryCount, delayMs);
    }

    private RetryMessagePayload clonePayloadWithNewCount(RetryMessagePayload payload, int newRetryCount) {
        return RetryMessagePayload.builder()
                .taskId(payload.getTaskId())
                .sceneType(payload.getSceneType())
                .idempotentKey(payload.getIdempotentKey())
                .methodClass(payload.getMethodClass())
                .methodName(payload.getMethodName())
                .methodParams(payload.getMethodParams())
                .methodParamTypes(payload.getMethodParamTypes())
                .hookClass(payload.getHookClass())
                .backoffStrategy(payload.getBackoffStrategy())
                .backoffBase(payload.getBackoffBase())
                .retryIntervals(payload.getRetryIntervals())
                .retryCount(newRetryCount)
                .maxRetryCount(payload.getMaxRetryCount())
                .build();
    }

    /** 历史记录失败不影响主流程 */
    private void safeRecordHistory(String taskId, int retryCount, String result, String errorMsg, long costMs) {
        try {
            retryClient.recordHistory(taskId, retryCount, result, errorMsg, costMs);
        } catch (Exception e) {
            log.warn("[LocalRetryExecutor] Failed to record history: taskId={}, error={}", taskId, e.getMessage());
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

    /**
     * 加载 Hook Bean。
     * 业务方的 Hook 通常注册方式有两种：
     * 1. @Component("com.xxx.XxxHook") 显式指定全类名作为 Bean 名 → 按名字查
     * 2. @Component 用类型注册 → 按类型查
     * 兼容两种写法，避免 hook 查找失败。
     */
    private RetryHook getHookBean(String hookClass) throws Exception {
        // 优先：按 Bean 名（很多业务方显式用全类名注册）
        try {
            Object bean = applicationContext.getBean(hookClass);
            if (bean instanceof RetryHook) {
                log.debug("[LocalRetryExecutor] Hook found by name: {}", hookClass);
                return (RetryHook) bean;
            }
        } catch (Exception ignored) {
            // 按名字找不到，降级按类型找
        }
        // 降级：按类型查找（同样使用线程上下文 ClassLoader）
        ClassLoader contextCl = Thread.currentThread().getContextClassLoader();
        Class<?> clazz = Class.forName(hookClass, true, contextCl);
        log.debug("[LocalRetryExecutor] Hook found by type: {}", hookClass);
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
            default:        return Class.forName(typeName, true, Thread.currentThread().getContextClassLoader());
        }
    }

    private static final org.springframework.core.ParameterNameDiscoverer parameterNameDiscoverer = new org.springframework.core.DefaultParameterNameDiscoverer();

    /**
     * 按参数名从 Map 中还原方法入参。
     * <p>
     * 修复：移除了原来的‘参数名含 id/key 就用幂等键填充’的启发式规则——
     * 该规则将任意含“id”/“key”字符串的参数（如 buildingId、apiKey）误填为幂等键。
     * 修复2：使用 Spring 的 ParameterNameDiscoverer，防止未开启 -parameters 编译参数时，
     * 参数名变成 arg0, arg1 导致参数丢失的问题。
     */
    private Object[] prepareMethodArgs(Method method, Map<String, Object> paramsMap) throws Exception {
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];
        String[] paramNames = parameterNameDiscoverer.getParameterNames(method);
        
        for (int i = 0; i < parameters.length; i++) {
            String paramName = (paramNames != null && paramNames.length > i) ? paramNames[i] : parameters[i].getName();
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

    private RetryMessagePayload buildPayloadFromDTO(RetryTaskDTO task) {
        return RetryMessagePayload.builder()
                .taskId(task.getTaskId())
                .sceneType(task.getSceneType())
                .idempotentKey(task.getIdempotentKey())
                .methodClass(task.getMethodClass())
                .methodName(task.getMethodName())
                .methodParams(task.getMethodParams())
                .methodParamTypes(task.getMethodParamTypes())
                .hookClass(task.getHookClass())
                .backoffStrategy(task.getBackoffStrategy())
                .backoffBase(task.getBackoffBase())
                .retryIntervals(task.getRetryIntervals())
                .retryCount(task.getRetryCount())
                .maxRetryCount(task.getMaxRetryCount())
                .build();
    }

    private long calculateDelayMsFromPayload(RetryMessagePayload payload, int retryCount) {
        // 自定义或回退支持
        String strategy = payload.getBackoffStrategy() != null ? payload.getBackoffStrategy() : "CUSTOM";
        int baseMins = payload.getBackoffBase() != null ? payload.getBackoffBase() : 1;
        
        if ("FIXED".equalsIgnoreCase(strategy)) {
            return baseMins * 60L * 1000L;
        } else if ("LINEAR".equalsIgnoreCase(strategy)) {
            return (long) retryCount * baseMins * 60L * 1000L;
        } else if ("EXPONENTIAL".equalsIgnoreCase(strategy)) {
            int exp = Math.min(retryCount - 1, 30);
            return baseMins * (1L << exp) * 60L * 1000L;
        } else {
            // CUSTOM 自定义列表间隔解析，默认 1 分钟
            String intervals = payload.getRetryIntervals();
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

    private RetryContext buildContextFromPayload(RetryMessagePayload payload) {
        Map<String, Object> paramsMap = null;
        try {
            if (payload.getMethodParams() != null && !payload.getMethodParams().trim().isEmpty()) {
                paramsMap = objectMapper.readValue(payload.getMethodParams(), Map.class);
            }
        } catch (Exception e) {
            log.error("Failed to parse method params: taskId={}", payload.getTaskId(), e);
        }
        return RetryContext.builder()
                .taskId(payload.getTaskId())
                .sceneType(payload.getSceneType())
                .idempotentKey(payload.getIdempotentKey())
                .params(paramsMap)
                .retryCount(payload.getRetryCount() != null ? payload.getRetryCount() : 0)
                .maxRetryCount(payload.getMaxRetryCount() != null ? payload.getMaxRetryCount() : 3)
                .methodClass(payload.getMethodClass())
                .methodName(payload.getMethodName())
                .methodParamsJson(payload.getMethodParams())
                .methodParamTypes(payload.getMethodParamTypes())
                .build();
    }

    /**
     * 无 Hook 时的默认实现：checkStatus 始终返回 INIT，触发直接反射重试原始方法。
     * doQuery / doCallback 不做任何事。
     */
    private static class NoOpRetryHook implements RetryHook {
        @Override
        public String checkStatus(RetryContext context) {
            return "INIT"; // 直接走 handleInit → 反射调用原始业务方法
        }
        @Override
        public QueryResult doQuery(RetryContext context) {
            return QueryResult.failure("NoOpRetryHook: no query logic");
        }
        @Override
        public void doCallback(RetryContext context, QueryResult result) {
            // do nothing
        }
    }
}

