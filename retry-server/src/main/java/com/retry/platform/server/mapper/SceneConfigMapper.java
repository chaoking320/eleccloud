package com.retry.platform.server.mapper;

import com.retry.platform.server.entity.SceneConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 场景配置Mapper接口
 */
@Mapper
public interface SceneConfigMapper {
    
    /**
     * 插入场景配置
     * @param sceneConfig 场景配置
     * @return 影响行数
     */
    int insert(SceneConfig sceneConfig);
    
    /**
     * 根据主键ID更新
     * @param sceneConfig 场景配置
     * @return 影响行数
     */
    int updateById(SceneConfig sceneConfig);
    
    /**
     * 根据主键ID查询
     * @param id 主键ID
     * @return 场景配置
     */
    SceneConfig selectById(@Param("id") Long id);
    
    /**
     * 根据场景类型查询
     * @param sceneType 场景类型
     * @return 场景配置
     */
    SceneConfig selectBySceneType(@Param("sceneType") Integer sceneType);
    
    /**
     * 查询所有场景配置
     * @return 场景配置列表
     */
    List<SceneConfig> selectAll();
    
    /**
     * 查询所有启用的场景配置
     * @return 场景配置列表
     */
    List<SceneConfig> selectAllEnabled();
    
    /**
     * 根据主键ID删除
     * @param id 主键ID
     * @return 影响行数
     */
    int deleteById(@Param("id") Long id);
    
    /**
     * 更新启用状态
     * @param id 主键ID
     * @param enabled 是否启用
     * @return 影响行数
     */
    int updateEnabled(@Param("id") Long id, @Param("enabled") Integer enabled);
}
