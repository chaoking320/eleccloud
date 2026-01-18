package com.retry.platform.server.service;

/**
 * 告警服务接口
 * 提供统一的告警能力，支持多种告警渠道扩展
 */
public interface AlertService {
    
    /**
     * 发送告警
     * 
     * @param title 告警标题
     * @param message 告警内容
     */
    void sendAlert(String title, String message);
    
    /**
     * 发送告警（带级别）
     * 
     * @param title 告警标题
     * @param message 告警内容
     * @param level 告警级别（INFO/WARN/ERROR/CRITICAL）
     */
    void sendAlert(String title, String message, AlertLevel level);
    
    /**
     * 告警级别枚举
     */
    enum AlertLevel {
        INFO,      // 信息
        WARN,      // 警告
        ERROR,     // 错误
        CRITICAL   // 严重
    }
}
