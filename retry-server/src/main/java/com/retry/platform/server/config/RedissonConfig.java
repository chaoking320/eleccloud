package com.retry.platform.server.config;

import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson配置
 * 用于分布式锁实现
 */
@Slf4j
@Configuration
public class RedissonConfig {
    
    @Value("${spring.redis.redisson.config}")
    private String redissonConfig;
    
    /**
     * 创建RedissonClient
     * 使用YAML配置方式
     */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        try {
            Config config = Config.fromYAML(redissonConfig);
            RedissonClient redissonClient = Redisson.create(config);
            log.info("Redisson client initialized successfully");
            return redissonClient;
        } catch (Exception e) {
            log.error("Failed to initialize Redisson client", e);
            throw new RuntimeException("Failed to initialize Redisson client", e);
        }
    }
}
