package com.retry.platform.example.service;

import com.retry.platform.client.annotation.RetryableTask;
import com.retry.platform.example.hook.InventoryRetryHook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 库存服务 —— 接入方式：预提交模式（@RetryableTask(preSubmit = true)）
 *
 * <p>预提交模式在方法执行前注册任务，执行成功后标记 SUCCESS。
 * 即使应用在执行中崩溃，平台也会在下次调度时重试该方法。
 *
 * <p>关键特性：方法必须是幂等的！重试时平台会先通过 {@code checkStatus}
 * 检查是否已经成功，若已成功则跳过执行，避免重复扣减。
 */
@Slf4j
@Service
public class InventoryService {

    /** 模拟首次调用失败 */
    private final Map<String, Boolean> failedOnce = new ConcurrentHashMap<>();

    /**
     * 同步库存扣减指令到仓库系统
     *
     * <p>注解说明：
     * <ul>
     *   <li>{@code sceneType = 3}：对应场景3配置（EXPONENTIAL策略，间隔 1/2/4/8/16 分钟）</li>
     *   <li>{@code idempotentKey = "#transId"}：以唯一的交易流水号作为幂等键</li>
     *   <li>{@code preSubmit = true}：<b>执行前先注册任务</b>，即使崩溃也能重试</li>
     * </ul>
     */
    @RetryableTask(sceneType = 3, idempotentKey = "#transId", preSubmit = true, throwException = true)
    public void syncInventory(String transId, String skuId, Integer delta) {
        log.info("[InventoryService] Syncing inventory to warehouse: transId={}, skuId={}, delta={}", transId, skuId, delta);

        // 幂等性检查：若仓库已确认，直接返回
        String status = InventoryRetryHook.localDb.getOrDefault(transId, "INIT");
        if ("SUCCESS".equals(status)) {
            log.info("[InventoryService] Inventory for transId={} already synced (idempotent). Skipping.", transId);
            return;
        }

        // 模拟首次连接仓库系统超时
        if (!failedOnce.containsKey(transId)) {
            failedOnce.put(transId, true);
            log.warn("[InventoryService] Warehouse connection timeout for transId={}. Simulating failure...", transId);
            throw new RuntimeException("Warehouse system connection timeout");
        }

        // 第二次调用成功：记录扣减结果到本地
        log.info("[InventoryService] Warehouse confirmed inventory deduction: transId={}, skuId={}, delta={}", transId, skuId, delta);
        InventoryRetryHook.localDb.put(transId, "SUCCESS");
    }
}
