package com.retry.platform.client.util;

import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ParameterExtractorNestedPropertyTest {

    @Mock
    private MethodSignature methodSignature;

    // --- 模拟测试对象 ---
    static class BaseDTO {
        private String baseId = "BASE_999";
        public String getBaseId() { return baseId; }
    }

    static class CustomerDTO {
        private String name = "Alice";
        public String getName() { return name; }
    }

    static class OrderDTO extends BaseDTO {
        private String orderNo = "ORDER_12345";
        private CustomerDTO customer = new CustomerDTO();
        private boolean active = true;

        public String getOrderNo() { return orderNo; }
        public CustomerDTO getCustomer() { return customer; }
        public boolean isActive() { return active; }
    }

    private void mockSignature(String[] paramNames, Class<?> declaringClass, String methodName) throws Exception {
        Method dummyMethod = declaringClass.getDeclaredMethod(methodName, OrderDTO.class);
        when(methodSignature.getMethod()).thenReturn(dummyMethod);
        when(methodSignature.getParameterNames()).thenReturn(paramNames);
    }

    // 辅助定义供反射调用的假方法
    public void dummyMethod(OrderDTO order) {}
    public void dummyMapMethod(OrderDTO order) {} // overload placeholder

    @Test
    @DisplayName("支持直接提取对象的一级字段：#order.orderNo")
    void testExtractFirstLevelField() throws Exception {
        mockSignature(new String[]{"order"}, getClass(), "dummyMethod");
        OrderDTO order = new OrderDTO();

        String result = ParameterExtractor.extractIdempotentKey(methodSignature, new Object[]{order}, "#order.orderNo");
        assertEquals("ORDER_12345", result);
    }

    @Test
    @DisplayName("支持多级深层嵌套属性提取：#order.customer.name")
    void testExtractNestedField() throws Exception {
        mockSignature(new String[]{"order"}, getClass(), "dummyMethod");
        OrderDTO order = new OrderDTO();

        String result = ParameterExtractor.extractIdempotentKey(methodSignature, new Object[]{order}, "#order.customer.name");
        assertEquals("Alice", result);
    }

    @Test
    @DisplayName("支持从父类继承链中提取属性：#order.baseId (继承自 BaseDTO)")
    void testExtractInheritedFieldFromSuperClass() throws Exception {
        mockSignature(new String[]{"order"}, getClass(), "dummyMethod");
        OrderDTO order = new OrderDTO();

        String result = ParameterExtractor.extractIdempotentKey(methodSignature, new Object[]{order}, "#order.baseId");
        assertEquals("BASE_999", result);
    }

    @Test
    @DisplayName("支持提取布尔型 isXxx 属性：#order.active")
    void testExtractBooleanField() throws Exception {
        mockSignature(new String[]{"order"}, getClass(), "dummyMethod");
        OrderDTO order = new OrderDTO();

        String result = ParameterExtractor.extractIdempotentKey(methodSignature, new Object[]{order}, "#order.active");
        assertEquals("true", result);
    }

    @Test
    @DisplayName("支持从 Map 实例中提取键值：#params.transId")
    void testExtractFromMap() throws Exception {
        Method dummy = getClass().getDeclaredMethod("dummyMethodWithMap", Map.class);
        when(methodSignature.getMethod()).thenReturn(dummy);
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"params"});

        Map<String, Object> map = new HashMap<>();
        map.put("transId", "TRANS_8888");

        String result = ParameterExtractor.extractIdempotentKey(methodSignature, new Object[]{map}, "#params.transId");
        assertEquals("TRANS_8888", result);
    }

    public void dummyMethodWithMap(Map<String, Object> params) {}
}
