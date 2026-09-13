package com.retry.platform.client.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JsonUtilTest {

    @Test
    void testToJsonAndFromJson() {
        TestObject obj = new TestObject("test", 123);
        String json = JsonUtil.toJson(obj);
        TestObject result = JsonUtil.fromJson(json, TestObject.class);
        
        assertEquals("test", result.name);
        assertEquals(123, result.value);
    }

    @Test
    void testNullInput() {
        assertNull(JsonUtil.toJson(null));
        assertNull(JsonUtil.fromJson(null, TestObject.class));
        assertNull(JsonUtil.fromJson("", TestObject.class));
        assertNull(JsonUtil.toJsonBytes(null));
    }

    @Test
    void testUnknownPropertiesIgnored() {
        String json = "{\"name\":\"test\",\"value\":123,\"unknown\":\"prop\"}";
        TestObject result = JsonUtil.fromJson(json, TestObject.class);
        
        assertEquals("test", result.name);
        assertEquals(123, result.value);
    }

    public static class TestObject {
        public String name;
        public int value;

        public TestObject() {}

        public TestObject(String name, int value) {
            this.name = name;
            this.value = value;
        }
    }
}
