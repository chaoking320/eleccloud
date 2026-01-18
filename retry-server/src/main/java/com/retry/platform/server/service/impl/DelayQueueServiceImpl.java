package com.retry.platform.server.service.impl;

import com.retry.platform.server.mapper.RetryTaskMapper;
import com.retry.platform.server.service.DelayQueueService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 延时队列服务实现类
 * 基于Redis ZSET实现，score为执行时间戳
 */
@Slf4j
@Service
public class DelayQueueServiceImpl implements DelayQueueService {
    
    private static final String DELAY_QUEUE_KEY = "retry:delay:queue";
    
    @Autowired(required = false)
    private RedisTemplate<String, String> redisTemplate;
    
    @Autowired
    private RetryTaskMapper retryTaskMapper;
    
    @Override
    public void addTask(String taskId, long executeTime) {
        try {
            if (redisTemplate != null) {
                redisTemplate.opsForZSet().add(DELAY_QUEUE_KEY, taskId, executeTime);
                log.debug("Added task to delay queue: taskId={}, executeTime={}", taskId, executeTime);
            } else {
                log.warn("Redis not available, task will be handled by database fallback: taskId={}", taskId);
            }
        } catch (Exception e) {
            log.error("Failed to add task to delay queue: taskId={}", taskId, e);
            // Redis故障时不抛异常，依赖数据库兜底
        }
    }
    
    @Override
    public List<String> pollExpiredTasks(long currentTime, int limit) {
        try {
            if (redisTemplate != null) {
                return pollFromRedis(currentTime, limit);
            } else {
                log.warn("Redis not available, falling back to database query");
                return pollFromDatabase(currentTime, limit);
            }
        } catch (Exception e) {
            log.error("Failed to poll from Redis, falling back to database", e);
            return pollFromDatabase(currentTime, limit);
        }
    }
    
    @Override
    public void removeTask(String taskId) {
        try {
            if (redisTemplate != null) {
                redisTemplate.opsForZSet().remove(DELAY_QUEUE_KEY, taskId);
                log.debug("Removed task from delay queue: taskId={}", taskId);
            }
        } catch (Exception e) {
            log.error("Failed to remove task from delay queue: taskId={}", taskId, e);
        }
    }
    
    @Override
    public void updateTaskTime(String taskId, long newExecuteTime) {
        try {
            if (redisTemplate != null) {
                redisTemplate.opsForZSet().add(DELAY_QUEUE_KEY, taskId, newExecuteTime);
                log.debug("Updated task time in delay queue: taskId={}, newExecuteTime={}", 
                        taskId, newExecuteTime);
            }
        } catch (Exception e) {
            log.error("Failed to update task time in delay queue: taskId={}", taskId, e);
        }
    }
    
    /**
     * 从Redis获取到期任务
     */
    private List<String> pollFromRedis(long currentTime, int limit) {
        Set<String> taskIds = redisTemplate.opsForZSet()
                .rangeByScore(DELAY_QUEUE_KEY, 0, currentTime, 0, limit);
        
        if (taskIds == null || taskIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        log.debug("Polled {} expired tasks from Redis", taskIds.size());
        return new ArrayList<>(taskIds);
    }
    
    /**
     * 从数据库获取到期任务（降级方案）
     */
    private List<String> pollFromDatabase(long currentTime, int limit) {
        try {
            List<String> taskIds = retryTaskMapper.selectPendingTasks(currentTime, limit);
            log.debug("Polled {} expired tasks from database", taskIds.size());
            return taskIds;
        } catch (Exception e) {
            log.error("Failed to poll from database", e);
            return new ArrayList<>();
        }
    }
}
