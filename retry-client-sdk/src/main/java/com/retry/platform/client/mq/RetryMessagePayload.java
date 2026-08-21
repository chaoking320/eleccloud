package com.retry.platform.client.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 重试消息载体（胖消息）
 *
 * <p>改造前（瘦消息）：MQ 消息只存 taskId，消费时需要 5 次 HTTP 调用才能完成一次重试。
 * <p>改造后（胖消息）：MQ 消息携带执行所需的完整上下文，消费时只需：
 * <ol>
 *   <li>CAS 抢占 → POST /api/retry/executing/{taskId}</li>
 *   <li>本地执行业务方法</li>
 *   <li>最终状态更新 → POST /api/retry/success|failed|retry-info</li>
 * </ol>
 * 从 5 次减少到 2~3 次 HTTP 调用，高并发时效果显著。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetryMessagePayload implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 任务ID（必填） */
    private String taskId;

    /** 场景类型 */
    private Integer sceneType;

    /** 幂等键 */
    private String idempotentKey;

    /** 业务方法所在类的全限定名 */
    private String methodClass;

    /** 业务方法名 */
    private String methodName;

    /** 业务方法参数（JSON格式） */
    private String methodParams;

    /** 业务方法参数类型列表（逗号分隔，用于精确定位重载方法） */
    private String methodParamTypes;

    /** 钩子类全限定名 */
    private String hookClass;

    /** 退避策略：CUSTOM / FIXED / LINEAR / EXPONENTIAL */
    private String backoffStrategy;

    /** 退避基数（分钟） */
    private Integer backoffBase;

    /** 自定义重试间隔列表（逗号分隔，CUSTOM模式） */
    private String retryIntervals;

    /** 当前已重试次数 */
    private Integer retryCount;

    /** 最大重试次数 */
    private Integer maxRetryCount;
}
