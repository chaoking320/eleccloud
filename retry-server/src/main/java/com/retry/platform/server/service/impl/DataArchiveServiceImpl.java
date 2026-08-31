package com.retry.platform.server.service.impl;

import com.retry.platform.server.entity.FailedTask;
import com.retry.platform.server.mapper.FailedTaskMapper;
import com.retry.platform.server.mapper.RetryHistoryMapper;
import com.retry.platform.server.mapper.RetryTaskMapper;
import com.retry.platform.server.service.DataArchiveService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 数据归档服务实现
 */
@Slf4j
@Service
public class DataArchiveServiceImpl implements DataArchiveService {

    @Autowired
    private RetryTaskMapper retryTaskMapper;

    @Autowired
    private RetryHistoryMapper retryHistoryMapper;

    @Autowired
    private FailedTaskMapper failedTaskMapper;

    @Value("${retry.archive.export-dir:./data/export}")
    private String exportDir;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FILE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    @Override
    @Transactional
    public int archiveHistoryData(LocalDateTime beforeDate) {
        log.info("[DataArchive] Starting archive history data before: {}", beforeDate);

        try {
            // 删除历史记录
            int deletedCount = retryHistoryMapper.deleteByExecuteTimeBefore(beforeDate);
            log.info("[DataArchive] Archived {} history records", deletedCount);
            return deletedCount;
        } catch (Exception e) {
            log.error("[DataArchive] Failed to archive history data", e);
            throw e;
        }
    }

    @Override
    @Transactional
    public int cleanSuccessTasks(LocalDateTime beforeDate) {
        log.info("[DataArchive] Starting clean success tasks before: {}", beforeDate);

        try {
            // 删除成功任务
            int deletedCount = retryTaskMapper.deleteSuccessTasksBefore(beforeDate);
            log.info("[DataArchive] Cleaned {} success tasks", deletedCount);
            return deletedCount;
        } catch (Exception e) {
            log.error("[DataArchive] Failed to clean success tasks", e);
            throw e;
        }
    }

    @Override
    public String exportFailedTasks(Integer sceneType, LocalDateTime startTime, LocalDateTime endTime) {
        log.info("[DataArchive] Exporting failed tasks: sceneType={}, startTime={}, endTime={}",
                sceneType, startTime, endTime);

        try {
            // 查询失败任务（最多导出10万条）
            List<FailedTask> failedTasks = failedTaskMapper.selectByConditions(
                    sceneType,
                    null,  // idempotentKey
                    startTime,
                    endTime,
                    0,     // offset
                    100000 // limit
            );
            log.info("[DataArchive] Found {} failed tasks to export", failedTasks.size());

            if (failedTasks.isEmpty()) {
                log.warn("[DataArchive] No failed tasks found to export");
                return null;
            }

            // 生成CSV文件
            String fileName = "failed_tasks_" + LocalDateTime.now().format(FILE_FORMATTER) + ".csv";
            File exportFile = new File(exportDir, fileName);

            // 创建导出目录
            exportFile.getParentFile().mkdirs();

            // 写入CSV
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(exportFile))) {
                // 写入表头
                writer.write("任务ID,场景类型,幂等键,方法类,方法名,重试次数,失败原因,创建时间,失败时间");
                writer.newLine();

                // 写入数据
                for (FailedTask task : failedTasks) {
                    writer.write(String.format("%s,%d,%s,%s,%s,%d,\"%s\",%s,%s",
                            escapeCsv(task.getTaskId()),
                            task.getSceneType(),
                            escapeCsv(task.getIdempotentKey()),
                            escapeCsv(task.getMethodClass()),
                            escapeCsv(task.getMethodName()),
                            task.getRetryCount(),
                            escapeCsv(task.getFailReason()),
                            task.getCreateTime() != null ? task.getCreateTime().format(FORMATTER) : "",
                            task.getFailTime() != null ? task.getFailTime().format(FORMATTER) : ""
                    ));
                    writer.newLine();
                }
            }

            log.info("[DataArchive] Exported {} failed tasks to: {}", failedTasks.size(), exportFile.getAbsolutePath());
            return exportFile.getAbsolutePath();

        } catch (IOException e) {
            log.error("[DataArchive] Failed to export failed tasks", e);
            throw new RuntimeException("Failed to export failed tasks", e);
        }
    }

    /**
     * CSV特殊字符转义
     */
    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        // 如果包含逗号、双引号或换行符，需要用双引号包裹
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
