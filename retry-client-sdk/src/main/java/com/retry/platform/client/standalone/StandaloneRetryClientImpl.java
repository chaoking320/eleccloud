package com.retry.platform.client.standalone;

import com.retry.platform.client.api.RetryClient;
import com.retry.platform.client.config.RetryClientProperties;
import com.retry.platform.client.config.StandaloneSceneConfig;
import com.retry.platform.client.dto.RetryTaskDTO;
import com.retry.platform.client.dto.RetryTaskRequest;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/**
 * Standalone 模式下的 RetryClient 实现。
 *
 * <p>与 {@link com.retry.platform.client.api.impl.RetryClientImpl}（远程 HTTP 实现）的区别：
 * <ul>
 *   <li>本类直接操作本地数据库（通过 MyBatis Mapper），无需依赖 retry-server</li>
 *   <li>场景策略采用<b>三级优先级</b>：Request 字段 > application.yml scenes > SDK 内置默认值</li>
 *   <li>表结构与 retry-server 保持一致，方便未来升级到 Remote 模式时无缝迁移</li>
 * </ul>
 *
 * <p><b>最简接入（零 YAML 配置）：</b>
 * <pre>
 * // 注解直接声明策略，无需 application.yml 中的 scenes 配置
 * {@code @RetryableTask(sceneType=100, idempotentKey="#id", maxRetryCount=5,
 *     retryIntervals="1,2,5,10,30", hookClass=MyHook.class)}
 * public void myMethod(String id) { ... }
 * </pre>
 *
 * <p>只需在 application.yml 配置基本项：
 * <pre>
 * retry:
 *   client:
 *     mode: standalone
 *     enabled: true
 *     mq-type: REDIS
 * </pre>
 */
@Slf4j
public class StandaloneRetryClientImpl implements RetryClient {

    /** SDK 内置默认值（第三级，兜底使用） */
    private static final int DEFAULT_MAX_RETRY_COUNT = 3;
    private static final String DEFAULT_RETRY_INTERVALS = "1,3,5";
    private static final String DEFAULT_BACKOFF_STRATEGY = "CUSTOM";
    private static final int DEFAULT_BACKOFF_BASE = 1;

    private final StandaloneRetryTaskMapper taskMapper;
    private final StandaloneRetryHistoryMapper historyMapper;
    private final RetryClientProperties properties;

    public StandaloneRetryClientImpl(StandaloneRetryTaskMapper taskMapper,
                                     StandaloneRetryHistoryMapper historyMapper,
                                     RetryClientProperties properties) {
        this.taskMapper = taskMapper;
        this.historyMapper = historyMapper;
        this.properties = properties;
    }

    // ==================== 提交任务 ====================

    @Override
    public String submit(RetryTaskRequest request) {
        // 1. 幂等检查：同一 (sceneType, idempotentKey) 若已有 INIT/WAIT/EXECUTING 任务，直接复用
        RetryTaskDTO existing = taskMapper.selectBySceneAndIdempotentKey(
                request.getSceneType(), request.getIdempotentKey());
        if (existing != null) {
            String status = existing.getTaskStatus();
            if ("INIT".equals(status) || "WAIT".equals(status) || "EXECUTING".equals(status)) {
                log.info("[Standalone] Task already exists (status={}), reusing taskId={}",
                        status, existing.getTaskId());
                return existing.getTaskId();
            }
            // SUCCESS/FAILED 任务不复用，重新创建（如业务方再次触发失败）
        }

        // 2. 三级策略合并：Request > YAML scenes > SDK 默认值
        StandaloneSceneConfig sceneConfig = findSceneConfig(request.getSceneType());
        String resolvedHookClass   = resolveHookClass(request, sceneConfig);
        int resolvedMaxRetry       = resolveMaxRetryCount(request, sceneConfig);
        String resolvedIntervals   = resolveRetryIntervals(request, sceneConfig);
        String resolvedStrategy    = resolveBackoffStrategy(request, sceneConfig);
        int resolvedBase           = resolveBackoffBase(request, sceneConfig);

        log.info("[Standalone] Resolved strategy for sceneType={}: maxRetry={}, intervals=[{}], hookClass={}",
                request.getSceneType(), resolvedMaxRetry, resolvedIntervals, resolvedHookClass);

        // 3. 生成 taskId 并构造 DTO
        String taskId = generateTaskId();
        RetryTaskDTO task = new RetryTaskDTO();
        task.setTaskId(taskId);
        task.setSceneType(request.getSceneType());
        task.setIdempotentKey(request.getIdempotentKey());
        task.setMethodClass(request.getMethodClass());
        task.setMethodName(request.getMethodName());
        task.setMethodParams(request.getMethodParams());
        task.setMethodParamTypes(request.getMethodParamTypes());
        task.setTaskStatus("INIT");
        task.setSubmitMode(request.getSubmitMode() != null ? request.getSubmitMode() : "POST_FAIL");
        task.setRetryCount(0);
        task.setMaxRetryCount(resolvedMaxRetry);
        task.setHookClass(resolvedHookClass);
        task.setBackoffStrategy(resolvedStrategy);
        task.setBackoffBase(resolvedBase);
        task.setRetryIntervals(resolvedIntervals);
        task.setNextRetryTime(System.currentTimeMillis() + 60_000L);

        // 4. 写入本地 DB
        taskMapper.insert(task);
        log.info("[Standalone] Task submitted. taskId={}, sceneType={}, idempotentKey={}",
                taskId, request.getSceneType(), request.getIdempotentKey());
        return taskId;
    }

