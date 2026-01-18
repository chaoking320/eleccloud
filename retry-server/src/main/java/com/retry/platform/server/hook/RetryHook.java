package com.retry.platform.server.hook;

/**
 * 重试钩子接口
 * 业务方需要实现此接口来定义重试逻辑
 */
public interface RetryHook {
    
    /**
     * 检查任务当前状态
     * 在重试执行前调用，用于判断任务是否已经完成
     * 
     * @param context 重试上下文
     * @return 任务状态: INIT-初始状态, WAIT-等待中, SUCCESS-已成功
     */
    String checkStatus(RetryContext context);
    
    /**
     * 主动查询远程服务状态
     * 当checkStatus返回WAIT时调用，用于主动查询远程服务的处理结果
     * 
     * @param context 重试上下文
     * @return 查询结果
     */
    QueryResult doQuery(RetryContext context);
    
    /**
     * 执行回调逻辑
     * 当doQuery返回成功结果时调用，用于执行业务回调
     * 
     * @param context 重试上下文
     * @param result 查询结果
     */
    void doCallback(RetryContext context, QueryResult result);
}
