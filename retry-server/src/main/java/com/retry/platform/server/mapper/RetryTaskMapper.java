package com.retry.platform.server.mapper;

import com.retry.platform.server.entity.RetryTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 重试任务Mapper接口
 */
@Mapper
public interface RetryTaskMapper {
    
    /**
     * 插入重试任务
     * @param retryTask 重试任务
     * @return 影响行数
     */
    int insert(RetryTask retryTask);
    
    /**
     * 根据任务ID查询
     * @param taskId 任务ID
     * @return 重试任务
     */
    RetryTask selectByTaskId(@Param("taskId") String taskId);
    
    /**
     * 根据场景类型和幂等键查询
     * @param sceneType 场景类型
     * @param idempotentKey 幂等键
     * @return 重试任务
     */
    RetryTask selectBySceneAndIdempotentKey(@Param("sceneType") Integer sceneType, 
                                            @Param("idempotentKey") String idempotentKey);
    
    /**
     * 更新任务状态
     * @param taskId 任务ID
     * @param taskStatus 任务状态
     * @return 影响行数
     */
    int updateStatus(@Param("taskId") String taskId, @Param("taskStatus") String taskStatus);
    
    /**
     * 更新重试信息
     * @param taskId 任务ID
     * @param retryCount 重试次数
     * @param nextRetryTime 下次重试时间
     * @return 影响行数
     */
    int updateRetryInfo(@Param("taskId") String taskId, 
                       @Param("retryCount") Integer retryCount,
                       @Param("nextRetryTime") Long nextRetryTime);
    
    /**
     * 更新任务状态和重试信息
     * @param taskId 任务ID
     * @param taskStatus 任务状态
     * @param retryCount 重试次数
     * @param nextRetryTime 下次重试时间
     * @return 影响行数
     */
    int updateStatusAndRetryInfo(@Param("taskId") String taskId,
                                 @Param("taskStatus") String taskStatus,
                                 @Param("retryCount") Integer retryCount,
                                 @Param("nextRetryTime") Long nextRetryTime);
    
    /**
     * 查询待执行任务（数据库兜底）
     * @param currentTime 当前时间戳
     * @param limit 限制数量
     * @return 任务ID列表
     */
    List<String> selectPendingTasks(@Param("currentTime") Long currentTime, 
                                    @Param("limit") Integer limit);
    
    /**
     * 根据主键ID删除
     * @param id 主键ID
     * @return 影响行数
     */
    int deleteById(@Param("id") Long id);
    
    /**
     * 根据任务ID删除
     * @param taskId 任务ID
     * @return 影响行数
     */
    int deleteByTaskId(@Param("taskId") String taskId);
    
    /**
     * 统计任务数量
     * @param sceneType 场景类型（可选）
     * @param taskStatus 任务状态（可选）
     * @return 任务数量
     */
    long countTasks(@Param("sceneType") Integer sceneType, 
                   @Param("taskStatus") String taskStatus);

    /**
     * 查询重试任务列表（支持条件查询与分页）
     */
    List<RetryTask> selectByConditions(@Param("sceneType") Integer sceneType,
                                       @Param("idempotentKey") String idempotentKey,
                                       @Param("taskStatus") String taskStatus,
                                       @Param("offset") Integer offset,
                                       @Param("limit") Integer limit);
    
    /**
     * 统计重试任务数量（支持条件查询）
     */
    long countByConditions(@Param("sceneType") Integer sceneType,
                          @Param("idempotentKey") String idempotentKey,
                          @Param("taskStatus") String taskStatus);
}
