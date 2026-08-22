package com.retry.platform.admin.config;

import org.springframework.boot.web.server.ErrorPage;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

/**
 * 解决 Vue Router History 模式下，直接刷新页面或访问子路径返回 404 的问题。
 * 将所有的 404 错误转发到 index.html，交由前端 Vue Router 处理。
 */
@Configuration
public class SpaWebConfig {

    @Bean
    public WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> webServerFactoryCustomizer() {
        return factory -> {
            ErrorPage error404Page = new ErrorPage(HttpStatus.NOT_FOUND, "/index.html");
            factory.addErrorPages(error404Page);
        };
    }
}
