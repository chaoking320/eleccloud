package com.retry.platform.example.service;

import com.retry.platform.client.annotation.RetryableTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 双层混合重试业务示例：
 * 采用 localRetryTimes = 2, localIntervalMs = 200 进行本地进程内快速重试消解瞬时抖动；
 * 若本地 2 次均失败，则平滑升级至服务端分布式调度。
 */
@Slf4j
@Service
public class TwoTierBusinessService {

    private final AtomicInteger attemptCounter = new AtomicInteger(0);

    /**
     * 发送短信通知（双层重试模式）
     *
     * @param msgId 消息唯一流水号
     * @param phone 目标手机号
     * @param content 消息内容
     * @param targetFailTimes 预设失败次数
     * @return 执行结果
     */
    @RetryableTask(
            sceneType = 10,
            idempotentKey = "#msgId",
            localRetryTimes = 2,
            localIntervalMs = 200,
            throwException = true
    )
    public String sendSms(String msgId, String phone, String content, int targetFailTimes) {
        int attempt = attemptCounter.incrementAndGet();
        log.info("[TwoTier] 尝试发送短信, msgId={}, attempt={}, targetFailTimes={}", msgId, attempt, targetFailTimes);

        if (attempt <= targetFailTimes) {
            throw new RuntimeException("下游短信网关瞬时超时(第" + attempt + "次尝试失败)");
        }

        log.info("[TwoTier] 短信发送成功, msgId={}, 在第{}次尝试成功", msgId, attempt);
        return "SMS_SENT_OK";
    }

    public int getAttempts() {
        return attemptCounter.get();
    }

    public void resetCounter() {
        attemptCounter.set(0);
    }
}
