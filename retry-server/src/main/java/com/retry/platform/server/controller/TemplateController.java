package com.retry.platform.server.controller;

import com.retry.platform.server.template.SceneTemplate;
import com.retry.platform.server.template.SceneTemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 场景模板API - 提供开箱即用的配置模板
 * 
 * <p>降低用户配置难度，提供常见场景的推荐配置。
 * 
 * @since 1.1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/templates")
public class TemplateController {
    
    @Autowired
    private SceneTemplateService templateService;
    
    /**
     * 获取所有模板列表
     * 
     * @return 模板列表
     */
    @GetMapping("/list")
    public Map<String, Object> listTemplates() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("templates", templateService.getAllTemplates());
        result.put("statistics", templateService.getStatistics());
        return result;
    }
    
    /**
     * 获取模板详情
     * 
     * @param templateId 模板ID
     * @return 模板详情
     */
    @GetMapping("/{templateId}")
    public Map<String, Object> getTemplate(@PathVariable String templateId) {
        Map<String, Object> result = new HashMap<>();
        
        SceneTemplate template = templateService.getTemplate(templateId);
        if (template == null) {
            result.put("success", false);
            result.put("error", "Template not found: " + templateId);
            return result;
        }
        
        result.put("success", true);
        result.put("template", template);
        return result;
    }
    
    /**
     * 根据分类获取模板
     * 
     * @param category 分类（支付类/同步类/消息类等）
     * @return 模板列表
     */
    @GetMapping("/category/{category}")
    public Map<String, Object> getTemplatesByCategory(@PathVariable String category) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("category", category);
        result.put("templates", templateService.getTemplatesByCategory(category));
        return result;
    }
    
    /**
     * 获取推荐模板（零Hook模板）
     * 
     * @return 零Hook模板列表
     */
    @GetMapping("/recommended/zero-hook")
    public Map<String, Object> getZeroHookTemplates() {
        Map<String, Object> result = new HashMap<>();
        List<SceneTemplate> zeroHookTemplates = templateService.getAllTemplates().stream()
                .filter(SceneTemplate::getUseDefaultHook)
                .toList();
        
        result.put("success", true);
        result.put("count", zeroHookTemplates.size());
        result.put("templates", zeroHookTemplates);
        result.put("message", "These templates can be used without implementing Hook interface");
        return result;
    }
    
    /**
     * 根据模板创建场景配置（预览）
     * 
     * @param templateId 模板ID
     * @param sceneName 场景名称
     * @param sceneType 场景类型
     * @return 配置预览
     */
    @PostMapping("/preview")
    public Map<String, Object> previewConfig(
            @RequestParam String templateId,
            @RequestParam String sceneName,
            @RequestParam Integer sceneType) {
        
        Map<String, Object> result = new HashMap<>();
        
        SceneTemplate template = templateService.getTemplate(templateId);
        if (template == null) {
            result.put("success", false);
            result.put("error", "Template not found: " + templateId);
            return result;
        }
        
        // 构建配置预览
        Map<String, Object> config = new HashMap<>();
        config.put("sceneType", sceneType);
        config.put("sceneName", sceneName);
        config.put("backoffStrategy", template.getBackoffStrategy());
        config.put("backoffBase", template.getBackoffBase());
        config.put("retryIntervals", template.getRetryIntervals());
        config.put("maxRetryCount", template.getMaxRetryCount());
        config.put("maxRetryDuration", template.getMaxRetryDuration());
        config.put("hookClass", template.getUseDefaultHook() ? null : "com.your.company.YourHook");
        config.put("enabled", true);
        
        result.put("success", true);
        result.put("template", template);
        result.put("config", config);
        result.put("message", "Configuration preview based on template: " + template.getTemplateName());
        
        // 提供SQL/API示例
        if (template.getUseDefaultHook()) {
            result.put("quickStart", Map.of(
                    "step1", "Admin后台创建场景，hookClass留空",
                    "step2", "代码中添加@RetryableTask注解",
                    "step3", "完成！无需实现Hook接口"
            ));
        } else {
            result.put("quickStart", Map.of(
                    "step1", "Admin后台创建场景",
                    "step2", "实现RetryHook接口（checkStatus、doQuery、doCallback）",
                    "step3", "配置hookClass为Hook全限定类名",
                    "step4", "代码中添加@RetryableTask注解"
            ));
        }
        
        return result;
    }
}
