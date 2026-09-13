package com.retry.platform.client.api;

import com.retry.platform.client.dto.RetryTaskDTO;
import com.retry.platform.client.dto.RetryTaskRequest;

/**
 * 重试客户端接口
 * 提供向分布式重试平台提交、查询、取消任务以及预提交模式标记成功的能力
 */
public interface RetryClient extends RetryInternalClient {

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
}
