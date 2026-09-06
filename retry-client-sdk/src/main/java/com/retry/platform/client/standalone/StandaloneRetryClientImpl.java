package com.retry.platform.client.standalone;

import com.retry.platform.client.api.RetryClient;
import com.retry.platform.client.config.RetryClientProperties;
import com.retry.platform.client.config.StandaloneSceneConfig;
import com.retry.platform.client.dto.RetryTaskDTO;
import com.retry.platform.client.dto.RetryTaskRequest;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Standalone 模式下的 RetryClient 实现。
 *
 * <p>与 {@link com.retry.platform.client.api.impl.RetryClientImpl}（远程 HTTP 实现）的区别：
 * <ul>
 *   <li>本类直接操作本地数据库（通过 MyBatis Mapper），无需依赖 retry-server</li>
 *   <li>场景配置从 application.yml 中读取（{@link StandaloneSceneConfig}），无需查 scene_config 表</li>
 *   <li>表结构与 retry-server 保持一致，方便未来升级到 Remote 模式时无缝迁移</li>
 * </ul>
 *
 * <p>接入方只需在 application.yml 配置：
 * <pre>
 * retry:
 *   client:
 *     mode: standalone
 *     scenes:
 *       - scene-type: 1001
 *         scene-name: 退款重试
 *         retry-intervals: "1,3,6,9"
 *         max-retry-count: 4
 *         hook-class: com.example.RefundRetryHook
 * </pre>
 * 以及在自己的 DB 中执行 {@code standalone-retry-schema.sql} 建表即可。
 */
@Slf4j
public class StandaloneRetryClientImpl implements RetryClient {

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
        // 1. 幂等检查：同一 (sceneType, idempotentKey) 若已有 INIT/WAIT 任务，直接复用
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

        // 2. 从 yml 场景配置中读取策略信息
        StandaloneSceneConfig sceneConfig = findSceneConfig(request.getSceneType());
        if (sceneConfig == null) {
            throw new IllegalStateException(
                    "[Standalone] No scene config found for sceneType=" + request.getSceneType() +
                    ". Please configure it in application.yml under retry.client.scenes[]"
            );
        }

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
        // 从 yml 场景配置读取策略
        task.setMaxRetryCount(sceneConfig.getMaxRetryCount());
        task.setHookClass(sceneConfig.getHookClass());
        task.setBackoffStrategy(sceneConfig.getBackoffStrategy());
        task.setBackoffBase(sceneConfig.getBackoffBase());
        task.setRetryIntervals(sceneConfig.getRetryIntervals());
        task.setNextRetryTime(System.currentTimeMillis() + 60_000L); // 初始下次执行时间 +1分钟

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
            int rows = taskMapper.updateStatus(taskId, "FAILED");
            log.info("[Standalone] cancel: taskId={}, affected={}", taskId, rows);
            return rows > 0;
        } catch (Exception e) {
            log.error("[Standalone] Failed to cancel: taskId={}", taskId, e);
            return false;
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 根据 sceneType 从 yml 场景配置中查找对应配置
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

    /**
     * 生成全局唯一的任务ID
     * 格式：RT + 毫秒时间戳 + 8位UUID随机串
     */
    private static String generateTaskId() {
        String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return "RT" + System.currentTimeMillis() + uuid;
    }
}
