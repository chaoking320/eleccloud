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

/**
 * 重试客户端自动配置类
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(RetryClientProperties.class)
@ConditionalOnProperty(prefix = "retry.client", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RetryClientAutoConfiguration {
    
    /**
     * 配置RestTemplate
     */
    @Bean
    @ConditionalOnMissingBean(name = "retryRestTemplate")
    public RestTemplate retryRestTemplate(RetryClientProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeout());
        factory.setReadTimeout(properties.getReadTimeout());
        
        RestTemplate restTemplate = new RestTemplate(factory);
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
     * 2. 装配 REDIS 消费者
     */
    @Bean
    @ConditionalOnProperty(prefix = "retry.client", name = "mq-type", havingValue = "REDIS", matchIfMissing = true)
    @ConditionalOnClass(StringRedisTemplate.class)
    public RedisRetryMessageConsumer redisRetryMessageConsumer(StringRedisTemplate stringRedisTemplate,
                                                               LocalRetryExecutor localRetryExecutor,
                                                               RetryClientProperties properties) {
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
     * 4. 装配 RABBITMQ 消费者
     */
    @Bean
    @ConditionalOnProperty(prefix = "retry.client", name = "mq-type", havingValue = "RABBITMQ")
    @ConditionalOnClass(RabbitTemplate.class)
    public RabbitRetryMessageConsumer rabbitRetryMessageConsumer(LocalRetryExecutor localRetryExecutor) {
        log.info("[Config] Initializing RABBITMQ-based RabbitRetryMessageConsumer");
        return new RabbitRetryMessageConsumer(localRetryExecutor);
    }
}
