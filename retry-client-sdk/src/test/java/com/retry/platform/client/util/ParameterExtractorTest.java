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
    void throwsWhenKeyNotFoundAmongParameters() {
        MethodSignature sig = mock(MethodSignature.class);
        when(sig.getParameterNames()).thenReturn(new String[]{"orderId"});
        assertThrows(IllegalArgumentException.class,
                () -> ParameterExtractor.extractIdempotentKey(sig, new Object[]{"ORD-1"}, "missingKey"));
    }

    @Test
    void throwsWhenArgsEmpty() {
        MethodSignature sig = mock(MethodSignature.class);
        assertThrows(IllegalArgumentException.class,
                () -> ParameterExtractor.extractIdempotentKey(sig, new Object[]{}, "orderId"));
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
}
