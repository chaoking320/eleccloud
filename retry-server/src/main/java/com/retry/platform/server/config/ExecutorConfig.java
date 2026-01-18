package com.retry.platform.server.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 线程池配置
 * 用于异步执行重试任务
 */
@Slf4j
@Configuration
public class ExecutorConfig {
    
    @Value("${retry.executor.core-pool-size:10}")
    private int corePoolSize;
    
    @Value("${retry.executor.max-pool-size:50}")
    private int maxPoolSize;
    
    @Value("${retry.executor.queue-capacity:1000}")
    private int queueCapacity;
    
    @Value("${retry.executor.thread-name-prefix:retry-executor-}")
    private String threadNamePrefix;
    
    /**
     * 创建重试任务执行线程池
     */
    @Bean(name = "retryTaskThreadPoolExecutor")
    public Executor retryTaskThreadPoolExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 核心线程数
        executor.setCorePoolSize(corePoolSize);
        
        // 最大线程数
        executor.setMaxPoolSize(maxPoolSize);
        
        // 队列容量
        executor.setQueueCapacity(queueCapacity);
        
        // 线程名称前缀
        executor.setThreadNamePrefix(threadNamePrefix);
        
        // 拒绝策略：由调用线程执行
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        // 等待所有任务完成后再关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);
        
        // 等待时间
        executor.setAwaitTerminationSeconds(60);
        
        executor.initialize();
        
        log.info("Retry task executor initialized: corePoolSize={}, maxPoolSize={}, queueCapacity={}", 
                corePoolSize, maxPoolSize, queueCapacity);
        
        return executor;
    }
}
