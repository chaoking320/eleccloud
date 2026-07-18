package com.retry.platform.client.mq.redis;

import com.retry.platform.client.executor.LocalRetryExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis ZSET 的 SDK 本地延时消息轮询消费者
 */
@Slf4j
public class RedisRetryMessageConsumer {

    private final String delayQueueKey;
    private final StringRedisTemplate redisTemplate;
    private final LocalRetryExecutor localRetryExecutor;
    private final ExecutorService executorService;
    private volatile boolean running = true;

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
        log.info("[Redis MQ] Local delay queue consumer thread started.");
    }

    private void pollLoop() {
        while (running) {
            try {
                long currentTime = System.currentTimeMillis();
                // 每次最多拉取 10 条到期的任务
                Set<String> expiredTaskIds = redisTemplate.opsForZSet().rangeByScore(delayQueueKey, 0, currentTime, 0, 10);
                
                if (expiredTaskIds != null && !expiredTaskIds.isEmpty()) {
                    for (String taskId : expiredTaskIds) {
                        // 从 ZSET 中原子移出该元素，谁移出成功谁拥有执行权，防多节点并发消费
                        Long removed = redisTemplate.opsForZSet().remove(delayQueueKey, taskId);
                        if (removed != null && removed > 0) {
                            executorService.submit(() -> {
                                try {
                                    localRetryExecutor.execute(taskId);
                                } catch (Exception e) {
                                    log.error("[Redis MQ] Error processing task: taskId={}", taskId, e);
                                }
                            });
                        }
                    }
                }
                
                // 减少 CPU 空转，睡眠 500 毫秒
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
