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

    /**
     * 尝试将任务标记为 EXECUTING 状态 (原子 CAS 锁定)
     * <p>
     * 原先实现：先查询状态，再 update（Check-Then-Act）—— 多节点并发下会出现同一任务被执行两次的竞态。
     * 修復后：单条 SQL WHERE task_id=? AND task_status='INIT'，通过 affected rows 判断抢占结果。
     */
    @PostMapping("/executing/{taskId}")
    public Result<Boolean> markExecuting(@PathVariable String taskId) {
        try {
            // 原子 CAS：UPDATE ... WHERE task_id=? AND task_status='INIT'
            // 多节点并发时，数据库行锁保证只有一个节点 affected rows=1，其余返回 0
            int affected = retryTaskService.casMarkExecuting(taskId);
            boolean acquired = (affected > 0);
            if (acquired) {
                log.info("Task CAS locked to EXECUTING: taskId={}", taskId);
            } else {
                log.info("Task CAS lock failed (already executing or non-INIT): taskId={}", taskId);
            }
            return Result.success(acquired);
        } catch (Exception e) {
            log.error("Failed to CAS lock task: {}", taskId, e);
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 更新状态接口
     */
    @PostMapping("/status")
    public Result<Void> updateStatus(@RequestParam String taskId, @RequestParam String status) {
        try {
            retryTaskService.updateTaskStatus(taskId, status);
            return Result.success(null);
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 更新重试信息接口
     */
    @PostMapping("/retry-info")
    public Result<Void> updateRetryInfo(@RequestParam String taskId, @RequestParam int retryCount, @RequestParam String status) {
        try {
            retryTaskService.updateTaskStatusAndRetryInfo(taskId, status, retryCount, System.currentTimeMillis());
            return Result.success(null);
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 执行异常时，将状态回退到 INIT 并记录历史
     */
    @PostMapping("/rollback")
    public Result<Void> rollbackToPending(@RequestParam String taskId, @RequestParam String errorMsg) {
        try {
            RetryTaskDTO task = retryTaskService.getTask(taskId);
            if (task != null) {
                retryTaskService.updateTaskStatus(taskId, "INIT");
                retryTaskService.recordHistory(taskId, task.getRetryCount(), "FAILURE", errorMsg, 0);
            }
            return Result.success(null);
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 标记最终失败
     */
    @PostMapping("/failed")
    public Result<Void> markFailed(@RequestParam String taskId, @RequestParam String reason) {
        try {
            retryTaskService.updateTaskStatus(taskId, "FAILED");
            retryTaskService.recordHistory(taskId, 0, "FAILED", reason, 0);
            return Result.success(null);
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    @Autowired(required = false)
    private com.retry.platform.client.mq.RetryMessageProducer retryMessageProducer;

    /**
     * 人工在 Admin 后台手动触发重试任务
     * 发送 delay=0 的即时延时消息到 MQ
     */
    @PostMapping("/trigger/{taskId}")
    public Result<Boolean> triggerTask(@PathVariable String taskId) {
        try {
            RetryTaskDTO task = retryTaskService.getTask(taskId);
            if (task == null) {
                return Result.fail("Task not found");
            }
            
            // 重置为 INIT 状态，使其能够被抢占
            retryTaskService.updateTaskStatus(taskId, "INIT");
            
            if (retryMessageProducer != null) {
                // 向 MQ 发送即时投递消息，直接唤醒 SDK Consumer
                retryMessageProducer.sendDelayMessage(taskId, 0L, task.getSceneType());
                log.info("Manually triggered task by sending delay=0 message to MQ. taskId={}", taskId);
                return Result.success(true);
            } else {
                return Result.fail("RetryMessageProducer not configured on server side");
            }
        } catch (Exception e) {
            log.error("Failed to trigger task manually. taskId={}", taskId, e);
            return Result.fail(e.getMessage());
        }
    }

    /**
     * SDK 端记录每次重试执行历史（供 Admin 后台展示执行明细）
     *
     * @param taskId        任务ID
     * @param retryCount    本次是第几次重试
     * @param executeResult 执行结果：SUCCESS / FAILED
     * @param errorMessage  错误信息（可为空）
     * @param costTimeMs    执行耗时（毫秒）
     */
    @PostMapping("/history")
    public Result<Void> recordHistory(
            @RequestParam String taskId,
            @RequestParam int retryCount,
            @RequestParam String executeResult,
            @RequestParam(required = false, defaultValue = "") String errorMessage,
            @RequestParam(defaultValue = "0") long costTimeMs) {
        try {
            retryTaskService.recordHistory(taskId, retryCount, executeResult,
                    errorMessage.isEmpty() ? null : errorMessage, (int) Math.min(costTimeMs, Integer.MAX_VALUE));
            return Result.success(null);
        } catch (Exception e) {
            log.error("Failed to record history: taskId={}", taskId, e);
            return Result.fail(e.getMessage());
        }
    }
}
