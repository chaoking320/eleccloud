package com.retry.platform.example.service;

import com.retry.platform.client.api.RetryClient;
import com.retry.platform.client.dto.RetryTaskRequest;
import com.retry.platform.example.hook.SettlementRetryHook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 结算服务 —— 接入方式：API 模式（手动调用 RetryClient.submit()）
 *
 * <p>不使用注解，业务代码直接调用 {@link RetryClient#submit} 提交重试任务。
 * 适合以下场景：
 * <ul>
 *   <li>方法签名无法修改（如继承的接口实现）</li>
 *   <li>需要根据异常类型决定是否重试（只重试超时，不重试业务异常）</li>
 *   <li>需要在捕获异常后做一些清理操作，再提交重试</li>
 * </ul>
 */
@Slf4j
@Service
public class SettlementService {

    @Autowired
    private RetryClient retryClient;

    /** 记录首次发送是否失败，模拟OTA网关超时 */
    private final Map<String, Boolean> failedOnce = new ConcurrentHashMap<>();

    /**
     * 向 OTA 平台发起结算请求
     *
     * <p>API 模式下，开发者完全掌控何时、是否提交重试任务：
     * <ol>
     *   <li>捕获超时异常</li>
     *   <li>手动构建 {@link RetryTaskRequest}，设置场景类型、幂等键、方法信息</li>
     *   <li>调用 {@code retryClient.submit(request)} 提交到平台</li>
     * </ol>
     */
    public String settle(String transId, Double amount, String hotelCode) {
        log.info("[SettlementService] Initiating settlement: transId={}, amount={}, hotel={}",
                transId, amount, hotelCode);

        // 检查幂等性
        String status = SettlementRetryHook.localDb.getOrDefault(transId, "INIT");
        if ("SUCCESS".equals(status)) {
            log.info("[SettlementService] Settlement {} already completed (idempotent). Skipping.", transId);
            return transId;
        }

        try {
            // 模拟首次调用超时
            if (!failedOnce.containsKey(transId)) {
                failedOnce.put(transId, true);
                log.warn("[SettlementService] OTA gateway timeout for transId={}", transId);
                throw new RuntimeException("OTA gateway connection timeout");
            }

            // 成功：发送结算请求，进入 OTA 审批流程（异步审批，状态变为 WAIT）
            log.info("[SettlementService] Settlement request sent to OTA for transId={}, status -> WAIT", transId);
            SettlementRetryHook.localDb.put(transId, "WAIT");
            return transId;

        } catch (RuntimeException e) {
            log.error("[SettlementService] Settlement failed for {}: {}. Submitting to retry platform...",
                    transId, e.getMessage());

            // ===== API 模式核心：手动构建并提交重试任务 =====
            RetryTaskRequest request = new RetryTaskRequest();
            request.setSceneType(2);                                               // 场景2：酒店结算
            request.setIdempotentKey(transId);                                     // 幂等键：统一交易流水号
            request.setMethodClass(this.getClass().getName());                     // 类名（平台回调时用）
            request.setMethodName("settle");                                       // 方法名
            request.setSubmitMode("POST_FAIL");                                    // 失败后提交

            // 将方法参数序列化为 JSON，平台回调时传回（需确保参数可序列化）
            Map<String, Object> params = new HashMap<>();
            params.put("transId", transId);
            params.put("amount", amount);
            params.put("hotelCode", hotelCode);
            request.setMethodParams(new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(params).toString());

            String taskId = retryClient.submit(request);
            log.info("[SettlementService] Retry task submitted. taskId={}", taskId);

            return null; // 首次失败，返回null，等待平台重试
        }
    }
}
