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

    /**
     * 方法参数类型列表（逗号分隔的全限定类名）
     * 例: "java.lang.String,java.lang.Integer,java.lang.Double"
     * <p>用于 LocalRetryExecutor 反射重建方法调用时，按方法名 + 参数类型独立定位重载方法，
     * 避免原来仅按参数数量匹配导致的重载歧义。
     */
    private String methodParamTypes;
}