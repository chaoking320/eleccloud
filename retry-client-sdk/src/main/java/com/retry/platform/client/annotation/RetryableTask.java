package com.retry.platform.client.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 可重试任务注解
 * 标注在方法上，当方法执行失败时自动创建重试任务
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RetryableTask {
    
    /**
     * 场景类型
     */
    int sceneType();
    
    /**
     * 幂等键参数名
     * 用于从方法参数中提取幂等键值
     */
    String idempotentKey();
    
    /**
     * 是否异步提交（默认true）
     */
    boolean async() default true;
    
    /**
     * 失败时是否抛出异常（默认false）
     */
    boolean throwException() default false;
}
