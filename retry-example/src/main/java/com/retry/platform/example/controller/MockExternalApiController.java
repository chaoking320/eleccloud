package com.retry.platform.example.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 模拟第三方外部服务 API
 *
 * <p>这些接口扮演"下游服务"的角色（支付宝、OTA平台、仓储系统等）。
 * 它们被 {@link BusinessController} 里的业务方法通过 HTTP 调用。
 *
 * <p>每个接口都内置了可配置的失败行为：
 * <ul>
 *   <li>前 N 次调用返回失败/超时</li>
 *   <li>N 次后自动恢复正常，返回成功</li>
 * </ul>
 *
 * <p>这样就能演示：业务调用失败 → ElecCloud SDK 提交重试任务 → 平台自动重试 → 最终成功
 */
@Slf4j
@RestController
@RequestMapping("/mock-api")
public class MockExternalApiController {

    /**
     * 每个业务ID的调用次数计数器（用于控制"前N次失败"行为）
     * key: transId, value: 调用次数
     */
    private final Map<String, AtomicInteger> callCounters = new ConcurrentHashMap<>();

    /**
     * 需要失败的次数（前 FAIL_TIMES 次调用返回失败，之后恢复正常）
     * 可通过 /mock-api/config 接口动态调整
     */
    private volatile int failTimes = 2;

    // ================================================================
    // 支付/退款 API（模拟支付宝/微信支付接口）
    // ================================================================

    /**
     * 发起退款申请
     *
     * <p>模拟行为：
     * <ul>
     *   <li>第1次调用 → 连接超时（直接返回 500）</li>
     *   <li>第2次调用 → 请求已受理，退款处理中（返回 PROCESSING）</li>
     *   <li>第3次调用及以后 → 退款成功（返回 SUCCESS）</li>
     * </ul>
     */
    @PostMapping("/payment/refund")
    public Map<String, Object> refund(@RequestBody Map<String, Object> req) {
        String transId = String.valueOf(req.get("transId"));
        int callCount = callCounters.computeIfAbsent(transId, k -> new AtomicInteger(0))
                                    .incrementAndGet();

        log.info("[MockPaymentAPI] refund called: transId={}, callCount={}", transId, callCount);

        if (callCount <= failTimes) {
            log.warn("[MockPaymentAPI] ❌ Simulating timeout/failure for transId={} (call #{}/{})",
                    transId, callCount, failTimes);
            // 模拟超时：抛出运行时异常，让业务方捕获到调用失败
            throw new RuntimeException("支付宝网关连接超时（模拟），transId=" + transId);
        }

        log.info("[MockPaymentAPI] ✅ Refund accepted for transId={} (recovered after {} failures)",
                transId, failTimes);
        return com.retry.platform.example.util.MapUtil.of(
            "code", "SUCCESS",
            "transId", transId,
            "message", "退款申请已受理，预计T+1到账",
            "callCount", callCount
        );
    }

    /**
     * 查询退款状态（幂等查询接口）
     */
    @GetMapping("/payment/refund/status")
    public Map<String, Object> queryRefundStatus(@RequestParam String transId) {
        int callCount = callCounters.getOrDefault(transId, new AtomicInteger(0)).get();
        // 已调用过退款接口且超过失败次数，则认为已成功
        boolean success = callCount > failTimes;
        return com.retry.platform.example.util.MapUtil.of(
            "transId", transId,
            "status", success ? "SUCCESS" : "PROCESSING",
            "callCount", callCount
        );
    }

    // ================================================================
    // OTA 结算 API（模拟美团/携程酒店结算接口）
    // ================================================================

