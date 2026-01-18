package com.retry.platform.server.scheduler;

import com.retry.platform.server.executor.RetryTaskExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 重试任务执行服务
 * 负责执行具体的重试任务逻辑
 */
@Slf4j
@Service
public class RetryTaskExecutorService {
    
    @Autowired
    private RetryTaskExecutor retryTaskExecutor;
    
    /**
     * 执行重试任务
     * 
     * @param taskId 任务ID
     */
    public void executeTask(String taskId) {
        try {
            log.info("Start executing retry task: taskId={}", taskId);
            
            // 委托给RetryTaskExecutor执行
            retryTaskExecutor.execute(taskId);
            
            log.info("Task execution completed: taskId={}", taskId);
            
        } catch (Exception e) {
            log.error("Task execution failed: taskId={}", taskId, e);
        }
    }
}
