package com.retry.platform.client.mq.rabbit;

import com.retry.platform.client.mq.RetryMessageProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * 基于 RabbitMQ x-delayed-message 插件的延时消息发送者
 */
@Slf4j
public class RabbitRetryMessageProducer implements RetryMessageProducer {

    public static final String DELAYED_EXCHANGE = "retry.delayed.exchange";
    private final String routingKey;
    private final RabbitTemplate rabbitTemplate;

    public RabbitRetryMessageProducer(RabbitTemplate rabbitTemplate, String queueName) {
        this.rabbitTemplate = rabbitTemplate;
        this.routingKey = queueName != null ? queueName : "retry.task.key";
    }

    @Override
    public void sendDelayMessage(String taskId, long delayMs, int sceneType) {
        try {
            rabbitTemplate.convertAndSend(DELAYED_EXCHANGE, routingKey, taskId, message -> {
                // 设置延迟投递时间（毫秒数）
                message.getMessageProperties().setDelay((int) delayMs);
                return message;
            });
            log.info("[Rabbit MQ] Sent delay message: exchange={}, routingKey={}, taskId={}, delayMs={}", 
                    DELAYED_EXCHANGE, routingKey, taskId, delayMs);
        } catch (Exception e) {
            log.error("[Rabbit MQ] Failed to send delay message: taskId={}", taskId, e);
            throw new RuntimeException("RabbitMQ MQ send failed", e);
        }
    }
}
