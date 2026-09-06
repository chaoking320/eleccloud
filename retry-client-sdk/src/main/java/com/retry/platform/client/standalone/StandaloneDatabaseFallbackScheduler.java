package com.retry.platform.client.standalone;

import com.retry.platform.client.mq.RetryMessagePayload;
import com.retry.platform.client.mq.RetryMessageProducer;
import com.retry.platform.client.dto.RetryTaskDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;

/**
 * Standalone 模式下的数据库兜底扫描定时任务
 *
 * <p>用途：防止 MQ 消息丢失（如 Redis 重启、RabbitMQ 不持久化等情况）导致任务永久停滞。
 *
 * <p>执行逻辑（每分钟）：
 * <ol>
 *   <li>扫描本地 {@code retry_task} 表中 {@code next_retry_time <= now} 且状态为 INIT/WAIT 的任务</li>
 *   <li>对每个任务重新投递一条延时消息到 MQ，让 {@link com.retry.platform.client.executor.LocalRetryExecutor} 拾取执行</li>
 *   <li>同时恢复超时卡死在 EXECUTING 状态的任务（进程 kill -9 后可能出现）</li>
 * </ol>
 */
@Slf4j
public class StandaloneDatabaseFallbackScheduler {

    private final StandaloneRetryTaskMapper taskMapper;
    private final RetryMessageProducer retryMessageProducer;

    /**
     * EXECUTING 状态超时阈值（毫秒），超过此时间未更新视为卡死
     */
    private static final long EXECUTING_TIMEOUT_MS = 5 * 60 * 1000L; // 5分钟

    /**
     * 每次扫描最多拉取的任务数（防止一次性投递过多消息）
     */
    private static final int FALLBACK_BATCH_LIMIT = 100;

    public StandaloneDatabaseFallbackScheduler(StandaloneRetryTaskMapper taskMapper,
                                               RetryMessageProducer retryMessageProducer) {
        this.taskMapper = taskMapper;
        this.retryMessageProducer = retryMessageProducer;
    }

    /**
     * 每分钟执行一次数据库兜底扫描
     */
    @Scheduled(fixedDelay = 60_000L, initialDelay = 30_000L)
    public void scanAndReschedule() {
        try {
            // 1. 恢复超时 EXECUTING 任务
            long timeoutThreshold = System.currentTimeMillis() - EXECUTING_TIMEOUT_MS;
            int recovered = taskMapper.recoverStuckExecutingTasks(timeoutThreshold);
            if (recovered > 0) {
                log.warn("[Standalone][Fallback] Recovered {} stuck EXECUTING tasks (timeout > {}ms)",
                        recovered, EXECUTING_TIMEOUT_MS);
            }

            // 2. 扫描待重试任务（next_retry_time 已到期但 MQ 消息可能丢失）
            long now = System.currentTimeMillis();
            List<String> pendingTaskIds = taskMapper.selectPendingTasks(now, FALLBACK_BATCH_LIMIT);

            if (pendingTaskIds.isEmpty()) {
                return;
            }

            log.info("[Standalone][Fallback] Found {} overdue tasks, re-enqueuing...", pendingTaskIds.size());

            for (String taskId : pendingTaskIds) {
                try {
                    RetryTaskDTO task = taskMapper.selectByTaskId(taskId);
                    if (task == null) {
                        continue;
                    }
                    // 构建胖消息重新投递（立刻执行，delayMs = 0）
                    RetryMessagePayload payload = buildPayload(task);
                    retryMessageProducer.sendDelayMessageWithPayload(payload, 0L);
                    log.info("[Standalone][Fallback] Re-enqueued taskId={}, retryCount={}/{}",
                            taskId, task.getRetryCount(), task.getMaxRetryCount());
                } catch (Exception e) {
                    log.error("[Standalone][Fallback] Failed to re-enqueue taskId={}", taskId, e);
                }
            }
        } catch (Exception e) {
            log.error("[Standalone][Fallback] Unexpected error during DB fallback scan", e);
        }
    }

    private RetryMessagePayload buildPayload(RetryTaskDTO task) {
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
                .retryCount(task.getRetryCount() != null ? task.getRetryCount() : 0)
                .maxRetryCount(task.getMaxRetryCount() != null ? task.getMaxRetryCount() : 3)
                .build();
    }
}
