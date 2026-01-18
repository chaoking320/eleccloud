package com.retry.platform.server.handler;

import com.retry.platform.server.exception.BusinessException;
import com.retry.platform.server.exception.ConfigException;
import com.retry.platform.server.exception.SystemException;
import com.retry.platform.server.service.AlertService;
import com.retry.platform.server.service.FailedTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 重试异常处理器
 * 负责分类处理业务异常、系统异常、配置异常
 */
@Component
public class RetryExceptionHandler {
    
    private static final Logger log = LoggerFactory.getLogger(RetryExceptionHandler.class);
    
    @Autowired
    private AlertService alertService;
    
    @Autowired
    private FailedTaskService failedTaskService;
    
    /**
     * 处理任务执行异常
     * 
     * @param taskId 任务ID
     * @param e 异常
     * @return true表示可以继续重试，false表示应该停止重试
     */
    public boolean handleExecutionException(String taskId, Throwable e) {
        if (e instanceof BusinessException) {
            return handleBusinessException(taskId, (BusinessException) e);
        } else if (e instanceof SystemException) {
            return handleSystemException(taskId, (SystemException) e);
        } else if (e instanceof ConfigException) {
            return handleConfigException(taskId, (ConfigException) e);
        } else {
            // 未知异常，按业务异常处理
            return handleUnknownException(taskId, e);
        }
    }
    
    /**
     * 处理业务异常
     * 业务异常记录日志，继续重试流程
     */
    private boolean handleBusinessException(String taskId, BusinessException e) {
        log.warn("Task {} execution failed with business exception: [{}] {}", 
            taskId, e.getErrorCode(), e.getMessage());
        
        // 业务异常可以继续重试
        return true;
    }
    
    /**
     * 处理系统异常
     * 系统异常发送告警，但仍可继续重试
     */
    private boolean handleSystemException(String taskId, SystemException e) {
        log.error("Task {} execution failed with system exception: [{}] {}", 
            taskId, e.getErrorCode(), e.getMessage(), e);
        
        // 发送告警
        String alertMessage = String.format(
            "系统异常 - 任务ID: %s, 错误码: %s, 错误信息: %s",
            taskId, e.getErrorCode(), e.getMessage()
        );
        alertService.sendAlert("系统异常告警", alertMessage);
        
        // 系统异常可以继续重试（可能是临时故障）
        return true;
    }
    
    /**
     * 处理配置异常
     * 配置异常标记任务失败，不再重试
     */
    private boolean handleConfigException(String taskId, ConfigException e) {
        log.error("Task {} execution failed with config exception: [{}] {}", 
            taskId, e.getErrorCode(), e.getMessage(), e);
        
        // 配置异常不应该继续重试，直接标记为失败
        try {
            // TODO: 2026/1/17  标记失败
//            failedTaskService.markTaskAsFailed(taskId,
//                String.format("配置异常: [%s] %s", e.getErrorCode(), e.getMessage()));
        } catch (Exception ex) {
            log.error("Failed to mark task {} as failed", taskId, ex);
        }
        
        // 发送告警
        String alertMessage = String.format(
            "配置异常 - 任务ID: %s, 错误码: %s, 错误信息: %s",
            taskId, e.getErrorCode(), e.getMessage()
        );
        alertService.sendAlert("配置异常告警", alertMessage);
        
        // 配置异常不应继续重试
        return false;
    }
    
    /**
     * 处理未知异常
     * 未知异常按业务异常处理，记录日志并继续重试
     */
    private boolean handleUnknownException(String taskId, Throwable e) {
        log.warn("Task {} execution failed with unknown exception: {}", 
            taskId, e.getMessage(), e);
        
        // 未知异常可以继续重试
        return true;
    }
    
    /**
     * 处理钩子方法异常
     * 钩子方法异常记录详细日志，继续重试流程
     * 
     * @param taskId 任务ID
     * @param hookMethod 钩子方法名（checkStatus/doQuery/doCallback）
     * @param e 异常
     */
    public void handleHookException(String taskId, String hookMethod, Throwable e) {
        log.error("Task {} hook method [{}] execution failed: {}", 
            taskId, hookMethod, e.getMessage(), e);
        
        // 如果是系统异常，发送告警
        if (e instanceof SystemException) {
            String alertMessage = String.format(
                "钩子方法异常 - 任务ID: %s, 方法: %s, 错误信息: %s",
                taskId, hookMethod, e.getMessage()
            );
            alertService.sendAlert("钩子方法异常告警", alertMessage);
        }
    }
    
    /**
     * 处理反射调用异常
     * 
     * @param taskId 任务ID
     * @param methodName 方法名
     * @param e 异常
     */
    public void handleReflectionException(String taskId, String methodName, Throwable e) {
        log.error("Task {} reflection invocation failed for method [{}]: {}", 
            taskId, methodName, e.getMessage(), e);
        
        // 反射调用失败可能是配置问题，记录详细信息
        if (e instanceof ClassNotFoundException || e instanceof NoSuchMethodException) {
            String alertMessage = String.format(
                "反射调用失败 - 任务ID: %s, 方法: %s, 错误: %s",
                taskId, methodName, e.getMessage()
            );
            alertService.sendAlert("反射调用失败告警", alertMessage);
        }
    }
}
