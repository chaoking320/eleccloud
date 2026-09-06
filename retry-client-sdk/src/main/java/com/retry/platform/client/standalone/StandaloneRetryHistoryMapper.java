package com.retry.platform.client.standalone;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * Standalone 模式下本地重试历史表操作
 */
@Mapper
public interface StandaloneRetryHistoryMapper {

    /**
     * 插入执行历史记录
     *
     * @param taskId        任务ID
     * @param retryCount    本次是第几次重试
     * @param executeResult 执行结果：SUCCESS / FAILED / PENDING
     * @param errorMessage  失败时的错误信息，成功时传 null
     * @param costTimeMs    本次执行耗时（毫秒）
     */
    int insert(@Param("taskId") String taskId,
               @Param("retryCount") int retryCount,
               @Param("executeResult") String executeResult,
               @Param("errorMessage") String errorMessage,
               @Param("costTimeMs") long costTimeMs);

    /**
     * 清理历史记录（按 execute_time 清理旧数据）
     */
    int deleteByExecuteTimeBefore(@Param("beforeDate") LocalDateTime beforeDate);
}
