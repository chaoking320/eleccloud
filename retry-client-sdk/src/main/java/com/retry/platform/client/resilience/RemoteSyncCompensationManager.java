package com.retry.platform.client.resilience;

import com.retry.platform.client.api.RetryClient;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 远程状态同步容灾补偿管理器。
 *
 * <p>当 retry-server 发生瞬时网络抖动、宕机或滚动重启时，SDK 侧的重试终态同步（如 markSuccess、markFailed、updateStatus）
 * 会自动进入内存有界容灾缓冲池，由后台守护线程在 Server 恢复后自动重放补偿，防止任务状态悬空。
 */
@Slf4j
public class RemoteSyncCompensationManager implements SmartLifecycle {

    public enum EventType {
        MARK_SUCCESS,
        MARK_FAILED,
        UPDATE_STATUS,
        UPDATE_RETRY_COUNT_AND_STATUS,
        ROLLBACK
    }

    @Data
    @Builder
    public static class SyncEvent {
        private EventType type;
        private String taskId;
        private String status;
        private String reason;
        private int retryCount;
        private long timestamp;
    }

    private static final int MAX_BUFFER_CAPACITY = 5000;
    private final BlockingQueue<SyncEvent> buffer = new LinkedBlockingQueue<>(MAX_BUFFER_CAPACITY);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "eleccloud-sync-compensation-worker");
        t.setDaemon(true);
        return t;
    });

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private RetryClient retryClient;

    public void setRetryClient(RetryClient retryClient) {
        this.retryClient = retryClient;
    }

    /**
     * 将同步失败的事件放入内存容灾缓冲区
     */
    public boolean offer(SyncEvent event) {
        if (event == null || event.getTaskId() == null) {
            return false;
        }
        boolean offered = buffer.offer(event);
        if (offered) {
            log.warn("[ResilienceBuffer] Server temporarily unreachable, buffered state update for compensation: taskId={}, type={}",
                    event.getTaskId(), event.getType());
        } else {
            log.error("[ResilienceBuffer] Buffer full, dropped state update: taskId={}, type={}",
                    event.getTaskId(), event.getType());
        }
        return offered;
    }

    /**
     * 触发一轮补偿重放
     */
    public int drainAndCompensate() {
        if (retryClient == null || buffer.isEmpty()) {
            return 0;
        }

        int successCount = 0;
        int initialSize = buffer.size();

        for (int i = 0; i < initialSize; i++) {
            SyncEvent event = buffer.poll();
            if (event == null) {
                break;
            }

            try {
                boolean success = replay(event);
                if (success) {
                    successCount++;
                    log.info("[ResilienceBuffer] Successfully compensated buffered state to server: taskId={}, type={}",
                            event.getTaskId(), event.getType());
                } else {
                    // Server 仍未恢复，重新放回队列末尾等待下一次轮询
                    buffer.offer(event);
                    break;
                }
            } catch (Exception e) {
                log.debug("[ResilienceBuffer] Server still unreachable during replay: taskId={}, error={}",
                        event.getTaskId(), e.getMessage());
                buffer.offer(event);
                break;
            }
        }
        return successCount;
    }

    private boolean replay(SyncEvent event) {
        switch (event.getType()) {
            case MARK_SUCCESS:
                return retryClient.markSuccess(event.getTaskId());
            case MARK_FAILED:
                retryClient.markFailed(event.getTaskId(), event.getReason());
                return true;
            case UPDATE_STATUS:
                retryClient.updateStatus(event.getTaskId(), event.getStatus());
                return true;
            case UPDATE_RETRY_COUNT_AND_STATUS:
                retryClient.updateRetryCountAndStatus(event.getTaskId(), event.getRetryCount(), event.getStatus());
                return true;
            case ROLLBACK:
                retryClient.rollbackToPending(event.getTaskId(), event.getReason());
                return true;
            default:
                return false;
        }
    }

    public int getPendingSize() {
        return buffer.size();
    }

    @Override
    public void start() {
        if (isRunning.compareAndSet(false, true)) {
            scheduler.scheduleWithFixedDelay(this::drainAndCompensate, 5, 10, TimeUnit.SECONDS);
            log.info("[ResilienceBuffer] Remote sync compensation manager started.");
        }
    }

    @Override
    public void stop() {
        if (isRunning.compareAndSet(true, false)) {
            scheduler.shutdownNow();
            log.info("[ResilienceBuffer] Remote sync compensation manager stopped.");
        }
    }

    @Override
    public boolean isRunning() {
        return isRunning.get();
    }
}
