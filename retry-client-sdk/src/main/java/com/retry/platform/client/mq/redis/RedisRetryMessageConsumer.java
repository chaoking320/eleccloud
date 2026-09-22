package com.retry.platform.client.mq.redis;

import com.retry.platform.client.executor.LocalRetryExecutor;
import com.retry.platform.client.mq.RetryMessagePayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.List;
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
public class RedisRetryMessageConsumer implements InitializingBean, DisposableBean {

    private static final DefaultRedisScript<List> LUA_SCRIPT;
    static {
        String script = "-- KEYS[1] = delay queue key\n" +
                        "-- ARGV[1] = current time (score upper bound)\n" +
                        "-- ARGV[2] = batch size\n" +
                        "local expired = redis.call('ZRANGEBYSCORE', KEYS[1], '0', ARGV[1], 'LIMIT', '0', ARGV[2])\n" +
                        "if #expired > 0 then\n" +
                        "    for i, member in ipairs(expired) do\n" +
                        "        redis.call('ZREM', KEYS[1], member)\n" +
                        "    end\n" +
                        "end\n" +
                        "return expired";
        LUA_SCRIPT = new DefaultRedisScript<>(script, List.class);
    }

    private final String delayQueueKey;
    private final StringRedisTemplate redisTemplate;
    private final LocalRetryExecutor localRetryExecutor;
    private final ExecutorService executorService;
    private final int batchSize;
    private volatile boolean running = true;

    public RedisRetryMessageConsumer(StringRedisTemplate redisTemplate,
                                     LocalRetryExecutor localRetryExecutor,
                                     int concurrency,
                                     String queueName) {
        this(redisTemplate, localRetryExecutor, concurrency, queueName, 10);
    }

    public RedisRetryMessageConsumer(StringRedisTemplate redisTemplate,
                                     LocalRetryExecutor localRetryExecutor,
                                     int concurrency,
                                     String queueName,
                                     int batchSize) {
        this.redisTemplate = redisTemplate;
        this.localRetryExecutor = localRetryExecutor;
        this.delayQueueKey = "retry:client:delay:queue:" + (queueName != null ? queueName : "default");
        this.batchSize = batchSize > 0 ? batchSize : 10;
        int corePoolSize = concurrency > 0 ? concurrency : 5;
        this.executorService = new java.util.concurrent.ThreadPoolExecutor(
                corePoolSize,
                corePoolSize,
                60L, TimeUnit.SECONDS,
                new java.util.concurrent.LinkedBlockingQueue<>(1000),
                new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    @Override
    public void afterPropertiesSet() {
        start();
    }

    public void start() {
        Thread workerThread = new Thread(this::pollLoop, "redis-retry-message-consumer-thread");
        workerThread.setDaemon(true);
        workerThread.start();
        log.info("[Redis MQ] Local delay queue consumer thread started. Queue: {}", delayQueueKey);
    }

    @Override
    public void destroy() {
        stop();
    }

    private void pollLoop() {
        long pollInterval = 500; // 初始值
        final long MIN_INTERVAL = 200;
        final long MAX_INTERVAL = 2000;

        while (running) {
            try {
                long currentTime = System.currentTimeMillis();
                
                @SuppressWarnings("unchecked")
                List<String> expiredMembers = redisTemplate.execute(
                        LUA_SCRIPT,
                        Collections.singletonList(delayQueueKey),
                        String.valueOf(currentTime),
                        String.valueOf(batchSize)
                );

                boolean hasMessages = false;
                if (expiredMembers != null && !expiredMembers.isEmpty()) {
                    hasMessages = true;
                    for (String member : expiredMembers) {
                        log.info("[Redis MQ] Popped message from delay queue: {}", 
                                member.length() > 60 ? member.substring(0, 60) + "..." : member);
                        final String memberCopy = member;
                        executorService.submit(() -> {
                            try {
                                dispatch(memberCopy);
                            } catch (Exception e) {
                                log.error("[Redis MQ] Error dispatching member, will re-enqueue after 30s", e);
                                // Bug 修复：dispatch 失败时将消息重入 ZSET，避免消息静默丢失。
                                // 延迟 30s 重试，防止因持续异常（如类找不到）导致紧密循环。
                                // DatabaseFallbackScheduler 作为最后兜底仍然存在。
                                reEnqueue(memberCopy, 30_000L);
                            }
                        });
                    }
                }

                if (hasMessages) {
                    pollInterval = MIN_INTERVAL;
                } else {
                    pollInterval = Math.min(pollInterval + 200, MAX_INTERVAL);
                }
                TimeUnit.MILLISECONDS.sleep(pollInterval);
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
     * 将消息重新入队，延迟 delayMs 毫秒后重试。
     * 用于 dispatch 异常时的安全保底，避免消息静默丢失。
     */
    private void reEnqueue(String member, long delayMs) {
        try {
            long retryScore = System.currentTimeMillis() + delayMs;
            redisTemplate.opsForZSet().add(delayQueueKey, member, retryScore);
            log.warn("[Redis MQ] Re-enqueued failed message to delay queue (delay={}ms): {}",
                    delayMs, member.length() > 60 ? member.substring(0, 60) + "..." : member);
        } catch (Exception ex) {
            log.error("[Redis MQ] CRITICAL: Failed to re-enqueue message, message may be lost! member={}",
                    member.length() > 60 ? member.substring(0, 60) + "..." : member, ex);
        }
    }

    /**
     * 分发消息：优先尝试胖消息解析，失败则降级为瘦消息（向后兼容）。
     */
    private void dispatch(String member) {
        // 尝试解析为胖消息
        if (member.startsWith("{")) {
            try {
                RetryMessagePayload payload = com.retry.platform.client.util.JsonUtil.fromJson(member, RetryMessagePayload.class);
                if (payload != null && payload.getTaskId() != null) {
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
