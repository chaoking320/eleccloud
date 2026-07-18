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
                    delayed = "true", 
                    type = "x-delayed-message"
            ),
            key = "${retry.client.queue-name:retry.delayed.queue}"
    ))
    public void onMessage(String taskId) {
        log.info("[Rabbit MQ] Received delay task trigger message from queue: taskId={}", taskId);
        try {
            localRetryExecutor.execute(taskId);
        } catch (Exception e) {
            log.error("[Rabbit MQ] Failed to process delayed task message: taskId={}", taskId, e);
        }
    }
}
