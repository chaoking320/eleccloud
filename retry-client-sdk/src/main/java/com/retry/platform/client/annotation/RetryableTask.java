package com.retry.platform.client.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 可重试任务注解
 * 标注在方法上，当方法执行失败时自动向分布式重试平台提交重试任务。
 *
 * <p><b>两种提交模式：</b>
 * <ul>
 *   <li><b>POST_FAIL 模式（默认）</b>：{@code preSubmit = false}<br>
 *       方法执行 → 失败抛异常 → AOP 拦截 → 提交重试任务（INIT 状态）→ 平台定时重试。<br>
 *       适用场景：调用失败会明确抛出异常的场景（如 HTTP 调用超时、RPC 异常等）。
 *   </li>
 *   <li><b>PRE_SUBMIT 模式（预提交）</b>：{@code preSubmit = true}<br>
 *       先提交 INIT 状态任务 → 再执行方法 → 成功后标记 SUCCESS。<br>
 *       如果方法执行途中崩溃/超时，任务保持 INIT 状态，平台会主动触发重试。<br>
 *       适用场景：对数据一致性要求极高的场景（如库存扣减、金额变更），
 *       即使进程崩溃也能保证最终执行。每次多一次 HTTP 调用开销。
 *   </li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 模式1：POST_FAIL（默认，失败后提交）
 * @RetryableTask(sceneType = 1, idempotentKey = "#orderId")
 * public boolean refund(String orderId, Double amount) { ... }
 *
 * // 模式2：PRE_SUBMIT（预提交，执行前注册）
 * @RetryableTask(sceneType = 3, idempotentKey = "#skuId", preSubmit = true)
 * public void syncInventory(String skuId, Integer delta) { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RetryableTask {

    /**
     * 场景类型（对应 scene_config.scene_type）
     * 不同场景可配置不同的重试策略、退避算法和钩子实现
     */
    int sceneType();

    /**
     * 幂等键参数表达式（SpEL 简写语法）
     * 用于从方法参数中提取幂等键值，格式：{@code "#参数名"} 或 {@code "#对象.字段名"}
     * 示例：{@code "#orderId"}、{@code "#request.userId"}
     */
    String idempotentKey();

    /**
     * 是否使用预提交模式
     * <ul>
     *   <li>{@code false}（默认）：POST_FAIL 模式，方法失败后才提交重试任务</li>
     *   <li>{@code true}：PRE_SUBMIT 模式，方法执行前先注册任务，成功后标记 SUCCESS</li>
     * </ul>
     */
    boolean preSubmit() default false;

    /**
     * 是否异步提交重试任务（默认 true）
     * 设为 false 时，提交任务会阻塞当前线程等待服务端响应
     */
    boolean async() default true;

    /**
     * 失败时是否向调用方抛出原始异常（默认 false）
     * <ul>
     *   <li>{@code false}：静默处理，方法返回 null，不向上抛异常</li>
     *   <li>{@code true}：提交重试任务后，继续向上抛出原始异常</li>
     * </ul>
     */
    boolean throwException() default false;

    /**
     * 是否使用默认重试钩子（DefaultRetryHook）
     * <ul>
     *   <li>{@code false}（默认）：使用 scene_config 中配置的 hookClass</li>
     *   <li>{@code true}：跳过 Hook 三步检查（checkStatus/doQuery/doCallback），
     *       直接反射调用业务方法执行重试。适用于简单场景，无需编写 Hook 类。</li>
     * </ul>
     */
    boolean useDefaultHook() default false;
}
