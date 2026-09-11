package com.retry.platform.client.mq.redis;

import com.retry.platform.client.mq.RetryMessagePayload;
import com.retry.platform.client.mq.RetryMessageProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 基于 Redis ZSET 的延时消息发送者（支持胖消息）
 *
 * <p>胖消息格式：ZSET member = JSON(RetryMessagePayload)，score = 执行时间戳
 * <p>瘦消息格式（兼容旧数据）：ZSET member = taskId
 */
@Slf4j
public class RedisRetryMessageProducer implements RetryMessageProducer {

    private final String delayQueueKey;
    private final StringRedisTemplate redisTemplate;

    public RedisRetryMessageProducer(StringRedisTemplate redisTemplate, String queueName) {
        this.redisTemplate = redisTemplate;
        this.delayQueueKey = "retry:client:delay:queue:" + (queueName != null ? queueName : "default");
    }

    /** 瘦消息：仅存 taskId（向后兼容，DatabaseFallbackScheduler 触发时使用） */
    @Override
    public void sendDelayMessage(String taskId, long delayMs, int sceneType) {
        long executeTime = System.currentTimeMillis() + delayMs;
        try {
            redisTemplate.opsForZSet().add(delayQueueKey, taskId, executeTime);
            log.info("[Redis MQ] Sent slim message: key={}, taskId={}, delayMs={}", delayQueueKey, taskId, delayMs);
        } catch (Exception e) {
            log.error("[Redis MQ] Failed to send slim message: taskId={}", taskId, e);
            throw new RuntimeException("Redis MQ send failed", e);
        }
    }

    /**
     * 胖消息：将完整 payload 序列化为 JSON 存入 ZSET。
     * Consumer 反序列化后直接执行，无需再向 Server 查询任务详情。
     */
    @Override
    public void sendDelayMessageWithPayload(RetryMessagePayload payload, long delayMs) {
        long executeTime = System.currentTimeMillis() + delayMs;
        try {
            String member = com.retry.platform.client.util.JsonUtil.toJson(payload);
            redisTemplate.opsForZSet().add(delayQueueKey, member, executeTime);
            log.info("[Redis MQ] Sent fat message: key={}, taskId={}, delayMs={}, retryCount={}",
                    delayQueueKey, payload.getTaskId(), delayMs, payload.getRetryCount());
        } catch (Exception e) {
            log.error("[Redis MQ] Failed to send fat message: taskId={}", payload.getTaskId(), e);
            // 降级为瘦消息
            log.warn("[Redis MQ] Falling back to slim message for taskId={}", payload.getTaskId());
            sendDelayMessage(payload.getTaskId(), delayMs, payload.getSceneType() != null ? payload.getSceneType() : 0);
        }
    }
}
