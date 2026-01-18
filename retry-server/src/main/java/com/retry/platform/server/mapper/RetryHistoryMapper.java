package com.retry.platform.server.mapper;

import com.retry.platform.server.entity.RetryHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 重试历史Mapper接口
 */
@Mapper
public interface RetryHistoryMapper {
    
    /**
     * 插入重试历史
     * @param retryHistory 重试历史
     * @return 影响行数
     */
    int insert(RetryHistory retryHistory);
    
    /**
     * 根据任务ID查询历史记录
     * @param taskId 任务ID
     * @return 重试历史列表
     */
    List<RetryHistory> selectByTaskId(@Param("taskId") String taskId);
    
    /**
     * 根据任务ID查询最新一条历史记录
     * @param taskId 任务ID
     * @return 重试历史
     */
    RetryHistory selectLatestByTaskId(@Param("taskId") String taskId);
    
    /**
     * 根据任务ID删除历史记录
     * @param taskId 任务ID
     * @return 影响行数
     */
    int deleteByTaskId(@Param("taskId") String taskId);
}
