package com.retry.platform.server.service.impl;

import com.retry.platform.server.entity.FailedTask;
import com.retry.platform.server.entity.RetryTask;
import com.retry.platform.server.mapper.FailedTaskMapper;
import com.retry.platform.server.service.FailedTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 失败任务服务实现类
 */
@Slf4j
@Service
public class FailedTaskServiceImpl implements FailedTaskService {
    
    @Autowired
    private FailedTaskMapper failedTaskMapper;
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void moveToFailedTask(RetryTask retryTask, String failReason) {
        // 构建失败任务实体
        FailedTask failedTask = new FailedTask();
        failedTask.setTaskId(retryTask.getTaskId());
        failedTask.setSceneType(retryTask.getSceneType());
        failedTask.setIdempotentKey(retryTask.getIdempotentKey());
        failedTask.setMethodClass(retryTask.getMethodClass());
        failedTask.setMethodName(retryTask.getMethodName());
        failedTask.setMethodParams(retryTask.getMethodParams());
        failedTask.setRetryCount(retryTask.getRetryCount());
        failedTask.setFailReason(failReason);
        failedTask.setCreateTime(retryTask.getCreateTime());
        failedTask.setFailTime(LocalDateTime.now());
        
        // 插入失败任务表
        failedTaskMapper.insert(failedTask);
        
        log.info("Moved task to failed_task table: taskId={}, retryCount={}, failReason={}", 
                retryTask.getTaskId(), retryTask.getRetryCount(), failReason);
    }
}