    // ==================== 任务状态操作 ====================

    @Override
    public boolean markSuccess(String taskId) {
        int rows = taskMapper.updateStatus(taskId, "SUCCESS");
        log.info("[Standalone] markSuccess: taskId={}, affected={}", taskId, rows);
        return rows > 0;
    }

    @Override
    public boolean markExecuting(String taskId) {
        // CAS：仅当状态为 INIT 或 WAIT 时才成功
        int rows = taskMapper.casUpdateToExecuting(taskId);
        log.debug("[Standalone] markExecuting CAS: taskId={}, acquired={}", taskId, rows > 0);
        return rows > 0;
    }

    @Override
    public void touchHeartbeat(String taskId) {
        try {
            taskMapper.touchHeartbeat(taskId);
            log.debug("[Standalone] touchHeartbeat: taskId={}", taskId);
        } catch (Exception e) {
            log.warn("[Standalone] Failed to touchHeartbeat: taskId={}, error={}", taskId, e.getMessage());
        }
    }

    @Override
    public void updateStatus(String taskId, String status) {
        try {
            taskMapper.updateStatus(taskId, status);
            log.debug("[Standalone] updateStatus: taskId={}, status={}", taskId, status);
        } catch (Exception e) {
            log.error("[Standalone] Failed to updateStatus: taskId={}, status={}", taskId, status, e);
        }
    }

    @Override
    public void updateRetryCountAndStatus(String taskId, int retryCount, String status) {
        try {
            taskMapper.updateStatusAndRetryInfo(taskId, status, retryCount,
                    System.currentTimeMillis());
            log.debug("[Standalone] updateRetryCountAndStatus: taskId={}, retryCount={}, status={}",
                    taskId, retryCount, status);
        } catch (Exception e) {
            log.error("[Standalone] Failed to updateRetryCountAndStatus: taskId={}", taskId, e);
        }
    }

    @Override
    public void rollbackToPending(String taskId, String errorMsg) {
        try {
            taskMapper.rollbackToPending(taskId, errorMsg);
            log.warn("[Standalone] rollbackToPending: taskId={}, reason={}", taskId, errorMsg);
        } catch (Exception e) {
            log.error("[Standalone] Failed to rollbackToPending: taskId={}", taskId, e);
        }
    }

    @Override
    public void markFailed(String taskId, String reason) {
        try {
            taskMapper.updateStatus(taskId, "FAILED");
            log.warn("[Standalone] markFailed: taskId={}, reason={}", taskId, reason);
        } catch (Exception e) {
            log.error("[Standalone] Failed to markFailed: taskId={}", taskId, e);
        }
    }

    // ==================== 查询 ====================

    @Override
    public RetryTaskDTO queryTask(String taskId) {
        try {
            RetryTaskDTO task = taskMapper.selectByTaskId(taskId);
            if (task == null) {
                log.warn("[Standalone] queryTask: task not found, taskId={}", taskId);
            }
            return task;
        } catch (Exception e) {
            log.error("[Standalone] Failed to queryTask: taskId={}", taskId, e);
            return null;
        }
    }

    // ==================== 历史记录 ====================

