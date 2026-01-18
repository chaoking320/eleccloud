package com.retry.platform.server.service.impl;

import com.retry.platform.server.entity.SceneConfig;
import com.retry.platform.server.mapper.SceneConfigMapper;
import com.retry.platform.server.service.SceneConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 场景配置服务实现类
 */
@Slf4j
@Service
public class SceneConfigServiceImpl implements SceneConfigService {
    
    private static final String REDIS_KEY_PREFIX = "retry:scene:config:";
    private static final long REDIS_CACHE_EXPIRE_SECONDS = 3600; // 1小时
    
    @Autowired
    private SceneConfigMapper sceneConfigMapper;
    
    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;
    
    /**
     * 本地缓存：场景类型 -> 场景配置
     */
    private final Map<Integer, SceneConfig> localCache = new ConcurrentHashMap<>();
    
    /**
     * 初始化：加载所有启用的场景配置到本地缓存
     */
    @PostConstruct
    public void init() {
        try {
            refreshCache();
            log.info("SceneConfigService initialized, loaded {} configs", localCache.size());
        } catch (Exception e) {
            log.error("Failed to initialize SceneConfigService", e);
        }
    }
    
    @Override
    public Long createSceneConfig(SceneConfig sceneConfig) {
        // 验证重试间隔配置
        if (!validateRetryIntervals(sceneConfig.getRetryIntervals())) {
            throw new IllegalArgumentException("Invalid retry intervals format");
        }
        
        // 验证最大重试次数与间隔数量一致
        List<Integer> intervals = sceneConfig.getRetryIntervalList();
        if (sceneConfig.getMaxRetryCount() != null && 
            sceneConfig.getMaxRetryCount() != intervals.size()) {
            throw new IllegalArgumentException(
                "Max retry count must match the number of retry intervals");
        }
        
        // 设置创建时间和更新时间
        LocalDateTime now = LocalDateTime.now();
        sceneConfig.setCreateTime(now);
        sceneConfig.setUpdateTime(now);
        
        // 插入数据库
        sceneConfigMapper.insert(sceneConfig);
        
        // 刷新缓存
        if (sceneConfig.isEnabled()) {
            localCache.put(sceneConfig.getSceneType(), sceneConfig);
            saveToRedis(sceneConfig);
        }
        
        log.info("Created scene config: sceneType={}, sceneName={}", 
                sceneConfig.getSceneType(), sceneConfig.getSceneName());
        
        return sceneConfig.getId();
    }
    
    @Override
    public boolean updateSceneConfig(SceneConfig sceneConfig) {
        // 验证重试间隔配置
        if (!validateRetryIntervals(sceneConfig.getRetryIntervals())) {
            throw new IllegalArgumentException("Invalid retry intervals format");
        }
        
        // 验证最大重试次数与间隔数量一致
        List<Integer> intervals = sceneConfig.getRetryIntervalList();
        if (sceneConfig.getMaxRetryCount() != null && 
            sceneConfig.getMaxRetryCount() != intervals.size()) {
            throw new IllegalArgumentException(
                "Max retry count must match the number of retry intervals");
        }
        
        // 设置更新时间
        sceneConfig.setUpdateTime(LocalDateTime.now());
        
        // 更新数据库
        int rows = sceneConfigMapper.updateById(sceneConfig);
        
        if (rows > 0) {
            // 清除缓存
            evictCache(sceneConfig.getSceneType());
            
            // 如果启用，重新加载到缓存
            if (sceneConfig.isEnabled()) {
                localCache.put(sceneConfig.getSceneType(), sceneConfig);
                saveToRedis(sceneConfig);
            }
            
            log.info("Updated scene config: sceneType={}, sceneName={}", 
                    sceneConfig.getSceneType(), sceneConfig.getSceneName());
            return true;
        }
        
        return false;
    }
    
    @Override
    public boolean deleteSceneConfig(Long id) {
        SceneConfig config = sceneConfigMapper.selectById(id);
        if (config == null) {
            return false;
        }
        
        int rows = sceneConfigMapper.deleteById(id);
        
        if (rows > 0) {
            // 清除缓存
            evictCache(config.getSceneType());
            log.info("Deleted scene config: id={}, sceneType={}", id, config.getSceneType());
            return true;
        }
        
        return false;
    }
    
