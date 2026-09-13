package com.retry.platform.client.hook;

/**
 * 重试钩子接口 - 业务方自定义重试逻辑的核心接口
 * 
 * <h2>概述</h2>
 * <p>RetryHook 接口允许业务方定义自己的重试状态检查、远程查询和成功回调逻辑。
 * 它是 ElecCloud 分布式重试平台的核心扩展点，通过实现此接口可以实现：
 * <ul>
 *   <li>幂等性保障 - 通过 checkStatus 避免重复执行</li>
 *   <li>远程状态确认 - 通过 doQuery 查询第三方系统</li>
 *   <li>成功后处理 - 通过 doCallback 执行后续业务逻辑</li>
 * </ul>
 * 
 * <h2>工作流程</h2>
 * <p>当重试任务被触发时，LocalRetryExecutor 会按以下顺序调用 Hook 方法：
 * <pre>
 * 1. checkStatus(context)      → 检查本地状态（如数据库）
 *    ├─ 返回 "SUCCESS" → 任务已完成，跳过重试，调用 doCallback
 *    ├─ 返回 "WAIT"    → 任务执行中，调用 doQuery 确认状态
 *    └─ 返回 "INIT"    → 任务未完成，反射执行业务方法
 * 
 * 2. doQuery(context)           → 查询第三方系统（如支付宝）
 *    ├─ 返回 success   → 操作成功，调用 doCallback，标记任务 SUCCESS
 *    ├─ 返回 pending   → 操作处理中，继续重试
 *    └─ 抛出异常       → 查询失败，继续重试
 * 
 * 3. doCallback(context, result) → 执行成功后回调（如更新订单状态）
 *    └─ 失败不影响任务标记为 SUCCESS
 * </pre>
 * 
 * <h2>实现示例</h2>
 * 
 * <h3>示例1：退款场景</h3>
 * <pre>{@code
 * @Component
 * public class RefundRetryHook implements RetryHook {
 *     
 *     @Autowired
 *     private OrderService orderService;
 *     
 *     @Autowired
 *     private AlipayClient alipayClient;
 *     
 *     @Override
 *     public String checkStatus(RetryContext context) {
 *         // 检查本地数据库状态（快速判断）
 *         String orderId = context.getIdempotentKey();
 *         Order order = orderService.getOrder(orderId);
 *         
 *         if (order == null) {
 *             return "INIT";  // 订单不存在，需要执行
 *         }
 *         
 *         if (order.getRefundStatus() == RefundStatus.SUCCESS) {
 *             return "SUCCESS";  // 已退款成功，跳过重试
 *         }
 *         
 *         if (order.getRefundStatus() == RefundStatus.PROCESSING) {
 *             return "WAIT";  // 退款处理中，需要查询支付宝确认
 *         }
 *         
 *         return "INIT";  // 其他状态，需要执行退款
 *     }
 *     
 *     @Override
 *     public QueryResult doQuery(RetryContext context) {
 *         // 查询支付宝退款状态（远程确认）
 *         String orderId = context.getIdempotentKey();
 *         
 *         try {
 *             AlipayRefundQueryResponse response = alipayClient.queryRefund(orderId);
 *             
 *             if (response.isSuccess()) {
 *                 // 退款成功
 *                 return QueryResult.success(response.getRefundAmount());
 *             } else if (response.isPending()) {
 *                 // 退款处理中
 *                 return QueryResult.pending("退款处理中，请稍后查询");
 *             } else {
 *                 // 退款失败
 *                 return QueryResult.failure("退款失败：" + response.getErrorMsg());
 *             }
 *         } catch (Exception e) {
 *             // 查询异常，任务会继续重试
 *             throw new RuntimeException("查询支付宝退款状态失败", e);
 *         }
 *     }
 *     
 *     @Override
 *     public void doCallback(RetryContext context, QueryResult result) {
 *         // 退款成功后的回调处理
 *         String orderId = context.getIdempotentKey();
 *         
 *         // 1. 更新订单状态
 *         orderService.updateRefundStatus(orderId, RefundStatus.SUCCESS);
 *         
 *         // 2. 发送退款成功通知
 *         notificationService.sendRefundSuccessNotification(orderId);
 *         
 *         // 3. 记录退款成功日志
 *         log.info("退款成功回调完成: orderId={}, amount={}", orderId, result.getData());
 *     }
 * }
 * }</pre>
 * 
 * <h3>示例2：库存扣减场景</h3>
 * <pre>{@code
 * @Component
 * public class InventoryRetryHook implements RetryHook {
 *     
 *     @Autowired
 *     private InventoryService inventoryService;
 *     
 *     @Override
 *     public String checkStatus(RetryContext context) {
 *         // 检查本地库存扣减记录
 *         String deductionId = context.getIdempotentKey();
 *         InventoryRecord record = inventoryService.getRecord(deductionId);
 *         
 *         if (record != null && record.getStatus() == RecordStatus.SUCCESS) {
 *             return "SUCCESS";  // 已扣减成功
 *         }
 *         
 *         return "INIT";  // 未扣减或失败，需要重试
 *     }
 *     
 *     @Override
 *     public QueryResult doQuery(RetryContext context) {
 *         // 库存扣减不需要远程查询，直接返回成功
 *         // （因为业务方法执行成功即表示扣减成功）
 *         String deductionId = context.getIdempotentKey();
 *         InventoryRecord record = inventoryService.getRecord(deductionId);
 *         
 *         if (record != null && record.getStatus() == RecordStatus.SUCCESS) {
 *             return QueryResult.success("库存扣减成功");
 *         }
 *         
 *         return QueryResult.pending("库存扣减记录未找到");
 *     }
 *     
 *     @Override
 *     public void doCallback(RetryContext context, QueryResult result) {
 *         // 库存扣减成功后，发送MQ消息通知下游系统
 *         String deductionId = context.getIdempotentKey();
 *         messageProducer.sendInventoryDeductedEvent(deductionId);
 *         
 *         log.info("库存扣减完成，已发送MQ通知: deductionId={}", deductionId);
 *     }
 * }
 * }</pre>
 * 
 * <h2>最佳实践</h2>
 * 
 * <h3>1. checkStatus 方法</h3>
 * <ul>
 *   <li>✅ 快速返回 - 仅查询本地数据库，不要调用远程服务</li>
 *   <li>✅ 幂等检查 - 确保返回 SUCCESS 时任务确实已完成</li>
 *   <li>✅ 异常处理 - 捕获异常并返回 INIT（会记录日志并继续重试）</li>
 *   <li>❌ 避免耗时操作 - 不要执行复杂计算或远程调用</li>
 * </ul>
 * 
 * <h3>2. doQuery 方法</h3>
 * <ul>
 *   <li>✅ 幂等查询 - 支持多次调用，每次返回一致结果</li>
 *   <li>✅ 超时控制 - 设置合理的超时时间，避免阻塞</li>
 *   <li>✅ 异常抛出 - 查询失败时抛出异常（会触发重试）</li>
 *   <li>✅ 返回详细信息 - 使用 QueryResult 返回状态和数据</li>
 *   <li>❌ 不要修改状态 - 仅查询，不要执行业务操作</li>
 * </ul>
 * 
 * <h3>3. doCallback 方法</h3>
 * <ul>
 *   <li>✅ 幂等实现 - 支持多次调用，避免重复处理</li>
 *   <li>✅ 异步处理 - 复杂逻辑建议发送MQ异步处理</li>
 *   <li>✅ 异常捕获 - 回调失败不应影响任务标记为 SUCCESS</li>
 *   <li>❌ 避免长时间阻塞 - 不要执行耗时操作</li>
 * </ul>
 * 
 * <h2>返回值说明</h2>
 * 
 * <h3>checkStatus 返回值</h3>
 * <table border="1">
 * <tr><th>返回值</th><th>含义</th><th>后续动作</th></tr>
 * <tr><td>"SUCCESS"</td><td>任务已完成</td><td>跳过重试，调用 doCallback，标记 SUCCESS</td></tr>
 * <tr><td>"WAIT"</td><td>任务执行中</td><td>调用 doQuery 查询远程状态</td></tr>
 * <tr><td>"INIT"</td><td>任务未完成</td><td>反射执行业务方法</td></tr>
 * <tr><td>其他值</td><td>视为 INIT</td><td>反射执行业务方法</td></tr>
 * </table>
 * 
 * <h3>doQuery 返回值</h3>
 * <table border="1">
 * <tr><th>返回值</th><th>含义</th><th>后续动作</th></tr>
 * <tr><td>QueryResult.success(data)</td><td>操作成功</td><td>调用 doCallback，标记 SUCCESS</td></tr>
 * <tr><td>QueryResult.pending(msg)</td><td>操作处理中</td><td>继续重试</td></tr>
 * <tr><td>QueryResult.failure(msg)</td><td>操作失败</td><td>继续重试</td></tr>
 * <tr><td>抛出异常</td><td>查询失败</td><td>继续重试</td></tr>
 * </table>
 * 
 * <h2>配置说明</h2>
 * <p>在场景配置中指定 Hook 类的完整类名：
 * <pre>
 * INSERT INTO scene_config (scene_type, scene_name, hook_class, ...) 
 * VALUES (1001, '退款重试', 'com.example.RefundRetryHook', ...);
 * </pre>
 * 
 * <p>或通过管理后台配置：
 * <pre>
 * POST /api/admin/scene/create
 * {
 *   "sceneType": 1001,
 *   "sceneName": "退款重试",
 *   "hookClass": "com.example.RefundRetryHook",
 *   "maxRetryCount": 5,
 *   "retryIntervals": "1,5,10,30,60"
 * }
 * </pre>
 * 
 * <h2>注意事项</h2>
 * <ul>
 *   <li>Hook 类必须添加 @Component 注解，由 Spring 管理</li>
 *   <li>Hook 类必须在客户端应用的 classpath 中</li>
 *   <li>所有方法都应该支持幂等调用</li>
 *   <li>避免在 Hook 方法中执行耗时操作</li>
 *   <li>异常处理要完善，避免影响重试流程</li>
 * </ul>
 * 
 * @see RetryContext
 * @see QueryResult
 * @see com.retry.platform.client.annotation.RetryableTask
 * @since 1.0.0
 */
