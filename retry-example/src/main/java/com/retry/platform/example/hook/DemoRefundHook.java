package com.retry.platform.example.hook;

import com.retry.platform.client.hook.QueryResult;
import com.retry.platform.client.hook.RetryContext;
import com.retry.platform.client.hook.RetryHook;
import com.retry.platform.example.controller.BusinessController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 退款业务重试钩子（Demo 场景 10）
 *
 * <p>ElecCloud 平台在执行重试的每个阶段都会调用这里的方法：
 * <ol>
 *   <li>{@link #checkStatus} — 执行重试前先检查本地状态（幂等防重）</li>
 *   <li>{@link #doQuery}    — 业务方法执行后查询下游最终结果</li>
 *   <li>{@link #doCallback} — 确认成功后回调，更新本地状态</li>
 * </ol>
 */
@Slf4j
@Component("com.retry.platform.example.hook.DemoRefundHook")
public class DemoRefundHook implements RetryHook {

    @Autowired
    private RestTemplate restTemplate;

    @Value("${app.self-url:http://localhost:8082}")
    private String selfUrl;

    /** 模拟本地业务数据库（key: transId, value: 状态） */
    public static final Map<String, String> localDb = new ConcurrentHashMap<>();

    @Override
    public String checkStatus(RetryContext context) {
        String transId = (String) context.getParams().get("transId");
        String status = localDb.getOrDefault(transId, "INIT");
        log.info("[DemoRefundHook] checkStatus: transId={}, status={}", transId, status);
        return status;
    }

    @Override
    public QueryResult doQuery(RetryContext context) {
        String transId = (String) context.getParams().get("transId");
        log.info("[DemoRefundHook] doQuery: 查询支付宝退款状态, transId={}", transId);

        try {
            // 真实调用 Mock 支付宝查询接口
            Map result = restTemplate.getForObject(
                selfUrl + "/mock-api/payment/refund/status?transId=" + transId,
                Map.class
            );
            String apiStatus = result != null ? String.valueOf(result.get("status")) : "UNKNOWN";
            log.info("[DemoRefundHook] doQuery result: transId={}, apiStatus={}", transId, apiStatus);

            if ("SUCCESS".equals(apiStatus)) {
                localDb.put(transId, "SUCCESS");
                // 通知 Demo 页面
                updateDemoRecord(transId, "SUCCESS");
                return QueryResult.success("支付宝退款已成功到账");
            }
            return QueryResult.failure("支付宝处理中，等待下次查询");
        } catch (Exception e) {
            log.warn("[DemoRefundHook] doQuery failed: {}", e.getMessage());
            return QueryResult.failure("查询失败：" + e.getMessage());
        }
    }

    @Override
    public void doCallback(RetryContext context, QueryResult result) {
        String transId = (String) context.getParams().get("transId");
        log.info("[DemoRefundHook] doCallback: 退款成功，更新本地状态 SUCCESS. transId={}", transId);
        localDb.put(transId, "SUCCESS");
        updateDemoRecord(transId, "SUCCESS");
    }

    private void updateDemoRecord(String transId, String status) {
        Map<String, Object> rec = BusinessController.TASK_RECORDS.get(transId);
        if (rec != null) rec.put("status", status);
    }
}
