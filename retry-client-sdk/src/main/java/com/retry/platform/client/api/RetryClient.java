package com.retry.platform.client.api;

import com.retry.platform.client.dto.RetryTaskDTO;
import com.retry.platform.client.dto.RetryTaskRequest;

/**
 * 重试客户端接口
 * 提供向分布式重试平台提交、查询、取消任务以及预提交模式标记成功的能力
 */
public interface RetryClient {

    /**
     * 提交重试任务（POST_FAIL 模式）
     *
     * @param request 重试任务请求
     * @return 任务ID
     */
    String submit(RetryTaskRequest request);

    /**
     * 取消重试任务
     *
     * @param taskId 任务ID
     * @return 是否成功
     */
    boolean cancel(String taskId);

    /**
     * 查询任务状态
     *
     * @param taskId 任务ID
     * @return 任务详情
     */
    RetryTaskDTO queryTask(String taskId);

    /**
     * 标记任务执行成功（PRE_SUBMIT 模式专用）
     * <p>
     * 在预提交模式下，业务方法执行成功后需调用此方法通知平台，
     * 平台将把任务状态从 INIT 更新为 SUCCESS，停止后续重试。
     *
     * @param taskId 任务ID（preSubmit 提交时返回的 ID）
     * @return 是否成功
     */
    boolean markSuccess(String taskId);

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
