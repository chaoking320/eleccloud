package com.retry.platform.example.service;

import com.retry.platform.client.annotation.RetryableTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 退款业务服务 —— 接入方式：注解模式（@RetryableTask）
 *
 * <p><b>Demo 演示链路：</b>
 * <pre>
 * 用户触发
 *   → BusinessController.triggerRefund()
 *   → RefundBusinessService.refund()  ← 此方法有 @RetryableTask 注解
 *   → HTTP POST /mock-api/payment/refund  ← 调用模拟支付宝接口
 *   → 支付宝接口前2次返回失败（超时/报错）
 *   → AOP 捕获异常，自动将任务提交到 ElecCloud 平台（状态 INIT）
 *   → ElecCloud 平台 5 秒后自动重试（调用 retry()）
 *   → 支付宝接口第3次返回成功
 *   → Hook.checkStatus 确认成功，任务状态 → SUCCESS
 *   → Admin 后台可见完整重试历史
 * </pre>
 *
 * <p><b>接入要点（3步接入）：</b>
 * <ol>
 *   <li>方法上加 {@code @RetryableTask(sceneType=10, idempotentKey="#transId")}</li>
 *   <li>在 scene_config 表配置场景（或通过 Admin 后台配置）</li>
 *   <li>实现 {@link com.retry.platform.client.hook.RetryHook} 定义幂等检查逻辑</li>
 * </ol>
 */
@Slf4j
@Service
public class RefundBusinessService {

    @Autowired
    private RestTemplate restTemplate;

    @Value("${app.self-url:http://localhost:8082}")
    private String selfUrl;

    /**
     * 向支付宝发起退款申请
     *
     * <p>注解说明：
     * <ul>
     *   <li>{@code sceneType = 10}：对应 Demo 快速场景（间隔 ~5秒，方便演示）</li>
     *   <li>{@code idempotentKey = "#transId"}：以交易流水号作为全局唯一幂等键</li>
     *   <li>方法抛出异常时，AOP 自动将任务注册到 ElecCloud 平台</li>
     * </ul>
     *
     * @param transId 交易流水号（幂等键，全局唯一）
     * @param orderId 订单号
     * @param amount  退款金额
     */
    @RetryableTask(sceneType = 10, idempotentKey = "#transId", throwException = true)
    public void refund(String transId, String orderId, Double amount) {
        log.info("[RefundBusiness] 调用支付宝退款接口: transId={}, orderId={}, amount={}", transId, orderId, amount);

        // 真实 HTTP 调用下游外部服务（这里调用同应用内的 MockAPI，实际场景中换成真实支付宝URL）
        Map<String, Object> requestBody = com.retry.platform.example.util.MapUtil.of(
            "transId", transId,
            "orderId", orderId,
            "amount", amount,
            "currency", "CNY"
        );

        // 若 MockAPI 返回失败（抛出异常），@RetryableTask AOP 会捕获并提交重试任务
        Map response = restTemplate.postForObject(
            selfUrl + "/mock-api/payment/refund",
            requestBody,
            Map.class
        );

        log.info("[RefundBusiness] 支付宝接口返回成功: transId={}, response={}", transId, response);
    }
}