    @Override
    public SceneConfig getSceneConfigById(Long id) {
        return sceneConfigMapper.selectById(id);
    }
    
    @Override
    public SceneConfig getSceneConfigByType(Integer sceneType) {
        // 1. 先从本地缓存获取
        SceneConfig config = localCache.get(sceneType);
        if (config != null) {
            return config;
        }
        
        // 2. 从Redis缓存获取
        config = getFromRedis(sceneType);
        if (config != null) {
            localCache.put(sceneType, config);
            return config;
        }
        
        // 3. 从数据库查询
        config = sceneConfigMapper.selectBySceneType(sceneType);
        if (config != null && config.isEnabled()) {
            localCache.put(sceneType, config);
            saveToRedis(config);
        }
        
        return config;
    }
    
    @Override
    public List<SceneConfig> getAllSceneConfigs() {
        return sceneConfigMapper.selectAll();
    }
    
    @Override
    public List<SceneConfig> getAllEnabledSceneConfigs() {
        return sceneConfigMapper.selectAllEnabled();
    }
    
    @Override
    public boolean updateSceneEnabled(Long id, boolean enabled) {
        SceneConfig config = sceneConfigMapper.selectById(id);
        if (config == null) {
            return false;
        }
        
        int enabledValue = enabled ? 1 : 0;
        int rows = sceneConfigMapper.updateEnabled(id, enabledValue);
        
        if (rows > 0) {
            // 清除缓存
            evictCache(config.getSceneType());
            
            // 如果启用，重新加载到缓存
            if (enabled) {
                config.setEnabled(enabledValue);
                localCache.put(config.getSceneType(), config);
                saveToRedis(config);
            }
            
            log.info("Updated scene enabled status: sceneType={}, enabled={}", 
                    config.getSceneType(), enabled);
            return true;
        }
        
        return false;
    }
    
    @Override
    public boolean validateRetryIntervals(String retryIntervals) {
        if (retryIntervals == null || retryIntervals.trim().isEmpty()) {
            return false;
        }
        
        try {
            String[] parts = retryIntervals.split(",");
            if (parts.length == 0) {
                return false;
            }
            
            for (String part : parts) {
                int interval = Integer.parseInt(part.trim());
                if (interval <= 0) {
                    return false;
                }
            }
            
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    @Override
    public void refreshCache() {
        try {
            List<SceneConfig> configs = sceneConfigMapper.selectAllEnabled();
            
            // 清空本地缓存
            localCache.clear();
            
            // 重新加载
            for (SceneConfig config : configs) {
                localCache.put(config.getSceneType(), config);
                saveToRedis(config);
            }
            
            log.info("Refreshed scene config cache, loaded {} configs", configs.size());
        } catch (Exception e) {
            log.error("Failed to refresh scene config cache", e);
        }
    }
    
    @Override
    public void evictCache(Integer sceneType) {
        // 清除本地缓存
        localCache.remove(sceneType);
        
        // 清除Redis缓存
        deleteFromRedis(sceneType);
        
        log.debug("Evicted cache for sceneType={}", sceneType);
    }
    
    /**
     * 保存到Redis缓存
     */
    private void saveToRedis(SceneConfig config) {
        if (redisTemplate == null) {
            return;
        }
        
        try {
            String key = REDIS_KEY_PREFIX + config.getSceneType();
            redisTemplate.opsForValue().set(key, config, 
                    REDIS_CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Failed to save scene config to Redis: sceneType={}", 
                    config.getSceneType(), e);
        }
    }
    
    /**
     * 从Redis缓存获取
     */
    private SceneConfig getFromRedis(Integer sceneType) {
        if (redisTemplate == null) {
            return null;
        }
        
        try {
            String key = REDIS_KEY_PREFIX + sceneType;
            Object value = redisTemplate.opsForValue().get(key);
            return value != null ? (SceneConfig) value : null;
        } catch (Exception e) {
            log.warn("Failed to get scene config from Redis: sceneType={}", sceneType, e);
            return null;
        }
    }
    
    /**
     * 从Redis缓存删除
     */
    private void deleteFromRedis(Integer sceneType) {
        if (redisTemplate == null) {
            return;
        }
        
        try {
            String key = REDIS_KEY_PREFIX + sceneType;
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Failed to delete scene config from Redis: sceneType={}", sceneType, e);
        }
    }
}
