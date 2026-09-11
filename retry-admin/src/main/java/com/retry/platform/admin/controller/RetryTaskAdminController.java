package com.retry.platform.admin.controller;

import com.retry.platform.server.entity.RetryHistory;
import com.retry.platform.server.entity.RetryTask;
import com.retry.platform.server.mapper.FailedTaskMapper;
import com.retry.platform.server.mapper.RetryTaskMapper;
import com.retry.platform.server.service.RetryTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务管理核心控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/task")
@CrossOrigin
public class RetryTaskAdminController {

    @Value("${retry.server.url:http://localhost:8080}")
    private String retryServerUrl;

    @Autowired
    private RetryTaskMapper retryTaskMapper;

    @Autowired
    private FailedTaskMapper failedTaskMapper;

    @Autowired
    private RetryTaskService retryTaskService;

    /**
     * 分页条件查询当前重试任务列表
     */
    @GetMapping("/list")
    public Result<Map<String, Object>> getTaskList(
            @RequestParam(required = false) Integer sceneType,
            @RequestParam(required = false) String idempotentKey,
            @RequestParam(required = false) String taskStatus,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        try {
            int offset = (pageNum - 1) * pageSize;
            List<RetryTask> list = retryTaskMapper.selectByConditions(sceneType, idempotentKey, taskStatus, offset, pageSize);
            long total = retryTaskMapper.countByConditions(sceneType, idempotentKey, taskStatus);

            Map<String, Object> pageResult = new HashMap<>();
            pageResult.put("list", list);
            pageResult.put("total", total);
            pageResult.put("pageNum", pageNum);
            pageResult.put("pageSize", pageSize);

            return Result.success(pageResult);
        } catch (Exception e) {
            log.error("Failed to query task list", e);
            return Result.error("获取任务列表失败: " + e.getMessage());
        }
    }

    /**
     * 查询单个任务详情
     */
    @GetMapping("/{taskId}")
    public Result<RetryTask> getTaskDetail(@PathVariable String taskId) {
        try {
            RetryTask task = retryTaskMapper.selectByTaskId(taskId);
            if (task == null) {
                return Result.error("任务不存在");
            }
            return Result.success(task);
        } catch (Exception e) {
            log.error("Failed to query task detail", e);
            return Result.error("获取任务详情失败: " + e.getMessage());
        }
    }

    /**
     * 查询任务的重试执行历史
     */
    @GetMapping("/{taskId}/history")
    public Result<List<RetryHistory>> getTaskHistory(@PathVariable String taskId) {
        try {
            List<RetryHistory> historyList = retryTaskService.getTaskHistory(taskId);
            return Result.success(historyList);
        } catch (Exception e) {
            log.error("Failed to query task history", e);
            return Result.error("获取任务执行历史失败: " + e.getMessage());
        }
    }

    /**
     * 查询仪表盘/统计数据
     */
    @GetMapping("/stats")
    public Result<Map<String, Long>> getDashboardStats() {
        try {
            Map<String, Long> stats = new HashMap<>();
            
            // 统计各状态任务数（包含活跃重试任务与已归档失败任务）
            long countInit = retryTaskMapper.countTasks(null, "INIT");
            long countWait = retryTaskMapper.countTasks(null, "WAIT");
            long countSuccess = retryTaskMapper.countTasks(null, "SUCCESS");
            long countFailedActive = retryTaskMapper.countTasks(null, "FAILED");
            long countFailedArchived = failedTaskMapper.countByConditions(null, null, null, null);
            long countFailedTotal = countFailedActive + countFailedArchived;
            long countTotal = countInit + countWait + countSuccess + countFailedTotal;

            stats.put("init", countInit);
            stats.put("wait", countWait);
            stats.put("success", countSuccess);
            stats.put("failed", countFailedTotal);
            stats.put("total", countTotal);
            
            return Result.success(stats);
        } catch (Exception e) {
            log.error("Failed to fetch dashboard stats", e);
            return Result.error("获取统计数据失败: " + e.getMessage());
        }
    }

    @Autowired
    private org.springframework.web.client.RestTemplate restTemplate;

    /**
     * 手动触发执行任务
     */
    @PostMapping("/{taskId}/retry")
    public Result<Void> triggerManualRetry(@PathVariable String taskId) {
        try {
            log.info("Triggering manual retry for taskId={}", taskId);
            
            RetryTask activeTask = retryTaskMapper.selectByTaskId(taskId);
            if (activeTask == null) {
                com.retry.platform.server.entity.FailedTask failedTask = failedTaskMapper.selectByTaskId(taskId);
                if (failedTask == null) {
                    return Result.error("找不到该任务，无法手动触发");
                }
                
                activeTask = new RetryTask();
                activeTask.setTaskId(failedTask.getTaskId());
                activeTask.setSceneType(failedTask.getSceneType());
                activeTask.setIdempotentKey(failedTask.getIdempotentKey());
                activeTask.setMethodClass(failedTask.getMethodClass());
                activeTask.setMethodName(failedTask.getMethodName());
                activeTask.setMethodParams(failedTask.getMethodParams());
                activeTask.setTaskStatus("INIT"); 
                activeTask.setRetryCount(0);
                activeTask.setMaxRetryCount(failedTask.getRetryCount() + 1); 
                activeTask.setNextRetryTime(System.currentTimeMillis());
                
                retryTaskMapper.insert(activeTask);
                failedTaskMapper.deleteByTaskId(taskId);
                log.info("Successfully restored failed task to active retry queue: taskId={}", taskId);
            }
            
            // 向 retry-server 投递 /trigger 触发请求，间接向 MQ 发送 delay=0 消息
            String triggerUrl = retryServerUrl + "/api/retry/trigger/" + taskId;
            restTemplate.postForObject(triggerUrl, null, com.retry.platform.client.dto.Result.class);
            
            return Result.success();
        } catch (Exception e) {
            log.error("Failed to execute manual retry", e);
            return Result.error("手动执行失败: " + e.getMessage());
        }
    }
}
