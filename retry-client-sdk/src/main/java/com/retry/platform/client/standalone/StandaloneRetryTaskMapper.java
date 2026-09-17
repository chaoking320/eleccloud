package com.retry.platform.client.standalone;

import com.retry.platform.client.dto.RetryTaskDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Standalone 模式下本地重试任务表操作（表结构与 retry-server 保持一致）
 */
@Mapper
public interface StandaloneRetryTaskMapper {

    /**
     * 插入重试任务
     */
    int insert(RetryTaskDTO task);

    /**
     * 根据任务ID查询
     */
    RetryTaskDTO selectByTaskId(@Param("taskId") String taskId);

    /**
     * 根据场景类型和幂等键查询（用于幂等判断，防止重复提交）
     */
    RetryTaskDTO selectBySceneAndIdempotentKey(@Param("sceneType") Integer sceneType,
                                               @Param("idempotentKey") String idempotentKey);

    /**
     * 更新任务状态
     */
    int updateStatus(@Param("taskId") String taskId, @Param("taskStatus") String taskStatus);

    /**
     * 原子 CAS 操作：仅当 task_status IN ('INIT','WAIT') 时将其更新为 EXECUTING
     * affected rows = 1 表示抢占成功，= 0 表示已被其他线程抢占
     */
    int casUpdateToExecuting(@Param("taskId") String taskId);

    /**
     * 更新重试计数与任务状态（同时更新 next_retry_time）
     */
    int updateStatusAndRetryInfo(@Param("taskId") String taskId,
                                 @Param("taskStatus") String taskStatus,
                                 @Param("retryCount") Integer retryCount,
                                 @Param("nextRetryTime") Long nextRetryTime);

    /**
     * 回滚错误信息（写入 error_msg 字段，状态改回 INIT）
     */
    int rollbackToPending(@Param("taskId") String taskId,
                          @Param("errorMsg") String errorMsg);

    /**
     * 查询待重试任务（数据库兜底扫描）
     *
     * @param currentTime 当前时间戳（毫秒）
     * @param limit       最多返回条数
     */
    List<String> selectPendingTasks(@Param("currentTime") Long currentTime,
                                    @Param("limit") Integer limit);

    /**
     * 将超时卡死在 EXECUTING 状态的任务恢复为 INIT，防止强杀进程后任务永久卡死
     *
     * @param timeoutMillis update_time 在此时间戳（毫秒）之前的 EXECUTING 任务视为卡死
     */
    int recoverStuckExecutingTasks(@Param("timeoutMillis") long timeoutMillis);

    /**
     * Watchdog 心跳刷新：更新 EXECUTING 状态任务的 update_time，
     * 防止长时间执行（>5分钟）被 StandaloneDatabaseFallbackScheduler 误判为卡死并回收。
     *
     * @param taskId 任务ID
     */
    int touchHeartbeat(@Param("taskId") String taskId);
}
