package com.retry.platform.server.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 重试历史实体类
 */
@Data
public class RetryHistory {
    
    /**
     * 主键ID
     */
    private Long id;
    
    /**
     * 任务ID
     */
    private String taskId;
    
    /**
     * 重试次数
     */
    private Integer retryCount;
    
    /**
     * 执行时间
     */
    private LocalDateTime executeTime;
    
    /**
     * 执行结果: SUCCESS/FAILED/RETRY
     */
    private String executeResult;
    
    /**
     * 错误信息
     */
    private String errorMessage;
    
    /**
     * 耗时(ms)
     */
    private Integer costTime;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
