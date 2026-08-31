package com.retry.platform.server.scheduler;

import com.retry.platform.server.config.AlertConfig;
import com.retry.platform.server.mapper.FailedTaskMapper;
import com.retry.platform.server.mapper.RetryHistoryMapper;
import com.retry.platform.server.service.AlertService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 告警监控调度器
 * 定期检查失败任务数量和失败率，触发告警
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "retry.alert", name = "enabled", havingValue = "true")
public class AlertMonitorScheduler {

    @Autowired
    private AlertConfig alertConfig;

    @Autowired
    private AlertService alertService;

    @Autowired
    private FailedTaskMapper failedTaskMapper;

    @Autowired(required = false)
    private RetryHistoryMapper retryHistoryMapper;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 监控死信任务突增（每10分钟检查一次）
     */
    @Scheduled(fixedDelay = 600000, initialDelay = 60000)
    public void monitorFailedTasksSpike() {
        if (!alertConfig.getThreshold().isEnableFailedTaskAlert()) {
            return;
        }

        try {
            int timeWindowMinutes = alertConfig.getThreshold().getTimeWindowMinutes();
            LocalDateTime startTime = LocalDateTime.now().minusMinutes(timeWindowMinutes);

            // 查询时间窗口内的失败任务数量
            int failedCount = failedTaskMapper.countFailedTasksSince(startTime);
            int threshold = alertConfig.getThreshold().getFailedTasksPerHour();

            // 按小时归一化阈值
            int normalizedThreshold = (int) (threshold * (timeWindowMinutes / 60.0));

            if (failedCount > normalizedThreshold) {
                String title = "死信任务突增告警";
                String message = String.format(
                        "检测到死信任务数量异常增长！\n\n" +
                                "统计时间: %s 至 %s\n" +
                                "失败任务数: %d\n" +
                                "告警阈值: %d\n" +
                                "超出比例: %.1f%%\n\n" +
                                "建议: 请立即检查系统日志和下游服务状态",
                        startTime.format(FORMATTER),
                        LocalDateTime.now().format(FORMATTER),
                        failedCount,
                        normalizedThreshold,
                        (failedCount - normalizedThreshold) * 100.0 / normalizedThreshold
                );

                alertService.sendAlert(title, message, AlertService.AlertLevel.CRITICAL);
                log.warn("[AlertMonitor] Failed tasks spike detected: {} tasks in {} minutes (threshold: {})",
                        failedCount, timeWindowMinutes, normalizedThreshold);
            } else {
                log.debug("[AlertMonitor] Failed tasks count normal: {} (threshold: {})", failedCount, normalizedThreshold);
            }
        } catch (Exception e) {
            log.error("[AlertMonitor] Error monitoring failed tasks spike", e);
        }
    }

    /**
     * 监控执行失败率（每15分钟检查一次）
     */
    @Scheduled(fixedDelay = 900000, initialDelay = 120000)
    public void monitorFailureRate() {
        if (!alertConfig.getThreshold().isEnableFailureRateAlert()) {
            return;
        }

        if (retryHistoryMapper == null) {
            log.debug("[AlertMonitor] RetryHistoryMapper not available, skipping failure rate check");
            return;
        }

        try {
            int timeWindowMinutes = alertConfig.getThreshold().getTimeWindowMinutes();
            LocalDateTime startTime = LocalDateTime.now().minusMinutes(timeWindowMinutes);

            // 查询时间窗口内的执行统计
            int totalExecutions = retryHistoryMapper.countExecutionsSince(startTime);
            int failedExecutions = retryHistoryMapper.countFailedExecutionsSince(startTime);

            if (totalExecutions == 0) {
                log.debug("[AlertMonitor] No executions in time window, skipping failure rate check");
                return;
            }

            int failureRatePercent = (failedExecutions * 100) / totalExecutions;
            int threshold = alertConfig.getThreshold().getFailureRatePercent();

            if (failureRatePercent > threshold) {
                String title = "执行失败率过高告警";
                String message = String.format(
                        "检测到重试任务执行失败率过高！\n\n" +
                                "统计时间: %s 至 %s\n" +
                                "总执行次数: %d\n" +
                                "失败次数: %d\n" +
                                "失败率: %d%%\n" +
                                "告警阈值: %d%%\n\n" +
                                "建议: 请检查下游服务可用性和网络连通性",
                        startTime.format(FORMATTER),
                        LocalDateTime.now().format(FORMATTER),
                        totalExecutions,
                        failedExecutions,
                        failureRatePercent,
                        threshold
                );

                alertService.sendAlert(title, message, AlertService.AlertLevel.ERROR);
                log.warn("[AlertMonitor] High failure rate detected: {}% ({}/{}) (threshold: {}%)",
                        failureRatePercent, failedExecutions, totalExecutions, threshold);
            } else {
                log.debug("[AlertMonitor] Failure rate normal: {}% (threshold: {}%)", failureRatePercent, threshold);
            }
        } catch (Exception e) {
            log.error("[AlertMonitor] Error monitoring failure rate", e);
        }
    }

    /**
     * 系统健康检查（每小时一次）
     */
    @Scheduled(fixedDelay = 3600000, initialDelay = 300000)
    public void systemHealthCheck() {
        try {
            int timeWindowMinutes = 60;
            LocalDateTime startTime = LocalDateTime.now().minusMinutes(timeWindowMinutes);

            int failedCount = failedTaskMapper.countFailedTasksSince(startTime);
            int totalExecutions = retryHistoryMapper != null ? retryHistoryMapper.countExecutionsSince(startTime) : 0;

            log.info("[AlertMonitor] System health check - Failed tasks: {}, Total executions: {} in last hour",
                    failedCount, totalExecutions);
        } catch (Exception e) {
            log.error("[AlertMonitor] Error during system health check", e);
        }
    }
}
