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
import com.retry.platform.client.standalone.StandaloneDatabaseFallbackScheduler;
import com.retry.platform.client.standalone.StandaloneRetryClientImpl;
import com.retry.platform.client.standalone.StandaloneRetryHistoryMapper;
import com.retry.platform.client.standalone.StandaloneRetryTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;

/**
 * 重试客户端自动配置类
 *
 * <p>支持两种运行模式，通过 {@code retry.client.mode} 切换：
 * <ul>
 *   <li>{@code remote}（默认）：依赖远程 retry-server，HTTP 调用存取任务</li>
 *   <li>{@code standalone}：本地 DB 直读写，无需部署 retry-server</li>
 * </ul>
 */
@Slf4j
@Configuration
@EnableScheduling
@EnableConfigurationProperties(RetryClientProperties.class)
@ConditionalOnProperty(prefix = "retry.client", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RetryClientAutoConfiguration {
    
    private final RetryClientProperties properties;
    private final org.springframework.core.env.Environment environment;
    
    public RetryClientAutoConfiguration(RetryClientProperties properties, org.springframework.core.env.Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }
    
    /**
     * 启动时配置校验 - 快速失败原则
     */
    @PostConstruct
    public void validateConfiguration() {
        log.info("========================================");
        log.info("ElecCloud Retry Client Configuration Validation");
        log.info("========================================");

        String mode = properties.getMode() != null ? properties.getMode().toLowerCase() : "remote";

        if ("standalone".equals(mode)) {
            // Standalone 模式：不需要 serverUrl，但需要至少配置一个场景
            log.info("Running in STANDALONE mode (local DB, no retry-server required)");
            if (properties.getScenes() == null || properties.getScenes().isEmpty()) {
                log.warn("⚠️  retry.client.scenes is empty in standalone mode. " +
                         "No retryable scenes configured. All @RetryableTask submissions will fail.");
            } else {
                log.info("  - Standalone scenes configured: {}", properties.getScenes().size());
                properties.getScenes().forEach(s ->
                    log.info("    scene-type={}, name={}, maxRetry={}, intervals={}, hookClass={}",
                        s.getSceneType(), s.getSceneName(), s.getMaxRetryCount(),
                        s.getRetryIntervals(), s.getHookClass())
                );
            }
        } else {
            // Remote 模式：校验 server-url（必填）
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
                    "Or switch to standalone mode (no server needed):\n" +
                    "  retry.client.mode=standalone\n"
                );
            }
            
            // 校验 server-url 格式
            String serverUrl = properties.getServerUrl().trim();
            if (!serverUrl.startsWith("http://") && !serverUrl.startsWith("https://")) {
                throw new IllegalStateException(
                    "❌ Configuration Error: retry.client.server-url must start with 'http://' or 'https://'.\n" +
                    "Current value: " + serverUrl
                );
            }
            log.info("Running in REMOTE mode. Server URL: {}", serverUrl);
        }
        
        // 通用校验
        if (properties.getConnectTimeout() <= 0) {
            properties.setConnectTimeout(5000);
        }
        if (properties.getReadTimeout() <= 0) {
            properties.setReadTimeout(30000);
        }
        if (properties.getConsumerConcurrency() <= 0) {
            properties.setConsumerConcurrency(5);
        }
        if (properties.getQueueName() == null || properties.getQueueName().trim().isEmpty()) {
            properties.setQueueName("retry.delayed.queue");
        }
        if ("retry.delayed.queue".equals(properties.getQueueName())) {
            String appName = environment.getProperty("spring.application.name");
            if (appName != null && !appName.isEmpty()) {
                properties.setQueueName("retry.delayed.queue." + appName);
                log.info("Auto-configured queueName to: {}", properties.getQueueName());
            }
        }
        String mqType = properties.getMqType();
        if (mqType != null && !mqType.isEmpty()) {
            if (!"REDIS".equalsIgnoreCase(mqType) && !"RABBITMQ".equalsIgnoreCase(mqType)) {
                throw new IllegalStateException(
                    "❌ Configuration Error: Invalid retry.client.mq-type value: " + mqType +
                    ". Supported values: REDIS, RABBITMQ"
                );
            }
        }
        
        log.info("✅ Configuration validation passed!");
        log.info("Configuration Summary:");
        log.info("  - Mode: {}", mode);
        log.info("  - MQ Type: {}", properties.getMqType());
        log.info("  - Queue Name: {}", properties.getQueueName());
        log.info("  - Consumer Enabled: {}", properties.isConsumerEnabled());
        log.info("  - Consumer Concurrency: {}", properties.getConsumerConcurrency());
        log.info("========================================");
    }
    
    // ==================== Remote 模式：RestTemplate + RetryClientImpl ====================

    /**
     * Remote 模式专用 RestTemplate（含 API Key 鉴权拦截器）
     */
    @Bean("retryRestTemplate")
    @ConditionalOnMissingBean(name = "retryRestTemplate")
    @ConditionalOnProperty(prefix = "retry.client", name = "mode", havingValue = "remote", matchIfMissing = true)
    public RestTemplate retryRestTemplate(RetryClientProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeout());
        factory.setReadTimeout(properties.getReadTimeout());
        
        RestTemplate restTemplate = new RestTemplate(factory);
        
        if (properties.getApiKey() != null && !properties.getApiKey().isEmpty()) {
            restTemplate.getInterceptors().add((request, body, execution) -> {
                request.getHeaders().add("X-API-Key", properties.getApiKey());
                return execution.execute(request, body);
            });
        }
        log.info("RetryClient RestTemplate initialized. ServerUrl: {}", properties.getServerUrl());
        return restTemplate;
    }

    /**
     * Remote 模式下注册 RetryClientImpl（HTTP 实现）
     */
    @Bean
    @ConditionalOnMissingBean(RetryClient.class)
    @ConditionalOnProperty(prefix = "retry.client", name = "mode", havingValue = "remote", matchIfMissing = true)
    public RetryClient retryClientRemote() {
        log.info("[Remote] RetryClientImpl (HTTP) bean registered");
        return new RetryClientImpl();
    }

    // ==================== Standalone 模式：本地 DB Mapper + StandaloneRetryClientImpl ====================

    /**
     * Standalone 模式内部专用的 SqlSessionFactory（指向业务方主 DataSource）
     * 注意：绝不向 Spring 容器注册为公共 @Bean，彻底消除与宿主应用（MyBatis-Plus、Jeecg、多数据源）
     * 的 SqlSessionFactory 产生多候选 Bean（NoUniqueBeanDefinitionException）冲突！
     */
    private volatile SqlSessionFactory standaloneInternalSqlSessionFactory;

    private synchronized SqlSessionFactory getOrCreateStandaloneSqlSessionFactory(DataSource dataSource) throws Exception {
        if (standaloneInternalSqlSessionFactory == null) {
            SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
            factoryBean.setDataSource(dataSource);
            // 加载 SDK 内置的 Mapper XML
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            factoryBean.setMapperLocations(
                    resolver.getResources("classpath:mapper/standalone/*.xml"));
            standaloneInternalSqlSessionFactory = factoryBean.getObject();
            log.info("[Standalone] Isolated SqlSessionFactory initialized with mapper: classpath:mapper/standalone/*.xml");
        }
        return standaloneInternalSqlSessionFactory;
    }

    /**
     * Standalone 模式下注册 StandaloneRetryTaskMapper
     */
    @Bean
    @ConditionalOnProperty(prefix = "retry.client", name = "mode", havingValue = "standalone")
    public StandaloneRetryTaskMapper standaloneRetryTaskMapper(DataSource dataSource) throws Exception {
        MapperFactoryBean<StandaloneRetryTaskMapper> factoryBean = new MapperFactoryBean<>(StandaloneRetryTaskMapper.class);
        factoryBean.setSqlSessionFactory(getOrCreateStandaloneSqlSessionFactory(dataSource));
        return factoryBean.getObject();
    }

    /**
     * Standalone 模式下注册 StandaloneRetryHistoryMapper
     */
    @Bean
    @ConditionalOnProperty(prefix = "retry.client", name = "mode", havingValue = "standalone")
    public StandaloneRetryHistoryMapper standaloneRetryHistoryMapper(DataSource dataSource) throws Exception {
        MapperFactoryBean<StandaloneRetryHistoryMapper> factoryBean = new MapperFactoryBean<>(StandaloneRetryHistoryMapper.class);
        factoryBean.setSqlSessionFactory(getOrCreateStandaloneSqlSessionFactory(dataSource));
        return factoryBean.getObject();
    }

    /**
     * Standalone 模式下注册 RetryClient（本地 DB 实现，替代 HTTP）
     */
    @Bean
    @ConditionalOnMissingBean(RetryClient.class)
    @ConditionalOnProperty(prefix = "retry.client", name = "mode", havingValue = "standalone")
    public RetryClient retryClientStandalone(StandaloneRetryTaskMapper taskMapper,
                                             StandaloneRetryHistoryMapper historyMapper) {
        log.info("[Standalone] StandaloneRetryClientImpl (local DB) bean registered");
        return new StandaloneRetryClientImpl(taskMapper, historyMapper, properties);
    }

    /**
     * Standalone 模式下注册数据库兜底扫描定时任务
     */
    @Bean
    @ConditionalOnProperty(prefix = "retry.client", name = "mode", havingValue = "standalone")
    public StandaloneDatabaseFallbackScheduler standaloneDatabaseFallbackScheduler(
            StandaloneRetryTaskMapper taskMapper,
            RetryMessageProducer retryMessageProducer) {
        log.info("[Standalone] StandaloneDatabaseFallbackScheduler registered");
        return new StandaloneDatabaseFallbackScheduler(taskMapper, retryMessageProducer);
    }

    // ==================== 公共 Bean（两种模式共用）====================

    /**
     * 注册AOP切面（两种模式均需要）
     */
    @Bean
    @ConditionalOnMissingBean
    public RetryableTaskAspect retryableTaskAspect() {
        log.info("RetryableTaskAspect bean registered");
        return new RetryableTaskAspect();
    }

    /**
     * 注册本地重试状态机驱动器（两种模式均需要）
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
    @ConditionalOnMissingBean(RetryMessageProducer.class)
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
     * 4. 装配 RABBITMQ 消费者
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
