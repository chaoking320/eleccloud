package com.retry.platform.server.controller;

import com.retry.platform.server.service.DataArchiveService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDateTime;

/**
 * 数据归档控制器
 * 提供手动触发归档和导出的接口
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/archive")
public class DataArchiveController {

    @Autowired
    private DataArchiveService dataArchiveService;

    /**
     * 手动归档历史数据
     */
    @PostMapping("/history")
    public ResponseEntity<?> archiveHistory(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime beforeDate) {
        try {
            if (beforeDate == null) {
                beforeDate = LocalDateTime.now().minusDays(90);
            }

            int count = dataArchiveService.archiveHistoryData(beforeDate);

            return ResponseEntity.ok(new Result(true, "历史数据归档成功", count));
        } catch (Exception e) {
            log.error("Failed to archive history data", e);
            return ResponseEntity.ok(new Result(false, "归档失败: " + e.getMessage(), null));
        }
    }

    /**
     * 手动清理成功任务
     */
    @PostMapping("/success-tasks")
    public ResponseEntity<?> cleanSuccessTasks(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime beforeDate) {
        try {
            if (beforeDate == null) {
                beforeDate = LocalDateTime.now().minusDays(30);
            }

            int count = dataArchiveService.cleanSuccessTasks(beforeDate);

            return ResponseEntity.ok(new Result(true, "成功任务清理完成", count));
        } catch (Exception e) {
            log.error("Failed to clean success tasks", e);
            return ResponseEntity.ok(new Result(false, "清理失败: " + e.getMessage(), null));
        }
    }

    /**
     * 导出失败任务
     */
    @GetMapping("/export-failed-tasks")
    public ResponseEntity<?> exportFailedTasks(
            @RequestParam(required = false) Integer sceneType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        try {
            String filePath = dataArchiveService.exportFailedTasks(sceneType, startTime, endTime);

            if (filePath == null) {
                return ResponseEntity.ok(new Result(false, "没有找到失败任务", null));
            }

            // 读取文件并返回
            File file = new File(filePath);
            FileInputStream fis = new FileInputStream(file);
            byte[] bytes = new byte[(int) file.length()];
            fis.read(bytes);
            fis.close();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", file.getName());

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(bytes);

        } catch (IOException e) {
            log.error("Failed to export failed tasks", e);
            return ResponseEntity.ok(new Result(false, "导出失败: " + e.getMessage(), null));
        }
    }

    /**
     * 统一返回结果
     */
    static class Result {
        public boolean success;
        public String message;
        public Object data;

        public Result(boolean success, String message, Object data) {
            this.success = success;
            this.message = message;
            this.data = data;
        }
    }
}
