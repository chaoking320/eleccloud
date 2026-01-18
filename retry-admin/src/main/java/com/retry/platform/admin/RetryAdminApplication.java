package com.retry.platform.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Retry Admin Application
 * 管理后台启动类
 */
@SpringBootApplication(scanBasePackages = {"com.retry.platform.admin", "com.retry.platform.server"})
@MapperScan("com.retry.platform.server.mapper")
public class RetryAdminApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(RetryAdminApplication.class, args);
    }
}
