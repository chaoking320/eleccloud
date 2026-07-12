package com.retry.platform.client.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 重试任务请求DTO
 */
@Data
public class RetryTaskRequest implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 场景类型
     */
    private Integer sceneType;
    
    /**
     * 幂等键值
     */
    private String idempotentKey;
    
    /**
     * 方法类名（全限定名）
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
     * 是否异步提交
     */
    private Boolean async;

    /**
     * 提交模式: POST_FAIL(默认) / PRE_SUBMIT(预提交)
     */
    private String submitMode;
}
