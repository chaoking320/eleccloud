package com.retry.platform.server.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * API Key 鉴权过滤器
 * 对所有 /api/retry/** 接口进行 API Key 验证
 * 
 * 支持以下方式传递 API Key：
 * 1. HTTP Header: X-API-Key
 * 2. Query Parameter: apiKey
 * 
 * 白名单接口不需要鉴权：
 * - /actuator/** (监控端点)
 * - /error (错误页面)
 */
@Slf4j
@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    @Value("${retry.security.enabled:true}")
    private boolean securityEnabled;

    @Value("${retry.security.api-keys:}")
    private String apiKeysConfig;

    @Value("${retry.security.whitelist:/actuator/**,/error}")
    private String whitelistConfig;

    private Set<String> validApiKeys;
    private Set<String> whitelistPatterns;

    @Override
    protected void initFilterBean() throws ServletException {
        super.initFilterBean();
        
        // 解析有效的 API Keys
        validApiKeys = new HashSet<>();
        if (StringUtils.hasText(apiKeysConfig)) {
            validApiKeys.addAll(Arrays.asList(apiKeysConfig.split(",")));
            log.info("[Security] API Key authentication enabled, {} keys configured", validApiKeys.size());
        }

        // 解析白名单
        whitelistPatterns = new HashSet<>();
        if (StringUtils.hasText(whitelistConfig)) {
            whitelistPatterns.addAll(Arrays.asList(whitelistConfig.split(",")));
        }

        if (!securityEnabled) {
            log.warn("[Security] API Key authentication is DISABLED! This should only be used in development.");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        
        // 如果安全机制未启用，直接放行
        if (!securityEnabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String requestUri = request.getRequestURI();

        // 检查是否在白名单中
        if (isWhitelisted(requestUri)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 检查是否需要鉴权（/api/retry/** 和 /api/admin/** 路径）
        if (!requestUri.startsWith("/api/retry/") && !requestUri.startsWith("/api/admin/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 提取 API Key
        String apiKey = extractApiKey(request);

        // 验证 API Key
        if (!isValidApiKey(apiKey)) {
            log.warn("[Security] Unauthorized access attempt: uri={}, ip={}, apiKey={}", 
                    requestUri, getClientIp(request), maskApiKey(apiKey));
            
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\"Unauthorized: Invalid or missing API Key\"}");
            return;
        }

        // 验证通过，记录访问日志
        log.debug("[Security] Authorized access: uri={}, ip={}, apiKey={}", 
                requestUri, getClientIp(request), maskApiKey(apiKey));

        filterChain.doFilter(request, response);
    }

    /**
     * 从请求中提取 API Key
     * 优先级：Header > Query Parameter
     */
    private String extractApiKey(HttpServletRequest request) {
        // 1. 尝试从 Header 获取
        String apiKey = request.getHeader("X-API-Key");
        if (StringUtils.hasText(apiKey)) {
            return apiKey.trim();
        }

        // 2. 尝试从 Query Parameter 获取
        apiKey = request.getParameter("apiKey");
        if (StringUtils.hasText(apiKey)) {
            return apiKey.trim();
        }

        return null;
    }

    /**
     * 验证 API Key 是否有效
     */
    private boolean isValidApiKey(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return false;
        }
        return validApiKeys.contains(apiKey);
    }

    /**
     * 检查 URI 是否在白名单中
     */
    private boolean isWhitelisted(String uri) {
        for (String pattern : whitelistPatterns) {
            if (pattern.endsWith("**")) {
                String prefix = pattern.substring(0, pattern.length() - 2);
                if (uri.startsWith(prefix)) {
                    return true;
                }
            } else if (uri.equals(pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取客户端真实IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            // 多次反向代理后会有多个IP值，第一个为真实IP
            int index = ip.indexOf(',');
            if (index != -1) {
                return ip.substring(0, index);
            } else {
                return ip;
            }
        }
        ip = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        return request.getRemoteAddr();
    }

    /**
     * 脱敏显示 API Key（只显示前4位和后4位）
     */
    private String maskApiKey(String apiKey) {
        if (apiKey == null) {
            return "null";
        }
        if (apiKey.length() <= 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }
}
