package com.retry.platform.server.service;

import com.retry.platform.server.entity.SceneConfig;

import java.util.List;

/**
 * 场景配置服务接口
 */
public interface SceneConfigService {
    
    /**
     * 创建场景配置
     * @param sceneConfig 场景配置
     * @return 主键ID
     */
    Long createSceneConfig(SceneConfig sceneConfig);
    
    /**
     * 更新场景配置
     * @param sceneConfig 场景配置
     * @return 是否成功
     */
    boolean updateSceneConfig(SceneConfig sceneConfig);
    
    /**
     * 根据主键ID删除场景配置
     * @param id 主键ID
     * @return 是否成功
     */
    boolean deleteSceneConfig(Long id);
    
    /**
     * 根据主键ID查询场景配置
     * @param id 主键ID
     * @return 场景配置
     */
    SceneConfig getSceneConfigById(Long id);
    
    /**
     * 根据场景类型查询场景配置（带缓存）
     * @param sceneType 场景类型
     * @return 场景配置
     */
    SceneConfig getSceneConfigByType(Integer sceneType);
    
    /**
     * 查询所有场景配置
     * @return 场景配置列表
     */
    List<SceneConfig> getAllSceneConfigs();
    
    /**
     * 查询所有启用的场景配置
     * @return 场景配置列表
     */
    List<SceneConfig> getAllEnabledSceneConfigs();
    
    /**
     * 更新场景启用状态
     * @param id 主键ID
     * @param enabled 是否启用
     * @return 是否成功
     */
    boolean updateSceneEnabled(Long id, boolean enabled);
    
    /**
     * 验证重试间隔配置
     * @param retryIntervals 重试间隔字符串（逗号分隔）
     * @return 是否有效
     */
    boolean validateRetryIntervals(String retryIntervals);
    
    /**
     * 刷新缓存
     */
    void refreshCache();
    
    /**
     * 清除指定场景类型的缓存
     * @param sceneType 场景类型
     */
    void evictCache(Integer sceneType);
}
