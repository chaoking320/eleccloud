package com.retry.platform.client.api;

import com.retry.platform.client.dto.RetryTaskDTO;
import com.retry.platform.client.dto.RetryTaskRequest;

/**
 * 重试客户端接口
 */
public interface RetryClient {
    
    /**
     * 提交重试任务
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
}
