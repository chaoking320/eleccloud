package com.retry.platform.client.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 重试客户端配置属性
 */
@Data
@ConfigurationProperties(prefix = "retry.client")
public class RetryClientProperties {
    
    /**
     * 重试服务端地址
     */
    private String serverUrl = "http://localhost:8080";
    
    /**
     * 是否启用重试客户端
     */
    private boolean enabled = true;
    
    /**
     * 开发模式（开启后重试任务仅记录日志不实际发送）
     */
    private boolean devMode = false;
    
    /**
     * HTTP连接超时时间（毫秒）
     */
    private int connectTimeout = 5000;
    
    /**
     * HTTP读取超时时间（毫秒）
     */
    private int readTimeout = 10000;
}
