package com.retry.platform.client.util;

import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link ParameterExtractor} 单元测试
 * 覆盖幂等键提取（按参数名 / SpEL 前缀 / 对象字段）与参数 Map 构造，
 * 以及参数缺失、键不存在等异常分支。
 */
class ParameterExtractorTest {

    @Test
    void extractsIdempotentKeyByParameterName() {
        MethodSignature sig = mock(MethodSignature.class);
        when(sig.getParameterNames()).thenReturn(new String[]{"orderId", "amount"});
        String key = ParameterExtractor.extractIdempotentKey(sig, new Object[]{"ORD-123", 100.0}, "orderId");
        assertEquals("ORD-123", key);
    }

    @Test
    void extractsIdempotentKeyWithSpelPrefix() {
        MethodSignature sig = mock(MethodSignature.class);
        when(sig.getParameterNames()).thenReturn(new String[]{"orderId"});
        String key = ParameterExtractor.extractIdempotentKey(sig, new Object[]{"ORD-456"}, "#orderId");
        assertEquals("ORD-456", key);
    }

    @Test
    void extractsIdempotentKeyFromObjectField() {
        MethodSignature sig = mock(MethodSignature.class);
        when(sig.getParameterNames()).thenReturn(new String[]{"req"});
        ParamObject req = new ParamObject();
        req.transId = "TX-999";
        String key = ParameterExtractor.extractIdempotentKey(sig, new Object[]{req}, "transId");
        assertEquals("TX-999", key);
    }

    @Test
    void throwsWhenKeyNotFoundAmongParameters() throws NoSuchMethodException {
        MethodSignature sig = mock(MethodSignature.class);
        when(sig.getParameterNames()).thenReturn(new String[]{"orderId"});
        when(sig.getMethod()).thenReturn(ParameterExtractorTest.class.getMethod("toString"));
        assertThrows(IllegalArgumentException.class,
                () -> ParameterExtractor.extractIdempotentKey(sig, new Object[]{"ORD-1"}, "missingKey"));
    }

    @Test
    void throwsWhenArgsEmpty() throws NoSuchMethodException {
        MethodSignature sig = mock(MethodSignature.class);
        when(sig.getMethod()).thenReturn(ParameterExtractorTest.class.getMethod("toString"));
        assertThrows(IllegalArgumentException.class,
                () -> ParameterExtractor.extractIdempotentKey(sig, new Object[]{}, "orderId"));
    }

    @Test
    void extractsIdempotentKeyWithNestedSpel() {
        MethodSignature sig = mock(MethodSignature.class);
        when(sig.getParameterNames()).thenReturn(new String[]{"req"});
        NestedParamObject req = new NestedParamObject();
        req.user = new UserObject();
        req.user.id = "U-777";
        String key = ParameterExtractor.extractIdempotentKey(sig, new Object[]{req}, "#req.user.id");
        assertEquals("U-777", key);
    }

    @Test
    void extractsIdempotentKeyFromGetterMethod() {
        MethodSignature sig = mock(MethodSignature.class);
        when(sig.getParameterNames()).thenReturn(new String[]{"req"});
        GetterObject req = new GetterObject("G-888");
        String key = ParameterExtractor.extractIdempotentKey(sig, new Object[]{req}, "transId");
        assertEquals("G-888", key);
    }

    @Test
    void extractsParametersToMap() {
        MethodSignature sig = mock(MethodSignature.class);
        when(sig.getParameterNames()).thenReturn(new String[]{"a", "b"});
        Map<String, Object> map = ParameterExtractor.extractParameters(sig, new Object[]{"x", 2});
        assertEquals(2, map.size());
        assertEquals("x", map.get("a"));
        assertEquals(2, map.get("b"));
    }

    /** 简单 POJO，用于验证从对象字段提取幂等键。 */
    public static class ParamObject {
        public String transId;
    }

    public static class NestedParamObject {
        public UserObject user;
    }

    public static class UserObject {
        public String id;
    }

    public static class GetterObject {
        private String transId;
        public GetterObject(String transId) {
            this.transId = transId;
        }
        public String getTransId() {
            return transId;
        }
    }
}
