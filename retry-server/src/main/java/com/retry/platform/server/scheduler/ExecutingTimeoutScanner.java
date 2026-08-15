package com.retry.platform.server.scheduler;

import com.retry.platform.server.mapper.RetryTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * EXECUTING 状态超时保护扫描器
 *
 * <p>修复 Bug6：进程被 kill -9 / OOM Killer / 容器强制驱逐后，
 * 正在执行的任务状态会永久卡在 EXECUTING。
 * 由于 CAS 抢占要求状态为 INIT，卡死的任务将永远无法被再次执行，
 * 只能依赖人工介入——这在生产中是严重隐患。
 *
 * <p>解决方案：每分钟扫描一次，将 update_time 超过阈值（默认5分钟）的
 * EXECUTING 任务原子回滚为 INIT，让它们重新参与调度。
 *
 * <p>配置项：
 * <pre>
 * retry.scheduler.executing-timeout-minutes: 5  # EXECUTING 超时阈值（分钟），默认5
 * </pre>
 */
@Slf4j
@Component
public class ExecutingTimeoutScanner {

    @Autowired
    private RetryTaskMapper retryTaskMapper;

    /**
     * EXECUTING 超时阈值（分钟）。
     * 取决于业务方法最长执行时间，建议设置为业务超时的 2 倍。
     * 默认 5 分钟：足以覆盖绝大多数正常执行场景，同时保证卡死任务能及时恢复。
     */
    @Value("${retry.scheduler.executing-timeout-minutes:5}")
    private int executingTimeoutMinutes;

    /**
     * 每分钟扫描一次，回滚超时 EXECUTING 任务。
     *
     * <p>实现原理：
     * <pre>
     * UPDATE retry_task SET task_status = 'INIT'
     * WHERE task_status = 'EXECUTING'
     *   AND UNIX_TIMESTAMP(update_time) * 1000 &lt; :timeoutThreshold
     * </pre>
     * 正常执行中的任务 update_time 会随状态变化不断刷新，不会触发此条件。
     * 只有进程崩溃后 update_time 停止更新，超出阈值后才被回滚。
     */
    @Scheduled(fixedDelayString = "${retry.scheduler.executing-scan-interval:60000}")
    public void recoverStuckTasks() {
        try {
            long timeoutThreshold = System.currentTimeMillis()
                    - (long) executingTimeoutMinutes * 60 * 1000;

            int recovered = retryTaskMapper.recoverStuckExecutingTasks(timeoutThreshold);

            if (recovered > 0) {
                log.warn("[ExecutingTimeoutScanner] Recovered {} task(s) stuck in EXECUTING state "
                        + "(timeout={}min). These tasks were likely left by a crashed process and "
                        + "will be rescheduled for retry.", recovered, executingTimeoutMinutes);
            } else {
                log.debug("[ExecutingTimeoutScanner] No stuck EXECUTING tasks found.");
            }
        } catch (Exception e) {
            log.error("[ExecutingTimeoutScanner] Failed to scan for stuck tasks", e);
        }
    }
}
