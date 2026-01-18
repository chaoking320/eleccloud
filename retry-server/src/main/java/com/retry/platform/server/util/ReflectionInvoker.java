package com.retry.platform.server.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;

/**
 * 反射调用工具类
 * 用于通过反射调用业务方法
 */
@Slf4j
@Component
public class ReflectionInvoker {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 通过反射调用方法
     * 
     * @param className 类全限定名
     * @param methodName 方法名
     * @param paramsJson 参数JSON字符串
     * @param idempotentKey 幂等键值
     * @return 方法执行结果
     * @throws Exception 反射调用异常
     */
    public Object invoke(String className, String methodName, String paramsJson, String idempotentKey) throws Exception {
        // 1. 加载类
        Class<?> clazz = Class.forName(className);
        
        // 2. 解析参数JSON
        Map<String, Object> paramsMap = parseParamsJson(paramsJson);
        
        // 3. 查找匹配的方法
        Method targetMethod = findMethod(clazz, methodName, paramsMap);
        
        if (targetMethod == null) {
            throw new NoSuchMethodException("Method not found: " + className + "." + methodName);
        }
        
        // 4. 准备方法参数
        Object[] args = prepareMethodArgs(targetMethod, paramsMap, idempotentKey);
        
        // 5. 创建实例（假设有无参构造函数）
        Object instance = clazz.getDeclaredConstructor().newInstance();
        
        // 6. 调用方法
        targetMethod.setAccessible(true);
        Object result = targetMethod.invoke(instance, args);
        
        log.info("Method invoked successfully: {}.{}", className, methodName);
        return result;
    }
    
    /**
     * 解析参数JSON
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseParamsJson(String paramsJson) throws Exception {
        if (paramsJson == null || paramsJson.trim().isEmpty()) {
            return new HashMap<>();
        }
        return objectMapper.readValue(paramsJson, Map.class);
    }
    
    /**
     * 查找匹配的方法
     * 根据方法名和参数数量查找
     */
    private Method findMethod(Class<?> clazz, String methodName, Map<String, Object> paramsMap) {
        Method[] methods = clazz.getDeclaredMethods();
        
        for (Method method : methods) {
            if (method.getName().equals(methodName)) {
                // 简单匹配：参数数量相同
                if (method.getParameterCount() == paramsMap.size()) {
                    return method;
                }
            }
        }
        
        return null;
    }
    
    /**
     * 准备方法参数
     * 将Map中的参数转换为方法所需的类型
     */
    private Object[] prepareMethodArgs(Method method, Map<String, Object> paramsMap, String idempotentKey) throws Exception {
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];
        
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            String paramName = parameter.getName();
            Class<?> paramType = parameter.getType();
            
            Object paramValue = paramsMap.get(paramName);
            
            // 如果参数值为null，尝试使用幂等键
            if (paramValue == null && idempotentKey != null) {
                // 假设幂等键参数名可能是 transId, orderId 等
                if (paramName.toLowerCase().contains("id") || 
                    paramName.toLowerCase().contains("key")) {
                    paramValue = idempotentKey;
                }
            }
            
            // 类型转换
            args[i] = convertType(paramValue, paramType);
        }
        
        return args;
    }
    
    /**
     * 类型转换
     * 将Object转换为目标类型
     */
    private Object convertType(Object value, Class<?> targetType) throws Exception {
        if (value == null) {
            return null;
        }
        
        // 如果类型已经匹配，直接返回
        if (targetType.isInstance(value)) {
            return value;
        }
        
        // 基本类型转换
        if (targetType == String.class) {
            return value.toString();
        } else if (targetType == Integer.class || targetType == int.class) {
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            return Integer.parseInt(value.toString());
        } else if (targetType == Long.class || targetType == long.class) {
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            return Long.parseLong(value.toString());
        } else if (targetType == Double.class || targetType == double.class) {
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
            return Double.parseDouble(value.toString());
        } else if (targetType == Boolean.class || targetType == boolean.class) {
            if (value instanceof Boolean) {
                return value;
            }
            return Boolean.parseBoolean(value.toString());
        } else {
            // 复杂对象使用Jackson转换
            String json = objectMapper.writeValueAsString(value);
            return objectMapper.readValue(json, targetType);
        }
    }
}
