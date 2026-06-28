package com.retry.platform.server.entity;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 场景配置实体类
 */
@Data
public class SceneConfig {
    
    /**
     * 主键ID
     */
    private Long id;
    
    /**
     * 场景类型
     */
    private Integer sceneType;
    
    /**
     * 场景名称
     */
    private String sceneName;
    
    /**
     * 重试间隔(分钟),逗号分隔
     */
    private String retryIntervals;
    
    /**
     * 最大重试次数
     */
    private Integer maxRetryCount;
    
    /**
     * 钩子类名
     */
    private String hookClass;
    private String clientAppUrl;
    
    /**
     * 是否启用: 0-禁用, 1-启用
     */
    private Integer enabled;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
    
    /**
     * 解析重试间隔列表
     * @return 重试间隔列表(分钟)
     */
    public List<Integer> getRetryIntervalList() {
        if (retryIntervals == null || retryIntervals.trim().isEmpty()) {
            return Arrays.asList();
        }
        return Arrays.stream(retryIntervals.split(","))
                .map(String::trim)
                .map(Integer::parseInt)
                .collect(Collectors.toList());
    }
    
    /**
     * 判断是否启用
     * @return true-启用, false-禁用
     */
    public boolean isEnabled() {
        return enabled != null && enabled == 1;
    }
    
    /**
     * 设置启用状态
     * @param enabled true-启用, false-禁用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled ? 1 : 0;
    }
    
    /**
     * 设置启用状态
     * @param enabled 0-禁用, 1-启用
     */
    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }
}
