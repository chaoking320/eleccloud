package com.retry.platform.server.config;

import com.retry.platform.server.security.ApiKeyAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 安全配置
 * 注册 API Key 鉴权过滤器
 */
@Configuration
public class SecurityConfig {

    @Autowired
    private ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;

    @Bean
    public FilterRegistrationBean<ApiKeyAuthenticationFilter> apiKeyFilterRegistration() {
        FilterRegistrationBean<ApiKeyAuthenticationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(apiKeyAuthenticationFilter);
        registration.addUrlPatterns("/*");
        registration.setName("ApiKeyAuthenticationFilter");
        registration.setOrder(1); // 优先级设置为1，尽早执行
        return registration;
    }
}
