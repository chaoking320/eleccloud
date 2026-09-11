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
            log.info("[Rabbit MQ] Sent slim delay message: exchange={}, routingKey={}, taskId={}, delayMs={}", 
                    DELAYED_EXCHANGE, routingKey, taskId, delayMs);
        } catch (Exception e) {
            log.error("[Rabbit MQ] Failed to send slim delay message: taskId={}", taskId, e);
            throw new RuntimeException("Rabbit MQ send failed", e);
        }
    }

    @Override
    public void sendDelayMessageWithPayload(com.retry.platform.client.mq.RetryMessagePayload payload, long delayMs) {
        try {
            String payloadJson = com.retry.platform.client.util.JsonUtil.toJson(payload);
            rabbitTemplate.convertAndSend(DELAYED_EXCHANGE, routingKey, payloadJson, message -> {
                message.getMessageProperties().setDelay((int) delayMs);
                return message;
            });
            log.info("[Rabbit MQ] Sent fat delay message: exchange={}, routingKey={}, taskId={}, delayMs={}, retryCount={}",
                    DELAYED_EXCHANGE, routingKey, payload.getTaskId(), delayMs, payload.getRetryCount());
        } catch (Exception e) {
            log.error("[Rabbit MQ] Failed to send fat delay message: taskId={}", payload.getTaskId(), e);
            log.warn("[Rabbit MQ] Falling back to slim message for taskId={}", payload.getTaskId());
            sendDelayMessage(payload.getTaskId(), delayMs, payload.getSceneType() != null ? payload.getSceneType() : 0);
        }
    }
}
