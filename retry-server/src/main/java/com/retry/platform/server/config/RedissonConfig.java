package com.retry.platform.server.config;

import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
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
    
    @Value("${spring.redis.redisson.config:#{null}}")
    private String redissonConfig;

    @Value("${spring.redis.host:127.0.0.1}")
    private String redisHost;

    @Value("${spring.redis.port:6379}")
    private int redisPort;

    @Value("${spring.redis.password:#{null}}")
    private String redisPassword;

    @Value("${spring.redis.database:2}")
    private int redisDatabase;
    
    /**
     * 创建RedissonClient
     * 优先使用YAML配置（自动处理转义换行符），若无或解析失败则回退至标准Spring Redis配置
     */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        if (redissonConfig != null && !redissonConfig.trim().isEmpty()) {
            try {
                // 兼容环境变量中字面量 \r\n 或 \n 转义符
                String normalizedYaml = redissonConfig
                        .replace("\\r\\n", "\n")
                        .replace("\\n", "\n");
                Config config = Config.fromYAML(normalizedYaml);
                RedissonClient redissonClient = Redisson.create(config);
                log.info("Redisson client initialized successfully from YAML config");
                return redissonClient;
            } catch (Exception e) {
                log.warn("Failed to parse Redisson YAML config, falling back to programmatic host/port configuration. Error: {}", e.getMessage());
            }
        }

        try {
            Config config = new Config();
            String address = "redis://" + redisHost + ":" + redisPort;
            SingleServerConfig singleServer = config.useSingleServer()
                    .setAddress(address)
                    .setDatabase(redisDatabase);
            if (redisPassword != null && !redisPassword.trim().isEmpty()) {
                singleServer.setPassword(redisPassword.trim());
            }
            RedissonClient redissonClient = Redisson.create(config);
            log.info("Redisson client initialized successfully using fallback config: address={}, database={}", address, redisDatabase);
            return redissonClient;
        } catch (Exception e) {
            log.error("Failed to initialize Redisson client via fallback configuration", e);
            throw new RuntimeException("Failed to initialize Redisson client", e);
        }
    }
}
