package com.retry.platform.client.hook;

public enum RetryStatus {
    INIT,    // 未完成，继续执行重试逻辑
    WAIT,    // 已发出请求，等待第三方确认
    SUCCESS  // 任务已完成，跳过本次重试
}
