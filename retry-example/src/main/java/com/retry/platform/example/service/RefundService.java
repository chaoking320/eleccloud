package com.retry.platform.example.service;

import com.retry.platform.client.annotation.RetryableTask;
import com.retry.platform.example.hook.RefundRetryHook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模拟支付中心退款业务服务
 */
@Slf4j
@Service
public class RefundService {

    // 记录订单的首次失败标记，用以模拟首次调用超时或不可用
    private final Map<String, Boolean> failedOnce = new ConcurrentHashMap<>();

    /**
     * 发起退款申请
     * 使用 @RetryableTask 注解，当发生异常时会触发分布式重试平台
     */
    @RetryableTask(sceneType = 1, idempotentKey = "#orderId")
    public boolean refund(String orderId, Double amount, String reason) {
        log.info("[RefundService] Executing refund method: orderId={}, amount={}, reason={}", 
                orderId, amount, reason);

        // 1. 检查本地数据库订单状态
        String currentStatus = RefundRetryHook.localDb.getOrDefault(orderId, "INIT");
        if ("SUCCESS".equals(currentStatus)) {
            log.info("[RefundService] Refund order {} already succeeded. Ignoring.", orderId);
            return true;
        }

        // 2. 模拟首次网络超时/服务不可用
        if (!failedOnce.containsKey(orderId)) {
            failedOnce.put(orderId, true);
            log.warn("[RefundService] Network connection timeout to payment center for orderId={}. Throwing exception...", orderId);
            throw new RuntimeException("Simulated network timeout connecting to Alipay/WeChat API");
        }

        // 3. 第二次调用（由重试平台执行），成功将退款请求推送到支付中心，状态更新为 WAIT
        log.info("[RefundService] Refund request successfully pushed to Alipay/WeChat! Waiting for status confirmation for orderId={}", orderId);
        RefundRetryHook.localDb.put(orderId, "WAIT");
        return true;
    }
}
