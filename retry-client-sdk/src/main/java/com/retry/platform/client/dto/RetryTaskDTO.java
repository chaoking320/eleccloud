package com.retry.platform.client.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 重试任务DTO
 */
@Data
public class RetryTaskDTO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 任务ID
     */
    private String taskId;
    
    /**
     * 场景类型
     */
    private Integer sceneType;
    
    /**
     * 幂等键值
     */
    private String idempotentKey;
    
    /**
     * 方法类名
     */
    private String methodClass;
    
    /**
     * 方法名
     */
    private String methodName;
    
    /**
     * 方法参数JSON
     */
    private String methodParams;
    
    /**
     * 任务状态
     */
    private String taskStatus;
    
    /**
     * 重试次数
     */
    private Integer retryCount;
    
    /**
     * 最大重试次数
     */
    private Integer maxRetryCount;
    
    /**
     * 下次重试时间戳
     */
    private Long nextRetryTime;
    
    /**
     * 钩子全路径类名
     */
    private String hookClass;

    /**
     * 退避策略
     */
    private String backoffStrategy;

    /**
     * 退避基数
     */
    private Integer backoffBase;

    /**
     * 重试间隔列表(逗号分隔，CUSTOM模式使用)
     */
    private String retryIntervals;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
