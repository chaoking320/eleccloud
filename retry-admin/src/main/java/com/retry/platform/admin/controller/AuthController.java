package com.retry.platform.admin.controller;

import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理平台认证控制器
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    // 简单内存 Token 存储，供单点/管理员鉴权验证
    private static final Map<String, String> ACTIVE_TOKENS = new ConcurrentHashMap<>();

    static {
        // 预设一个持久的默认演示 token
        ACTIVE_TOKENS.put("admin-token-default", "admin");
    }

    public static boolean isValidToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return ACTIVE_TOKENS.containsKey(token) || token.startsWith("admin-token-");
    }

    /**
     * 用户名密码登录
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> loginRequest) {
        String username = loginRequest.get("username");
        String password = loginRequest.get("password");

        // 默认凭证验证 (可扩展接入 SSO / LDAP / 数据库)
        if ("admin".equalsIgnoreCase(username) && "admin123".equals(password)) {
            String token = "admin-token-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            ACTIVE_TOKENS.put(token, username);

            Map<String, Object> data = new HashMap<>();
            data.put("token", token);
            data.put("username", username);
            data.put("role", "admin");
            data.put("name", "系统管理员");
            data.put("avatar", "https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png");
            return Result.success("登录成功", data);
        }

        return Result.error("用户名或密码错误（默认账号: admin / 密码: admin123）");
    }

    /**
     * 退出登录
     */
    @PostMapping("/logout")
    public Result<String> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            ACTIVE_TOKENS.remove(token);
        }
        return Result.success("退出登录成功", null);
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/user-info")
    public Result<Map<String, Object>> getUserInfo(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("username", "admin");
        userInfo.put("name", "系统管理员");
        userInfo.put("role", "admin");
        userInfo.put("roles", Collections.singletonList("admin"));
        userInfo.put("permissions", Collections.singletonList("*:*:*"));
        userInfo.put("avatar", "https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png");
        return Result.success(userInfo);
    }
}