public interface RetryHook {
    
    /**
     * 检查任务当前状态（本地快速检查）
     * 
     * <p>此方法在每次重试前被调用，用于快速判断任务是否已完成，避免重复执行。
     * 
     * <h3>使用场景</h3>
     * <ul>
     *   <li>查询本地数据库状态（如订单状态、库存记录）</li>
     *   <li>检查本地缓存（Redis）</li>
     *   <li>快速幂等性校验</li>
     * </ul>
     * 
     * <h3>实现要点</h3>
     * <ul>
     *   <li>✅ 仅查询本地资源（数据库、缓存），响应速度 < 100ms</li>
     *   <li>✅ 不要调用远程服务（HTTP、RPC），远程查询应在 doQuery 中进行</li>
     *   <li>✅ 捕获所有异常，异常时返回 "INIT"（继续重试）</li>
     *   <li>✅ 幂等实现，多次调用返回一致结果</li>
     * </ul>
     * 
     * <h3>返回值说明</h3>
     * <table border="1">
     * <tr><th>返回值</th><th>含义</th><th>后续动作</th><th>使用场景</th></tr>
     * <tr>
     *   <td>"SUCCESS"</td>
     *   <td>任务已成功完成</td>
     *   <td>跳过重试，直接调用 doCallback 并标记任务为 SUCCESS</td>
     *   <td>订单已退款成功、库存已扣减成功</td>
     * </tr>
     * <tr>
     *   <td>"WAIT"</td>
     *   <td>任务已提交到远程系统，等待确认</td>
     *   <td>调用 doQuery 查询远程系统状态</td>
     *   <td>支付宝退款已提交，等待支付宝处理</td>
     * </tr>
     * <tr>
     *   <td>"INIT"</td>
     *   <td>任务未完成，需要执行</td>
     *   <td>反射调用业务方法，执行实际操作</td>
     *   <td>退款未提交、库存未扣减</td>
     * </tr>
     * <tr>
     *   <td>其他值</td>
     *   <td>视为 "INIT"</td>
     *   <td>反射调用业务方法</td>
     *   <td>-</td>
     * </tr>
     * </table>
     * 
     * <h3>代码示例</h3>
     * <pre>{@code
     * @Override
     * public String checkStatus(RetryContext context) {
     *     try {
     *         String orderId = context.getIdempotentKey();
     *         Order order = orderRepository.findById(orderId);
     *         
     *         if (order == null) {
     *             return "INIT";  // 订单不存在，需要创建
     *         }
     *         
     *         switch (order.getStatus()) {
     *             case REFUND_SUCCESS:
     *                 return "SUCCESS";  // 已成功，跳过重试
     *             case REFUND_PROCESSING:
     *                 return "WAIT";  // 处理中，查询远程状态
     *             default:
     *                 return "INIT";  // 需要重试
     *         }
     *     } catch (Exception e) {
     *         log.error("checkStatus failed", e);
     *         return "INIT";  // 异常时返回 INIT，继续重试
     *     }
     * }
     * }</pre>
     * 
     * @param context 重试上下文，包含幂等键、方法参数等信息
     *                通过 context.getIdempotentKey() 获取业务唯一标识
     *                通过 context.getMethodParamsJson() 获取方法参数
     * @return 任务状态：
     *         <ul>
     *         <li>"SUCCESS" - 任务已成功，跳过重试</li>
     *         <li>"WAIT" - 任务执行中，需要查询确认</li>
     *         <li>"INIT" - 任务未完成，需要执行/重试</li>
     *         </ul>
     * @throws RuntimeException 如果检查过程出现异常，建议捕获并返回 "INIT"，
     *                          否则异常会被框架捕获，任务进入下一轮重试
     */
    RetryStatus checkStatus(RetryContext context);
    
