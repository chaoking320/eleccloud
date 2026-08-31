package com.retry.platform.server.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import javax.servlet.ServletException;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * API Key 鉴权过滤器测试
 */
class ApiKeyAuthenticationFilterTest {

    private ApiKeyAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain filterChain;

    @BeforeEach
    void setUp() throws ServletException {
        filter = new ApiKeyAuthenticationFilter();
        
        // 设置测试配置
        ReflectionTestUtils.setField(filter, "securityEnabled", true);
        ReflectionTestUtils.setField(filter, "apiKeysConfig", "test-key-123,test-key-456");
        ReflectionTestUtils.setField(filter, "whitelistConfig", "/actuator/**,/error");
        
        // 初始化过滤器（解析配置）
        filter.initFilterBean();

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = new MockFilterChain();
    }

    @Test
    void shouldAllowWhitelistedUrls() throws ServletException, IOException {
        request.setRequestURI("/actuator/prometheus");
        
        filter.doFilterInternal(request, response, filterChain);
        
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldAllowNonApiUrls() throws ServletException, IOException {
        request.setRequestURI("/health");
        
        filter.doFilterInternal(request, response, filterChain);
        
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldRejectMissingApiKey() throws ServletException, IOException {
        request.setRequestURI("/api/retry/submit");
        
        filter.doFilterInternal(request, response, filterChain);
        
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Invalid or missing API Key"));
    }

    @Test
    void shouldRejectInvalidApiKey() throws ServletException, IOException {
        request.setRequestURI("/api/retry/submit");
        request.addHeader("X-API-Key", "invalid-key");
        
        filter.doFilterInternal(request, response, filterChain);
        
        assertEquals(401, response.getStatus());
    }

    @Test
    void shouldAllowValidApiKeyInHeader() throws ServletException, IOException {
        request.setRequestURI("/api/retry/submit");
        request.addHeader("X-API-Key", "test-key-123");
        
        filter.doFilterInternal(request, response, filterChain);
        
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldAllowValidApiKeyInQueryParam() throws ServletException, IOException {
        request.setRequestURI("/api/retry/submit");
        request.setParameter("apiKey", "test-key-456");
        
        filter.doFilterInternal(request, response, filterChain);
        
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldPrioritizeHeaderOverQueryParam() throws ServletException, IOException {
        request.setRequestURI("/api/retry/submit");
        request.addHeader("X-API-Key", "test-key-123");
        request.setParameter("apiKey", "invalid-key");
        
        filter.doFilterInternal(request, response, filterChain);
        
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldAllowAllWhenSecurityDisabled() throws ServletException, IOException {
        ReflectionTestUtils.setField(filter, "securityEnabled", false);
        
        request.setRequestURI("/api/retry/submit");
        // 不设置API Key
        
        filter.doFilterInternal(request, response, filterChain);
        
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldExtractRealIpFromXForwardedFor() throws ServletException, IOException {
        request.setRequestURI("/api/retry/submit");
        request.addHeader("X-Forwarded-For", "1.2.3.4, 5.6.7.8");
        request.addHeader("X-API-Key", "test-key-123");
        
        filter.doFilterInternal(request, response, filterChain);
        
        assertEquals(200, response.getStatus());
    }
}
