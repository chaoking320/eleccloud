package com.retry.platform.server.mapper;

import com.retry.platform.server.entity.RetryHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 重试历史Mapper接口
 */
@Mapper
public interface RetryHistoryMapper {

    /**
     * 插入重试历史记录
     */
    int insert(RetryHistory history);

    /**
     * 根据任务ID查询历史记录
     */
    List<RetryHistory> selectByTaskId(@Param("taskId") String taskId);

    /**
     * 统计指定时间之后的执行次数
     */
    int countExecutionsSince(@Param("startTime") LocalDateTime startTime);

    /**
     * 统计指定时间之后的失败执行次数
     */
    int countFailedExecutionsSince(@Param("startTime") LocalDateTime startTime);

    /**
     * 删除指定时间之前的历史记录
     * @param beforeDate 截止时间
     * @return 删除的记录数
     */
    int deleteByExecuteTimeBefore(@Param("beforeDate") LocalDateTime beforeDate);
}
