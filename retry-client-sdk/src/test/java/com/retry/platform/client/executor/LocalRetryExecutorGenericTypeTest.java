package com.retry.platform.client.executor;

import com.retry.platform.client.util.JsonUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class LocalRetryExecutorGenericTypeTest {

    static class OrderItemDTO {
        private String itemId;
        private int quantity;

        public OrderItemDTO() {}
        public OrderItemDTO(String itemId, int quantity) {
            this.itemId = itemId;
            this.quantity = quantity;
        }

        public String getItemId() { return itemId; }
        public void setItemId(String itemId) { this.itemId = itemId; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
    }

    // 目标模拟方法：入参为泛型集合 List<OrderItemDTO>
    public void processItems(List<OrderItemDTO> items, String batchId) {}

    @Test
    @DisplayName("验证重试执行器反射准备方法实参时，复杂泛型 List<DTO> 能够被精准还原为 DTO 对象而非 LinkedHashMap")
    void testPrepareMethodArgs_withGenericList() throws Exception {
        LocalRetryExecutor executor = new LocalRetryExecutor();

        Method targetMethod = getClass().getDeclaredMethod("processItems", List.class, String.class);

        // 模拟从存储层或 MQ 还原出来的 paramsMap（通常是 JSON 反序列化得到的原生 Map 或未经类型特化的 List<Map>）
        Map<String, Object> rawItem = new HashMap<>();
        rawItem.put("itemId", "ITEM_1001");
        rawItem.put("quantity", 5);

        Map<String, Object> paramsMap = new HashMap<>();
        paramsMap.put("items", Collections.singletonList(rawItem));
        paramsMap.put("batchId", "BATCH_2026");

        // 调用 prepareMethodArgs
        Object[] args = (Object[]) ReflectionTestUtils.invokeMethod(executor, "prepareMethodArgs", targetMethod, paramsMap);

        assertNotNull(args);
        assertEquals(2, args.length);

        // 验证第一个参数 items
        assertTrue(args[0] instanceof List);
        List<?> resultList = (List<?>) args[0];
        assertEquals(1, resultList.size());

        // 关键断言：List 内部的元素必须是 OrderItemDTO 实例，绝不能是 LinkedHashMap！
        Object element = resultList.get(0);
        assertTrue(element instanceof OrderItemDTO, "Element should be deserialized into OrderItemDTO, but was: " + element.getClass());
        OrderItemDTO dto = (OrderItemDTO) element;
        assertEquals("ITEM_1001", dto.getItemId());
        assertEquals(5, dto.getQuantity());

        // 验证第二个参数
        assertEquals("BATCH_2026", args[1]);
    }
}