    /**
     * 提交酒店结算请求
     *
     * <p>模拟行为：前 failTimes 次返回"网关繁忙"，之后返回"已提交审批"
     */
    @PostMapping("/hotel/settlement")
    public Map<String, Object> settlement(@RequestBody Map<String, Object> req) {
        String transId = String.valueOf(req.get("transId"));
        int callCount = callCounters.computeIfAbsent(transId, k -> new AtomicInteger(0))
                                    .incrementAndGet();

        log.info("[MockOtaAPI] settlement called: transId={}, callCount={}", transId, callCount);

        if (callCount <= failTimes) {
            log.warn("[MockOtaAPI] ❌ OTA gateway busy for transId={} (call #{}/{})",
                    transId, callCount, failTimes);
            throw new RuntimeException("OTA网关繁忙，请稍后重试，transId=" + transId);
        }

        log.info("[MockOtaAPI] ✅ Settlement submitted for transId={}", transId);
        return com.retry.platform.example.util.MapUtil.of(
            "code", "ACCEPTED",
            "transId", transId,
            "message", "结算申请已提交，等待OTA平台审批",
            "estimatedTime", "T+2工作日"
        );
    }

    /**
     * 查询结算审批状态
     */
    @GetMapping("/hotel/settlement/status")
    public Map<String, Object> querySettlementStatus(@RequestParam String transId) {
        int callCount = callCounters.getOrDefault(transId, new AtomicInteger(0)).get();
        boolean approved = callCount > failTimes + 1; // 多一次延迟，模拟审批需要时间
        return com.retry.platform.example.util.MapUtil.of(
            "transId", transId,
            "approvalStatus", approved ? "APPROVED" : "PENDING",
            "callCount", callCount
        );
    }

    // ================================================================
    // 仓储库存 API（模拟WMS仓库管理系统接口）
    // ================================================================

    /**
     * 同步库存变更
     *
     * <p>模拟行为：前 failTimes 次返回"WMS系统维护中"，之后同步成功
     */
    @PostMapping("/warehouse/inventory/sync")
    public Map<String, Object> syncInventory(@RequestBody Map<String, Object> req) {
        String transId = String.valueOf(req.get("transId"));
        String skuId   = String.valueOf(req.get("skuId"));
        int callCount = callCounters.computeIfAbsent(transId, k -> new AtomicInteger(0))
                                    .incrementAndGet();

        log.info("[MockWmsAPI] inventory sync called: transId={}, sku={}, callCount={}",
                transId, skuId, callCount);

        if (callCount <= failTimes) {
            log.warn("[MockWmsAPI] ❌ WMS system maintenance for transId={} (call #{}/{})",
                    transId, callCount, failTimes);
            throw new RuntimeException("WMS系统维护中，库存同步失败，transId=" + transId);
        }

        log.info("[MockWmsAPI] ✅ Inventory synced for transId={}, sku={}", transId, skuId);
        return com.retry.platform.example.util.MapUtil.of(
            "code", "SUCCESS",
            "transId", transId,
            "skuId", skuId,
            "message", "库存同步成功"
        );
    }

    // ================================================================
    // 配置接口（动态调整失败次数）
    // ================================================================

    /**
     * 查看/修改失败次数配置
     * GET  /mock-api/config         → 查看当前配置
     * POST /mock-api/config?failTimes=3 → 修改失败次数
     */
    @GetMapping("/config")
    public Map<String, Object> getConfig() {
        return com.retry.platform.example.util.MapUtil.of(
            "failTimes", failTimes,
            "description", "前 failTimes 次调用会模拟失败，之后自动恢复"
        );
    }

    @PostMapping("/config")
    public Map<String, Object> setConfig(@RequestParam int failTimes) {
        this.failTimes = failTimes;
        log.info("[MockAPI] Config updated: failTimes={}", failTimes);
        return com.retry.platform.example.util.MapUtil.of("success", true, "failTimes", failTimes);
    }

    /**
     * 重置所有计数器（方便重新演示）
     */
    @PostMapping("/reset")
    public Map<String, Object> reset() {
        callCounters.clear();
        log.info("[MockAPI] All call counters reset");
        return com.retry.platform.example.util.MapUtil.of("success", true, "message", "所有外部API调用计数已清零，可重新演示");
    }
}
