package com.retry.platform.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Retry Server Application
 * 重试服务端启动类
 */
@SpringBootApplication
@EnableScheduling
public class RetryServerApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(RetryServerApplication.class, args);

        System.out.println("Retry Server Started.");
    }
}
