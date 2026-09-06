package com.retry.platform.client.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

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

    /**
     * 消息队列类型：REDIS / RABBITMQ
     */
    private String mqType = "REDIS";

    /**
     * 本地并发消费最大线程数
     */
    private int consumerConcurrency = 5;

    /**
     * 业务线专用延迟消息队列主题名称 (多业务隔离配置)
     */
    private String queueName = "retry.delayed.queue";

    /**
     * 是否启用本地重试消费者（服务端或Admin只发消息不消费时设为 false）
     */
    private boolean consumerEnabled = true;

    /**
     * API Key（用于服务端鉴权）
     */
    private String apiKey;

    /**
     * 运行模式：remote（默认，依赖远程 retry-server）/ standalone（本地模式，无需 Server）
     */
    private String mode = "remote";

    /**
     * Standalone 模式下的场景配置列表（替代 Server DB 中的 scene_config 表）
     * 每个场景对应一个 @RetryableTask 使用的 sceneType
     */
    private List<StandaloneSceneConfig> scenes = new ArrayList<>();
}
