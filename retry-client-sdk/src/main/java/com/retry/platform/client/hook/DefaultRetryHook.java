package com.retry.platform.client.hook;

import lombok.extern.slf4j.Slf4j;

/**
 * 默认重试Hook实现 - 零配置模式
 * 
 * <p>适用于简单场景，无需查询第三方状态，方法执行成功即认为任务完成。
 * 
 * <h3>适用场景</h3>
 * <ul>
 *   <li>库存扣减 - 方法成功即扣减成功</li>
 *   <li>数据同步 - 方法成功即同步成功</li>
 *   <li>消息发送 - 方法成功即发送成功</li>
 *   <li>简单HTTP调用 - 不需要回查状态</li>
 * </ul>
 * 
 * <h3>使用方式</h3>
 * <pre>{@code
 * // 方式1: 注解自动使用（推荐）
 * @RetryableTask(
 *     sceneType = 1001,
 *     idempotentKey = "#orderId",
 *     useDefaultHook = true  // 使用默认Hook
 * )
 * public void syncInventory(String orderId, Integer quantity) {
 *     warehouseApi.syncStock(orderId, quantity);
 * }
 * 
 * // 方式2: 场景配置中不指定hookClass
 * // Admin后台创建场景时，hookClass留空，系统自动使用DefaultRetryHook
 * }</pre>
 * 
 * <h3>工作原理</h3>
 * <pre>
 * 1. checkStatus() → 总是返回 "INIT"，每次都重试
 * 2. 反射执行业务方法
 * 3. doQuery() → 方法成功即返回 success
 * 4. doCallback() → 无额外操作
 * 5. 标记任务 SUCCESS
 * </pre>
 * 
 * <h3>注意事项</h3>
 * <ul>
 *   <li>⚠️ 业务方法必须实现幂等（会被多次调用）</li>
 *   <li>⚠️ 不适合需要查询第三方状态的场景（如支付退款）</li>
 *   <li>⚠️ 方法抛异常会继续重试，直到成功或达到最大重试次数</li>
 * </ul>
 * 
 * @see RetryHook
 * @since 1.1.0
 */
@Slf4j
public class DefaultRetryHook implements RetryHook {
    
    /**
     * 总是返回 INIT，每次都重试
     * 
     * <p>默认实现不检查本地状态，直接重试业务方法。
     * 适用于简单场景，如库存扣减、数据同步等。
     * 
     * @param context 重试上下文
     * @return 总是返回 "INIT"
     */
    @Override
    public String checkStatus(RetryContext context) {
        log.debug("[DefaultRetryHook] checkStatus: taskId={}, always return INIT", 
                context.getTaskId());
        return "INIT";
    }
    
    /**
     * 方法执行成功即认为任务成功
     * 
     * <p>默认实现不查询第三方系统，仅根据方法执行结果判断。
     * 如果方法执行成功（没有抛异常），则认为任务成功。
     * 
     * @param context 重试上下文
     * @return 总是返回成功结果
     */
    @Override
    public QueryResult doQuery(RetryContext context) {
        log.debug("[DefaultRetryHook] doQuery: taskId={}, method executed successfully", 
                context.getTaskId());
        return QueryResult.success("Method executed successfully - using DefaultRetryHook");
    }
    
    /**
     * 无额外回调操作
     * 
     * <p>默认实现不执行任何回调逻辑。
     * 如果需要在任务成功后执行额外操作（如发送通知、更新状态），
     * 请自定义Hook实现。
     * 
     * @param context 重试上下文
     * @param result 查询结果
     */
    @Override
    public void doCallback(RetryContext context, QueryResult result) {
        log.debug("[DefaultRetryHook] doCallback: taskId={}, no additional callback action", 
                context.getTaskId());
        // 默认无操作
    }
}
