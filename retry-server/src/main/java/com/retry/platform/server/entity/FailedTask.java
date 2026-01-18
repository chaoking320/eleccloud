package com.retry.platform.server.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 失败任务实体类
 */
@Data
public class FailedTask {
    
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
     * 重试次数
     */
    private Integer retryCount;
    
    /**
     * 失败原因
     */
    private String failReason;
    
    /**
     * 任务创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 失败时间
     */
    private LocalDateTime failTime;
}
