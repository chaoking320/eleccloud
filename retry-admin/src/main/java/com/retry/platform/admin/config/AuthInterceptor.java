package com.retry.platform.admin.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retry.platform.admin.controller.AuthController;
import com.retry.platform.admin.controller.Result;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 管理平台 API 认证拦截器 (兼容 Spring Boot 2.7.x javax.servlet)
 */
public class AuthInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final boolean authEnabled;

    public AuthInterceptor(boolean authEnabled) {
        this.authEnabled = authEnabled;
    }

    public AuthInterceptor() {
        this(true);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 若全局关闭鉴权则直接放行
        if (!authEnabled) {
            return true;
        }

        // 放行 OPTIONS 跨域预检请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            if (AuthController.isValidToken(token)) {
                return true;
            }
        }

        // 也可以放行携带 query param token 的请求（如 SSE 或快捷链接）
        String tokenParam = request.getParameter("token");
        if (tokenParam != null && AuthController.isValidToken(tokenParam)) {
            return true;
        }

        // 未登录或 Token 无效，返回 401
        sendUnauthorized(response);
        return false;
    }

    private void sendUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        Result<?> errorResult = Result.error("登录状态已过期或未提供认证凭证，请重新登录");
        response.getWriter().write(objectMapper.writeValueAsString(errorResult));
    }
}
