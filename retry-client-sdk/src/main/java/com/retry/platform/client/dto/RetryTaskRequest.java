package com.retry.platform.client.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 重试任务请求DTO
 *
 * <p>支持三级策略配置（优先级从高到低）：
 * <ol>
 *   <li>本 DTO 字段（代码/注解直接指定，最高优先级）</li>
 *   <li>application.yml scenes 配置</li>
 *   <li>SDK 内置默认值（maxRetryCount=3, retryIntervals="1,3,5"）</li>
 * </ol>
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

    // ==================== 策略字段（注解/API 直接指定，免 YAML 配置）====================

    /**
     * 最大重试次数
     * <p>null 或 0 表示未指定，将从 YAML scenes 配置读取，再无则使用 SDK 默认值(3)
     */
    private Integer maxRetryCount;

    /**
     * 自定义重试间隔（分钟，逗号分隔的整数列表）
     * <p>格式: "1,3,5,10,30" 表示第1次等1分钟、第2次等3分钟，超出范围取最后一个值
     * <p>null 或空字符串表示未指定，将从 YAML scenes 配置读取，再无则使用 SDK 默认值("1,3,5")
     */
    private String retryIntervals;

    /**
     * 退避策略: CUSTOM(默认) / FIXED / LINEAR / EXPONENTIAL
     * <p>null 表示未指定，将从 YAML scenes 配置读取，再无则使用 CUSTOM
     */
    private String backoffStrategy;

    /**
     * 退避基数（分钟），FIXED/LINEAR/EXPONENTIAL 策略使用
     * <p>null 或 0 表示未指定，将从 YAML scenes 配置读取，再无则使用 SDK 默认值(1)
     */
    private Integer backoffBase;

    /**
     * Hook 类全限定名（可选）
     * <p>null 或空字符串表示未指定，将从 YAML scenes 配置读取
     * <p>注：通过注解声明时请使用 RetryableTask.hookClass = XxxHook.class（IDE 可跳转），
     *     Aspect 会自动将 Class 转为全限定名写入此字段
     */
    private String hookClass;
}