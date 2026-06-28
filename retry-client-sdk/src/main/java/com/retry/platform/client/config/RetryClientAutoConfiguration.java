package com.retry.platform.client.config;

import com.retry.platform.client.api.RetryClient;
import com.retry.platform.client.api.impl.RetryClientImpl;
import com.retry.platform.client.aspect.RetryableTaskAspect;
import com.retry.platform.client.callback.RetryCallbackController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
     * 注册回调控制器 Bean
     */
    @Bean
    @ConditionalOnMissingBean
    public RetryCallbackController retryCallbackController() {
        log.info("RetryCallbackController bean registered");
        return new RetryCallbackController();
    }
}
