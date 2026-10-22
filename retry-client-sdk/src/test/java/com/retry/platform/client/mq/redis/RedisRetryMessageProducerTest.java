package com.retry.platform.client.mq.redis;

import com.retry.platform.client.mq.RetryMessagePayload;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisRetryMessageProducerTest {

    @Test
    @SuppressWarnings("unchecked")
    void sendDelayMessageToQueueShouldRouteToTargetQueueOrFallbackToDefault() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ZSetOperations<String, String> zSetOps = mock(ZSetOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);

        RedisRetryMessageProducer producer = new RedisRetryMessageProducer(redisTemplate, "default.queue");
        RetryMessagePayload payload = RetryMessagePayload.builder()
                .taskId("TASK-ROUTE-1")
                .sceneType(1)
                .retryCount(0)
                .build();

        producer.sendDelayMessageWithPayload(payload, 1000L);
        verify(zSetOps).add(eq("retry:client:delay:queue:default.queue"), contains("TASK-ROUTE-1"), anyDouble());

        producer.sendDelayMessageToQueue("order.queue", payload, 1000L);
        verify(zSetOps).add(eq("retry:client:delay:queue:order.queue"), contains("TASK-ROUTE-1"), anyDouble());
    }
}
