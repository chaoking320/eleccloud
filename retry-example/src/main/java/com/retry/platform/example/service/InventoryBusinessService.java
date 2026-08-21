package com.retry.platform.example.service;

import com.retry.platform.client.annotation.RetryableTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 库存同步业务服务 —— 接入方式：预提交模式（preSubmit = true）
 *
 * <p><b>Demo 演示链路：</b>
 * <pre>
 * 用户触发
 *   → BusinessController.triggerInventorySync()
 *   → InventoryBusinessService.syncInventory()  ← @RetryableTask(preSubmit=true)
 *   → AOP 拦截：先向 ElecCloud 注册 INIT 任务（事前保护）
 *   → 执行方法：HTTP POST /mock-api/warehouse/inventory/sync
 *   → WMS接口前2次返回"维护中"（失败）
 *   → AOP 不重复提交（任务已在执行前注册）
 *   → ElecCloud 平台 5 秒后自动重试
 *   → WMS接口第3次成功
 *   → AOP 调用 markSuccess()，任务状态 → SUCCESS
 *   → Admin 后台可见完整重试历史
 * </pre>
 *
 * <p><b>PRE_SUBMIT 模式的核心价值：</b>
 * 任务在方法执行前就已注册（INIT 状态）。即使进程在执行中途崩溃（kill -9、OOM），
 * ElecCloud 也能在进程重启后继续重试，不会因崩溃而丢失任务。
 * 这是普通 POST_FAIL 模式做不到的（崩溃时异常无法被捕获，任务永远不会被提交）。
 */
@Slf4j
@Service
public class InventoryBusinessService {

    @Autowired
    private RestTemplate restTemplate;

    @Value("${app.self-url:http://localhost:8082}")
    private String selfUrl;

    /**
     * 同步库存变更到 WMS 仓储系统
     *
     * <p>注解说明：
     * <ul>
     *   <li>{@code preSubmit = true}：方法执行前先注册任务，进程崩溃也能恢复</li>
     *   <li>{@code sceneType = 12}：Demo 快速场景12（间隔 ~5秒）</li>
     *   <li>{@code throwException = true}：失败时向调用方抛出异常（默认不抛）</li>
     * </ul>
     *
     * @param transId 同步流水号（幂等键）
     * @param skuId   商品SKU
     * @param delta   库存变化量（正=入库，负=出库）
     */
    @RetryableTask(sceneType = 12, idempotentKey = "#transId", preSubmit = true, throwException = true)
    public void syncInventory(String transId, String skuId, Integer delta) {
        log.info("[InventoryBusiness] 调用WMS库存同步接口: transId={}, sku={}, delta={}", transId, skuId, delta);

        Map<String, Object> requestBody = com.retry.platform.example.util.MapUtil.of(
            "transId", transId,
            "skuId", skuId,
            "delta", delta,
            "warehouseId", "WH_BEIJING_01"
        );

        // 真实 HTTP 调用 WMS 接口
        // preSubmit 模式下：即使此处崩溃，任务已预先注册，进程重启后仍能重试
        Map response = restTemplate.postForObject(
            selfUrl + "/mock-api/warehouse/inventory/sync",
            requestBody,
            Map.class
        );

        log.info("[InventoryBusiness] WMS接口返回成功: transId={}, response={}", transId, response);
        // 成功后 AOP 自动调用 retryClient.markSuccess(taskId)，任务状态 → SUCCESS
    }
}
