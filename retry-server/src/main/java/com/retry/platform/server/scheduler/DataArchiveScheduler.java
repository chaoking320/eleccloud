package com.retry.platform.server.scheduler;

import com.retry.platform.server.service.DataArchiveService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 数据归档调度器
 * 定期清理历史数据和成功任务
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "retry.archive", name = "enabled", havingValue = "true")
public class DataArchiveScheduler {

    @Autowired
    private DataArchiveService dataArchiveService;

    @Value("${retry.archive.history-retention-days:90}")
    private int historyRetentionDays;

    @Value("${retry.archive.success-task-retention-days:30}")
    private int successTaskRetentionDays;

    /**
     * 清理历史记录（每天凌晨3点执行）
     */
    @Scheduled(cron = "${retry.archive.history-clean-cron:0 0 3 * * ?}")
    public void cleanHistoryData() {
        log.info("[DataArchive] Starting scheduled history data cleanup...");

        try {
            LocalDateTime beforeDate = LocalDateTime.now().minusDays(historyRetentionDays);
            int archivedCount = dataArchiveService.archiveHistoryData(beforeDate);

            log.info("[DataArchive] History cleanup completed: {} records archived (retention: {} days)",
                    archivedCount, historyRetentionDays);
        } catch (Exception e) {
            log.error("[DataArchive] Failed to clean history data", e);
        }
    }

    /**
     * 清理成功任务（每天凌晨4点执行）
     */
    @Scheduled(cron = "${retry.archive.success-clean-cron:0 0 4 * * ?}")
    public void cleanSuccessTasks() {
        log.info("[DataArchive] Starting scheduled success tasks cleanup...");

        try {
            LocalDateTime beforeDate = LocalDateTime.now().minusDays(successTaskRetentionDays);
            int cleanedCount = dataArchiveService.cleanSuccessTasks(beforeDate);

            log.info("[DataArchive] Success tasks cleanup completed: {} tasks cleaned (retention: {} days)",
                    cleanedCount, successTaskRetentionDays);
        } catch (Exception e) {
            log.error("[DataArchive] Failed to clean success tasks", e);
        }
    }
}
