package com.retry.platform.client.mq.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retry.platform.client.executor.LocalRetryExecutor;
import com.retry.platform.client.mq.RetryMessagePayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis ZSET 的 SDK 本地延时消息轮询消费者（支持胖消息/瘦消息）
 *
 * <p>胖消息：member = JSON(RetryMessagePayload)，直接传给 LocalRetryExecutor 执行，无需 HTTP 查询任务详情。
 * <p>瘦消息：member = taskId，降级走原有路径（向后兼容旧数据和 DatabaseFallbackScheduler）。
 */
@Slf4j
public class RedisRetryMessageConsumer {

    private final String delayQueueKey;
    private final StringRedisTemplate redisTemplate;
    private final LocalRetryExecutor localRetryExecutor;
    private final ExecutorService executorService;
    private volatile boolean running = true;
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public RedisRetryMessageConsumer(StringRedisTemplate redisTemplate,
                                     LocalRetryExecutor localRetryExecutor,
                                     int concurrency,
                                     String queueName) {
        this.redisTemplate = redisTemplate;
        this.localRetryExecutor = localRetryExecutor;
        this.delayQueueKey = "retry:client:delay:queue:" + (queueName != null ? queueName : "default");
        this.executorService = Executors.newFixedThreadPool(concurrency > 0 ? concurrency : 5);
    }

    @PostConstruct
    public void start() {
        Thread workerThread = new Thread(this::pollLoop, "redis-retry-message-consumer-thread");
        workerThread.setDaemon(true);
        workerThread.start();
        log.info("[Redis MQ] Local delay queue consumer thread started. Queue: {}", delayQueueKey);
    }

    private void pollLoop() {
        while (running) {
            try {
                long currentTime = System.currentTimeMillis();
                // 使用 Lua 脚本原子性地 rangeByScore + remove，防止多节点竞争
                Set<String> expiredMembers = redisTemplate.opsForZSet()
                        .rangeByScore(delayQueueKey, 0, currentTime, 0, 10);

                if (expiredMembers != null && !expiredMembers.isEmpty()) {
                    for (String member : expiredMembers) {
                        // 原子移除：谁移出成功谁执行，防多节点并发消费
                        Long removed = redisTemplate.opsForZSet().remove(delayQueueKey, member);
                        if (removed != null && removed > 0) {
                            log.info("[Redis MQ] Popped message from delay queue: {}", 
                                    member.length() > 60 ? member.substring(0, 60) + "..." : member);
                            final String memberCopy = member;
                            executorService.submit(() -> {
                                try {
                                    dispatch(memberCopy);
                                } catch (Exception e) {
                                    log.error("[Redis MQ] Error dispatching member", e);
                                }
                            });
                        }
                    }
                }

                // 减少 CPU 空转
                TimeUnit.MILLISECONDS.sleep(500);
            } catch (InterruptedException e) {
                log.info("[Redis MQ] Poll thread interrupted, stopping.");
                running = false;
            } catch (Exception e) {
                log.error("[Redis MQ] Poll error in consumer loop", e);
                try {
                    TimeUnit.SECONDS.sleep(2);
                } catch (InterruptedException ex) {
                    running = false;
                }
            }
        }
    }

    /**
     * 分发消息：优先尝试胖消息解析，失败则降级为瘦消息（向后兼容）。
     */
    private void dispatch(String member) {
        // 尝试解析为胖消息
        if (member.startsWith("{")) {
            try {
                RetryMessagePayload payload = MAPPER.readValue(member, RetryMessagePayload.class);
                if (payload.getTaskId() != null) {
                    log.info("[Redis MQ] Dispatching fat message: taskId={}, retryCount={}",
                            payload.getTaskId(), payload.getRetryCount());
                    localRetryExecutor.executeWithPayload(payload);
                    return;
                }
            } catch (Exception e) {
                log.warn("[Redis MQ] Failed to parse fat message, falling back to slim. member={}", member, e);
            }
        }
        // 降级为瘦消息（member 直接是 taskId）
        log.info("[Redis MQ] Dispatching slim message: taskId={}", member);
        localRetryExecutor.execute(member);
    }

    @PreDestroy
    public void stop() {
        running = false;
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(3, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
        log.info("[Redis MQ] Consumer thread stopped.");
    }
}
