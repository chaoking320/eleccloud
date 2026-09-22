package com.retry.platform.client.resilience;

import com.retry.platform.client.api.RetryClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RemoteSyncCompensationManagerTest {

    @Mock
    private RetryClient retryClient;

    @Test
    @DisplayName("当Server不可用时缓冲更新事件，Server恢复后成功重放补偿")
    void testBufferAndCompensateOnRecovery() {
        RemoteSyncCompensationManager manager = new RemoteSyncCompensationManager();
        manager.setRetryClient(retryClient);

        // 1. 模拟断网，缓冲 2 个状态同步事件
        manager.offer(RemoteSyncCompensationManager.SyncEvent.builder()
                .type(RemoteSyncCompensationManager.EventType.MARK_SUCCESS)
                .taskId("TASK_SUCCESS_01")
                .timestamp(System.currentTimeMillis())
                .build());

        manager.offer(RemoteSyncCompensationManager.SyncEvent.builder()
                .type(RemoteSyncCompensationManager.EventType.UPDATE_STATUS)
                .taskId("TASK_WAIT_02")
                .status("WAIT")
                .timestamp(System.currentTimeMillis())
                .build());

        assertEquals(2, manager.getPendingSize());

        // 2. 模拟 Server 恢复，重试 client 调用成功
        when(retryClient.markSuccess(eq("TASK_SUCCESS_01"))).thenReturn(true);

        int compensated = manager.drainAndCompensate();

        assertEquals(2, compensated);
        assertEquals(0, manager.getPendingSize(), "All buffered events should be drained");

        verify(retryClient, times(1)).markSuccess("TASK_SUCCESS_01");
        verify(retryClient, times(1)).updateStatus("TASK_WAIT_02", "WAIT");
    }

    @Test
    @DisplayName("重放中若Server仍不可用，未成功的事件安全保留在队列末尾")
    void testCompensatePartialFailure_shouldRetainInBuffer() {
        RemoteSyncCompensationManager manager = new RemoteSyncCompensationManager();
        manager.setRetryClient(retryClient);

        manager.offer(RemoteSyncCompensationManager.SyncEvent.builder()
                .type(RemoteSyncCompensationManager.EventType.MARK_SUCCESS)
                .taskId("TASK_RETRYING")
                .timestamp(System.currentTimeMillis())
                .build());

        // 模拟 Server 依然报错
        when(retryClient.markSuccess(eq("TASK_RETRYING"))).thenReturn(false);

        int compensated = manager.drainAndCompensate();

        assertEquals(0, compensated);
        assertEquals(1, manager.getPendingSize(), "Failed event must be retained in buffer for next retry");
    }
}
