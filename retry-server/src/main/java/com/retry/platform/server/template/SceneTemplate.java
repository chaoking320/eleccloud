package com.retry.platform.server.template;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 场景配置模板
 * 
 * <p>提供常见重试场景的推荐配置，降低用户配置难度。
 * 
 * @since 1.1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SceneTemplate {
    
    /**
     * 模板ID
     */
    private String templateId;
    
    /**
     * 模板名称
     */
    private String templateName;
    
    /**
     * 模板描述
     */
    private String description;
    
    /**
     * 适用场景说明
     */
    private String applicableScenarios;
    
    /**
     * 退避策略
     */
    private String backoffStrategy;
    
    /**
     * 退避基数（分钟）
     */
    private Integer backoffBase;
    
    /**
     * 重试间隔（逗号分隔，分钟）
     */
    private String retryIntervals;
    
    /**
     * 最大重试次数
     */
    private Integer maxRetryCount;
    
    /**
     * 最大重试时长（秒，0表示不限）
     */
    private Integer maxRetryDuration;
    
    /**
     * 是否推荐使用默认Hook
     */
    private Boolean useDefaultHook;
    
    /**
     * 配置说明
     */
    private String configNotes;
    
    /**
     * 使用示例
     */
    private String exampleCode;
}
