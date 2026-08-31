package com.retry.platform.server.integration;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 集成测试基类
 * 使用TestContainers启动真实的MySQL和Redis容器
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public abstract class BaseIntegrationTest {

    // MySQL容器
    static final MySQLContainer<?> MYSQL_CONTAINER = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("retry_platform_test")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("test-schema.sql");

    // Redis容器
    static final GenericContainer<?> REDIS_CONTAINER = new GenericContainer<>(DockerImageName.parse("redis:6-alpine"))
            .withExposedPorts(6379);

    @BeforeAll
    static void startContainers() {
        MYSQL_CONTAINER.start();
        REDIS_CONTAINER.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // 配置MySQL连接
        registry.add("spring.datasource.url", MYSQL_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL_CONTAINER::getUsername);
        registry.add("spring.datasource.password", MYSQL_CONTAINER::getPassword);

        // 配置Redis连接
        registry.add("spring.redis.host", REDIS_CONTAINER::getHost);
        registry.add("spring.redis.port", REDIS_CONTAINER::getFirstMappedPort);
        registry.add("spring.redis.password", () -> "");

        // 配置Redisson
        registry.add("spring.redis.redisson.config", () -> String.format(
                "singleServerConfig:\n  address: \"redis://%s:%d\"\n  database: 2",
                REDIS_CONTAINER.getHost(),
                REDIS_CONTAINER.getFirstMappedPort()
        ));

        // 禁用安全认证（测试环境）
        registry.add("retry.security.enabled", () -> "false");

        // 禁用告警（测试环境）
        registry.add("retry.alert.enabled", () -> "false");

        // 禁用客户端消费者（测试环境）
        registry.add("retry.client.consumer-enabled", () -> "false");
    }
}
