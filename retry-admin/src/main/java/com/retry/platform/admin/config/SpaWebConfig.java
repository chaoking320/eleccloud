package com.retry.platform.admin.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.server.ErrorPage;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 解决 Vue Router History 模式 404 跳转，并注册管理平台认证拦截器。
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    @Value("${retry.admin.auth.enabled:true}")
    private boolean authEnabled;

    @Bean
    public WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> webServerFactoryCustomizer() {
        return factory -> {
            ErrorPage error404Page = new ErrorPage(HttpStatus.NOT_FOUND, "/index.html");
            factory.addErrorPages(error404Page);
        };
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AuthInterceptor(authEnabled))
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/actuator/**",
                        "/error",
                        "/static/**",
                        "/assets/**",
                        "/index.html"
                );
    }
}
