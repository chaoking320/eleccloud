package com.retry.platform.example.controller;

import com.retry.platform.example.hook.InventoryRetryHook;
import com.retry.platform.example.hook.RefundRetryHook;
import com.retry.platform.example.hook.SettlementRetryHook;
import com.retry.platform.example.service.InventoryService;
import com.retry.platform.example.service.RefundService;
import com.retry.platform.example.service.SettlementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/**
 * Demo 交互页面 Controller
 *
 * <p>为 /demo.html 提供后端支持：
 * <ul>
 *   <li>SSE 实时日志推送 —— 用户触发场景后，日志实时流到浏览器</li>
 *   <li>场景触发接口 —— 使用 Demo 专用快速场景（scene_type 10/11/12）</li>
 *   <li>状态查询接口 —— 前端轮询获取任务当前状态</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/demo-api")
@CrossOrigin
public class DemoPageController {

    @Autowired
    private RefundService refundService;
    @Autowired
    private SettlementService settlementService;
    @Autowired
    private InventoryService inventoryService;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * 所有活跃的 SSE 连接（全局广播）
     * key: emitterId, value: SseEmitter
     */
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * 已触发任务的状态记录（用于状态查询接口）
     * key: transId, value: {scene, status, taskId, startTime}
     */
    private final Map<String, Map<String, Object>> taskRegistry = new ConcurrentHashMap<>();

    // ================================================================
    // SSE 连接管理
    // ================================================================

    /**
     * 建立 SSE 连接。前端 EventSource 连接此接口，接收实时日志。
     */
    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe() {
        String emitterId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L); // 5分钟超时
        emitters.put(emitterId, emitter);

        emitter.onCompletion(() -> emitters.remove(emitterId));
        emitter.onTimeout(() -> emitters.remove(emitterId));
        emitter.onError(e -> emitters.remove(emitterId));

        // 发送连接成功事件
        sendEvent("system", "✅ 已连接到 ElecCloud Demo 实时日志流，等待触发场景...");
        return emitter;
    }

    // ================================================================
    // 场景触发（使用 Demo 快速场景，scene_type 10/11/12）
    // ================================================================

    /**
     * 触发场景1：退款（注解模式 + 快速场景10）
     */
    @PostMapping("/trigger/refund")
    public Map<String, Object> triggerRefund() {
        String transId = "RFD_" + randomSuffix();
        String orderId = "ORD_" + randomSuffix();

        sendLog("🚀", "场景1 [注解模式] 触发退款请求", "transId=" + transId);
        sendLog("📝", "方法签名", "@RetryableTask(sceneType=10, idempotentKey=\"#transId\")");

        Map<String, Object> record = newRecord("退款(注解模式)", transId);
        taskRegistry.put(transId, record);

        try {
            // 使用 Demo 快速场景10触发（会立刻模拟失败，AOP 捕获并提交重试任务）
            refundService.refund(transId, orderId, 100.0, "七天无理由退货");
            record.put("status", "SUCCESS");
            sendLog("✅", "首次调用成功（幂等：可能已完成）", "status=SUCCESS");
        } catch (Exception e) {
            record.put("status", "RETRYING");
            sendLog("❌", "首次调用失败（模拟网络超时）", e.getMessage());
            sendLog("📤", "AOP 自动捕获异常，任务已提交到重试平台", "status→INIT");
            sendLog("⏰", "平台将在 ~5 秒后自动重试...", "请观察下方状态变化");
        }

        return result(transId, record);
    }

    /**
     * 触发场景2：结算（API模式 + 快速场景11）
     */
    @PostMapping("/trigger/settlement")
    public Map<String, Object> triggerSettlement() {
        String transId = "STL_" + randomSuffix();

        sendLog("🚀", "场景2 [API模式] 触发结算请求", "transId=" + transId);
        sendLog("📝", "接入方式", "手动调用 retryClient.submit(request)，无需注解");

        Map<String, Object> record = newRecord("结算(API模式)", transId);
        taskRegistry.put(transId, record);

        String result = settlementService.settle(transId, 5000.0, "HT_MARRIOTT");
        if (result != null) {
            record.put("status", "SUCCESS");
            sendLog("✅", "首次调用成功", "status=SUCCESS");
        } else {
            record.put("status", "RETRYING");
            sendLog("❌", "OTA网关超时（模拟）", "在 catch 块中手动调用 retryClient.submit()");
            sendLog("📤", "重试任务已手动提交到平台", "status→INIT");
            sendLog("⏰", "平台将在 ~5 秒后自动重试...", "请观察下方状态变化");
        }

        return this.result(transId, record);
    }

    /**
     * 触发场景3：库存同步（预提交模式 + 快速场景12）
     */
    @PostMapping("/trigger/inventory")
    public Map<String, Object> triggerInventory() {
        String transId = "INV_" + randomSuffix();

        sendLog("🚀", "场景3 [预提交模式] 触发库存同步", "transId=" + transId);
        sendLog("📝", "方法签名", "@RetryableTask(sceneType=12, idempotentKey=\"#transId\", preSubmit=true)");
        sendLog("🔒", "PRE_SUBMIT 模式：方法执行前先注册 INIT 任务", "即使进程崩溃也能恢复");

        Map<String, Object> record = newRecord("库存同步(预提交)", transId);
        taskRegistry.put(transId, record);

        try {
            inventoryService.syncInventory(transId, "SKU_12345", 10);
            record.put("status", "SUCCESS");
            sendLog("✅", "首次调用成功，已自动标记 SUCCESS", "preSubmit 模式下成功后自动 markSuccess()");
        } catch (Exception e) {
            record.put("status", "RETRYING");
            sendLog("❌", "方法执行失败（模拟）", e.getMessage());
            sendLog("🔒", "PRE_SUBMIT 优势体现", "任务在执行前已注册，进程此时崩溃也不会丢失");
            sendLog("⏰", "平台将在 ~5 秒后自动重试...", "请观察下方状态变化");
        }

        return this.result(transId, record);
    }

    // ================================================================
    // 状态查询
    // ================================================================

    /**
     * 返回所有已触发任务的当前本地状态
     */
    @GetMapping("/tasks")
    public Map<String, Object> getTasks() {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("tasks", taskRegistry);
        resp.put("localStatus", com.retry.platform.example.util.MapUtil.of(
            "refund",     RefundRetryHook.localDb,
            "settlement", SettlementRetryHook.localDb,
            "inventory",  InventoryRetryHook.localDb
        ));
        return resp;
    }

    /**
     * 单个任务状态轮询（前端卡片实时刷新用）
     */
    @GetMapping("/task/{transId}/status")
    public Map<String, Object> getTaskStatus(@PathVariable String transId) {
        // 先从本地Hook状态Map推断最终状态
        String localStatus = null;
        if (RefundRetryHook.localDb.containsKey(transId)) {
            localStatus = RefundRetryHook.localDb.get(transId);
        } else if (SettlementRetryHook.localDb.containsKey(transId)) {
            localStatus = SettlementRetryHook.localDb.get(transId);
        } else if (InventoryRetryHook.localDb.containsKey(transId)) {
            localStatus = InventoryRetryHook.localDb.get(transId);
        }

        Map<String, Object> record = taskRegistry.getOrDefault(transId, com.retry.platform.example.util.MapUtil.of());
        Map<String, Object> resp = new LinkedHashMap<>(record);

        if ("SUCCESS".equals(localStatus)) {
            resp.put("status", "SUCCESS");
            // 状态变成 SUCCESS 时，推送一条 SSE 成功事件
            if (!"SUCCESS".equals(record.get("lastReportedStatus"))) {
                record.put("lastReportedStatus", "SUCCESS");
                sendLog("🎉", "任务最终成功！transId=" + transId, "doCallback() 已更新本地状态 → SUCCESS");
            }
        } else if ("WAIT".equals(localStatus)) {
            resp.put("status", "QUERYING");
        }

        resp.put("localHookStatus", localStatus != null ? localStatus : "INIT");
        return resp;
    }

    // ================================================================
    // 工具方法
    // ================================================================

    private void sendLog(String icon, String action, String detail) {
        String time = LocalTime.now().format(TIME_FMT);
        String msg = String.format("[%s] %s %s — %s", time, icon, action, detail);
        sendEvent("log", msg);
        log.info("[Demo SSE] {}", msg);
    }

    private void sendEvent(String eventType, String data) {
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, SseEmitter> entry : emitters.entrySet()) {
            try {
                entry.getValue().send(SseEmitter.event().name(eventType).data(data));
            } catch (IOException e) {
                toRemove.add(entry.getKey());
            }
        }
        toRemove.forEach(emitters::remove);
    }

    private Map<String, Object> newRecord(String scene, String transId) {
        Map<String, Object> r = new ConcurrentHashMap<>();
        r.put("scene", scene);
        r.put("transId", transId);
        r.put("status", "TRIGGERED");
        r.put("startTime", LocalTime.now().format(TIME_FMT));
        return r;
    }

    private Map<String, Object> result(String transId, Map<String, Object> record) {
        Map<String, Object> resp = new LinkedHashMap<>(record);
        resp.put("adminUrl", "http://localhost:8081");
        return resp;
    }

    private String randomSuffix() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
