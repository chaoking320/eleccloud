package com.retry.platform.server.service.impl;

import com.retry.platform.server.service.AlertService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 日志告警服务实现
 * 简单实现，将告警信息记录到日志中
 * 生产环境可以扩展为邮件、钉钉、企业微信等告警方式
 */
@Service
public class LogAlertServiceImpl implements AlertService {
    
    private static final Logger log = LoggerFactory.getLogger(LogAlertServiceImpl.class);
    
    @Override
    public void sendAlert(String title, String message) {
        sendAlert(title, message, AlertLevel.ERROR);
    }
    
    @Override
    public void sendAlert(String title, String message, AlertLevel level) {
        String alertLog = formatAlertMessage(title, message, level);
        
        switch (level) {
            case INFO:
                log.info(alertLog);
                break;
            case WARN:
                log.warn(alertLog);
                break;
            case ERROR:
                log.error(alertLog);
                break;
            case CRITICAL:
                log.error("[CRITICAL] {}", alertLog);
                break;
            default:
                log.error(alertLog);
        }
        
        // TODO: 扩展点 - 可以在这里添加其他告警渠道
        // 例如：发送邮件、钉钉机器人、企业微信等
        // sendEmail(title, message, level);
        // sendDingTalk(title, message, level);
        // sendWeChat(title, message, level);
    }
    
    /**
     * 格式化告警消息
     */
    private String formatAlertMessage(String title, String message, AlertLevel level) {
        return String.format("[ALERT][%s] %s - %s", level, title, message);
    }
    
    // ========== 扩展方法示例（预留） ==========
    
    /**
     * 发送邮件告警（预留扩展点）
     * 
     * 使用示例：
     * 1. 添加邮件依赖（如spring-boot-starter-mail）
     * 2. 配置邮件服务器信息
     * 3. 实现邮件发送逻辑
     */
    @SuppressWarnings("unused")
    private void sendEmail(String title, String message, AlertLevel level) {
        // TODO: 实现邮件告警
        // JavaMailSender mailSender;
        // SimpleMailMessage mailMessage = new SimpleMailMessage();
        // mailMessage.setTo("admin@example.com");
        // mailMessage.setSubject(title);
        // mailMessage.setText(message);
        // mailSender.send(mailMessage);
    }
    
    /**
     * 发送钉钉告警（预留扩展点）
     * 
     * 使用示例：
     * 1. 创建钉钉机器人并获取webhook地址
     * 2. 使用HttpClient发送POST请求
     * 3. 构造钉钉消息格式（markdown或text）
     */
    @SuppressWarnings("unused")
    private void sendDingTalk(String title, String message, AlertLevel level) {
        // TODO: 实现钉钉告警
        // String webhook = "https://oapi.dingtalk.com/robot/send?access_token=xxx";
        // String content = String.format("### %s\n\n%s\n\n**级别**: %s", title, message, level);
        // 
        // JSONObject json = new JSONObject();
        // json.put("msgtype", "markdown");
        // JSONObject markdown = new JSONObject();
        // markdown.put("title", title);
        // markdown.put("text", content);
        // json.put("markdown", markdown);
        // 
        // HttpClient.post(webhook, json.toString());
    }
    
    /**
     * 发送企业微信告警（预留扩展点）
     * 
     * 使用示例：
     * 1. 创建企业微信机器人并获取webhook地址
     * 2. 使用HttpClient发送POST请求
     * 3. 构造企业微信消息格式
     */
    @SuppressWarnings("unused")
    private void sendWeChat(String title, String message, AlertLevel level) {
        // TODO: 实现企业微信告警
        // String webhook = "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx";
        // String content = String.format("%s\n\n%s\n\n级别: %s", title, message, level);
        // 
        // JSONObject json = new JSONObject();
        // json.put("msgtype", "text");
        // JSONObject text = new JSONObject();
        // text.put("content", content);
        // json.put("text", text);
        // 
        // HttpClient.post(webhook, json.toString());
    }
}
