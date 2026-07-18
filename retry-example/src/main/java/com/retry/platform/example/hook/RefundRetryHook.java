package com.retry.platform.example.hook;

import com.retry.platform.client.hook.QueryResult;
import com.retry.platform.client.hook.RetryContext;
import com.retry.platform.client.hook.RetryHook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 退款重试钩子 —— 场景1（注解模式 @RetryableTask）
 *
 * <p>实现三个核心方法：
 * <ol>
 *   <li>{@link #checkStatus} - 查询本地数据库，判断退款单当前状态</li>
 *   <li>{@link #doQuery} - 主动向第三方支付平台（支付宝/微信）发起状态回查</li>
 *   <li>{@link #doCallback} - 收到第三方成功结果后，更新本地数据库</li>
 * </ol>
 */
@Slf4j
@Component("com.retry.platform.example.hook.RefundRetryHook")
public class RefundRetryHook implements RetryHook {

    /**
     * 模拟本地数据库存储退款单状态
     * key: transId, value: INIT / WAIT / SUCCESS
     */
    public static final Map<String, String> localDb = new ConcurrentHashMap<>();

    /**
     * Step 1: 检查本地状态
     * 平台在执行重试前先调用此方法，若已成功则跳过本次重试
     */
    @Override
    public String checkStatus(RetryContext context) {
        String transId = (String) context.getParams().get("transId");
        String status = localDb.getOrDefault(transId, "INIT");
        log.info("[RefundHook] checkStatus: transId={}, localStatus={}", transId, status);
        return status;
    }

    /**
     * Step 2: 主动向第三方支付平台回查退款结果
     * checkStatus 返回 WAIT 时触发，通过调用支付宝/微信查询接口确认最终状态
     */
    @Override
    public QueryResult doQuery(RetryContext context) {
        String transId = (String) context.getParams().get("transId");
        log.info("[RefundHook] doQuery: calling payment API for transId={}, retryCount={}",
                transId, context.getRetryCount());

        // 模拟：第三方在重试1次后返回成功（实际应调用支付宝/微信查询接口）
        if (context.getRetryCount() >= 1) {
            log.info("[RefundHook] doQuery: payment center confirmed SUCCESS for transId={}", transId);
            return QueryResult.success("Refund confirmed by payment center");
        }

        log.warn("[RefundHook] doQuery: payment still processing for transId={}", transId);
        return QueryResult.failure("Refund still in progress, will retry");
    }

    /**
     * Step 3: 执行成功后回调
     * doQuery 返回成功后调用，更新本地数据库状态
     */
    @Override
    public void doCallback(RetryContext context, QueryResult result) {
        String transId = (String) context.getParams().get("transId");
        log.info("[RefundHook] doCallback: updating local DB to SUCCESS. transId={}, result={}",
                transId, result.getData());
        localDb.put(transId, "SUCCESS");
    }
}
