package com.retry.platform.client.mq;

/**
 * 延时消息投递生产者接口
 * 用于支持多消息源（Redis ZSET / RabbitMQ）的任意精度延时消息投递
 */
public interface RetryMessageProducer {

    /**
     * 发送延时重试消息
     *
     * @param taskId    任务ID
     * @param delayMs   延迟毫秒数
     * @param sceneType 场景类型
     */
    void sendDelayMessage(String taskId, long delayMs, int sceneType);
}
