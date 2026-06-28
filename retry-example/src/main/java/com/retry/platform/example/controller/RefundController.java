package com.retry.platform.example.controller;

import com.retry.platform.example.hook.RefundRetryHook;
import com.retry.platform.example.service.RefundService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 示例演示控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/demo")
@CrossOrigin
public class RefundController {

    @Autowired
    private RefundService refundService;

    /**
     * 发起模拟退款
     * http://localhost:8082/api/demo/refund?amount=100.0&reason=七天无理由退货
     */
    @GetMapping("/refund")
    public Map<String, Object> triggerRefund(
            @RequestParam(defaultValue = "100.0") Double amount,
            @RequestParam(defaultValue = "七天无理由退货") String reason) {
        
        String orderId = "REFUND_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("[DemoController] Triggering simulated refund for orderId={}, amount={}", orderId, amount);

        Map<String, Object> response = new HashMap<>();
        response.put("orderId", orderId);
        response.put("amount", amount);
        response.put("reason", reason);
        response.put("initialLocalDbStatus", "INIT");

        try {
            // 这将调用带有 @RetryableTask 的方法
            // 第一次执行必然抛出 RuntimeException(模拟网络超时)，AOP 会将其拦截并提交到重试中心
            refundService.refund(orderId, amount, reason);
            response.put("status", "SUCCESS_IMMEDIATELY");
            response.put("message", "退款立即成功");
        } catch (Exception e) {
            log.warn("[DemoController] Refund initially failed as expected: {}", e.getMessage());
            response.put("status", "SUBMITTED_TO_RETRY_PLATFORM");
            response.put("message", "首次调用失败 (模拟超时)，已成功异步提交到重试平台！错误: " + e.getMessage());
        }

        // 获取最新的本地状态
        response.put("currentLocalDbStatus", RefundRetryHook.localDb.getOrDefault(orderId, "INIT"));
        return response;
    }

    /**
     * 查询本地模拟数据库中所有订单状态
     * http://localhost:8082/api/demo/orders
     */
    @GetMapping("/orders")
    public Map<String, Object> getOrders() {
        Map<String, Object> response = new HashMap<>();
        response.put("orders", RefundRetryHook.localDb);
        return response;
    }
}
