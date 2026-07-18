package com.retry.platform.example.hook;

import com.retry.platform.client.hook.QueryResult;
import com.retry.platform.client.hook.RetryContext;
import com.retry.platform.client.hook.RetryHook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 酒店结算重试钩子 —— 场景2（API 模式 RetryClient.submit()）
 *
 * <p>接入方式：手动调用 {@code RetryClient.submit()} 提交任务，
 * 无需在方法上添加注解。适用于：
 * <ul>
 *   <li>方法签名无法修改（如第三方 SDK 的回调方法）</li>
 *   <li>需要在特定条件下才提交重试（如仅超时异常才重试，业务异常不重试）</li>
 *   <li>已有事务控制，需要在事务内决定是否提交重试任务</li>
 * </ul>
 *
 * <p>业务场景：酒店系统向 OTA 平台发起结算，OTA 审批需要时间（模拟异步审批），
 * 通过查询 API 轮询审批结果。
 */
@Slf4j
@Component("com.retry.platform.example.hook.SettlementRetryHook")
public class SettlementRetryHook implements RetryHook {

    /**
     * 模拟本地数据库存储结算单状态
     * key: transId, value: INIT / WAIT / SUCCESS / REJECTED
     */
    public static final Map<String, String> localDb = new ConcurrentHashMap<>();

    @Override
    public String checkStatus(RetryContext context) {
        String transId = (String) context.getParams().get("transId");
        String status = localDb.getOrDefault(transId, "INIT");
        log.info("[SettlementHook] checkStatus: transId={}, localStatus={}", transId, status);
        return status;
    }

    @Override
    public QueryResult doQuery(RetryContext context) {
        String transId = (String) context.getParams().get("transId");
        log.info("[SettlementHook] doQuery: polling OTA approval system for transId={}, retryCount={}",
                transId, context.getRetryCount());

        // 模拟：OTA 系统审批需要 2 次轮询才完成
        if (context.getRetryCount() >= 2) {
            log.info("[SettlementHook] doQuery: OTA approved settlement for transId={}", transId);
            return QueryResult.success("OTA settlement approved, amount transferred");
        }

        log.warn("[SettlementHook] doQuery: settlement under review for transId={}", transId);
        return QueryResult.failure("Settlement pending approval");
    }

    @Override
    public void doCallback(RetryContext context, QueryResult result) {
        String transId = (String) context.getParams().get("transId");
        log.info("[SettlementHook] doCallback: settlement SUCCESS. transId={}", transId);
        localDb.put(transId, "SUCCESS");
    }
}
