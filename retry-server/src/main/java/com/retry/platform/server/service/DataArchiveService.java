package com.retry.platform.server.service;

import java.time.LocalDateTime;

/**
 * 数据归档服务接口
 */
public interface DataArchiveService {

    /**
     * 归档历史数据
     * 将指定时间之前的数据归档或删除
     *
     * @param beforeDate 归档截止时间
     * @return 归档的记录数
     */
    int archiveHistoryData(LocalDateTime beforeDate);

    /**
     * 清理成功任务
     * 删除已成功完成且超过保留期限的任务
     *
     * @param beforeDate 清理截止时间
     * @return 清理的记录数
     */
    int cleanSuccessTasks(LocalDateTime beforeDate);

    /**
     * 导出失败任务
     * 将失败任务导出为CSV格式
     *
     * @param sceneType 场景类型（可选）
     * @param startTime 开始时间（可选）
     * @param endTime 结束时间（可选）
     * @return CSV文件路径
     */
    String exportFailedTasks(Integer sceneType, LocalDateTime startTime, LocalDateTime endTime);
}
