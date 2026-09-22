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
            
            // 批量一次性预获取当前 Redis 队列中的任务ID集合，避免在循环中对每个任务重复全量扫描 Redis (O(N*M) -> O(1))
            java.util.Set<String> queuedTaskIds = getQueuedTaskIdsInRedis(1000);

            int syncedCount = 0;
            for (String taskId : pendingTaskIds) {
                // 优先从批量预取的内存集合中极速 O(1) 命中
                if (!queuedTaskIds.contains(taskId) && !isTaskInRedisSlim(taskId)) {
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
     * 单次批量预取 Redis 延时队列中的任务ID集合。
     *
     * <p>将原本循环内针对每个待处理任务扫描 Redis 的高开销行为（O(N * 1000)），
     * 降维为单次批量提取 + 内存 O(1) 比对，彻底消除队列积压时兜底调度器对 Redis 造成的性能瓶颈。
     *
     * @param limit 扫描的最大 member 数量
     * @return 存在于 Redis 队列中的 TaskId 集合
     */
    private java.util.Set<String> getQueuedTaskIdsInRedis(int limit) {
        java.util.Set<String> result = new java.util.HashSet<>();
        try {
            Set<String> members = redisTemplate.opsForZSet().range(DELAY_QUEUE_KEY, 0, Math.max(0, limit - 1));
            if (members != null && !members.isEmpty()) {
                for (String member : members) {
                    if (member == null || member.isEmpty()) {
                        continue;
                    }
                    if (member.startsWith("{")) {
                        try {
                            com.retry.platform.client.mq.RetryMessagePayload payload =
                                    com.retry.platform.client.util.JsonUtil.fromJson(
                                            member, com.retry.platform.client.mq.RetryMessagePayload.class);
                            if (payload != null && payload.getTaskId() != null) {
                                result.add(payload.getTaskId());
                            }
                        } catch (Exception ignored) {
                            // 忽略脏数据格式
                        }
                    } else {
                        // 瘦消息：member 即 taskId
                        result.add(member);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[FallbackScheduler] Failed to batch pre-fetch queued tasks from Redis", e);
        }
        return result;
    }

    /**
     * 快速检查瘦消息（O(1) ZSCORE），用于超出预取窗口时的单项安全兜底。
     */
    private boolean isTaskInRedisSlim(String taskId) {
        try {
            Double score = redisTemplate.opsForZSet().score(DELAY_QUEUE_KEY, taskId);
            return score != null;
        } catch (Exception e) {
            return false;
        }
    }
}
