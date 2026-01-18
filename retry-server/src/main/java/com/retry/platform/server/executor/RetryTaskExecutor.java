package com.retry.platform.server.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retry.platform.server.entity.RetryTask;
import com.retry.platform.server.entity.SceneConfig;
import com.retry.platform.server.hook.QueryResult;
import com.retry.platform.server.hook.RetryContext;
import com.retry.platform.server.hook.RetryHook;
import com.retry.platform.server.mapper.RetryTaskMapper;
import com.retry.platform.server.service.*;
import com.retry.platform.server.util.ReflectionInvoker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 重试任务执行器
 * 负责执行重试任务的核心逻辑
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
    
    @Autowired
    private ReflectionInvoker reflectionInvoker;
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 执行重试任务
     * 
     * @param taskId 任务ID
     */
    public void execute(String taskId) {
        long startTime = System.currentTimeMillis();
        String executeResult = "FAILURE";
        String errorMessage = null;
        
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
            SceneConfig sceneConfig = sceneConfigService.getSceneConfigByType(task.getSceneType());
            if (sceneConfig == null) {
                errorMessage = "Scene config not found: " + task.getSceneType();
                log.error(errorMessage);
                handleTaskFailure(task, errorMessage);
                return;
            }
            
            // 3. 获取钩子实现
            RetryHook hook = getHook(sceneConfig.getHookClass());
            
            // 4. 构建上下文
            RetryContext context = buildContext(task);
            
            // 5. 检查状态并执行相应逻辑
            String status = checkStatusSafely(hook, context);
            
            switch (status) {
                case "SUCCESS":
                    executeResult = handleSuccess(task);
                    break;
                case "WAIT":
                    executeResult = handleWait(task, hook, context, sceneConfig);
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
                    handleExecutionFailure(task, sceneConfigService.getSceneConfigByType(task.getSceneType()), errorMessage);
                }
            } catch (Exception ex) {
                log.error("Failed to handle execution failure: taskId={}", taskId, ex);
            }
        } finally {
            // 记录执行历史
            int costTime = (int) (System.currentTimeMillis() - startTime);
            try {
                RetryTask task = retryTaskMapper.selectByTaskId(taskId);
                if (task != null) {
                    retryTaskService.recordHistory(taskId, task.getRetryCount(), executeResult, errorMessage, costTime);
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
    private String handleWait(RetryTask task, RetryHook hook, RetryContext context, SceneConfig sceneConfig) {
        log.info("Task in WAIT status, querying remote service: taskId={}", task.getTaskId());
        
        try {
            // 调用doQuery查询远程服务
            QueryResult queryResult = hook.doQuery(context);
            
            if (queryResult != null && queryResult.isSuccess()) {
                log.info("Query succeeded, executing callback: taskId={}", task.getTaskId());
                
                // 调用doCallback执行回调
                hook.doCallback(context, queryResult);
                
                // 更新任务状态为SUCCESS
                retryTaskService.updateTaskStatus(task.getTaskId(), "SUCCESS");
                
                // 从延时队列中移除
                delayQueueService.removeTask(task.getTaskId());
                
                return "SUCCESS";
            } else {
                log.info("Query failed, scheduling next retry: taskId={}", task.getTaskId());
                
                // 查询失败，安排下次重试
                scheduleNextRetry(task, sceneConfig);
                
                return "RETRY_SCHEDULED";
            }
        } catch (Exception e) {
            log.error("Error during WAIT handling: taskId={}", task.getTaskId(), e);
            
            // 异常情况，安排下次重试
            scheduleNextRetry(task, sceneConfig);
            
            return "RETRY_SCHEDULED";
        }
    }
    
    /**
     * 处理INIT状态
     */
    private String handleInit(RetryTask task, RetryContext context, SceneConfig sceneConfig) {
        log.info("Task in INIT status, invoking original method: taskId={}", task.getTaskId());
        
        try {
            // 通过反射调用原始方法
            Object result = reflectionInvoker.invoke(
                    task.getMethodClass(),
                    task.getMethodName(),
                    task.getMethodParams(),
                    task.getIdempotentKey()
            );
            
            log.info("Method invoked successfully: taskId={}, result={}", task.getTaskId(), result);
            
            // 方法调用成功，更新状态为WAIT
            retryTaskService.updateTaskStatus(task.getTaskId(), "WAIT");
            
            // 安排下次重试（用于后续状态检查）
            scheduleNextRetry(task, sceneConfig);
            
            return "METHOD_INVOKED";
            
        } catch (Exception e) {
            log.error("Method invocation failed: taskId={}", task.getTaskId(), e);
            
            // 方法调用失败，安排下次重试
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
     * 获取钩子实现
     */
    private RetryHook getHook(String hookClass) {
        if (hookClass == null || hookClass.trim().isEmpty()) {
            log.debug("No hook class configured, using default hook");
            return new DefaultRetryHook();
        }
        
        try {
            // 尝试从Spring容器中获取Bean
            Class<?> clazz = Class.forName(hookClass);
            return (RetryHook) applicationContext.getBean(clazz);
        } catch (Exception e) {
            log.warn("Failed to load hook class: {}, using default hook", hookClass, e);
            return new DefaultRetryHook();
        }
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
     * 安全地检查状态（捕获异常）
     */
    private String checkStatusSafely(RetryHook hook, RetryContext context) {
        try {
            if (hook == null) {
                return "INIT";
            }
            String status = hook.checkStatus(context);
            return status != null ? status : "INIT";
        } catch (Exception e) {
            log.error("Error checking status: taskId={}", context.getTaskId(), e);
            return "INIT";
        }
    }
    
    /**
     * 默认钩子实现
     * 当没有配置钩子类时使用
     */
    private static class DefaultRetryHook implements RetryHook {
        
        @Override
        public String checkStatus(RetryContext context) {
            // 默认返回INIT，表示需要重新执行
            return "INIT";
        }
        
        @Override
        public QueryResult doQuery(RetryContext context) {
            // 默认返回失败
            return QueryResult.failure("No hook implementation");
        }
        
        @Override
        public void doCallback(RetryContext context, QueryResult result) {
            // 默认不执行任何操作
        }
    }
}
