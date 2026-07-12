package com.retry.platform.server.controller;

import com.retry.platform.client.dto.Result;
import com.retry.platform.client.dto.RetryTaskDTO;
import com.retry.platform.client.dto.RetryTaskRequest;
import com.retry.platform.server.service.RetryTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 重试任务控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/retry")
public class RetryTaskController {
    
    @Autowired
    private RetryTaskService retryTaskService;
    
    /**
     * 提交重试任务
     * @param request 重试任务请求
     * @return 任务ID
     */
    @PostMapping("/submit")
    public Result<String> submitTask(@RequestBody RetryTaskRequest request) {
        try {
            // 参数校验在service层进行
            String taskId = retryTaskService.createTask(request);
            log.info("Task submitted successfully: taskId={}, sceneType={}, idempotentKey={}", 
                    taskId, request.getSceneType(), request.getIdempotentKey());
            return Result.success(taskId);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid request parameters: {}", e.getMessage());
            return Result.fail("Invalid parameters: " + e.getMessage());
        } catch (IllegalStateException e) {
            log.warn("Invalid state: {}", e.getMessage());
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("Failed to submit task", e);
            return Result.fail("Failed to submit task: " + e.getMessage());
        }
    }
    
    /**
     * 查询任务详情
     * @param taskId 任务ID
     * @return 任务详情
     */
    @GetMapping("/task/{taskId}")
    public Result<RetryTaskDTO> getTask(@PathVariable String taskId) {
        try {
            RetryTaskDTO task = retryTaskService.getTask(taskId);
            if (task == null) {
                return Result.fail("Task not found: " + taskId);
            }
            return Result.success(task);
        } catch (Exception e) {
            log.error("Failed to get task: taskId={}", taskId, e);
            return Result.fail("Failed to get task: " + e.getMessage());
        }
    }
    
    /**
     * 取消重试任务
     * @param taskId 任务ID
     * @return 是否成功
     */
    @PostMapping("/cancel/{taskId}")
    public Result<Boolean> cancelTask(@PathVariable String taskId) {
        try {
            RetryTaskDTO task = retryTaskService.getTask(taskId);
            if (task == null) {
                return Result.fail("Task not found: " + taskId);
            }
            
            // 只有INIT和WAIT状态的任务可以取消
            if ("SUCCESS".equals(task.getTaskStatus()) || "FAILED".equals(task.getTaskStatus())) {
                return Result.fail("Task cannot be cancelled in current status: " + task.getTaskStatus());
            }
            
            retryTaskService.updateTaskStatus(taskId, "FAILED");
            log.info("Task cancelled: taskId={}", taskId);
            return Result.success(true);
        } catch (Exception e) {
            log.error("Failed to cancel task: taskId={}", taskId, e);
            return Result.fail("Failed to cancel task: " + e.getMessage());
        }
    }

    /**
     * 标记任务执行成功（PRE_SUBMIT 预提交模式专用）
     * 客户端业务方法执行成功后调用，将任务状态更新为 SUCCESS 以停止后续重试调度
     *
     * @param taskId 任务ID
     * @return 是否成功
     */
    @PostMapping("/success/{taskId}")
    public Result<Boolean> markSuccess(@PathVariable String taskId) {
        try {
            RetryTaskDTO task = retryTaskService.getTask(taskId);
            if (task == null) {
                return Result.fail("Task not found: " + taskId);
            }
            if ("SUCCESS".equals(task.getTaskStatus())) {
                // 幂等处理：已经是SUCCESS状态，直接返回成功
                log.info("Task already marked SUCCESS (idempotent): taskId={}", taskId);
                return Result.success(true);
            }
            if ("FAILED".equals(task.getTaskStatus())) {
                return Result.fail("Cannot mark a FAILED task as SUCCESS: " + taskId);
            }
            retryTaskService.updateTaskStatus(taskId, "SUCCESS");
            log.info("Task marked as SUCCESS by client (PRE_SUBMIT mode): taskId={}", taskId);
            return Result.success(true);
        } catch (Exception e) {
            log.error("Failed to mark task success: taskId={}", taskId, e);
            return Result.fail("Failed to mark task success: " + e.getMessage());
        }
    }
}

