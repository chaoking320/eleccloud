package com.retry.platform.example.controller;

import com.retry.platform.example.hook.InventoryRetryHook;
import com.retry.platform.example.hook.RefundRetryHook;
import com.retry.platform.example.hook.SettlementRetryHook;
import com.retry.platform.example.service.InventoryService;
import com.retry.platform.example.service.RefundService;
import com.retry.platform.example.service.SettlementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 分布式重试平台 Demo 控制器
 *
 * <p>演示三种接入模式在同一业务领域（支付/订单）下的不同使用方式：
 * <ul>
 *   <li>Mode 1 - 注解模式（退款）：最简单，自动拦截，适合大多数场景</li>
 *   <li>Mode 2 - API 模式（结算）：手动控制，适合复杂决策场景</li>
 *   <li>Mode 3 - 预提交模式（库存同步）：事前保护，适合强一致性场景</li>
 * </ul>
 *
 * <h3>快速体验步骤：</h3>
 * <ol>
 *   <li>启动 retry-server (port 8080)</li>
 *   <li>启动 retry-admin (port 8081)</li>
 *   <li>启动 retry-example (port 8082)</li>
 *   <li>在 Admin 后台配置好 3 个场景（或使用 init.sql 初始化数据）</li>
 *   <li>按顺序调用下面的 API 触发演示</li>
 * </ol>
 */
@Slf4j
@RestController
@RequestMapping("/api/demo")
@CrossOrigin
public class RefundController {

    @Autowired
    private RefundService refundService;

    @Autowired
    private SettlementService settlementService;

    @Autowired
    private InventoryService inventoryService;

    // ===================================================================
    // MODE 1: 注解模式（@RetryableTask）
    // ===================================================================

    /**
     * 触发退款（注解模式演示）
     *
     * <p>调用方式：GET http://localhost:8082/api/demo/mode1/refund
     *
     * <p>预期行为：
     * <ol>
     *   <li>第1次调用：RefundService.refund() 抛出超时异常，AOP 自动将任务提交到重试平台（INIT状态）</li>
     *   <li>平台定时（1分钟后）自动重试：再次调用 refund()，推送退款到支付中心（WAIT状态）</li>
     *   <li>平台继续查询（5分钟后）：调用 RefundRetryHook.doQuery() 确认支付中心退款成功</li>
     *   <li>平台回调：调用 RefundRetryHook.doCallback() 更新本地库为 SUCCESS</li>
     * </ol>
     */
    @GetMapping("/mode1/refund")
    public Map<String, Object> triggerRefund(
            @RequestParam(defaultValue = "100.0") Double amount,
            @RequestParam(defaultValue = "七天无理由退货") String reason) {

        String orderId = "REFUND_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Map<String, Object> resp = buildBaseResponse("MODE1_ANNOTATION", orderId);
        resp.put("description", "注解模式：@RetryableTask 自动拦截，失败后提交重试任务");
        resp.put("scene", "电商退款");
        resp.put("backoffStrategy", "CUSTOM（1/5/10/30分钟）");
        resp.put("amount", amount);

        try {
            refundService.refund(orderId, amount, reason);
            resp.put("firstCallResult", "SUCCESS_IMMEDIATELY（幂等：已完成）");
        } catch (Exception e) {
            resp.put("firstCallResult", "FAILED_AS_EXPECTED（模拟超时，已自动提交重试任务）");
            resp.put("failReason", e.getMessage());
        }

        resp.put("currentLocalStatus", RefundRetryHook.localDb.getOrDefault(orderId, "INIT"));
        resp.put("nextStep", "等待 retry-server 定时调度（约1分钟），观察 Admin 后台任务状态变化");
        return resp;
    }

    // ===================================================================
    // MODE 2: API 模式（手动调用 RetryClient.submit()）
    // ===================================================================

