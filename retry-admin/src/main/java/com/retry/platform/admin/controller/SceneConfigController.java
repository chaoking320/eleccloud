package com.retry.platform.admin.controller;

import com.retry.platform.server.entity.SceneConfig;
import com.retry.platform.server.service.SceneConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 场景配置管理控制器
 */
@RestController
@RequestMapping("/api/scene")
@CrossOrigin
public class SceneConfigController {

    @Autowired
    private SceneConfigService sceneConfigService;

    /**
     * 获取场景配置列表
     */
    @GetMapping("/list")
    public Result<List<SceneConfig>> getSceneList() {
        try {
            List<SceneConfig> sceneList = sceneConfigService.getAllSceneConfigs();
            return Result.success(sceneList);
        } catch (Exception e) {
            return Result.error("获取场景列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取场景配置详情
     */
    @GetMapping("/{id}")
    public Result<SceneConfig> getScene(@PathVariable Long id) {
        try {
            SceneConfig scene = sceneConfigService.getSceneConfigById(id);
            if (scene == null) {
                return Result.error("场景配置不存在");
            }
            return Result.success(scene);
        } catch (Exception e) {
            return Result.error("获取场景详情失败: " + e.getMessage());
        }
    }

    /**
     * 创建场景配置
     */
    @PostMapping
    public Result<Long> createScene(@RequestBody SceneConfig sceneConfig) {
        try {
            // 验证场景类型是否已存在
            SceneConfig existingScene = sceneConfigService.getSceneConfigByType(sceneConfig.getSceneType());
            if (existingScene != null) {
                return Result.error("场景类型 " + sceneConfig.getSceneType() + " 已存在");
            }

            // 验证重试间隔配置
            if (!sceneConfigService.validateRetryIntervals(sceneConfig.getRetryIntervals())) {
                return Result.error("重试间隔配置格式错误，应为逗号分隔的数字");
            }

            // 设置最大重试次数
            sceneConfig.setMaxRetryCount(parseRetryIntervals(sceneConfig.getRetryIntervals()).length);

            Long id = sceneConfigService.createSceneConfig(sceneConfig);
            return Result.success(id);
        } catch (Exception e) {
            return Result.error("创建场景配置失败: " + e.getMessage());
        }
    }

    /**
     * 更新场景配置
     */
    @PutMapping("/{id}")
    public Result<Void> updateScene(@PathVariable Long id, @RequestBody SceneConfig sceneConfig) {
        try {
            SceneConfig existingScene = sceneConfigService.getSceneConfigById(id);
            if (existingScene == null) {
                return Result.error("场景配置不存在");
            }

            // 验证重试间隔配置
            if (!sceneConfigService.validateRetryIntervals(sceneConfig.getRetryIntervals())) {
                return Result.error("重试间隔配置格式错误，应为逗号分隔的数字");
            }

            // 设置ID和最大重试次数
            sceneConfig.setId(id);
            sceneConfig.setMaxRetryCount(parseRetryIntervals(sceneConfig.getRetryIntervals()).length);

            boolean success = sceneConfigService.updateSceneConfig(sceneConfig);
            if (success) {
                return Result.success();
            } else {
                return Result.error("更新场景配置失败");
            }
        } catch (Exception e) {
            return Result.error("更新场景配置失败: " + e.getMessage());
        }
    }

    /**
     * 删除场景配置
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteScene(@PathVariable Long id) {
        try {
            SceneConfig existingScene = sceneConfigService.getSceneConfigById(id);
            if (existingScene == null) {
                return Result.error("场景配置不存在");
            }

            boolean success = sceneConfigService.deleteSceneConfig(id);
            if (success) {
                return Result.success();
            } else {
                return Result.error("删除场景配置失败");
            }
        } catch (Exception e) {
            return Result.error("删除场景配置失败: " + e.getMessage());
        }
    }

    /**
     * 解析重试间隔配置
     */
    private int[] parseRetryIntervals(String retryIntervals) {
        String[] intervals = retryIntervals.split(",");
        int[] result = new int[intervals.length];
        for (int i = 0; i < intervals.length; i++) {
            result[i] = Integer.parseInt(intervals[i].trim());
        }
        return result;
    }
}