    @Override
    public void recordHistory(String taskId, int retryCount, String executeResult,
                              String errorMessage, long costTimeMs) {
        try {
            historyMapper.insert(taskId, retryCount, executeResult, errorMessage, costTimeMs);
            log.debug("[Standalone] recordHistory: taskId={}, retryCount={}, result={}, cost={}ms",
                    taskId, retryCount, executeResult, costTimeMs);
        } catch (Exception e) {
            // 历史记录失败不影响主流程
            log.warn("[Standalone] Failed to recordHistory: taskId={}, error={}", taskId, e.getMessage());
        }
    }

    // ==================== 取消（可选实现） ====================

    @Override
    public boolean cancel(String taskId) {
        try {
            int rows = taskMapper.updateStatus(taskId, "CANCELLED");
            log.info("[Standalone] cancel: taskId={}, affected={}", taskId, rows);
            return rows > 0;
        } catch (Exception e) {
            log.error("[Standalone] Failed to cancel: taskId={}", taskId, e);
            return false;
        }
    }

    // ==================== 三级策略合并工具方法 ====================

    /**
     * 根据 sceneType 从 yml 场景配置中查找对应配置（可能为 null）
     */
    private StandaloneSceneConfig findSceneConfig(Integer sceneType) {
        if (sceneType == null || properties.getScenes() == null) {
            return null;
        }
        return properties.getScenes().stream()
                .filter(s -> sceneType.equals(s.getSceneType()))
                .findFirst()
                .orElse(null);
    }

    /** 三级合并：hookClass = Request > YAML > null */
    private String resolveHookClass(RetryTaskRequest req, StandaloneSceneConfig yml) {
        // 第一级：Request 字段（注解/编程式）
        if (req.getHookClass() != null && !req.getHookClass().trim().isEmpty()) {
            return req.getHookClass();
        }
        // 第二级：YAML scenes
        if (yml != null && yml.getHookClass() != null && !yml.getHookClass().trim().isEmpty()) {
            return yml.getHookClass();
        }
        // 第三级：无 Hook（降级为直接反射重试）
        return null;
    }

    /** 三级合并：maxRetryCount = Request > YAML > SDK默认(3) */
    private int resolveMaxRetryCount(RetryTaskRequest req, StandaloneSceneConfig yml) {
        if (req.getMaxRetryCount() != null && req.getMaxRetryCount() > 0) {
            return req.getMaxRetryCount();
        }
        if (yml != null && yml.getMaxRetryCount() != null && yml.getMaxRetryCount() > 0) {
            return yml.getMaxRetryCount();
        }
        return DEFAULT_MAX_RETRY_COUNT;
    }

    /** 三级合并：retryIntervals = Request > YAML > SDK默认("1,3,5") */
    private String resolveRetryIntervals(RetryTaskRequest req, StandaloneSceneConfig yml) {
        if (req.getRetryIntervals() != null && !req.getRetryIntervals().trim().isEmpty()) {
            return req.getRetryIntervals();
        }
        if (yml != null && yml.getRetryIntervals() != null && !yml.getRetryIntervals().trim().isEmpty()) {
            return yml.getRetryIntervals();
        }
        return DEFAULT_RETRY_INTERVALS;
    }

    /** 三级合并：backoffStrategy = Request > YAML > SDK默认("CUSTOM") */
    private String resolveBackoffStrategy(RetryTaskRequest req, StandaloneSceneConfig yml) {
        if (req.getBackoffStrategy() != null && !req.getBackoffStrategy().trim().isEmpty()) {
            return req.getBackoffStrategy();
        }
        if (yml != null && yml.getBackoffStrategy() != null && !yml.getBackoffStrategy().trim().isEmpty()) {
            return yml.getBackoffStrategy();
        }
        return DEFAULT_BACKOFF_STRATEGY;
    }

    /** 三级合并：backoffBase = Request > YAML > SDK默认(1) */
    private int resolveBackoffBase(RetryTaskRequest req, StandaloneSceneConfig yml) {
        if (req.getBackoffBase() != null && req.getBackoffBase() > 0) {
            return req.getBackoffBase();
        }
        if (yml != null && yml.getBackoffBase() != null && yml.getBackoffBase() > 0) {
            return yml.getBackoffBase();
        }
        return DEFAULT_BACKOFF_BASE;
    }

    /**
     * 生成全局唯一的任务ID
     * 格式：RT + 毫秒时间戳 + 8位UUID随机串
     */
    private static String generateTaskId() {
        String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return "RT" + System.currentTimeMillis() + uuid;
    }
}