    /**
     * 触发酒店结算（API 模式演示）
     *
     * <p>调用方式：GET http://localhost:8082/api/demo/mode2/settlement
     *
     * <p>预期行为：
     * <ol>
     *   <li>SettlementService.settle() 内部捕获超时异常，手动调用 RetryClient.submit() 提交任务</li>
     *   <li>平台定时重试（LINEAR策略，2分钟后）：再次调用 settle()，OTA网关成功（WAIT状态）</li>
     *   <li>平台继续查询（4分钟后）：调用 SettlementRetryHook.doQuery() 等待OTA审批</li>
     *   <li>第2次查询（6分钟后）：审批通过，doCallback() 更新状态为 SUCCESS</li>
     * </ol>
     */
    @GetMapping("/mode2/settlement")
    public Map<String, Object> triggerSettlement(
            @RequestParam(defaultValue = "5000.0") Double amount,
            @RequestParam(defaultValue = "HT_MARRIOTT") String hotelCode) {

        String settlementId = "STL_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Map<String, Object> resp = buildBaseResponse("MODE2_API", settlementId);
        resp.put("description", "API模式：手动调用 RetryClient.submit()，精细控制何时提交重试");
        resp.put("scene", "酒店结算");
        resp.put("backoffStrategy", "LINEAR（base=2，间隔递增 2/4/6/8/10分钟）");
        resp.put("amount", amount);

        String result = settlementService.settle(settlementId, amount, hotelCode);
        if (result != null) {
            resp.put("firstCallResult", "SUCCESS_IMMEDIATELY");
        } else {
            resp.put("firstCallResult", "FAILED_AS_EXPECTED（超时，已手动提交重试任务到平台）");
        }

        resp.put("currentLocalStatus", SettlementRetryHook.localDb.getOrDefault(settlementId, "INIT"));
        resp.put("nextStep", "等待 retry-server 定时调度（约2分钟），观察 Admin 后台任务状态变化");
        return resp;
    }

    // ===================================================================
    // MODE 3: 预提交模式（@RetryableTask(preSubmit = true)）
    // ===================================================================

    /**
     * 触发库存同步（预提交模式演示）
     *
     * <p>调用方式：GET http://localhost:8082/api/demo/mode3/inventory
     *
     * <p>预期行为：
     * <ol>
     *   <li>AOP 拦截 syncInventory()，先向平台注册 INIT 任务（事前保护）</li>
     *   <li>执行 syncInventory() → 失败（超时）→ AOP 不再重复提交，任务保持 INIT</li>
     *   <li>平台定时重试（EXPONENTIAL策略，1分钟后）：再次调用 syncInventory()，成功</li>
     *   <li>成功后 AOP 自动调用 markSuccess()，任务标记为 SUCCESS</li>
     * </ol>
     *
     * <p>关键价值：若应用在 Step 2 执行完但崩溃（无法 markSuccess），
     * 平台会在 Step 3 重试，此时 checkStatus 检测到本地已成功，直接标记 SUCCESS，
     * <b>不会重复执行扣减逻辑</b>。
     */
    @GetMapping("/mode3/inventory")
    public Map<String, Object> triggerInventorySync(
            @RequestParam(defaultValue = "SKU_12345") String skuId,
            @RequestParam(defaultValue = "10") Integer delta) {

        Map<String, Object> resp = buildBaseResponse("MODE3_PRE_SUBMIT", skuId);
        resp.put("description", "预提交模式：方法执行前先注册任务，成功后标记SUCCESS，崩溃也能重试");
        resp.put("scene", "库存同步");
        resp.put("backoffStrategy", "EXPONENTIAL（base=1，间隔指数 1/2/4/8/16分钟）");
        resp.put("delta", delta);

        try {
            inventoryService.syncInventory(skuId, delta);
            resp.put("firstCallResult", "SUCCESS_IMMEDIATELY（方法直接成功，已标记SUCCESS）");
        } catch (Exception e) {
            resp.put("firstCallResult", "FAILED_AS_EXPECTED（方法失败，预注册的任务保持INIT等待重试）");
            resp.put("failReason", e.getMessage());
        }

        resp.put("currentLocalStatus", InventoryRetryHook.localDb.getOrDefault(skuId, "INIT"));
        resp.put("nextStep", "等待 retry-server 定时调度（约1分钟），EXPONENTIAL策略间隔：1/2/4/8/16分钟");
        return resp;
    }

    // ===================================================================
    // 查询接口
    // ===================================================================

    /**
     * 查询所有模式的当前演示状态
     * GET http://localhost:8082/api/demo/status
     */
    @GetMapping("/status")
    public Map<String, Object> getAllStatus() {
        Map<String, Object> resp = new HashMap<>();
        resp.put("mode1_refund_orders", RefundRetryHook.localDb);
        resp.put("mode2_settlement_orders", SettlementRetryHook.localDb);
        resp.put("mode3_inventory_skus", InventoryRetryHook.localDb);
        resp.put("hint", "观察 Admin 后台（http://localhost:8081）查看详细任务状态和重试历史");
        return resp;
    }

    /**
     * 构建基础响应结构
     */
    private Map<String, Object> buildBaseResponse(String mode, String businessId) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("integrationMode", mode);
        resp.put("businessId", businessId);
        resp.put("retryServerAdmin", "http://localhost:8081");
        return resp;
    }
}
