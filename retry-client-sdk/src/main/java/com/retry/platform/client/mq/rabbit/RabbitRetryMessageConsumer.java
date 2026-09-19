package com.retry.platform.client.mq.rabbit;

import com.retry.platform.client.executor.LocalRetryExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

/**
 * 基于 RabbitMQ x-delayed-message 的 SDK 本地延时消息消费者
 */
@Slf4j
public class RabbitRetryMessageConsumer {

    private final LocalRetryExecutor localRetryExecutor;

    public RabbitRetryMessageConsumer(LocalRetryExecutor localRetryExecutor) {
        this.localRetryExecutor = localRetryExecutor;
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "${retry.client.queue-name:retry.delayed.queue}", durable = "true"),
            exchange = @Exchange(
                    value = RabbitRetryMessageProducer.DELAYED_EXCHANGE,
                    delayed = "true"
            ),
            key = "${retry.client.queue-name:retry.delayed.queue}"
    ))
    public void onMessage(String messageBody) {
        try {
            if (messageBody.startsWith("{")) {
                // 尝试按胖消息解析
                com.retry.platform.client.mq.RetryMessagePayload payload = com.retry.platform.client.util.JsonUtil.fromJson(messageBody, com.retry.platform.client.mq.RetryMessagePayload.class);
                if (payload != null && payload.getTaskId() != null) {
                    log.debug("[Rabbit MQ] Received fat message: taskId={}, retryCount={}", payload.getTaskId(), payload.getRetryCount());
                    localRetryExecutor.executeWithPayload(payload);
                    return;
                }
            }
        } catch (Exception e) {
            log.warn("[Rabbit MQ] Failed to parse fat message, treating as slim. body={}", messageBody, e);
        }

        // 降级按瘦消息（taskId）处理
        log.info("[Rabbit MQ] Received slim message: taskId={}", messageBody);
        try {
            localRetryExecutor.execute(messageBody);
        } catch (Exception e) {
            log.error("[Rabbit MQ] Failed to process delayed task message: taskId={}", messageBody, e);
        }
    }
}
