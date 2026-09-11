package com.retry.platform.client.util;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * JSON序列化工具类
 */
@Slf4j
public class JsonUtil {
    
    public static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * 对象转JSON字符串
     */
    public static String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("Object to JSON failed", e);
            throw new RuntimeException("JSON serialization failed", e);
        }
    }
    
    /**
     * JSON字符串转对象
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, clazz);
        } catch (Exception e) {
            log.error("JSON to Object failed", e);
            throw new RuntimeException("JSON deserialization failed", e);
        }
    }
    
    /**
     * 对象转JSON字节数组
     */
    public static byte[] toJsonBytes(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsBytes(obj);
        } catch (Exception e) {
            log.error("Object to JSON bytes failed", e);
            throw new RuntimeException("JSON serialization failed", e);
        }
    }
}
