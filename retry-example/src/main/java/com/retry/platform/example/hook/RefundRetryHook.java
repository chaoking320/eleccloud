package com.retry.platform.example.hook;

import com.retry.platform.client.hook.QueryResult;
import com.retry.platform.client.hook.RetryContext;
import com.retry.platform.client.hook.RetryHook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 业务方自定义退款重试钩子实现
 */
@Slf4j
@Component("com.retry.platform.example.RefundRetryHook")
public class RefundRetryHook implements RetryHook {

    // 模拟本地数据库中的退款订单状态: orderId -> status (INIT, WAIT, SUCCESS)
    public static final Map<String, String> localDb = new ConcurrentHashMap<>();

    @Override
    public String checkStatus(RetryContext context) {
        String orderId = (String) context.getParams().get("orderId");
        String currentStatus = localDb.getOrDefault(orderId, "INIT");
        log.info("[RefundRetryHook] Checking local status for orderId={}, status={}", orderId, currentStatus);
        return currentStatus;
    }

    @Override
    public QueryResult doQuery(RetryContext context) {
        String orderId = (String) context.getParams().get("orderId");
        log.info("[RefundRetryHook] Querying remote payment center ( WeChat/Alipay ) for orderId={}, retryCount={}", 
                orderId, context.getRetryCount());

        // 模拟拉模式：在多次重试后，第三方接口终于返回退款成功
        if (context.getRetryCount() >= 1) {
            log.info("[RefundRetryHook] Third-party payment center confirmed refund SUCCESS for orderId={}", orderId);
            return QueryResult.success("Refund processed successfully via WeChat query API");
        }

        log.warn("[RefundRetryHook] Third-party payment center returns: refund still in progress for orderId={}", orderId);
        return QueryResult.failure("Refund in progress");
    }

    @Override
    public void doCallback(RetryContext context, QueryResult result) {
        String orderId = (String) context.getParams().get("orderId");
        log.info("[RefundRetryHook] Executing success callback, updating local DB to SUCCESS: orderId={}, result={}", 
                orderId, result.getData());
        
        // 更新本地库状态为 SUCCESS
        localDb.put(orderId, "SUCCESS");
    }
}
