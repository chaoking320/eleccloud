package com.retry.platform.example.service;

import com.retry.platform.client.annotation.RetryableTask;
import com.retry.platform.example.hook.RefundRetryHook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 退款服务 —— 接入方式：注解模式（@RetryableTask）
 *
 * <p>直接在方法上标注 {@code @RetryableTask}，AOP 自动拦截并在失败时提交重试任务。
 * 这是最简单、侵入性最低的接入方式，适合大多数场景。
 */
@Slf4j
@Service
public class RefundService {

    /** 记录退款单的首次失败标记，模拟真实的首次网络超时 */
    private final Map<String, Boolean> failedOnce = new ConcurrentHashMap<>();

    /**
     * 发起退款申请
     *
     * <p>注解说明：
     * <ul>
     *   <li>{@code sceneType = 1}：对应场景1配置（CUSTOM策略，间隔 1/5/10/30 分钟）</li>
     *   <li>{@code idempotentKey = "#transId"}：用统一的交易流水号作为幂等键</li>
     *   <li>{@code preSubmit = false}（默认）：失败后提交，适合明确抛异常的场景</li>
     * </ul>
     */
    @RetryableTask(sceneType = 1, idempotentKey = "#transId", throwException = true)
    public boolean refund(String transId, String orderId, Double amount, String reason) {
        log.info("[RefundService] Executing refund: transId={}, orderId={}, amount={}, reason={}", transId, orderId, amount, reason);

        // 检查本地状态（幂等性保障）
        String currentStatus = RefundRetryHook.localDb.getOrDefault(transId, "INIT");
        if ("SUCCESS".equals(currentStatus)) {
            log.info("[RefundService] Refund {} already succeeded (idempotent check). Skipping.", transId);
            return true;
        }

        // 模拟首次网络超时 / 第三方服务不可用
        if (!failedOnce.containsKey(transId)) {
            failedOnce.put(transId, true);
            log.warn("[RefundService] Network timeout to payment center for transId={}. Simulating failure...", transId);
            throw new RuntimeException("Simulated network timeout: payment center unavailable");
        }

        // 第二次调用（由重试平台驱动）：成功推送退款请求，进入 WAIT 状态等待支付方确认
        log.info("[RefundService] Refund request pushed to payment center for transId={}. Status -> WAIT", transId);
        RefundRetryHook.localDb.put(transId, "WAIT");
        return true;
    }
}
