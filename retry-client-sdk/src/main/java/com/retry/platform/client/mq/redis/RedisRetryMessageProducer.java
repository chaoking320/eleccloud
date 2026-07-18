package com.retry.platform.client.mq.redis;

import com.retry.platform.client.mq.RetryMessageProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 基于 Redis ZSET 的延时消息发送者
 */
@Slf4j
public class RedisRetryMessageProducer implements RetryMessageProducer {

    private final String delayQueueKey;

    private final StringRedisTemplate redisTemplate;

    public RedisRetryMessageProducer(StringRedisTemplate redisTemplate, String queueName) {
        this.redisTemplate = redisTemplate;
        this.delayQueueKey = "retry:client:delay:queue:" + (queueName != null ? queueName : "default");
    }

    @Override
    public void sendDelayMessage(String taskId, long delayMs, int sceneType) {
        long executeTime = System.currentTimeMillis() + delayMs;
        try {
            redisTemplate.opsForZSet().add(delayQueueKey, taskId, executeTime);
            log.info("[Redis MQ] Sent delay message: key={}, taskId={}, delayMs={}, targetTime={}", delayQueueKey, taskId, delayMs, executeTime);
        } catch (Exception e) {
            log.error("[Redis MQ] Failed to send delay message: taskId={}", taskId, e);
            throw new RuntimeException("Redis MQ send failed", e);
        }
    }
}
