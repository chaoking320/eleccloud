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
    
    @Autowired(required = false)
    private com.retry.platform.client.mq.RetryMessageProducer retryMessageProducer;
    
    @org.springframework.beans.factory.annotation.Value("${retry.server.default-first-delay-ms:60000}")
    private long defaultFirstDelayMs;
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createTask(RetryTaskRequest request) {
        // 1. 参数校验
        validateRequest(request);
        
        // 2. 获取场景配置
        SceneConfig sceneConfig = sceneConfigService.getSceneConfigByType(request.getSceneType());
        if (sceneConfig == null) {
            // 优雅降级：若服务端库中尚未录入场景，但客户端通过 @RetryableTask 注解携带了策略字段，
            // 则自动构建临时场景策略，实现客户端“自包含策略”无缝接入
            if (request.getMaxRetryCount() != null || request.getRetryIntervals() != null || request.getHookClass() != null) {
                sceneConfig = new SceneConfig();
                sceneConfig.setSceneType(request.getSceneType());
                sceneConfig.setSceneName("Auto-derived Scene " + request.getSceneType());
                sceneConfig.setMaxRetryCount(request.getMaxRetryCount() != null ? request.getMaxRetryCount() : 3);
                sceneConfig.setRetryIntervals(request.getRetryIntervals() != null ? request.getRetryIntervals() : "1,3,5");
                sceneConfig.setBackoffStrategy(request.getBackoffStrategy() != null ? request.getBackoffStrategy() : "CUSTOM");
                sceneConfig.setBackoffBase(request.getBackoffBase() != null ? request.getBackoffBase() : 1);
                sceneConfig.setHookClass(request.getHookClass());
                sceneConfig.setEnabled(1);
                log.info("[Server] SceneConfig auto-derived from request for sceneType={}", request.getSceneType());
            } else {
                throw new IllegalArgumentException("Scene config not found for sceneType: " + request.getSceneType());
            }
        }
        
        if (!sceneConfig.checkEnabled()) {
            throw new IllegalArgumentException("Scene is disabled: " + request.getSceneType());
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
        retryTask.setHookClass(request.getHookClass() != null && !request.getHookClass().trim().isEmpty() 
                ? request.getHookClass() : sceneConfig.getHookClass());
        retryTask.setMethodParamTypes(request.getMethodParamTypes());
        retryTask.setTaskStatus("INIT");
        retryTask.setSubmitMode(request.getSubmitMode() != null ? request.getSubmitMode() : "POST_FAIL");
        retryTask.setRetryCount(0);
        // 防御性编程：sceneConfig.getMaxRetryCount() 可能为null
        Integer maxRetryCount = sceneConfig.getMaxRetryCount();
        retryTask.setMaxRetryCount(maxRetryCount != null ? maxRetryCount : 3); // 默认最多重试3次
        
        // 6. 计算下次重试时间（首轮重试，retryCount=0）
        // 注意：LINEAR/EXPONENTIAL/FIXED 策略的 retry_intervals 在库中可能为 NULL，
        // 不能硬取 retryIntervals.get(0)，必须按退避策略 + 基数计算（与 RetryControlServiceImpl 保持一致）
        long nextRetryTime;
        try {
            nextRetryTime = sceneConfig.getBackoffStrategyEnum().calculateNextRetryTime(
                    0,
                    sceneConfig.getBackoffBaseOrDefault(),
                    sceneConfig.getRetryIntervalList());
        } catch (Exception e) {
            // 如果计算失败，使用默认值
            log.warn("Failed to calculate next retry time for taskId={}, using default {}ms. Error: {}", 
                    taskId, defaultFirstDelayMs, e.getMessage());
            nextRetryTime = System.currentTimeMillis() + defaultFirstDelayMs;
        }
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
        
        // 8. 加入Redis延时队列 (在事务提交后执行)
        final String finalTaskId = taskId;
        final long finalNextRetryTime = nextRetryTime;
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    delayQueueService.addTask(finalTaskId, finalNextRetryTime);
                }
            }
        );
        
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
        
        // 补齐场景策略信息，使客户端 SDK 本地重试可以免去单独查配置的开销
        SceneConfig sceneConfig = sceneConfigService.getSceneConfigByType(retryTask.getSceneType());
        if (sceneConfig != null) {
            dto.setHookClass(retryTask.getHookClass() != null ? retryTask.getHookClass() : sceneConfig.getHookClass());
            dto.setBackoffStrategy(sceneConfig.getBackoffStrategy());
            dto.setBackoffBase(sceneConfig.getBackoffBase());
            dto.setRetryIntervals(sceneConfig.getRetryIntervals());
        } else {
            dto.setHookClass(retryTask.getHookClass());
            dto.setBackoffStrategy("CUSTOM");
            dto.setRetryIntervals("1,2,3,5,10");
            log.warn("Scene config not found for taskId={}, sceneType={}. Task will use default/task retry strategy.", 
                    taskId, retryTask.getSceneType());
        }
        
        return dto;
    }
    
    @Override
    public void updateTaskStatus(String taskId, String taskStatus) {
        int rows = retryTaskMapper.updateStatus(taskId, taskStatus);
        if (rows > 0) {
            log.info("Updated task status: taskId={}, status={}", taskId, taskStatus);
            if ("SUCCESS".equals(taskStatus) || "FAILED".equals(taskStatus)) {
                try {
                    delayQueueService.removeTask(taskId);
                } catch (Exception e) {
                    log.error("Failed to remove task from delay queue on status update: taskId={}", taskId, e);
                }
            }
        }
    }

    @Override
    public int casMarkExecuting(String taskId) {
        // 单条原子 SQL：UPDATE retry_task SET task_status='EXECUTING' WHERE task_id=? AND task_status='INIT'
        // 返回 affected rows：1=抢占成功，0=已被其他节点抢占或状态非 INIT
        int affected = retryTaskMapper.casUpdateToExecuting(taskId);
        log.debug("[CAS] markExecuting taskId={}, affectedRows={}", taskId, affected);
        return affected;
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
        // 防御性编程：处理可能的null值
        if (taskId == null || taskId.trim().isEmpty()) {
            log.warn("Cannot record history: taskId is null or empty");
            return;
        }
        
        RetryHistory history = new RetryHistory();
        history.setTaskId(taskId);
        history.setRetryCount(retryCount != null ? retryCount : 0);
        history.setExecuteTime(LocalDateTime.now());
        history.setExecuteResult(executeResult != null ? executeResult : "UNKNOWN");
        history.setErrorMessage(errorMessage);
        history.setCostTime(costTime != null ? costTime : 0);
        history.setCreateTime(LocalDateTime.now());
        
        try {
            retryHistoryMapper.insert(history);
            log.debug("Recorded task history: taskId={}, retryCount={}, result={}", 
                    taskId, retryCount, executeResult);
        } catch (Exception e) {
            // 历史记录失败不应影响主流程，只记录错误日志
            log.error("Failed to record history for taskId={}: {}", taskId, e.getMessage());
        }
    }
    
    @Override
    public List<RetryHistory> getTaskHistory(String taskId) {
        return retryHistoryMapper.selectByTaskId(taskId);
    }
    
    @Override
    public void triggerRetry(String taskId) {
        RetryTaskDTO task = getTask(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Task not found");
        }
        
        // 人工在后台手动触发重试：重置为 INIT 状态且将已重试次数重置为 0，赋予全新的重试机会
        updateTaskStatusAndRetryInfo(taskId, "INIT", 0, System.currentTimeMillis());
        
        if (retryMessageProducer != null) {
            // 向 MQ 发送胖消息即时投递，携带完整上下文直接唤醒 SDK Consumer，无需二次 HTTP 查询
            com.retry.platform.client.mq.RetryMessagePayload payload = com.retry.platform.client.mq.RetryMessagePayload.builder()
                    .taskId(task.getTaskId())
                    .sceneType(task.getSceneType())
                    .idempotentKey(task.getIdempotentKey())
                    .methodClass(task.getMethodClass())
                    .methodName(task.getMethodName())
                    .methodParams(task.getMethodParams())
                    .methodParamTypes(task.getMethodParamTypes())
                    .hookClass(task.getHookClass())
                    .backoffStrategy(task.getBackoffStrategy())
                    .backoffBase(task.getBackoffBase())
                    .retryIntervals(task.getRetryIntervals())
                    .retryCount(0)
                    .maxRetryCount(task.getMaxRetryCount())
                    .build();
            retryMessageProducer.sendDelayMessageWithPayload(payload, 0L);
            log.info("Manually triggered task by sending fat delay=0 message to MQ. taskId={}", taskId);
        } else {
            throw new IllegalStateException("RetryMessageProducer not configured on server side");
        }
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
