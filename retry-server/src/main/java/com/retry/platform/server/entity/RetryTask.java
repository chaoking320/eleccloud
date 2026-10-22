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
     * 提交模式: POST_FAIL-失败后提交(默认), PRE_SUBMIT-执行前预注册
     */
    private String submitMode;
    
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
     * Hook 类名（用于回查与幂等回调）
     */
    private String hookClass;

    /**
     * 方法参数类型列表（逗号分隔，用于精确反射匹配重载方法）
     */
    private String methodParamTypes;

    /**
     * 退避策略：CUSTOM/FIXED/LINEAR/EXPONENTIAL
     */
    private String backoffStrategy;

    /**
     * 退避基数（分钟）
     */
    private Integer backoffBase;

    /**
     * 自定义重试间隔（分钟，逗号分隔）
     */
    private String retryIntervals;

    /**
     * 最近一次执行失败的错误信息
     */
    private String errorMsg;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
