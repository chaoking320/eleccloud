package com.retry.platform.server.entity;

import com.retry.platform.server.strategy.BackoffStrategy;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 场景配置实体类
 */
@Data
public class SceneConfig {

    /** 主键ID */
    private Long id;

    /** 场景类型 */
    private Integer sceneType;

    /** 场景名称 */
    private String sceneName;

    /**
     * 自定义重试间隔列表(分钟),逗号分隔
     * 仅在 backoffStrategy = CUSTOM 时生效
     * 示例：1,5,10,30
     */
    private String retryIntervals;

    /** 最大重试次数 */
    private Integer maxRetryCount;

    /**
     * 退避策略
     * CUSTOM(默认) / FIXED / LINEAR / EXPONENTIAL
     * @see BackoffStrategy
     */
    private String backoffStrategy;

    /**
     * 退避基数（分钟）
     * FIXED/LINEAR/EXPONENTIAL 策略下的基础时间单位
     * 默认值：1
     */
    private Integer backoffBase;

    /**
     * 最大重试总时长（秒）
     * 0 表示不限制时长，只按次数控制
     * 超过此时长后，即使重试次数未耗尽也终止重试
     */
    private Integer maxRetryDuration;

    /** 钩子类全限定名（实现 RetryHook 接口的类） */
    private String hookClass;

    /** 客户端应用回调 URL */
    private String clientAppUrl;

    /** 是否启用: 0-禁用, 1-启用 */
    private Integer enabled;

    @com.fasterxml.jackson.annotation.JsonGetter("enabled")
    public Boolean getEnabledAsBoolean() {
        return isEnabled();
    }

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 解析自定义重试间隔列表（仅 CUSTOM 策略使用）
     *
     * @return 重试间隔列表（分钟）
     */
    public List<Integer> getRetryIntervalList() {
        if (retryIntervals == null || retryIntervals.trim().isEmpty()) {
            return Arrays.asList();
        }
        return Arrays.stream(retryIntervals.split(","))
                .map(String::trim)
                .map(Integer::parseInt)
                .collect(Collectors.toList());
    }

    /**
     * 获取解析后的退避策略枚举
     *
     * @return BackoffStrategy 枚举值，未配置则返回 CUSTOM
     */
    public BackoffStrategy getBackoffStrategyEnum() {
        return BackoffStrategy.fromName(this.backoffStrategy);
    }

    /**
     * 获取退避基数，兜底默认 1 分钟
     */
    public int getBackoffBaseOrDefault() {
        return (backoffBase != null && backoffBase > 0) ? backoffBase : 1;
    }

    /**
     * 获取最大重试时长（秒），0 表示不限制
     */
    public int getMaxRetryDurationOrZero() {
        return (maxRetryDuration != null && maxRetryDuration > 0) ? maxRetryDuration : 0;
    }

    /** 判断是否启用 */
    public boolean isEnabled() {
        return enabled != null && enabled == 1;
    }

    /** 处理前端传来的 boolean 或 integer 类型的 enabled */
    @com.fasterxml.jackson.annotation.JsonSetter("enabled")
    public void setEnabledFromJson(Object value) {
        if (value instanceof Boolean) {
            this.enabled = ((Boolean) value) ? 1 : 0;
        } else if (value instanceof Number) {
            this.enabled = ((Number) value).intValue();
        } else if (value instanceof String) {
            if ("true".equalsIgnoreCase((String) value)) {
                this.enabled = 1;
            } else if ("false".equalsIgnoreCase((String) value)) {
                this.enabled = 0;
            } else {
                this.enabled = Integer.parseInt((String) value);
            }
        }
    }
}
