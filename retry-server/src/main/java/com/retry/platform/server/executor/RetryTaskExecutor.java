package com.retry.platform.server.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retry.platform.server.entity.RetryTask;
import com.retry.platform.server.entity.SceneConfig;
import com.retry.platform.client.hook.QueryResult;
import com.retry.platform.client.hook.RetryContext;
import com.retry.platform.client.hook.RetryHook;
import com.retry.platform.client.dto.Result;
import com.retry.platform.server.mapper.RetryTaskMapper;
import com.retry.platform.server.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 重试任务执行器
 * 负责执行重试任务的核心逻辑 (基于远程 HTTP 回调架构)
 */
@Slf4j
@Component
public class RetryTaskExecutor {
    
    @Autowired
    private ApplicationContext applicationContext;
    
    @Autowired
    private RetryTaskMapper retryTaskMapper;
    
    @Autowired
    private SceneConfigService sceneConfigService;
    
    @Autowired
    private RetryTaskService retryTaskService;
    
    @Autowired
    private RetryControlService retryControlService;
    
    @Autowired
    private FailedTaskService failedTaskService;
    
    @Autowired
    private DelayQueueService delayQueueService;

    @Autowired(required = false)
    private com.retry.platform.server.metrics.RetryMetrics retryMetrics;
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private static final RestTemplate restTemplate;
    static {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000); // 5s 连接超时
        factory.setReadTimeout(10000);   // 10s 读取超时
        restTemplate = new RestTemplate(factory);
    }
    
    /**
     * 获取业务客户端地址
     */
    private String getClientAppUrl(SceneConfig sceneConfig) {
        if (sceneConfig == null || sceneConfig.getClientAppUrl() == null || sceneConfig.getClientAppUrl().trim().isEmpty()) {
            return "http://localhost:8082"; // 默认本地开发端口
        }
        return sceneConfig.getClientAppUrl();
    }
    
    /**
     * 执行重试任务
     * 
     * @param taskId 任务ID
     */
    public void execute(String taskId) {
        long startTime = System.currentTimeMillis();
        String executeResult = "FAILURE";
        String errorMessage = null;
        SceneConfig sceneConfig = null;
        
        try {
            // 1. 加载任务详情
            RetryTask task = retryTaskMapper.selectByTaskId(taskId);
            if (task == null) {
                log.warn("Task not found: taskId={}", taskId);
                return;
            }
            
            log.info("Executing retry task: taskId={}, sceneType={}, retryCount={}", 
                    taskId, task.getSceneType(), task.getRetryCount());
            
            // 2. 获取场景配置
            sceneConfig = sceneConfigService.getSceneConfigByType(task.getSceneType());
            if (sceneConfig == null) {
                errorMessage = "Scene config not found: " + task.getSceneType();
                log.error(errorMessage);
                handleTaskFailure(task, errorMessage);
                return;
            }
            
            // 3. 构建上下文
            RetryContext context = buildContext(task);
            
            // 4. 检查状态并执行相应逻辑 (通过远程客户端 SDK 触发)
            String status = checkStatusSafely(sceneConfig, context);
            
            switch (status) {
                case "SUCCESS":
                    executeResult = handleSuccess(task);
                    break;
                case "WAIT":
                    executeResult = handleWait(task, context, sceneConfig);
                    break;
                case "INIT":
                default:
                    executeResult = handleInit(task, context, sceneConfig);
                    break;
            }
            
        } catch (Exception e) {
            log.error("Task execution failed: taskId={}", taskId, e);
            errorMessage = e.getMessage();
            executeResult = "FAILURE";
            
            // 尝试加载任务并处理失败
            try {
                RetryTask task = retryTaskMapper.selectByTaskId(taskId);
                if (task != null) {
                    handleExecutionFailure(task, sceneConfig, errorMessage);
                }
            } catch (Exception ex) {
                log.error("Failed to handle execution failure: taskId={}", taskId, ex);
            }
        } finally {
            // 记录执行历史
            long cost = System.currentTimeMillis() - startTime;
            int costTime = (int) cost;
            try {
                RetryTask task = retryTaskMapper.selectByTaskId(taskId);
                if (task != null) {
                    retryTaskService.recordHistory(taskId, task.getRetryCount(), executeResult, errorMessage, costTime);
                    try {
                        if (retryMetrics != null) {
                            retryMetrics.recordExecutionTime(task.getSceneType(), cost);
                            retryMetrics.recordTaskExecuted(task.getSceneType(), executeResult);
                        }
                    } catch (Exception me) {
                        log.warn("Failed to record execution metrics in executor", me);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to record history: taskId={}", taskId, e);
            }
        }
    }
    
    /**
     * 处理SUCCESS状态
     */
    private String handleSuccess(RetryTask task) {
        log.info("Task already succeeded: taskId={}", task.getTaskId());
        
        // 更新任务状态为SUCCESS
        retryTaskService.updateTaskStatus(task.getTaskId(), "SUCCESS");
        
        // 从延时队列中移除
        delayQueueService.removeTask(task.getTaskId());
        
        return "SUCCESS";
    }
    
    /**
     * 处理WAIT状态
     */
    private String handleWait(RetryTask task, RetryContext context, SceneConfig sceneConfig) {
        log.info("Task in WAIT status, querying remote service: taskId={}", task.getTaskId());
        
        try {
            String clientUrl = getClientAppUrl(sceneConfig);
            
            // 1. 调用远程 doQuery
            String queryUrl = clientUrl + "/api/retry/callback/query?hookClass=" + sceneConfig.getHookClass();
            log.info("Calling remote query: url={}", queryUrl);
            
            Result<?> result = restTemplate.postForObject(queryUrl, context, Result.class);
            if (result != null && result.getSuccess() && result.getData() != null) {
                QueryResult queryResult = objectMapper.convertValue(result.getData(), QueryResult.class);
                
                if (queryResult.isSuccess()) {
                    log.info("Query succeeded, executing remote callback: taskId={}", task.getTaskId());
                    
                    // 2. 调用远程 doCallback
                    String callbackUrl = clientUrl + "/api/retry/callback/do-callback";
                    log.info("Calling remote do-callback: url={}", callbackUrl);
                    
                    // 构造回调请求
                    Map<String, Object> callbackReq = new HashMap<>();
                    callbackReq.put("context", context);
                    callbackReq.put("queryResult", queryResult);
                    callbackReq.put("hookClass", sceneConfig.getHookClass());
                    
                    Result<?> callbackResult = restTemplate.postForObject(callbackUrl, callbackReq, Result.class);
                    if (callbackResult != null && callbackResult.getSuccess()) {
                        log.info("Remote callback executed successfully: taskId={}", task.getTaskId());
                        
                        // 更新任务状态为SUCCESS
                        retryTaskService.updateTaskStatus(task.getTaskId(), "SUCCESS");
                        
                        // 从延时队列中移除
                        delayQueueService.removeTask(task.getTaskId());
                        
                        return "SUCCESS";
                    } else {
                        String msg = callbackResult != null ? callbackResult.getMessage() : "No response";
                        log.error("Remote callback failed: taskId={}, message={}", task.getTaskId(), msg);
                        scheduleNextRetry(task, sceneConfig);
                        return "RETRY_SCHEDULED";
                    }
                } else {
                    log.info("Query returned not successful, scheduling next retry: taskId={}", task.getTaskId());
                    scheduleNextRetry(task, sceneConfig);
                    return "RETRY_SCHEDULED";
                }
            } else {
                String msg = result != null ? result.getMessage() : "No response";
                log.error("Remote query failed: taskId={}, message={}", task.getTaskId(), msg);
                scheduleNextRetry(task, sceneConfig);
                return "RETRY_SCHEDULED";
            }
        } catch (Exception e) {
            log.error("Error during remote WAIT handling: taskId={}", task.getTaskId(), e);
            scheduleNextRetry(task, sceneConfig);
            return "RETRY_SCHEDULED";
        }
    }
    
    /**
     * 处理INIT状态
     */
    private String handleInit(RetryTask task, RetryContext context, SceneConfig sceneConfig) {
        log.info("Task in INIT status, invoking remote method: taskId={}", task.getTaskId());
        
        try {
            String clientUrl = getClientAppUrl(sceneConfig);
            String executeUrl = clientUrl + "/api/retry/callback/execute-method";
            log.info("Calling remote execute-method: url={}", executeUrl);
            
            Result<?> result = restTemplate.postForObject(executeUrl, context, Result.class);
            if (result != null && result.getSuccess()) {
                log.info("Remote method executed successfully: taskId={}, result={}", task.getTaskId(), result.getData());
                
                // 方法调用成功，更新状态为WAIT
                retryTaskService.updateTaskStatus(task.getTaskId(), "WAIT");
                
                // 安排下次重试（用于后续状态检查和查询）
                scheduleNextRetry(task, sceneConfig);
                
                return "METHOD_INVOKED";
            } else {
                String msg = result != null ? result.getMessage() : "No response";
                log.error("Remote method invocation failed: taskId={}, message={}", task.getTaskId(), msg);
                scheduleNextRetry(task, sceneConfig);
                return "RETRY_SCHEDULED";
            }
        } catch (Exception e) {
            log.error("Remote method invocation exception: taskId={}", task.getTaskId(), e);
            scheduleNextRetry(task, sceneConfig);
            return "RETRY_SCHEDULED";
        }
    }
    
    /**
     * 安排下次重试
     */
    private void scheduleNextRetry(RetryTask task, SceneConfig sceneConfig) {
        // 递增重试次数
        int newRetryCount = retryControlService.incrementRetryCount(task);
        
        // 检查是否应该继续重试
        if (!retryControlService.shouldContinueRetry(task)) {
            log.warn("Task reached max retry count, marking as FAILED: taskId={}, retryCount={}", 
                    task.getTaskId(), newRetryCount);
            
            handleTaskFailure(task, "Exceeded max retry count: " + task.getMaxRetryCount());
            return;
        }
        
        // 计算下次重试时间
        Long nextRetryTime = retryControlService.calculateNextRetryTime(sceneConfig, newRetryCount);
        
        if (nextRetryTime == null) {
            log.warn("Cannot calculate next retry time, marking as FAILED: taskId={}", task.getTaskId());
            handleTaskFailure(task, "Cannot calculate next retry time");
            return;
        }
        
        // 更新任务的重试信息
        retryTaskService.updateTaskStatusAndRetryInfo(
                task.getTaskId(),
                task.getTaskStatus(), // 保持当前状态
                newRetryCount,
                nextRetryTime
        );
        
        // 更新延时队列中的执行时间
        delayQueueService.updateTaskTime(task.getTaskId(), nextRetryTime);
        
        log.info("Scheduled next retry: taskId={}, retryCount={}, nextRetryTime={}", 
                task.getTaskId(), newRetryCount, nextRetryTime);
    }
    
    /**
     * 处理任务失败
     */
    private void handleTaskFailure(RetryTask task, String failReason) {
        // 更新任务状态为FAILED
        retryTaskService.updateTaskStatus(task.getTaskId(), "FAILED");
        
        // 从延时队列中移除
        delayQueueService.removeTask(task.getTaskId());
        
        // 移动到失败任务表
        failedTaskService.moveToFailedTask(task, failReason);
        
        log.error("Task marked as FAILED: taskId={}, failReason={}", task.getTaskId(), failReason);
    }
    
    /**
     * 处理执行失败
     */
    private void handleExecutionFailure(RetryTask task, SceneConfig sceneConfig, String errorMessage) {
        if (sceneConfig == null) {
            handleTaskFailure(task, errorMessage);
            return;
        }
        
        // 尝试安排下次重试
        scheduleNextRetry(task, sceneConfig);
    }
    
    /**
     * 构建重试上下文
     */
    @SuppressWarnings("unchecked")
    private RetryContext buildContext(RetryTask task) {
        Map<String, Object> paramsMap = null;
        
        try {
            if (task.getMethodParams() != null && !task.getMethodParams().trim().isEmpty()) {
                paramsMap = objectMapper.readValue(task.getMethodParams(), Map.class);
            }
        } catch (Exception e) {
            log.error("Failed to parse method params: taskId={}", task.getTaskId(), e);
        }
        
        return RetryContext.builder()
                .taskId(task.getTaskId())
                .sceneType(task.getSceneType())
                .idempotentKey(task.getIdempotentKey())
                .params(paramsMap)
                .retryCount(task.getRetryCount())
                .maxRetryCount(task.getMaxRetryCount())
                .methodClass(task.getMethodClass())
                .methodName(task.getMethodName())
                .methodParamsJson(task.getMethodParams())
                .build();
    }
    
    /**
     * 安全地检查状态（捕获异常，通过远程客户端）
     */
    private String checkStatusSafely(SceneConfig sceneConfig, RetryContext context) {
        if (sceneConfig.getHookClass() == null || sceneConfig.getHookClass().trim().isEmpty()) {
            return "INIT";
        }
        try {
            String clientUrl = getClientAppUrl(sceneConfig);
            String url = clientUrl + "/api/retry/callback/check-status?hookClass=" + sceneConfig.getHookClass();
            log.info("Calling remote check-status: url={}", url);
            
            Result<?> result = restTemplate.postForObject(url, context, Result.class);
            if (result != null && result.getSuccess()) {
                String status = (String) result.getData();
                return status != null ? status : "INIT";
            } else {
                String msg = result != null ? result.getMessage() : "No response";
                log.error("Remote check-status failed: {}", msg);
                return "INIT";
            }
        } catch (Exception e) {
            log.error("Error checking status: taskId={}, error={}", context.getTaskId(), e.getMessage());
            return "INIT";
        }
    }
}
