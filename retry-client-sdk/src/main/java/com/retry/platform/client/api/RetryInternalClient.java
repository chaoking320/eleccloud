package com.retry.platform.client.api;

/**
 * 内部驱动使用的重试客户端接口
 */
public interface RetryInternalClient {

    /**
     * 尝试将状态修改为 EXECUTING (用于 CAS 抢占互斥)
     *
     * @param taskId 任务ID
     * @return 是否成功抢占
     */
    boolean markExecuting(String taskId);

    /**
     * 更新任务状态
     *
     * @param taskId 任务ID
     * @param status 状态
     */
    void updateStatus(String taskId, String status);

    /**
     * 更新重试次数及任务状态
     *
     * @param taskId 任务ID
     * @param retryCount 重试次数
     * @param status 状态
     */
    void updateRetryCountAndStatus(String taskId, int retryCount, String status);

    /**
     * 发生异常时回滚状态到 INIT/WAIT (待重试)
     *
     * @param taskId 任务ID
     * @param errorMsg 错误信息
     */
    void rollbackToPending(String taskId, String errorMsg);

    /**
     * 标记任务为彻底失败
     *
     * @param taskId 任务ID
     * @param reason 失败原因
     */
    void markFailed(String taskId, String reason);

    /**
     * 记录一次重试执行历史
     *
     * @param taskId        任务ID
     * @param retryCount    本次是第几次重试
     * @param executeResult 执行结果：SUCCESS / FAILED
     * @param errorMessage  失败时的错误信息，成功时传 null
     * @param costTimeMs    本次执行耗时（毫秒）
     */
    void recordHistory(String taskId, int retryCount, String executeResult, String errorMessage, long costTimeMs);
}
