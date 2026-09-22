package com.retry.platform.admin.controller;

import com.retry.platform.server.entity.FailedTask;
import com.retry.platform.server.entity.RetryTask;
import com.retry.platform.server.mapper.FailedTaskMapper;
import com.retry.platform.server.mapper.RetryTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 失败超限任务控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/failed")
@CrossOrigin
public class FailedTaskController {

    @Autowired
    private FailedTaskMapper failedTaskMapper;

    @Autowired
    private RetryTaskMapper retryTaskMapper;

    /**
     * 条件分页查询失败任务列表
     */
    @GetMapping("/list")
    public Result<Map<String, Object>> getFailedList(
            @RequestParam(required = false) Integer sceneType,
            @RequestParam(required = false) String idempotentKey,
            @RequestParam(required = false) Long startTime,
            @RequestParam(required = false) Long endTime,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        try {
            int validPageNum = (pageNum == null || pageNum < 1) ? 1 : pageNum;
            int validPageSize = (pageSize == null || pageSize < 1) ? 10 : Math.min(pageSize, 100);
            int offset = (validPageNum - 1) * validPageSize;
            
            LocalDateTime start = null;
            if (startTime != null) {
                start = new java.sql.Timestamp(startTime).toLocalDateTime();
            }
            LocalDateTime end = null;
            if (endTime != null) {
                end = new java.sql.Timestamp(endTime).toLocalDateTime();
            }

            List<FailedTask> list = failedTaskMapper.selectByConditions(sceneType, idempotentKey, start, end, offset, validPageSize);
            long total = failedTaskMapper.countByConditions(sceneType, idempotentKey, start, end);

            Map<String, Object> pageResult = new HashMap<>();
            pageResult.put("list", list);
            pageResult.put("total", total);
            pageResult.put("pageNum", validPageNum);
            pageResult.put("pageSize", validPageSize);

            return Result.success(pageResult);
        } catch (Exception e) {
            log.error("Failed to query failed task list", e);
            return Result.error("获取失败任务列表失败: " + e.getMessage());
        }
    }

    /**
     * 查询单个失败任务详情
     */
    @GetMapping("/{taskId}")
    public Result<FailedTask> getFailedDetail(@PathVariable String taskId) {
        try {
            FailedTask task = failedTaskMapper.selectByTaskId(taskId);
            if (task == null) {
                return Result.error("失败任务不存在");
            }
            return Result.success(task);
        } catch (Exception e) {
            log.error("Failed to query failed task detail", e);
            return Result.error("获取失败任务详情失败: " + e.getMessage());
        }
    }

    /**
     * 恢复失败任务 (重置为 INIT 并移回活跃重试队列)
     */
    @PostMapping("/{taskId}/recover")
    public Result<Void> recoverFailedTask(@PathVariable String taskId) {
        try {
            log.info("Recovering failed task: taskId={}", taskId);
            FailedTask failedTask = failedTaskMapper.selectByTaskId(taskId);
            if (failedTask == null) {
                return Result.error("找不到该失败任务，无法恢复");
            }

            // 检查活跃队列中是否已存在
            RetryTask existing = retryTaskMapper.selectByTaskId(taskId);
            if (existing != null) {
                return Result.error("该任务已在活跃队列中，无需恢复");
            }

            // 插入活跃表
            RetryTask activeTask = new RetryTask();
            activeTask.setTaskId(failedTask.getTaskId());
            activeTask.setSceneType(failedTask.getSceneType());
            activeTask.setIdempotentKey(failedTask.getIdempotentKey());
            activeTask.setMethodClass(failedTask.getMethodClass());
            activeTask.setMethodName(failedTask.getMethodName());
            activeTask.setMethodParams(failedTask.getMethodParams());
            activeTask.setTaskStatus("INIT"); // 设回 INIT
            activeTask.setRetryCount(0);
            activeTask.setMaxRetryCount(failedTask.getRetryCount() + 1); // 额外给一次机会
            activeTask.setNextRetryTime(System.currentTimeMillis());
            activeTask.setCreateTime(failedTask.getCreateTime());

            retryTaskMapper.insert(activeTask);
            failedTaskMapper.deleteByTaskId(taskId);

            log.info("Failed task successfully recovered to active list: taskId={}", taskId);
            return Result.success();
        } catch (Exception e) {
            log.error("Failed to recover failed task", e);
            return Result.error("恢复任务失败: " + e.getMessage());
        }
    }

    /**
     * 删除失败任务
     */
    @DeleteMapping("/{taskId}")
    public Result<Void> deleteFailedTask(@PathVariable String taskId) {
        try {
            log.info("Deleting failed task: taskId={}", taskId);
            int rows = failedTaskMapper.deleteByTaskId(taskId);
            if (rows > 0) {
                return Result.success();
            } else {
                return Result.error("任务不存在或已被删除");
            }
        } catch (Exception e) {
            log.error("Failed to delete failed task", e);
            return Result.error("删除任务失败: " + e.getMessage());
        }
    }
}
