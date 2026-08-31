package com.retry.platform.server.service.impl;

import com.retry.platform.server.config.AlertConfig;
import com.retry.platform.server.service.AlertService;
import com.retry.platform.server.service.channel.AlertChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 多渠道告警服务实现
 * 支持邮件、钉钉、企业微信等多种告警渠道
 */
@Slf4j
@Service
public class LogAlertServiceImpl implements AlertService {

    @Autowired
    private AlertConfig alertConfig;

    @Autowired(required = false)
    private List<AlertChannel> alertChannels;

    @Override
    public void sendAlert(String title, String message) {
        sendAlert(title, message, AlertLevel.ERROR);
    }

    @Override
    public void sendAlert(String title, String message, AlertLevel level) {
        // 记录到日志（始终执行）
        logAlert(title, message, level);

        // 如果告警未启用，只记录日志不发送
        if (!alertConfig.isEnabled()) {
            log.debug("[Alert] Alert is disabled, only logged to file");
            return;
        }

        // 通过所有已启用的渠道发送告警
        if (alertChannels != null && !alertChannels.isEmpty()) {
            for (AlertChannel channel : alertChannels) {
                if (channel.isEnabled()) {
                    try {
                        channel.send(title, message, level);
                    } catch (Exception e) {
                        log.error("[Alert] Failed to send alert via {}: {}", channel.getChannelName(), title, e);
                    }
                } else {
                    log.debug("[Alert] Channel {} is disabled, skipping", channel.getChannelName());
                }
            }
        } else {
            log.warn("[Alert] No alert channels configured or available");
        }
    }

    /**
     * 记录告警到日志
     */
    private void logAlert(String title, String message, AlertLevel level) {
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
    }

    /**
     * 格式化告警消息
     */
    private String formatAlertMessage(String title, String message, AlertLevel level) {
        return String.format("[ALERT][%s] %s - %s", level, title, message);
    }
}
