package com.retry.platform.server.service.impl;

import com.retry.platform.client.dto.RetryTaskDTO;
import com.retry.platform.client.dto.RetryTaskRequest;
import com.retry.platform.server.entity.RetryHistory;
import com.retry.platform.server.entity.RetryTask;
import com.retry.platform.server.entity.SceneConfig;
import com.retry.platform.server.mapper.RetryHistoryMapper;
import com.retry.platform.server.mapper.RetryTaskMapper;
import com.retry.platform.server.service.DelayQueueService;
import com.retry.platform.server.service.RetryTaskService;
import com.retry.platform.server.service.SceneConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 重试任务服务实现类
 */
@Slf4j
@Service
public class RetryTaskServiceImpl implements RetryTaskService {
    
    @Autowired
    private RetryTaskMapper retryTaskMapper;
    
    @Autowired
    private RetryHistoryMapper retryHistoryMapper;
    
    @Autowired
    private SceneConfigService sceneConfigService;
    
    @Autowired
    private DelayQueueService delayQueueService;
    
    @Autowired(required = false)
    private com.retry.platform.server.metrics.RetryMetrics retryMetrics;
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createTask(RetryTaskRequest request) {
        // 1. 参数校验
        validateRequest(request);
        
        // 2. 获取场景配置
        SceneConfig sceneConfig = sceneConfigService.getSceneConfigByType(request.getSceneType());
        if (sceneConfig == null) {
            throw new IllegalArgumentException("Scene config not found for sceneType: " + request.getSceneType());
        }
        
        if (!sceneConfig.isEnabled()) {
            throw new IllegalStateException("Scene is disabled: " + request.getSceneType());
        }
        
        // 3. 幂等性校验：检查是否已存在相同场景类型+幂等键的任务
        RetryTask existingTask = retryTaskMapper.selectBySceneAndIdempotentKey(
                request.getSceneType(), request.getIdempotentKey());
        
        if (existingTask != null) {
            log.warn("Task already exists: sceneType={}, idempotentKey={}, taskId={}", 
                    request.getSceneType(), request.getIdempotentKey(), existingTask.getTaskId());
            return existingTask.getTaskId();
        }
        
        // 4. 生成任务ID
        String taskId = generateTaskId();
        
        // 5. 构建任务实体
        RetryTask retryTask = new RetryTask();
        retryTask.setTaskId(taskId);
        retryTask.setSceneType(request.getSceneType());
        retryTask.setIdempotentKey(request.getIdempotentKey());
        retryTask.setMethodClass(request.getMethodClass());
        retryTask.setMethodName(request.getMethodName());
        retryTask.setMethodParams(request.getMethodParams());
        retryTask.setTaskStatus("INIT");
        retryTask.setRetryCount(0);
        retryTask.setMaxRetryCount(sceneConfig.getMaxRetryCount());
        
        // 6. 计算下次重试时间（第一个重试间隔）
        List<Integer> retryIntervals = sceneConfig.getRetryIntervalList();
        long nextRetryTime = System.currentTimeMillis() + retryIntervals.get(0) * 60 * 1000L;
        retryTask.setNextRetryTime(nextRetryTime);
        
        LocalDateTime now = LocalDateTime.now();
        retryTask.setCreateTime(now);
        retryTask.setUpdateTime(now);
        
        // 7. 插入数据库
        try {
            retryTaskMapper.insert(retryTask);
            log.info("Created retry task: taskId={}, sceneType={}, idempotentKey={}", 
                    taskId, request.getSceneType(), request.getIdempotentKey());
            try {
                if (retryMetrics != null) {
                    retryMetrics.recordTaskSubmitted(request.getSceneType());
                }
            } catch (Exception me) {
                log.warn("Failed to record metrics for task submission", me);
            }
        } catch (DuplicateKeyException e) {
            // 并发情况下可能出现重复，返回已存在的任务ID
            log.warn("Duplicate task detected during insert: sceneType={}, idempotentKey={}", 
                    request.getSceneType(), request.getIdempotentKey());
            existingTask = retryTaskMapper.selectBySceneAndIdempotentKey(
                    request.getSceneType(), request.getIdempotentKey());
            return existingTask != null ? existingTask.getTaskId() : taskId;
        }
        
        // 8. 加入Redis延时队列
        delayQueueService.addTask(taskId, nextRetryTime);
        
        return taskId;
    }
    
    @Override
    public RetryTaskDTO getTask(String taskId) {
        RetryTask retryTask = retryTaskMapper.selectByTaskId(taskId);
        if (retryTask == null) {
            return null;
        }
        
        RetryTaskDTO dto = new RetryTaskDTO();
        BeanUtils.copyProperties(retryTask, dto);
        return dto;
    }
    
    @Override
    public void updateTaskStatus(String taskId, String taskStatus) {
        int rows = retryTaskMapper.updateStatus(taskId, taskStatus);
        if (rows > 0) {
            log.info("Updated task status: taskId={}, status={}", taskId, taskStatus);
        }
    }
    
    @Override
    public void updateTaskStatusAndRetryInfo(String taskId, String taskStatus, 
                                            Integer retryCount, Long nextRetryTime) {
        int rows = retryTaskMapper.updateStatusAndRetryInfo(taskId, taskStatus, retryCount, nextRetryTime);
        if (rows > 0) {
            log.info("Updated task status and retry info: taskId={}, status={}, retryCount={}, nextRetryTime={}", 
                    taskId, taskStatus, retryCount, nextRetryTime);
        }
    }
    
    @Override
    public List<String> queryPendingTasks(long currentTime, int limit) {
        return retryTaskMapper.selectPendingTasks(currentTime, limit);
    }
    
    @Override
    public void recordHistory(String taskId, Integer retryCount, String executeResult, 
                             String errorMessage, Integer costTime) {
        RetryHistory history = new RetryHistory();
        history.setTaskId(taskId);
        history.setRetryCount(retryCount);
        history.setExecuteTime(LocalDateTime.now());
        history.setExecuteResult(executeResult);
        history.setErrorMessage(errorMessage);
        history.setCostTime(costTime);
        history.setCreateTime(LocalDateTime.now());
        
        retryHistoryMapper.insert(history);
        log.debug("Recorded task history: taskId={}, retryCount={}, result={}", 
                taskId, retryCount, executeResult);
    }
    
    @Override
    public List<RetryHistory> getTaskHistory(String taskId) {
        return retryHistoryMapper.selectByTaskId(taskId);
    }
    
    /**
     * 生成任务ID
     */
    private String generateTaskId() {
        return "RT" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8);
    }
    
    /**
     * 验证请求参数
     */
    private void validateRequest(RetryTaskRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request cannot be null");
        }
        
        if (request.getSceneType() == null) {
            throw new IllegalArgumentException("SceneType cannot be null");
        }
        
        if (request.getIdempotentKey() == null || request.getIdempotentKey().trim().isEmpty()) {
            throw new IllegalArgumentException("IdempotentKey cannot be empty");
        }
        
        if (request.getMethodClass() == null || request.getMethodClass().trim().isEmpty()) {
            throw new IllegalArgumentException("MethodClass cannot be empty");
        }
        
        if (request.getMethodName() == null || request.getMethodName().trim().isEmpty()) {
            throw new IllegalArgumentException("MethodName cannot be empty");
        }
    }
}
