package com.retry.platform.server.scheduler;

import com.retry.platform.server.service.DelayQueueService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 数据库兜底扫描调度器
 * 定期扫描数据库中应执行但未在Redis中的任务，确保Redis故障时任务不丢失
 * <p>
 * 修复：原来 havingValue="false" 导致该调度器在默认情况下永远不会启动。
 * 现改为 havingValue="true"，并设 matchIfMissing=true 为默认开启。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "retry.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DatabaseFallbackScheduler {
    
    private static final String DELAY_QUEUE_KEY = "retry:delay:queue";
    
    @Autowired(required = false)
    private RedisTemplate<String, String> redisTemplate;
    
    @Autowired
    private DelayQueueService delayQueueService;
    
    @Value("${retry.scheduler.batch-size:100}")
    private int batchSize;
    
    /**
     * 定期扫描数据库，将应执行但未在Redis中的任务重新加入队列
     * 每分钟执行一次
     */
    @Scheduled(fixedDelayString = "${retry.scheduler.fallback-scan-interval:60000}")
    public void scanAndSyncToRedis() {
        if (redisTemplate == null) {
            log.debug("Redis not available, skipping fallback scan");
            return;
        }
        
        try {
            long currentTime = System.currentTimeMillis();
            
            // 从数据库获取应执行的任务
            List<String> pendingTaskIds = delayQueueService.pollExpiredTasks(currentTime, batchSize);
            
            if (pendingTaskIds.isEmpty()) {
                log.debug("No pending tasks found in database fallback scan");
                return;
            }
            
            // 检查哪些任务不在Redis中
            int syncedCount = 0;
            for (String taskId : pendingTaskIds) {
                if (!isTaskInRedis(taskId)) {
                    // 任务不在Redis中，重新加入队列
                    delayQueueService.addTask(taskId, currentTime);
                    syncedCount++;
                    log.info("Synced missing task to Redis: taskId={}", taskId);
                }
            }
            
            if (syncedCount > 0) {
                log.info("Database fallback scan completed: synced {} tasks to Redis", syncedCount);
            }
            
        } catch (Exception e) {
            log.error("Database fallback scan failed", e);
        }
    }
    
    /**
     * 检查任务是否在Redis延时队列中
     */
    private boolean isTaskInRedis(String taskId) {
        try {
            Double score = redisTemplate.opsForZSet().score(DELAY_QUEUE_KEY, taskId);
            return score != null;
        } catch (Exception e) {
            log.warn("Failed to check task in Redis: taskId={}", taskId, e);
            return false;
        }
    }
}
