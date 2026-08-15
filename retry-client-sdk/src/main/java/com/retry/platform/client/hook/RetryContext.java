package com.retry.platform.client.hook;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 重试任务上下文
 * 传递给钩子方法的上下文信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetryContext {
    
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
     * 方法参数（JSON反序列化后的Map）
     */
    private Map<String, Object> params;
    
    /**
     * 当前重试次数
     */
    private Integer retryCount;
    
    /**
     * 最大重试次数
     */
    private Integer maxRetryCount;
    
    /**
     * 方法类名
     */
    private String methodClass;
    
    /**
     * 方法名
     */
    private String methodName;
    
    /**
     * 方法参数JSON字符串
     */
    private String methodParamsJson;

    /**
     * 方法参数类型列表（逗号分隔的全限定类名）
     * 由 RetryTaskDTO.methodParamTypes 填入，用于 findMethod 按类型签名精确定位重载方法。
     */
    private String methodParamTypes;
}
