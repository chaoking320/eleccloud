package com.retry.platform.client.config;

import lombok.Data;

/**
 * Standalone 模式下的场景配置（直接在 application.yml 中配置，无需远程 Server）
 *
 * <p>配置示例：
 * <pre>
 * retry:
 *   client:
 *     mode: standalone
 *     scenes:
 *       - scene-type: 1001
 *         scene-name: 退款重试
 *         retry-intervals: "1,3,6,9"
 *         max-retry-count: 4
 *         hook-class: com.example.RefundRetryHook
 *       - scene-type: 1002
 *         scene-name: 库存扣减
 *         backoff-strategy: FIXED
 *         backoff-base: 2
 *         max-retry-count: 3
 * </pre>
 */
@Data
public class StandaloneSceneConfig {

    /**
     * 场景类型 ID（必填），与 @RetryableTask(sceneType = xxx) 对应
     */
    private Integer sceneType;

    /**
     * 场景名称（可选，仅用于日志展示）
     */
    private String sceneName;

    /**
     * 自定义重试间隔列表（分钟，逗号分隔），backoffStrategy=CUSTOM 时生效
     * 例如："1,3,6,9" 表示第1次重试等1分钟，第2次等3分钟，以此类推，超出范围取最后一个值
     */
    private String retryIntervals;

    /**
     * 最大重试次数（必填）
     */
    private Integer maxRetryCount = 3;

    /**
     * 退避策略：CUSTOM（默认）/ FIXED / LINEAR / EXPONENTIAL
     */
    private String backoffStrategy = "CUSTOM";

    /**
     * 退避基数（分钟），FIXED / LINEAR / EXPONENTIAL 策略使用
     */
    private Integer backoffBase = 1;

    /**
     * Hook 类全限定名（可选）
     * 若不配置，退化为纯反射重试（无幂等保护）
     */
    private String hookClass;
}
