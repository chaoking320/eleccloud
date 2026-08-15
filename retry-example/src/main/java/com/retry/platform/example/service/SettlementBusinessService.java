package com.retry.platform.example.service;

import com.retry.platform.client.api.RetryClient;
import com.retry.platform.client.dto.RetryTaskRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 酒店结算业务服务 —— 接入方式：API 模式（手动调用 RetryClient.submit()）
 *
 * <p><b>Demo 演示链路：</b>
 * <pre>
 * 用户触发
 *   → BusinessController.triggerSettlement()
 *   → SettlementBusinessService.settle()  ← 无注解，手动控制
 *   → HTTP POST /mock-api/hotel/settlement  ← 调用模拟OTA接口
 *   → OTA接口前2次返回"网关繁忙"（异常）
 *   → catch 块里手动调用 retryClient.submit() 提交重试任务
 *   → ElecCloud 平台 5 秒后自动重试
 *   → OTA接口第3次成功返回
 *   → Admin 后台可见完整重试历史
 * </pre>
 *
 * <p><b>适用场景（相比注解模式的优势）：</b>
 * <ul>
 *   <li>需要根据异常类型决定是否重试（只重试超时，不重试业务拒绝）</li>
 *   <li>需要在提交重试前做额外的清理操作或状态记录</li>
 *   <li>方法签名不能修改（接口实现）</li>
 * </ul>
 */
@Slf4j
@Service
public class SettlementBusinessService {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private RetryClient retryClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.self-url:http://localhost:8082}")
    private String selfUrl;

    /**
     * 向OTA平台提交酒店结算请求
     *
     * <p>API 模式：开发者完全掌控何时、是否提交重试任务
     *
     * @param transId   结算流水号（幂等键）
     * @param amount    结算金额
     * @param hotelCode 酒店编码
     * @return true=已成功提交，false=失败已提交重试任务
     */
    public boolean settle(String transId, Double amount, String hotelCode) {
        log.info("[SettlementBusiness] 调用OTA结算接口: transId={}, amount={}, hotel={}", transId, amount, hotelCode);

        Map<String, Object> requestBody = Map.of(
            "transId", transId,
            "amount", amount,
            "hotelCode", hotelCode,
            "currency", "CNY"
        );

        try {
            // 真实 HTTP 调用 OTA 接口
            Map response = restTemplate.postForObject(
                selfUrl + "/mock-api/hotel/settlement",
                requestBody,
                Map.class
            );
            log.info("[SettlementBusiness] OTA接口返回成功: transId={}, response={}", transId, response);
            return true;

        } catch (Exception e) {
            log.error("[SettlementBusiness] OTA接口调用失败: transId={}, error={}", transId, e.getMessage());
            log.info("[SettlementBusiness] 手动提交重试任务到 ElecCloud 平台...");

            // ===== API 模式核心：手动构建并提交重试任务 =====
            try {
                RetryTaskRequest request = new RetryTaskRequest();
                request.setSceneType(11);                          // Demo 快速场景11
                request.setIdempotentKey(transId);                 // 幂等键
                request.setMethodClass(this.getClass().getName()); // 当前类名
                request.setMethodName("settle");                   // 方法名
                request.setSubmitMode("POST_FAIL");
                request.setMethodParamTypes("java.lang.String,java.lang.Double,java.lang.String");

                // 序列化方法参数
                Map<String, Object> params = Map.of(
                    "transId", transId,
                    "amount", amount,
                    "hotelCode", hotelCode
                );
                request.setMethodParams(objectMapper.writeValueAsString(params));

                String taskId = retryClient.submit(request);
                log.info("[SettlementBusiness] 重试任务已提交: taskId={}", taskId);
            } catch (Exception submitEx) {
                log.error("[SettlementBusiness] 提交重试任务失败!", submitEx);
            }
            return false;
        }
    }
}
