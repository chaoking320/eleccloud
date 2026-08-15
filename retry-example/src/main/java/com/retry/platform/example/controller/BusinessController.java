package com.retry.platform.example.controller;

import com.retry.platform.example.service.InventoryBusinessService;
import com.retry.platform.example.service.RefundBusinessService;
import com.retry.platform.example.service.SettlementBusinessService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Demo 业务控制器
 *
 * <p>这里是"业务方"的角色——它调用真实的下游服务（/mock-api/**），
 * 并通过 ElecCloud SDK 实现自动重试。
 *
 * <p>架构示意：
 * <pre>
 * 浏览器 → POST /business/trigger/refund
 *           → RefundBusinessService.refund()   @RetryableTask
 *             → HTTP POST /mock-api/payment/refund   (模拟支付宝，前2次失败)
 *               → 失败 → AOP 提交任务到 ElecCloud
 *                 → ElecCloud 5秒后重试
 *                   → /mock-api/payment/refund 第3次成功
 *                     → 任务状态 → SUCCESS
 *                       → Admin 后台可见
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/business")
@CrossOrigin
public class BusinessController {

    @Autowired
    private RefundBusinessService refundBusinessService;

    @Autowired
    private SettlementBusinessService settlementBusinessService;

    @Autowired
    private InventoryBusinessService inventoryBusinessService;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    // SSE 连接池
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    // 任务记录（供前端轮询）
    public static final Map<String, Map<String, Object>> TASK_RECORDS = new ConcurrentHashMap<>();

    // ================================================================
    // SSE 实时日志推送
    // ================================================================

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe() {
        String id = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(10 * 60 * 1000L);
        emitters.put(id, emitter);
        emitter.onCompletion(() -> emitters.remove(id));
        emitter.onTimeout(() -> emitters.remove(id));
        emitter.onError(e -> emitters.remove(id));
        pushEvent("connect", "✅ 实时日志已连接，触发场景后可在此观察重试过程...");
        return emitter;
    }

    // ================================================================
    // 场景触发接口
    // ================================================================

    /**
     * 触发退款重试演示（注解模式）
     */
    @PostMapping("/trigger/refund")
    public Map<String, Object> triggerRefund() {
        String transId = "RFD_" + shortUUID();
        String orderId = "ORD_" + shortUUID();

        pushLog("🚀 [场景1·注解模式] 开始退款流程");
        pushLog("   transId = " + transId);
        pushLog("   业务层调用：refundBusinessService.refund()");
        pushLog("   注解：@RetryableTask(sceneType=10, idempotentKey=\"#transId\")");
        pushLog("   下游：HTTP POST /mock-api/payment/refund（模拟支付宝）");

        Map<String, Object> rec = rec("退款-注解模式", transId);
        TASK_RECORDS.put(transId, rec);

        try {
            refundBusinessService.refund(transId, orderId, 100.0);
            rec.put("status", "SUCCESS");
            pushLog("✅ 首次调用即成功（幂等：已完成）");
        } catch (Exception e) {
            rec.put("status", "PENDING");
            pushLog("❌ 支付宝接口返回失败：" + e.getMessage());
            pushLog("🎯 AOP 捕获异常，自动提交重试任务到 ElecCloud → 状态 INIT");
            pushLog("⏳ ElecCloud 将在约 5 秒后触发重试，请观察 Admin 后台...");
        }

        return resp(transId, rec);
    }

    /**
     * 触发结算重试演示（API 模式）
     */
    @PostMapping("/trigger/settlement")
    public Map<String, Object> triggerSettlement() {
        String transId = "STL_" + shortUUID();

        pushLog("🚀 [场景2·API模式] 开始结算流程");
        pushLog("   transId = " + transId);
        pushLog("   业务层调用：settlementBusinessService.settle()");
        pushLog("   接入方式：catch 块中手动调用 retryClient.submit()");
        pushLog("   下游：HTTP POST /mock-api/hotel/settlement（模拟OTA平台）");

        Map<String, Object> rec = rec("结算-API模式", transId);
        TASK_RECORDS.put(transId, rec);

        boolean success = settlementBusinessService.settle(transId, 5000.0, "HT_MARRIOTT");
        if (success) {
            rec.put("status", "SUCCESS");
            pushLog("✅ OTA接口首次调用成功");
        } else {
            rec.put("status", "PENDING");
            pushLog("❌ OTA网关繁忙，在 catch 块中手动调用 retryClient.submit()");
            pushLog("📤 重试任务已提交到 ElecCloud → 状态 INIT");
            pushLog("⏳ ElecCloud 将在约 5 秒后触发重试，请观察 Admin 后台...");
        }

        return resp(transId, rec);
    }

    /**
     * 触发库存同步重试演示（预提交模式）
     */
    @PostMapping("/trigger/inventory")
    public Map<String, Object> triggerInventory() {
        String transId = "INV_" + shortUUID();

        pushLog("🚀 [场景3·预提交模式] 开始库存同步流程");
        pushLog("   transId = " + transId);
        pushLog("   业务层调用：inventoryBusinessService.syncInventory()");
        pushLog("   注解：@RetryableTask(sceneType=12, preSubmit=true)");
        pushLog("   🔒 PRE_SUBMIT：方法执行前先注册任务，进程崩溃也不丢失！");
        pushLog("   下游：HTTP POST /mock-api/warehouse/inventory/sync（模拟WMS）");

        Map<String, Object> rec = rec("库存同步-预提交", transId);
        TASK_RECORDS.put(transId, rec);

        try {
            inventoryBusinessService.syncInventory(transId, "SKU_12345", 10);
            rec.put("status", "SUCCESS");
            pushLog("✅ WMS接口首次调用成功，AOP 自动 markSuccess()");
        } catch (Exception e) {
            rec.put("status", "PENDING");
            pushLog("❌ WMS系统维护，方法执行失败：" + e.getMessage());
            pushLog("🔒 PRE_SUBMIT 优势：任务已在执行前注册，即使此时进程崩溃也不丢失");
            pushLog("⏳ ElecCloud 将在约 5 秒后触发重试，请观察 Admin 后台...");
        }

        return resp(transId, rec);
    }

    // ================================================================
    // 状态查询
    // ================================================================

    @GetMapping("/tasks")
    public Map<String, Object> getTasks() {
        return Map.of("tasks", TASK_RECORDS);
    }

    // ================================================================
    // 工具方法
    // ================================================================

    private void pushLog(String msg) {
        String line = "[" + LocalTime.now().format(FMT) + "] " + msg;
        log.info("[Demo] {}", line);
        pushEvent("log", line);
    }

    private void pushEvent(String type, String data) {
        List<String> dead = new ArrayList<>();
        for (Map.Entry<String, SseEmitter> e : emitters.entrySet()) {
            try {
                e.getValue().send(SseEmitter.event().name(type).data(data));
            } catch (IOException ex) {
                dead.add(e.getKey());
            }
        }
        dead.forEach(emitters::remove);
    }

    private Map<String, Object> rec(String scene, String transId) {
        Map<String, Object> r = new ConcurrentHashMap<>();
        r.put("scene", scene);
        r.put("transId", transId);
        r.put("status", "TRIGGERED");
        r.put("time", LocalTime.now().format(FMT));
        return r;
    }

    private Map<String, Object> resp(String transId, Map<String, Object> rec) {
        Map<String, Object> r = new LinkedHashMap<>(rec);
        r.put("adminUrl", "http://localhost:8081");
        r.put("tip", "在 Admin 后台可查看详细重试历史（http://localhost:8081）");
        return r;
    }

    private String shortUUID() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
