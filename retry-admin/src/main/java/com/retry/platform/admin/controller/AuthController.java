package com.retry.platform.admin.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理平台认证控制器
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    // Token 有效期：8 小时
    private static final long TOKEN_TTL_MS = 8 * 60 * 60 * 1000L;

    public static class TokenSession {
        private final String username;
        private final long expireTime;

        public TokenSession(String username, long expireTime) {
            this.username = username;
            this.expireTime = expireTime;
        }

        public String getUsername() {
            return username;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
        }
    }

    // 内存 Token 存储，带 TTL
    private static final Map<String, TokenSession> ACTIVE_TOKENS = new ConcurrentHashMap<>();

    @Value("${retry.admin.security.username:admin}")
    private String adminUsername;

    @Value("${retry.admin.security.password:admin123}")
    private String adminPassword;

    public static boolean isValidToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        TokenSession session = ACTIVE_TOKENS.get(token);
        if (session == null) {
            return false;
        }
        if (session.isExpired()) {
            ACTIVE_TOKENS.remove(token);
            return false;
        }
        return true;
    }

    public static String getUsernameByToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        TokenSession session = ACTIVE_TOKENS.get(token);
        if (session != null && !session.isExpired()) {
            return session.getUsername();
        }
        return null;
    }

    /**
     * 用户名密码登录
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> loginRequest) {
        String username = loginRequest.get("username");
        String password = loginRequest.get("password");

        if (username == null || password == null) {
            return Result.error("用户名或密码不能为空");
        }

        // 常量时间比较防止时序侧信道攻击
        boolean usernameMatches = safeEquals(adminUsername, username);
        boolean passwordMatches = safeEquals(adminPassword, password);

        if (usernameMatches && passwordMatches) {
            // 清理过期 token 避免内存泄露
            cleanExpiredTokens();

            String token = "elec-" + UUID.randomUUID().toString().replace("-", "");
            long expireAt = System.currentTimeMillis() + TOKEN_TTL_MS;
            ACTIVE_TOKENS.put(token, new TokenSession(username, expireAt));

            Map<String, Object> data = new HashMap<>();
            data.put("token", token);
            data.put("username", username);
            data.put("role", "admin");
            data.put("name", "系统管理员");
            data.put("avatar", "https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png");
            return Result.success("登录成功", data);
        }

        return Result.error("用户名或密码错误");
    }

    /**
     * 退出登录
     */
    @PostMapping("/logout")
    public Result<String> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            ACTIVE_TOKENS.remove(token);
        }
        return Result.success("退出登录成功", null);
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/user-info")
    public Result<Map<String, Object>> getUserInfo(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Result.error("未提供有效凭据，请重新登录");
        }
        String token = authHeader.substring(7).trim();
        String username = getUsernameByToken(token);
        if (username == null) {
            return Result.error("登录状态已过期，请重新登录");
        }

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("username", username);
        userInfo.put("name", "系统管理员");
        userInfo.put("role", "admin");
        userInfo.put("roles", Collections.singletonList("admin"));
        userInfo.put("permissions", Collections.singletonList("*:*:*"));
        userInfo.put("avatar", "https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png");
        return Result.success(userInfo);
    }

    private static boolean safeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(aBytes, bBytes);
    }

    private static void cleanExpiredTokens() {
        if (ACTIVE_TOKENS.size() > 100) {
            ACTIVE_TOKENS.entrySet().removeIf(entry -> entry.getValue().isExpired());
        }
    }
}
