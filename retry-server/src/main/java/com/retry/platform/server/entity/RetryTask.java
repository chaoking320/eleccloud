package com.retry.platform.server.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 重试任务实体类
 */
@Data
public class RetryTask {
    
    /**
     * 主键ID
     */
    private Long id;
    
    /**
     * 任务ID
     */
    private String taskId;
    
    /**
     * 场景类型
     */
    private Integer sceneType;
    
    /**
     * 幂等键
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
     * 任务状态: INIT/WAIT/SUCCESS/FAILED
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
     * 下次重试时间戳(毫秒)
     */
    private Long nextRetryTime;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
