package com.retry.platform.server.controller;

import com.retry.platform.server.entity.SceneConfig;
import com.retry.platform.server.service.SceneConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 诊断控制器 - 用于快速排查系统配置和健康状态
 * 
 * <p>提供以下诊断功能：
 * <ul>
 *   <li>系统健康检查（数据库、Redis连接状态）</li>
 *   <li>场景配置验证（Hook类加载、重试策略检查）</li>
 *   <li>任务统计信息</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/diagnostic")
public class DiagnosticController {
    
    @Autowired
    private SceneConfigService sceneConfigService;
    
    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    /**
     * 系统健康检查
     * 
     * @return 健康状态信息
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "UP");
        result.put("timestamp", System.currentTimeMillis());
        
        // 检查数据库连接
        Map<String, Object> dbStatus = checkDatabase();
        result.put("database", dbStatus);
        
        // 检查Redis连接
        Map<String, Object> redisStatus = checkRedis();
        result.put("redis", redisStatus);
        
        // 获取任务统计
        Map<String, Object> taskStats = getTaskStatistics();
        result.put("taskStatistics", taskStats);
        
        // 判断整体状态
        boolean isHealthy = "UP".equals(dbStatus.get("status")) 
                && "UP".equals(redisStatus.get("status"));
        result.put("status", isHealthy ? "UP" : "DOWN");
        
        return result;
    }
    
    /**
     * 场景配置诊断
     * 
     * @param sceneType 场景类型
     * @return 诊断结果
     */
    @GetMapping("/scene-check")
    public Map<String, Object> checkSceneConfig(@RequestParam Integer sceneType) {
        Map<String, Object> result = new HashMap<>();
        result.put("sceneType", sceneType);
        result.put("timestamp", System.currentTimeMillis());
        
        try {
            // 1. 获取场景配置
            SceneConfig config = sceneConfigService.getSceneConfigByType(sceneType);
            if (config == null) {
                result.put("status", "ERROR");
                result.put("error", "Scene config not found for sceneType: " + sceneType);
                result.put("suggestion", "Please create scene config first via Admin Console or API:\n" +
                        "POST /api/admin/scene/create\n" +
                        "{\n" +
                        "  \"sceneType\": " + sceneType + ",\n" +
                        "  \"sceneName\": \"Your Scene Name\",\n" +
                        "  \"maxRetryCount\": 3,\n" +
                        "  \"retryIntervals\": \"1,5,10\",\n" +
                        "  \"hookClass\": \"com.example.YourRetryHook\"\n" +
                        "}");
                return result;
            }
            
            // 2. 检查配置基本信息
            result.put("sceneName", config.getSceneName());
            result.put("enabled", config.checkEnabled());
            result.put("maxRetryCount", config.getMaxRetryCount());
            result.put("retryIntervals", config.getRetryIntervals());
            result.put("hookClass", config.getHookClass());
            result.put("backoffStrategy", config.getBackoffStrategy());
            result.put("backoffBase", config.getBackoffBase());
            
            // 3. 检查启用状态
            if (!config.checkEnabled()) {
                result.put("status", "DISABLED");
                result.put("warning", "Scene is disabled. Tasks will be rejected.");
                result.put("suggestion", "Enable the scene via Admin Console or API:\n" +
                        "PUT /api/admin/scene/update\n" +
                        "Set enabled=1 or enabled=true");
                return result;
            }
            
            // 4. 检查Hook类
            String hookClass = config.getHookClass();
            Map<String, Object> hookStatus = new HashMap<>();
            if (hookClass != null && !hookClass.trim().isEmpty()) {
                try {
                    Class<?> clazz = Class.forName(hookClass);
                    hookStatus.put("status", "OK");
                    hookStatus.put("className", hookClass);
                    hookStatus.put("classFound", true);
                    
                    // 检查是否实现了RetryHook接口
                    boolean implementsInterface = com.retry.platform.client.hook.RetryHook.class
                            .isAssignableFrom(clazz);
                    hookStatus.put("implementsRetryHook", implementsInterface);
                    
                    if (!implementsInterface) {
                        hookStatus.put("warning", "Class does not implement RetryHook interface");
                        hookStatus.put("suggestion", "Make sure your Hook class implements: " +
                                "com.retry.platform.client.hook.RetryHook");
                    }
                } catch (ClassNotFoundException e) {
                    hookStatus.put("status", "ERROR");
                    hookStatus.put("className", hookClass);
                    hookStatus.put("classFound", false);
                    hookStatus.put("error", "Hook class not found: " + hookClass);
                    hookStatus.put("suggestion", "Possible causes:\n" +
                            "1. Hook class not in classpath\n" +
                            "2. Incorrect package/class name\n" +
                            "3. Hook class not deployed in client application\n" +
                            "\n" +
                            "Make sure:\n" +
                            "- Hook class exists in your project\n" +
                            "- Hook class is annotated with @Component\n" +
                            "- Hook class implements RetryHook interface");
                }
            } else {
                hookStatus.put("status", "NOT_CONFIGURED");
                hookStatus.put("warning", "No Hook class configured. Using default behavior.");
                hookStatus.put("info", "Without Hook, the system will:\n" +
                        "1. Directly retry the original method via reflection\n" +
                        "2. No custom status check logic\n" +
                        "3. No callback after success");
            }
            result.put("hookCheck", hookStatus);
            
            // 5. 检查重试间隔配置
            Map<String, Object> intervalCheck = new HashMap<>();
            String retryIntervals = config.getRetryIntervals();
            if (retryIntervals != null && !retryIntervals.trim().isEmpty()) {
                boolean valid = sceneConfigService.validateRetryIntervals(retryIntervals);
                intervalCheck.put("valid", valid);
                intervalCheck.put("intervals", retryIntervals);
                
                if (valid) {
                    intervalCheck.put("status", "OK");
                    // 解析并展示实际重试时间
                    String[] parts = retryIntervals.split(",");
                    intervalCheck.put("retryCount", parts.length);
                    intervalCheck.put("retrySchedule", buildRetrySchedule(parts));
                } else {
                    intervalCheck.put("status", "ERROR");
                    intervalCheck.put("error", "Invalid retry intervals format");
                    intervalCheck.put("suggestion", "Expected: comma-separated positive integers. " +
                            "Example: 1,5,10,30 (retry after 1min, 5min, 10min, 30min)");
                }
            } else {
                intervalCheck.put("status", "NOT_CONFIGURED");
                intervalCheck.put("warning", "No retry intervals configured");
            }
            result.put("intervalCheck", intervalCheck);
            
            // 6. 检查退避策略
            Map<String, Object> strategyCheck = new HashMap<>();
            String strategy = config.getBackoffStrategy();
            Integer backoffBase = config.getBackoffBase();
            strategyCheck.put("strategy", strategy);
            strategyCheck.put("backoffBase", backoffBase);
            
            if ("CUSTOM".equals(strategy) && (retryIntervals == null || retryIntervals.trim().isEmpty())) {
                strategyCheck.put("status", "ERROR");
                strategyCheck.put("error", "CUSTOM strategy requires retryIntervals");
            } else if (("LINEAR".equals(strategy) || "EXPONENTIAL".equals(strategy) || "FIXED".equals(strategy)) 
                    && (backoffBase == null || backoffBase <= 0)) {
                strategyCheck.put("status", "WARNING");
                strategyCheck.put("warning", strategy + " strategy recommends setting backoffBase");
                strategyCheck.put("suggestion", "Set backoffBase to control retry intervals. " +
                        "Example: backoffBase=60 (1 minute)");
            } else {
                strategyCheck.put("status", "OK");
            }
            result.put("strategyCheck", strategyCheck);
            
            // 7. 整体状态
            boolean hasError = "ERROR".equals(hookStatus.get("status")) 
                    || "ERROR".equals(intervalCheck.get("status"))
                    || "ERROR".equals(strategyCheck.get("status"));
            
            result.put("status", hasError ? "ERROR" : "OK");
            result.put("message", hasError 
                    ? "Configuration has errors. Please fix them before using."
                    : "Configuration is valid and ready to use.");
            
        } catch (Exception e) {
            log.error("Error checking scene config: sceneType={}", sceneType, e);
            result.put("status", "ERROR");
            result.put("error", "Unexpected error: " + e.getMessage());
            result.put("stackTrace", e.getClass().getName());
        }
        
        return result;
    }
    
