package com.retry.platform.admin.config;

import com.retry.platform.admin.controller.AuthController;
import com.retry.platform.admin.controller.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link AuthInterceptor} 与 {@link AuthController} 认证安全单元测试
 */
class AuthInterceptorTest {

    private AuthController authController;
    private AuthInterceptor authInterceptor;

    @BeforeEach
    void setUp() {
        authController = new AuthController();
        ReflectionTestUtils.setField(authController, "adminUsername", "admin");
        ReflectionTestUtils.setField(authController, "adminPassword", "StrongPass!2026");
        authInterceptor = new AuthInterceptor(true);
    }

    @Test
    @DisplayName("未携带 Authorization Header 或伪造固定前缀 Token 时应拦截并返回 401")
    void shouldRejectMissingOrForgedBearerToken() throws Exception {
        MockHttpServletRequest reqNoAuth = new MockHttpServletRequest("GET", "/api/admin/tasks");
        MockHttpServletResponse respNoAuth = new MockHttpServletResponse();
        assertFalse(authInterceptor.preHandle(reqNoAuth, respNoAuth, new Object()));
        assertEquals(401, respNoAuth.getStatus());

        MockHttpServletRequest reqForged = new MockHttpServletRequest("GET", "/api/admin/tasks");
        reqForged.addHeader("Authorization", "Bearer elec-forged-token-12345");
        MockHttpServletResponse respForged = new MockHttpServletResponse();
        assertFalse(authInterceptor.preHandle(reqForged, respForged, new Object()));
        assertEquals(401, respForged.getStatus());
    }

    @Test
    @DisplayName("真实登录签发的 Token 应放行，退出登录后立即失效")
    void shouldAllowValidLoginTokenAndInvalidateOnLogout() throws Exception {
        Map<String, String> loginReq = new HashMap<>();
        loginReq.put("username", "admin");
        loginReq.put("password", "StrongPass!2026");

        Result<Map<String, Object>> loginResult = authController.login(loginReq);
        assertTrue(loginResult.isSuccess());
        String token = (String) loginResult.getData().get("token");
        assertNotNull(token);

        MockHttpServletRequest reqValid = new MockHttpServletRequest("GET", "/api/admin/tasks");
        reqValid.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse respValid = new MockHttpServletResponse();
        assertTrue(authInterceptor.preHandle(reqValid, respValid, new Object()));

        // 退出登录后同一 Token 应被拒绝
        authController.logout("Bearer " + token);
        MockHttpServletResponse respAfterLogout = new MockHttpServletResponse();
        assertFalse(authInterceptor.preHandle(reqValid, respAfterLogout, new Object()));
        assertEquals(401, respAfterLogout.getStatus());
    }

    @Test
    @DisplayName("OPTIONS 预检请求应直接放行")
    void shouldAllowOptionsPreflightRequest() throws Exception {
        MockHttpServletRequest reqOptions = new MockHttpServletRequest("OPTIONS", "/api/admin/tasks");
        MockHttpServletResponse respOptions = new MockHttpServletResponse();
        assertTrue(authInterceptor.preHandle(reqOptions, respOptions, new Object()));
    }
}
