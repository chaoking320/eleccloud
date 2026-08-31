package com.retry.platform.client.config;

import com.retry.platform.client.api.RetryClient;
import com.retry.platform.client.api.impl.RetryClientImpl;
import com.retry.platform.client.aspect.RetryableTaskAspect;
import com.retry.platform.client.executor.LocalRetryExecutor;
import com.retry.platform.client.mq.RetryMessageProducer;
import com.retry.platform.client.mq.redis.RedisRetryMessageConsumer;
import com.retry.platform.client.mq.redis.RedisRetryMessageProducer;
import com.retry.platform.client.mq.rabbit.RabbitRetryMessageConsumer;
import com.retry.platform.client.mq.rabbit.RabbitRetryMessageProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;

/**
 * 重试客户端自动配置类
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(RetryClientProperties.class)
@ConditionalOnProperty(prefix = "retry.client", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RetryClientAutoConfiguration {
    
    private final RetryClientProperties properties;
    
    public RetryClientAutoConfiguration(RetryClientProperties properties) {
        this.properties = properties;
    }
    
    /**
     * 启动时配置校验 - 快速失败原则
     * 在应用启动时立即检测配置问题，避免运行时才发现错误
     */
    @PostConstruct
    public void validateConfiguration() {
        log.info("========================================");
        log.info("ElecCloud Retry Client Configuration Validation");
        log.info("========================================");
        
        // 1. 校验 server-url（必填）
        if (properties.getServerUrl() == null || properties.getServerUrl().trim().isEmpty()) {
            throw new IllegalStateException(
                "❌ Configuration Error: retry.client.server-url is required but not configured.\n" +
                "\n" +
                "The Retry Client needs to know where the Retry Server is located.\n" +
                "\n" +
                "Solution: Add the following to your application.yml or application.properties:\n" +
                "\n" +
                "YAML format (application.yml):\n" +
                "  retry:\n" +
                "    client:\n" +
                "      enabled: true\n" +
                "      server-url: http://retry-server:8080  # Change to your actual server address\n" +
                "      api-key: your-api-key-here            # Required if server security is enabled\n" +
                "\n" +
                "Properties format (application.properties):\n" +
                "  retry.client.enabled=true\n" +
                "  retry.client.server-url=http://retry-server:8080\n" +
                "  retry.client.api-key=your-api-key-here\n" +
                "\n" +
                "Common server URLs:\n" +
                "  - Local development: http://localhost:8080\n" +
                "  - Docker Compose: http://retry-server:8080\n" +
                "  - Production: https://retry.yourcompany.com\n"
            );
        }
        
        // 2. 校验 server-url 格式
        String serverUrl = properties.getServerUrl().trim();
        if (!serverUrl.startsWith("http://") && !serverUrl.startsWith("https://")) {
            throw new IllegalStateException(
                "❌ Configuration Error: retry.client.server-url must start with 'http://' or 'https://'.\n" +
                "Current value: " + serverUrl + "\n" +
                "\n" +
                "Valid examples:\n" +
                "  ✓ http://localhost:8080\n" +
                "  ✓ https://retry.yourcompany.com\n" +
                "  ✗ localhost:8080 (missing protocol)\n" +
                "  ✗ retry-server (missing protocol)\n"
            );
        }
        
        // 3. 校验超时配置
        if (properties.getConnectTimeout() <= 0) {
            log.warn("⚠️  Invalid retry.client.connect-timeout: {}ms. Using default: 5000ms", 
                    properties.getConnectTimeout());
            properties.setConnectTimeout(5000);
        } else if (properties.getConnectTimeout() < 1000) {
            log.warn("⚠️  retry.client.connect-timeout is very short: {}ms. Recommended: >= 3000ms", 
                    properties.getConnectTimeout());
        }
        
        if (properties.getReadTimeout() <= 0) {
            log.warn("⚠️  Invalid retry.client.read-timeout: {}ms. Using default: 30000ms", 
                    properties.getReadTimeout());
            properties.setReadTimeout(30000);
        } else if (properties.getReadTimeout() < 5000) {
            log.warn("⚠️  retry.client.read-timeout is very short: {}ms. May cause timeout for slow operations.", 
                    properties.getReadTimeout());
        }
        
        // 4. 校验消费者并发度
        if (properties.getConsumerConcurrency() <= 0) {
            log.warn("⚠️  Invalid retry.client.consumer-concurrency: {}. Using default: 5", 
                    properties.getConsumerConcurrency());
            properties.setConsumerConcurrency(5);
        } else if (properties.getConsumerConcurrency() > 50) {
            log.warn("⚠️  retry.client.consumer-concurrency is very high: {}. May cause resource exhaustion. Recommended: <= 20", 
                    properties.getConsumerConcurrency());
        }
        
        // 5. 校验队列名称
        if (properties.getQueueName() == null || properties.getQueueName().trim().isEmpty()) {
            log.warn("⚠️  retry.client.queue-name not configured. Using default: retry-tasks");
            properties.setQueueName("retry-tasks");
        }
        
        // 6. 校验 MQ 类型
        String mqType = properties.getMqType();
        if (mqType != null && !mqType.isEmpty()) {
            if (!"REDIS".equalsIgnoreCase(mqType) && !"RABBITMQ".equalsIgnoreCase(mqType)) {
                throw new IllegalStateException(
                    "❌ Configuration Error: Invalid retry.client.mq-type value: " + mqType + "\n" +
                    "\n" +
                    "Supported values:\n" +
                    "  - REDIS (default, recommended for most cases)\n" +
                    "  - RABBITMQ (requires RabbitMQ server)\n" +
                    "\n" +
                    "Current value: " + mqType + "\n" +
                    "Solution: Change to one of the supported values.\n"
                );
            }
        }
        
        // 7. API Key 检查（警告，不强制）
        if (properties.getApiKey() == null || properties.getApiKey().trim().isEmpty()) {
            if (!properties.isDevMode()) {
                log.warn("========================================");
                log.warn("⚠️  SECURITY WARNING");
                log.warn("========================================");
                log.warn("retry.client.api-key is not configured.");
                log.warn("If the Retry Server has security enabled, requests will be rejected.");
                log.warn("");
                log.warn("Solution: Add to your configuration:");
                log.warn("  retry.client.api-key: <your-api-key>");
                log.warn("");
                log.warn("To obtain an API key, contact your Retry Server administrator");
                log.warn("or check the server's API key management interface.");
                log.warn("========================================");
            }
        }
        
        // 输出配置摘要
        log.info("✅ Configuration validation passed!");
        log.info("Configuration Summary:");
        log.info("  - Server URL: {}", properties.getServerUrl());
        log.info("  - Dev Mode: {}", properties.isDevMode());
        log.info("  - API Key: {}", properties.getApiKey() != null ? "***configured***" : "not configured");
        log.info("  - MQ Type: {}", properties.getMqType());
        log.info("  - Queue Name: {}", properties.getQueueName());
        log.info("  - Consumer Enabled: {}", properties.isConsumerEnabled());
        log.info("  - Consumer Concurrency: {}", properties.getConsumerConcurrency());
        log.info("  - Connect Timeout: {}ms", properties.getConnectTimeout());
        log.info("  - Read Timeout: {}ms", properties.getReadTimeout());
        log.info("========================================");
    }
    
    /**
     * 配置RestTemplate（支持API Key鉴权）
     */
    @Bean
    @ConditionalOnMissingBean(name = "retryRestTemplate")
    public RestTemplate retryRestTemplate(RetryClientProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeout());
        factory.setReadTimeout(properties.getReadTimeout());
        
        RestTemplate restTemplate = new RestTemplate(factory);
        
        // 如果配置了API Key，添加拦截器自动注入Header
        if (properties.getApiKey() != null && !properties.getApiKey().isEmpty()) {
            restTemplate.getInterceptors().add((request, body, execution) -> {
                request.getHeaders().add("X-API-Key", properties.getApiKey());
                return execution.execute(request, body);
            });
            log.info("RetryClient RestTemplate initialized with API Key authentication");
        }
        
        log.info("RetryClient RestTemplate initialized. ServerUrl: {}, DevMode: {}", 
                properties.getServerUrl(), properties.isDevMode());
        return restTemplate;
    }
    
    /**
     * 注册RetryClient Bean
     */
    @Bean
    @ConditionalOnMissingBean
    public RetryClient retryClient() {
        log.info("RetryClient bean registered");
        return new RetryClientImpl();
    }
    
    /**
     * 注册AOP切面
     */
    @Bean
    @ConditionalOnMissingBean
    public RetryableTaskAspect retryableTaskAspect() {
        log.info("RetryableTaskAspect bean registered");
        return new RetryableTaskAspect();
    }

    /**
     * 注册本地重试状态机驱动器
     */
    @Bean
    @ConditionalOnMissingBean
    public LocalRetryExecutor localRetryExecutor() {
        log.info("LocalRetryExecutor bean registered");
        return new LocalRetryExecutor();
    }

    // ==================== MQ 双模式条件装配 ====================

    /**
     * 1. 装配 REDIS 生产者
     */
    @Bean
    @ConditionalOnProperty(prefix = "retry.client", name = "mq-type", havingValue = "REDIS", matchIfMissing = true)
    @ConditionalOnClass(StringRedisTemplate.class)
    public RetryMessageProducer redisRetryMessageProducer(StringRedisTemplate stringRedisTemplate,
                                                           RetryClientProperties properties) {
        log.info("[Config] Initializing REDIS-based ZSET RetryMessageProducer");
        return new RedisRetryMessageProducer(stringRedisTemplate, properties.getQueueName());
    }

    /**
     * 2. 装配 REDIS 消费者（仅在 client 端消费；服务端或控制台设 consumer-enabled: false 时不启动）
     */
    @Bean
    @ConditionalOnProperty(prefix = "retry.client", name = "consumer-enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnClass(StringRedisTemplate.class)
    public RedisRetryMessageConsumer redisRetryMessageConsumer(StringRedisTemplate stringRedisTemplate,
                                                               LocalRetryExecutor localRetryExecutor,
                                                               RetryClientProperties properties) {
        if (!"REDIS".equalsIgnoreCase(properties.getMqType())) {
            return null;
        }
        log.info("[Config] Initializing REDIS-based ZSET RedisRetryMessageConsumer with concurrency: {}, queueName: {}", 
                properties.getConsumerConcurrency(), properties.getQueueName());
        return new RedisRetryMessageConsumer(stringRedisTemplate, localRetryExecutor, properties.getConsumerConcurrency(), properties.getQueueName());
    }

    /**
     * 3. 装配 RABBITMQ 生产者
     */
    @Bean
    @ConditionalOnProperty(prefix = "retry.client", name = "mq-type", havingValue = "RABBITMQ")
    @ConditionalOnClass(RabbitTemplate.class)
    public RetryMessageProducer rabbitRetryMessageProducer(RabbitTemplate rabbitTemplate,
                                                           RetryClientProperties properties) {
        log.info("[Config] Initializing RABBITMQ-based RetryMessageProducer");
        return new RabbitRetryMessageProducer(rabbitTemplate, properties.getQueueName());
    }

    /**
     * 4. 装配 RABBITMQ 消费者（仅在 client 端消费；服务端或控制台设 consumer-enabled: false 时不启动）
     */
    @Bean
    @ConditionalOnProperty(prefix = "retry.client", name = "consumer-enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnClass(RabbitTemplate.class)
    public RabbitRetryMessageConsumer rabbitRetryMessageConsumer(LocalRetryExecutor localRetryExecutor,
                                                                RetryClientProperties properties) {
        if (!"RABBITMQ".equalsIgnoreCase(properties.getMqType())) {
            return null;
        }
        log.info("[Config] Initializing RABBITMQ-based RabbitRetryMessageConsumer");
        return new RabbitRetryMessageConsumer(localRetryExecutor);
    }
}