    /**
     * 快速配置检查（批量）
     * 
     * @return 所有场景配置的健康状态
     */
    @GetMapping("/scenes-overview")
    public Map<String, Object> scenesOverview() {
        Map<String, Object> result = new HashMap<>();
        result.put("timestamp", System.currentTimeMillis());
        
        try {
            // 这里可以扩展为查询所有场景配置
            result.put("message", "Use /api/diagnostic/scene-check?sceneType=<sceneType> to check specific scene");
            result.put("availableEndpoints", new String[]{
                    "GET /api/diagnostic/health - System health check",
                    "GET /api/diagnostic/scene-check?sceneType=<sceneType> - Scene config validation",
                    "GET /api/diagnostic/scenes-overview - All scenes overview"
            });
        } catch (Exception e) {
            log.error("Error in scenes overview", e);
            result.put("error", e.getMessage());
        }
        
        return result;
    }
    
    // ==================== 私有辅助方法 ====================
    
    /**
     * 检查数据库连接
     */
    private Map<String, Object> checkDatabase() {
        Map<String, Object> status = new HashMap<>();
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            status.put("status", "UP");
            status.put("type", "MySQL");
        } catch (Exception e) {
            status.put("status", "DOWN");
            status.put("error", e.getMessage());
            log.error("Database health check failed", e);
        }
        return status;
    }
    
    /**
     * 检查Redis连接
     */
    private Map<String, Object> checkRedis() {
        Map<String, Object> status = new HashMap<>();
        if (redisTemplate == null) {
            status.put("status", "NOT_CONFIGURED");
            status.put("message", "Redis not configured");
            return status;
        }
        
        try {
            redisTemplate.opsForValue().set("health-check", "OK");
            String value = redisTemplate.opsForValue().get("health-check");
            if ("OK".equals(value)) {
                status.put("status", "UP");
                status.put("type", "Redis");
                redisTemplate.delete("health-check");
            } else {
                status.put("status", "DOWN");
                status.put("error", "Redis read/write test failed");
            }
        } catch (Exception e) {
            status.put("status", "DOWN");
            status.put("error", e.getMessage());
            log.error("Redis health check failed", e);
        }
        return status;
    }
    
    /**
     * 获取任务统计信息
     */
    private Map<String, Object> getTaskStatistics() {
        Map<String, Object> stats = new HashMap<>();
        try {
            Long totalTasks = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM retry_task", Long.class);
            Long initTasks = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM retry_task WHERE task_status = 'INIT'", Long.class);
            Long executingTasks = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM retry_task WHERE task_status = 'EXECUTING'", Long.class);
            Long successTasks = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM retry_task WHERE task_status = 'SUCCESS'", Long.class);
            Long failedTasks = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM retry_task WHERE task_status = 'FAILED'", Long.class);
            
            stats.put("total", totalTasks != null ? totalTasks : 0);
            stats.put("init", initTasks != null ? initTasks : 0);
            stats.put("executing", executingTasks != null ? executingTasks : 0);
            stats.put("success", successTasks != null ? successTasks : 0);
            stats.put("failed", failedTasks != null ? failedTasks : 0);
        } catch (Exception e) {
            log.warn("Failed to get task statistics", e);
            stats.put("error", "Failed to retrieve statistics");
        }
        return stats;
    }
    
    /**
     * 构建重试时间表
     */
    private String buildRetrySchedule(String[] intervals) {
        StringBuilder schedule = new StringBuilder();
        int cumulativeMinutes = 0;
        for (int i = 0; i < intervals.length; i++) {
            try {
                int minutes = Integer.parseInt(intervals[i].trim());
                cumulativeMinutes += minutes;
                schedule.append("Retry ").append(i + 1)
                        .append(": after ").append(cumulativeMinutes).append(" minutes");
                if (i < intervals.length - 1) {
                    schedule.append("; ");
                }
            } catch (NumberFormatException e) {
                schedule.append("Invalid interval at position ").append(i + 1);
            }
        }
        return schedule.toString();
    }
}
