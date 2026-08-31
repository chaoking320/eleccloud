package com.retry.platform.server.mapper;

import com.retry.platform.server.entity.FailedTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 失败任务Mapper接口
 */
@Mapper
public interface FailedTaskMapper {
    
    /**
     * 插入失败任务
     * @param failedTask 失败任务
     * @return 影响行数
     */
    int insert(FailedTask failedTask);
    
    /**
     * 根据任务ID查询
     * @param taskId 任务ID
     * @return 失败任务
     */
    FailedTask selectByTaskId(@Param("taskId") String taskId);
    
    /**
     * 查询失败任务列表（支持条件查询）
     * @param sceneType 场景类型（可选）
     * @param idempotentKey 幂等键（可选）
     * @param startTime 开始时间（可选）
     * @param endTime 结束时间（可选）
     * @param offset 偏移量
     * @param limit 限制数量
     * @return 失败任务列表
     */
    List<FailedTask> selectByConditions(@Param("sceneType") Integer sceneType,
                                       @Param("idempotentKey") String idempotentKey,
                                       @Param("startTime") LocalDateTime startTime,
                                       @Param("endTime") LocalDateTime endTime,
                                       @Param("offset") Integer offset,
                                       @Param("limit") Integer limit);
    
    /**
     * 统计失败任务数量
     * @param sceneType 场景类型（可选）
     * @param idempotentKey 幂等键（可选）
     * @param startTime 开始时间（可选）
     * @param endTime 结束时间（可选）
     * @return 失败任务数量
     */
    long countByConditions(@Param("sceneType") Integer sceneType,
                          @Param("idempotentKey") String idempotentKey,
                          @Param("startTime") LocalDateTime startTime,
                          @Param("endTime") LocalDateTime endTime);
    
    /**
     * 根据任务ID删除
     * @param taskId 任务ID
     * @return 影响行数
     */
    int deleteByTaskId(@Param("taskId") String taskId);

    /**
     * 统计指定时间之后的失败任务数量
     * @param startTime 开始时间
     * @return 失败任务数量
     */
    int countFailedTasksSince(@Param("startTime") LocalDateTime startTime);
}
