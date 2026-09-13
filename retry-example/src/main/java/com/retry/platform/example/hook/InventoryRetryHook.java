package com.retry.platform.example.hook;

import com.retry.platform.client.hook.QueryResult;
import com.retry.platform.client.hook.RetryContext;
import com.retry.platform.client.hook.RetryHook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 库存同步重试钩子 —— 场景3（预提交模式 @RetryableTask(preSubmit=true)）
 *
 * <p>接入方式：注解 {@code preSubmit=true}，方法执行前先注册任务，成功后自动标记 SUCCESS。
 * 适用于：
 * <ul>
 *   <li>资金/库存等对数据一致性要求极高的操作</li>
 *   <li>操作幂等，可以安全重复执行</li>
 *   <li>即使应用宕机，也必须保证最终执行</li>
 * </ul>
 *
 * <p>业务场景：电商平台向仓库系统发送库存扣减指令。若下单扣减成功，立即标记 SUCCESS；
 * 若扣减指令发送途中崩溃（如网络抖动），平台通过 EXPONENTIAL 策略重试，
 * 下次重试时通过 {@link #checkStatus} 先确认本地状态，避免重复扣减。
 */
@Slf4j
@Component("com.retry.platform.example.hook.InventoryRetryHook")
public class InventoryRetryHook implements RetryHook {

    /**
     * 模拟本地数据库存储库存扣减状态
     * key: transId, value: INIT / SUCCESS
     */
    public static final Map<String, String> localDb = new ConcurrentHashMap<>();

    /**
     * 检查本地库存扣减是否已完成
     * 预提交模式下，重试前必须先检查本地状态，防止重复扣减
     */
    @Override
    public com.retry.platform.client.hook.RetryStatus checkStatus(RetryContext context) {
        String transId = (String) context.getParams().get("transId");
        String status = localDb.getOrDefault(transId, "INIT");
        log.info("[InventoryHook] checkStatus: transId={}, localStatus={}", transId, status);
        // 若本地已记录 SUCCESS，平台将直接标记任务成功，跳过重试
        try {
            return com.retry.platform.client.hook.RetryStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            return com.retry.platform.client.hook.RetryStatus.INIT;
        }
    }

    /**
     * 库存扣减场景通常同步完成，doQuery 主要用于确认仓储系统的确认回执
     */
    @Override
    public QueryResult doQuery(RetryContext context) {
        String transId = (String) context.getParams().get("transId");
        String skuId = (String) context.getParams().get("skuId");
        Integer delta = (Integer) context.getParams().get("delta");
        log.info("[InventoryHook] doQuery: confirming warehouse deduction for transId={}, skuId={}, delta={}, retryCount={}",
                transId, skuId, delta, context.getRetryCount());

        // 模拟仓库系统同步确认（实际应调用仓库API查询）
        if (context.getRetryCount() >= 0) {
            log.info("[InventoryHook] doQuery: warehouse confirmed deduction for transId={}", transId);
            return QueryResult.success("Inventory deducted by warehouse: transId=" + transId + ", skuId=" + skuId + ", delta=" + delta);
        }
        return QueryResult.failure("Warehouse confirmation pending");
    }

    /**
     * 仓库确认后，更新本地库存记录状态
     */
    @Override
    public void doCallback(RetryContext context, QueryResult result) {
        String transId = (String) context.getParams().get("transId");
        log.info("[InventoryHook] doCallback: inventory sync SUCCESS. transId={}", transId);
        localDb.put(transId, "SUCCESS");
    }
}
