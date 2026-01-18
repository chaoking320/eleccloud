package com.retry.platform.client.util;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;

/**
 * 参数提取工具类
 */
@Slf4j
public class ParameterExtractor {
    
    /**
     * 从方法参数中提取幂等键值
     * 
     * @param signature 方法签名
     * @param args 方法参数
     * @param idempotentKeyName 幂等键参数名
     * @return 幂等键值
     */
    public static String extractIdempotentKey(MethodSignature signature, Object[] args, String idempotentKeyName) {
        if (args == null || args.length == 0) {
            throw new IllegalArgumentException("Method has no parameters to extract idempotent key");
        }
        
        Method method = signature.getMethod();
        Parameter[] parameters = method.getParameters();
        
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            String paramName = parameter.getName();
            
            // 匹配参数名
            if (idempotentKeyName.equals(paramName)) {
                Object value = args[i];
                if (value == null) {
                    throw new IllegalArgumentException("Idempotent key value is null");
                }
                return value.toString();
            }
            
            // 如果参数是对象，尝试从对象字段中提取
            if (args[i] != null && !isPrimitiveOrWrapper(args[i].getClass())) {
                try {
                    Object value = extractFieldValue(args[i], idempotentKeyName);
                    if (value != null) {
                        return value.toString();
                    }
                } catch (Exception e) {
                    log.debug("Failed to extract field {} from parameter {}", idempotentKeyName, paramName);
                }
            }
        }
        
        throw new IllegalArgumentException("Idempotent key not found: " + idempotentKeyName);
    }
    
    /**
     * 从对象中提取字段值
     */
    private static Object extractFieldValue(Object obj, String fieldName) throws Exception {
        Class<?> clazz = obj.getClass();
        try {
            java.lang.reflect.Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(obj);
        } catch (NoSuchFieldException e) {
            // 尝试通过getter方法获取
            String getterName = "get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
            try {
                java.lang.reflect.Method getter = clazz.getMethod(getterName);
                return getter.invoke(obj);
            } catch (NoSuchMethodException ex) {
                throw new NoSuchFieldException("Field or getter not found: " + fieldName);
            }
        }
    }
    
    /**
     * 将方法参数转换为Map
     */
    public static Map<String, Object> extractParameters(MethodSignature signature, Object[] args) {
        Map<String, Object> paramMap = new HashMap<>();
        
        if (args == null || args.length == 0) {
            return paramMap;
        }
        
        Method method = signature.getMethod();
        Parameter[] parameters = method.getParameters();
        
        for (int i = 0; i < parameters.length; i++) {
            String paramName = parameters[i].getName();
            paramMap.put(paramName, args[i]);
        }
        
        return paramMap;
    }
    
    /**
     * 判断是否为基本类型或包装类型
     */
    private static boolean isPrimitiveOrWrapper(Class<?> clazz) {
        return clazz.isPrimitive() 
            || clazz == String.class
            || clazz == Integer.class
            || clazz == Long.class
            || clazz == Double.class
            || clazz == Float.class
            || clazz == Boolean.class
            || clazz == Character.class
            || clazz == Byte.class
            || clazz == Short.class;
    }
}
