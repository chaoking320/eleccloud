package com.retry.platform.server.scheduler;

import com.retry.platform.server.mapper.RetryTaskMapper;
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
    
    private final String DELAY_QUEUE_KEY;
    
    public DatabaseFallbackScheduler(@Value("${retry.client.queue-name:retry.delayed.queue}") String queueName) {
        this.DELAY_QUEUE_KEY = "retry:client:delay:queue:" + queueName;
    }
    
    @Autowired(required = false)
    private RedisTemplate<String, String> redisTemplate;
    
    @Autowired
    private RetryTaskMapper retryTaskMapper;

    @Autowired
    private com.retry.platform.server.service.SceneConfigService sceneConfigService;

    @Autowired(required = false)
    private com.retry.platform.client.mq.RetryMessageProducer retryMessageProducer;
    
    @Value("${retry.scheduler.batch-size:100}")
    private int batchSize;
    
    /**
     * 定期扫描数据库，将 INIT 状态已到期但未在 Redis 消费队列中的任务重新推送到 Redis
     * 每 15 秒执行一次兜底扫描
     */
    @Scheduled(fixedDelayString = "${retry.scheduler.fallback-scan-interval:15000}")
    @net.javacrumbs.shedlock.spring.annotation.SchedulerLock(name = "databaseFallbackScan", lockAtLeastFor = "PT10S", lockAtMostFor = "PT5M")
    public void scanAndSyncToRedis() {
        if (redisTemplate == null || retryMessageProducer == null) {
            return;
        }
        
        try {
            long currentTime = System.currentTimeMillis();
            
            // 直接从 MySQL 获取处于 INIT 状态且已到期的任务
            List<String> pendingTaskIds = retryTaskMapper.selectPendingTasks(currentTime, batchSize);
            
            if (pendingTaskIds == null || pendingTaskIds.isEmpty()) {
                return;
            }
            
            int syncedCount = 0;
            for (String taskId : pendingTaskIds) {
                // 检查任务是否已经在 Redis 中排队
                if (!isTaskInRedis(taskId)) {
                    com.retry.platform.server.entity.RetryTask task = retryTaskMapper.selectByTaskId(taskId);
                    if (task != null && "INIT".equals(task.getTaskStatus())) {
                        com.retry.platform.server.entity.SceneConfig sceneConfig = 
                                sceneConfigService.getSceneConfigByType(task.getSceneType());
                        
                        com.retry.platform.client.mq.RetryMessagePayload payload = 
                                com.retry.platform.client.mq.RetryMessagePayload.builder()
                                        .taskId(task.getTaskId())
                                        .sceneType(task.getSceneType())
                                        .idempotentKey(task.getIdempotentKey())
                                        .methodClass(task.getMethodClass())
                                        .methodName(task.getMethodName())
                                        .methodParams(task.getMethodParams())
                                        .methodParamTypes(task.getMethodParamTypes())
                                        .hookClass(sceneConfig != null ? sceneConfig.getHookClass() : null)
                                        .backoffStrategy(sceneConfig != null ? sceneConfig.getBackoffStrategy() : null)
                                        .backoffBase(sceneConfig != null ? sceneConfig.getBackoffBase() : null)
                                        .retryIntervals(sceneConfig != null ? sceneConfig.getRetryIntervals() : null)
                                        .retryCount(task.getRetryCount())
                                        .maxRetryCount(task.getMaxRetryCount())
                                        .build();

                        retryMessageProducer.sendDelayMessageWithPayload(payload, 0L);
                        syncedCount++;
                        log.info("[FallbackScheduler] Synced stuck INIT task from MySQL to Redis: taskId={}", taskId);
                    }
                }
            }
            
            if (syncedCount > 0) {
                log.info("[FallbackScheduler] Database fallback scan completed: synced {} stuck tasks to Redis", syncedCount);
            }
            
        } catch (Exception e) {
            log.error("[FallbackScheduler] Database fallback scan failed", e);
        }
    }
    
    /**
     * 检查任务是否在Redis延时队列中
     */
    private boolean isTaskInRedis(String taskId) {
        try {
            // O(1) 检查瘦消息（member=taskId）是否存在
            // 胖消息（member=JSON）无法精确判断，但幂等性由 CAS 保证安全
            Double score = redisTemplate.opsForZSet().score(DELAY_QUEUE_KEY, taskId);
            return score != null;
        } catch (Exception e) {
            log.warn("Failed to check task in Redis: taskId={}", taskId, e);
            return false;
        }
    }
}
