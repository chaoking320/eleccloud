package com.retry.platform.client.annotation;

import com.retry.platform.client.hook.RetryHook;
import com.retry.platform.client.strategy.BackoffType;

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
 * <p><b>三级配置优先级（Convention over Configuration）：</b>
 * <ol>
 *   <li><b>代码注解（最高优先级）</b>：直接在 {@code @RetryableTask} 上声明策略，零 YAML，即插即用</li>
 *   <li><b>application.yml scenes 配置（覆盖层）</b>：同一 sceneType 如有 YAML 配置则覆盖注解值，
 *       适用于生产运维时动态调整重试策略无需重打包</li>
 *   <li><b>SDK 内置默认值（兜底）</b>：maxRetryCount=3, retryIntervals="1,3,5"</li>
 * </ol>
 *
 * <p><b>使用示例（零 YAML 配置）：</b>
 * <pre>{@code
 * // 最简用法：只用 SDK 默认值（3次，间隔 1/3/5 分钟）
 * @RetryableTask(sceneType = 100, idempotentKey = "#docId")
 * public void deleteDoc(String docId) { ... }
 *
 * // 完整用法：注解直接声明策略，无需任何 YAML
 * @RetryableTask(
 *     sceneType     = 100,
 *     idempotentKey = "#docId",
 *     maxRetryCount = 5,
 *     retryIntervals = "1,2,5,10,30",   // 分钟，逗号分隔
 *     hookClass     = DocDeleteRetryHook.class
 * )
 * public void deleteDoc(String docId) { ... }
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
     * 最大重试次数（含首次执行失败后的第一次重试）
     * <p>0 表示使用默认值（3次）或 YAML 配置覆盖值。
     * <p><b>优先级：</b>YAML scenes[sceneType].maxRetryCount > 此值 > SDK 默认(3)
     */
    int maxRetryCount() default 0;

    /**
     * 自定义重试间隔（分钟，逗号分隔的整数列表）
     * <p>格式：{@code "1,3,5,10,30"} 表示第1次等1分钟、第2次等3分钟、以此类推，超出范围取最后一个值
     * <p>空字符串（默认）表示使用 YAML scenes 配置或 SDK 默认值（"1,3,5"）
     * <p><b>优先级：</b>YAML scenes[sceneType].retryIntervals > 此值 > SDK 默认("1,3,5")
     */
    String retryIntervals() default "";

    /**
     * 退避策略（仅在 retryIntervals 为空时生效）
     * <ul>
     *   <li>{@link BackoffType#CUSTOM}（默认）：使用 {@link #retryIntervals()} 中的自定义间隔列表</li>
     *   <li>{@link BackoffType#FIXED}：固定间隔，间隔 = {@link #backoffBase()} 分钟</li>
     *   <li>{@link BackoffType#LINEAR}：线性递增，间隔 = retryCount * {@link #backoffBase()} 分钟</li>
     *   <li>{@link BackoffType#EXPONENTIAL}：指数递增，间隔 = 2^(retryCount-1) * {@link #backoffBase()} 分钟</li>
     * </ul>
     */
    BackoffType backoffStrategy() default BackoffType.CUSTOM;

    /**
     * 退避基数（分钟），仅 FIXED/LINEAR/EXPONENTIAL 策略使用
     * <p>0 表示使用 YAML 配置或 SDK 默认值（1分钟）
     */
    int backoffBase() default 0;

    /**
     * 重试钩子类（实现 {@link RetryHook} 接口的类）
     * <p>提供三步幂等保护：checkStatus → doQuery → doCallback
     * <p>{@link RetryHook}.class（默认）表示未指定，退化为直接反射重试原始方法（无幂等保护）
     * <p><b>优先级：</b>YAML scenes[sceneType].hookClass > 此值
     * <p><b>IDE 友好</b>：直接写 {@code hookClass = DocDeleteRetryHook.class}，支持重构跳转，不再是字符串！
     */
    Class<? extends RetryHook> hookClass() default RetryHook.class;

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
     *   <li>{@code false}（默认）：使用 hookClass 中配置的钩子</li>
     *   <li>{@code true}：跳过 Hook 三步检查（checkStatus/doQuery/doCallback），
     *       直接反射调用业务方法执行重试。适用于简单场景，无需编写 Hook 类。</li>
     * </ul>
     * @deprecated 请直接使用 {@link #hookClass()} 默认值（RetryHook.class），效果等价
     */
    @Deprecated
    boolean useDefaultHook() default false;
}