    /**
     * 查询第三方系统确认最终状态（远程查询）
     * 
     * <p>此方法在以下情况被调用：
     * <ul>
     *   <li>checkStatus 返回 "WAIT" 时</li>
     *   <li>业务方法执行成功后</li>
     * </ul>
     * 
     * <h3>使用场景</h3>
     * <ul>
     *   <li>查询支付宝/微信退款状态</li>
     *   <li>查询第三方物流配送状态</li>
     *   <li>查询下游系统处理结果</li>
     * </ul>
     * 
     * <h3>实现要点</h3>
     * <ul>
     *   <li>✅ 必须支持幂等查询，可以被多次调用</li>
     *   <li>✅ 设置合理的超时时间（建议 3-10 秒）</li>
     *   <li>✅ 网络异常时抛出异常（会触发重试）</li>
     *   <li>✅ 使用 QueryResult 返回明确的状态和数据</li>
     *   <li>❌ 不要修改业务状态，仅查询</li>
     *   <li>❌ 不要执行重量级操作</li>
     * </ul>
     * 
     * <h3>返回值说明</h3>
     * <table border="1">
     * <tr><th>返回值</th><th>含义</th><th>后续动作</th></tr>
     * <tr>
     *   <td>QueryResult.success(data)</td>
     *   <td>操作已成功</td>
     *   <td>调用 doCallback，标记任务为 SUCCESS</td>
     * </tr>
     * <tr>
     *   <td>QueryResult.pending(message)</td>
     *   <td>操作处理中</td>
     *   <td>任务进入下一轮重试</td>
     * </tr>
     * <tr>
     *   <td>QueryResult.failure(message)</td>
     *   <td>操作失败</td>
     *   <td>任务进入下一轮重试</td>
     * </tr>
     * <tr>
     *   <td>抛出异常</td>
     *   <td>查询失败（网络超时等）</td>
     *   <td>任务进入下一轮重试</td>
     * </tr>
     * </table>
     * 
     * <h3>代码示例</h3>
     * <pre>{@code
     * @Override
     * public QueryResult doQuery(RetryContext context) {
     *     String orderId = context.getIdempotentKey();
     *     
     *     try {
     *         // 查询支付宝退款状态（远程调用）
     *         AlipayRefundQueryResponse response = alipayClient.queryRefund(orderId);
     *         
     *         if (response.isSuccess()) {
     *             // 退款成功，返回成功结果
     *             Map<String, Object> data = new HashMap<>();
     *             data.put("refundAmount", response.getRefundAmount());
     *             data.put("refundTime", response.getRefundTime());
     *             return QueryResult.success(data);
     *         } else if (response.isPending()) {
     *             // 退款处理中，返回待处理状态
     *             return QueryResult.pending("退款处理中，预计需要 1-3 分钟");
     *         } else {
     *             // 退款失败，返回失败状态（会继续重试）
     *             return QueryResult.failure("退款失败：" + response.getErrorMsg());
     *         }
     *     } catch (SocketTimeoutException e) {
     *         // 网络超时，抛出异常触发重试
     *         throw new RuntimeException("查询支付宝超时", e);
     *     } catch (Exception e) {
     *         // 其他异常，抛出异常触发重试
     *         throw new RuntimeException("查询支付宝失败", e);
     *     }
     * }
     * }</pre>
     * 
     * @param context 重试上下文，包含幂等键、方法参数等信息
     * @return QueryResult 查询结果：
     *         <ul>
     *         <li>QueryResult.success(data) - 操作已成功，data 为业务数据（可选）</li>
     *         <li>QueryResult.pending(message) - 操作处理中，需继续重试</li>
     *         <li>QueryResult.failure(message) - 操作失败，需继续重试</li>
     *         </ul>
     * @throws RuntimeException 如果查询异常（如网络超时、连接失败），
     *                          抛出异常会触发任务重试
     */
    QueryResult doQuery(RetryContext context);
    
