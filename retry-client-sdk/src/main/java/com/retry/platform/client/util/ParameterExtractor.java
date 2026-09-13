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
            throw new IllegalArgumentException(
                "Cannot extract idempotent key: method has no parameters.\n" +
                "Method: " + signature.getMethod().getDeclaringClass().getName() + "." + signature.getName() + "\n" +
                "Idempotent key: " + idempotentKeyName + "\n" +
                "Solution: Add at least one parameter to the method or use a different idempotent key source."
            );
        }
        
        String[] parameterNames = signature.getParameterNames();
        if (parameterNames == null || parameterNames.length == 0) {
            throw new IllegalStateException(
                "Failed to resolve parameter names - this is required for @RetryableTask to work.\n" +
                "Method: " + signature.getMethod().getDeclaringClass().getName() + "." + signature.getName() + "\n" +
                "Root cause: Spring AOP is not configured to preserve parameter names.\n" +
                "Solution: Add the following to your pom.xml:\n" +
                "  <plugin>\n" +
                "    <groupId>org.apache.maven.plugins</groupId>\n" +
                "    <artifactId>maven-compiler-plugin</artifactId>\n" +
                "    <configuration>\n" +
                "      <parameters>true</parameters>  <!-- Enable parameter name preservation -->\n" +
                "    </configuration>\n" +
                "  </plugin>"
            );
        }

        // 兼容 SpEL 风格前缀（如 "#orderId"），去掉前导 '#' 后按参数名匹配
        String normalizedKey = idempotentKeyName;
        if (normalizedKey != null && normalizedKey.startsWith("#")) {
            normalizedKey = normalizedKey.substring(1);
        }

        // 支持多级嵌套字段: "#req.user.id" → paramName="req", fieldPath="user.id"
        String paramName = normalizedKey;
        String fieldPath = null;
        int dotIndex = normalizedKey.indexOf('.');
        if (dotIndex > 0) {
            paramName = normalizedKey.substring(0, dotIndex);
            fieldPath = normalizedKey.substring(dotIndex + 1);
        }

        for (int i = 0; i < parameterNames.length; i++) {
            if (paramName.equals(parameterNames[i])) {
                Object value = args[i];
                if (value == null) {
                    throw new IllegalArgumentException(
                        "Idempotent key value is null - cannot use null as idempotent key.\n" +
                        "Method: " + signature.getMethod().getDeclaringClass().getName() + "." + signature.getName() + "\n" +
                        "Parameter: " + parameterNames[i] + " (position " + i + ")\n" +
                        "Idempotent key: " + idempotentKeyName + "\n" +
                        "Solution: Ensure the parameter value is not null before calling this method."
                    );
                }
                // 如果有嵌套字段路径，逐级提取
                if (fieldPath != null) {
                    Object resolved = resolveNestedField(value, fieldPath);
                    if (resolved != null) {
                        return resolved.toString();
                    }
                    throw new IllegalArgumentException(
                        "Failed to resolve nested field path '" + fieldPath + "' from parameter '" + paramName + "'.\n" +
                        "Method: " + signature.getMethod().getDeclaringClass().getName() + "." + signature.getName() + "\n" +
                        "Idempotent key: " + idempotentKeyName + "\n" +
                        "Solution: Check that each level of the field path has a public getter or accessible field."
                    );
                }
                return value.toString();
            }
        }
        
        // 如果没有通过参数名直接匹配（可能是对象的字段名），尝试从所有非基本类型参数中提取
        for (int i = 0; i < parameterNames.length; i++) {
            if (args[i] != null && !isPrimitiveOrWrapper(args[i].getClass())) {
                try {
                    Object value = extractFieldValue(args[i], normalizedKey);
                    if (value != null) {
                        return value.toString();
                    }
                } catch (Exception e) {
                    log.debug("Failed to extract field {} from parameter {}", normalizedKey, parameterNames[i]);
                }
            }
        }
        
        // 构建友好的错误提示
        StringBuilder errorMsg = new StringBuilder();
        errorMsg.append("Idempotent key '").append(idempotentKeyName).append("' not found in method parameters.\n");
        errorMsg.append("Method: ").append(signature.getMethod().getDeclaringClass().getName())
                .append(".").append(signature.getName()).append("\n");
        errorMsg.append("Available parameters: [");
        for (int i = 0; i < parameterNames.length; i++) {
            if (i > 0) errorMsg.append(", ");
            errorMsg.append(parameterNames[i]).append(" (").append(args[i] != null ? args[i].getClass().getSimpleName() : "null").append(")");
        }
        errorMsg.append("]\n");
        errorMsg.append("Requested key: ").append(normalizedKey).append("\n\n");
        errorMsg.append("Common solutions:\n");
        errorMsg.append("1. Check parameter name spelling (case-sensitive)\n");
        errorMsg.append("2. SpEL supports nested fields: @RetryableTask(idempotentKey = \"#req.user.id\")\n");
        errorMsg.append("3. If extracting from object field, ensure the field/getter exists\n");
        errorMsg.append("4. Verify Maven compiler plugin has <parameters>true</parameters> enabled\n");
        
        throw new IllegalArgumentException(errorMsg.toString());
    }
    
    /**
     * 解析多级嵌套字段路径（如 "user.address.city"）
     */
    private static Object resolveNestedField(Object root, String path) {
        Object current = root;
        String[] parts = path.split("\\.");
        for (String part : parts) {
            if (current == null) return null;
            try {
                current = extractFieldValue(current, part);
            } catch (Exception e) {
                log.debug("Failed to resolve field '{}' in nested path '{}'", part, path);
                return null;
            }
        }
        return current;
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
        
        String[] parameterNames = signature.getParameterNames();
        if (parameterNames == null || parameterNames.length == 0) {
            return paramMap;
        }
        
        for (int i = 0; i < parameterNames.length; i++) {
            paramMap.put(parameterNames[i], args[i]);
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
