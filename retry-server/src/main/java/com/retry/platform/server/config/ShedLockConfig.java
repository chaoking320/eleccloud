package com.retry.platform.server.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT5M")  // 默认最多持有锁 5 分钟
public class ShedLockConfig {
    @Bean
    public LockProvider lockProvider(RedisConnectionFactory connectionFactory) {
        return new RedisLockProvider(connectionFactory, "retry-server");
    }
}