    /**
     * 状态确认后的回调处理（可选的后置操作）
     * 
     * <p>此方法在 doQuery 返回成功后被调用，用于执行后续业务逻辑。
     * 
     * <h3>使用场景</h3>
     * <ul>
     *   <li>更新本地数据库状态</li>
     *   <li>发送业务通知（短信、邮件）</li>
     *   <li>发送MQ消息通知下游系统</li>
     *   <li>记录审计日志</li>
     * </ul>
     * 
     * <h3>实现要点</h3>
     * <ul>
     *   <li>✅ 必须支持幂等，可能被多次调用</li>
     *   <li>✅ 捕获所有异常，避免影响任务标记为 SUCCESS</li>
     *   <li>✅ 复杂逻辑建议异步处理（发送MQ）</li>
     *   <li>✅ 执行速度要快（< 1 秒），避免阻塞</li>
     *   <li>❌ 回调失败不会影响任务标记为 SUCCESS</li>
     *   <li>❌ 不要执行耗时操作（如大量数据处理）</li>
     * </ul>
     * 
     * <h3>异常处理</h3>
     * <p><strong>重要</strong>：doCallback 中的异常不会影响任务标记为 SUCCESS。
     * 因此必须自行捕获和处理所有异常，确保关键逻辑不会被忽略。
     * 
     * <h3>代码示例</h3>
     * <pre>{@code
     * @Override
     * public void doCallback(RetryContext context, QueryResult result) {
     *     String orderId = context.getIdempotentKey();
     *     
     *     try {
     *         // 1. 更新本地订单状态（幂等更新）
     *         orderRepository.updateRefundStatus(orderId, RefundStatus.SUCCESS);
     *         
     *         // 2. 发送退款成功通知（幂等发送）
     *         try {
     *             notificationService.sendRefundSuccessNotification(orderId);
     *         } catch (Exception e) {
     *             log.error("发送通知失败（非关键，仅记录）", e);
     *         }
     *         
     *         // 3. 发送MQ消息通知下游系统（建议异步处理）
     *         try {
     *             Map<String, Object> eventData = new HashMap<>();
     *             eventData.put("orderId", orderId);
     *             eventData.put("refundAmount", result.getData());
     *             messageProducer.sendRefundSuccessEvent(eventData);
     *         } catch (Exception e) {
     *             log.error("发送MQ失败（非关键，仅记录）", e);
     *         }
     *         
     *         // 4. 记录审计日志
     *         log.info("退款成功回调完成: orderId={}, amount={}", 
     *                 orderId, result.getData());
     *                 
     *     } catch (Exception e) {
     *         // ⚠️ 必须捕获异常，否则会影响后续流程
     *         log.error("doCallback 执行失败（不影响任务标记为 SUCCESS）: orderId={}", 
     *                 orderId, e);
     *         
     *         // 可选：发送告警通知运维人员
     *         alertService.sendAlert("doCallback 执行失败", e);
     *     }
     * }
     * }</pre>
     * 
     * <h3>幂等性保障</h3>
     * <p>由于此方法可能被多次调用，必须确保幂等性：
     * <pre>{@code
     * // 示例：使用唯一键保证幂等
     * public void doCallback(RetryContext context, QueryResult result) {
     *     String orderId = context.getIdempotentKey();
     *     String callbackKey = "refund_callback:" + orderId;
     *     
     *     // 使用 Redis 分布式锁或数据库唯一约束保证幂等
     *     if (redisTemplate.opsForValue().setIfAbsent(callbackKey, "1", 1, TimeUnit.HOURS)) {
     *         try {
     *             // 执行回调逻辑...
     *         } finally {
     *             // 可选：保留标记，避免重复执行
     *             // redisTemplate.delete(callbackKey);
     *         }
     *     } else {
     *         log.info("回调已执行过，跳过: orderId={}", orderId);
     *     }
     * }
     * }</pre>
     * 
     * @param context 重试上下文，包含幂等键、方法参数等信息
     * @param result doQuery 返回的成功结果，包含业务数据（如果有）
     *               通过 result.getData() 获取业务数据
     *               通过 result.getMessage() 获取成功消息
     */
    void doCallback(RetryContext context, QueryResult result);
}
