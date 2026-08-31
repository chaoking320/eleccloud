package com.retry.platform.server.service.channel;

import com.alibaba.fastjson2.JSONObject;
import com.retry.platform.server.config.AlertConfig;
import com.retry.platform.server.service.AlertService.AlertLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 企业微信告警渠道
 */
@Slf4j
@Component
public class WeChatAlertChannel implements AlertChannel {

    @Autowired
    private AlertConfig alertConfig;

    @Autowired
    private RestTemplate restTemplate;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void send(String title, String message, AlertLevel level) {
        if (!isEnabled()) {
            log.debug("[WeChatAlert] WeChat alert is disabled or not configured");
            return;
        }

        try {
            String webhook = alertConfig.getWechat().getWebhook();
            String content = formatWeChatContent(title, message, level);

            JSONObject json = new JSONObject();
            json.put("msgtype", "markdown");

            JSONObject markdown = new JSONObject();
            markdown.put("content", content);

            // 添加@人员
            if (!alertConfig.getWechat().getMentionedList().isEmpty()
                    || !alertConfig.getWechat().getMentionedMobileList().isEmpty()) {
                markdown.put("mentioned_list", alertConfig.getWechat().getMentionedList());
                markdown.put("mentioned_mobile_list", alertConfig.getWechat().getMentionedMobileList());
            }

            json.put("markdown", markdown);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(json.toString(), headers);

            ResponseEntity<String> response = restTemplate.postForEntity(webhook, entity, String.class);
            log.info("[WeChatAlert] Alert sent successfully: {}, response: {}", title, response.getBody());
        } catch (Exception e) {
            log.error("[WeChatAlert] Failed to send WeChat alert: {}", title, e);
        }
    }

    @Override
    public String getChannelName() {
        return "WECHAT";
    }

    @Override
    public boolean isEnabled() {
        return alertConfig.isEnabled()
                && alertConfig.getChannels().contains("WECHAT")
                && StringUtils.hasText(alertConfig.getWechat().getWebhook());
    }

    /**
     * 格式化企业微信消息内容（Markdown格式）
     */
    private String formatWeChatContent(String title, String message, AlertLevel level) {
        String levelEmoji = getLevelEmoji(level);
        String levelColor = getLevelColor(level);

        return String.format(
                "### %s <font color='%s'>%s</font>\n" +
                        "> **告警级别**: <font color='%s'>%s</font>\n" +
                        "> **告警时间**: %s\n\n" +
                        "**告警内容**:\n%s\n\n" +
                        "<font color='info'>ElecCloud 分布式重试平台</font>",
                levelEmoji, levelColor, title,
                levelColor, level,
                LocalDateTime.now().format(FORMATTER),
                message
        );
    }

    private String getLevelEmoji(AlertLevel level) {
        switch (level) {
            case INFO: return "ℹ️";
            case WARN: return "⚠️";
            case ERROR: return "❌";
            case CRITICAL: return "🚨";
            default: return "📢";
        }
    }

    private String getLevelColor(AlertLevel level) {
        switch (level) {
            case INFO: return "info";
            case WARN: return "warning";
            case ERROR: return "warning";
            case CRITICAL: return "warning";
            default: return "comment";
        }
    }
}
