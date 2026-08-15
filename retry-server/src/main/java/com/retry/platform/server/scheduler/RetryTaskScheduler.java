package com.retry.platform.server.scheduler;

/**
 * 重试任务服务端调度器（已废除）
 *
 * <p>历史背景：早期版本曾在 Server 端集中扫描 Redis 延时队列并主动 HTTP 回调业务服务，
 * 这与 ElecCloud 的核心设计理念冲突——Server 不应主动连接业务服务。
 *
 * <p>现行架构（SDK 自驱动）：
 * <ul>
 *   <li>调度权完全移交给 SDK 内的 {@code RedisRetryMessageConsumer} / {@code RabbitRetryMessageConsumer}</li>
 *   <li>Server 仅负责数据持久化，不参与调度</li>
 *   <li>SDK Consumer 在业务进程内轮询 MQ，到期后在本地反射调用原方法</li>
 * </ul>
 *
 * <p>本类保留仅作为架构演进的历史记录，不再注册为 Spring Bean，不会被加载。
 *
 * @deprecated 已由 SDK 侧 Consumer 替代，此类不再使用。
 * @see com.retry.platform.client.mq.redis.RedisRetryMessageConsumer
 * @see com.retry.platform.client.mq.rabbit.RabbitRetryMessageConsumer
 */
@Deprecated
public class RetryTaskScheduler {
    // 已废除，保留仅供历史参考
}
