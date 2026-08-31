package com.retry.platform.server.service.channel;

import com.alibaba.fastjson2.JSON;
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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * 钉钉告警渠道
 */
@Slf4j
@Component
public class DingTalkAlertChannel implements AlertChannel {

    @Autowired
    private AlertConfig alertConfig;

    @Autowired
    private RestTemplate restTemplate;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void send(String title, String message, AlertLevel level) {
        if (!isEnabled()) {
            log.debug("[DingTalkAlert] DingTalk alert is disabled or not configured");
            return;
        }

        try {
            String webhook = buildWebhookUrl();
            String content = formatDingTalkContent(title, message, level);

            JSONObject json = new JSONObject();
            json.put("msgtype", "markdown");

            JSONObject markdown = new JSONObject();
            markdown.put("title", title);
            markdown.put("text", content);
            json.put("markdown", markdown);

            // 添加@人员
            if (!alertConfig.getDingtalk().getAtMobiles().isEmpty() || alertConfig.getDingtalk().isAtAll()) {
                JSONObject at = new JSONObject();
                at.put("atMobiles", alertConfig.getDingtalk().getAtMobiles());
                at.put("isAtAll", alertConfig.getDingtalk().isAtAll());
                json.put("at", at);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(json.toString(), headers);

            ResponseEntity<String> response = restTemplate.postForEntity(webhook, entity, String.class);
            log.info("[DingTalkAlert] Alert sent successfully: {}, response: {}", title, response.getBody());
        } catch (Exception e) {
            log.error("[DingTalkAlert] Failed to send DingTalk alert: {}", title, e);
        }
    }

    @Override
    public String getChannelName() {
        return "DINGTALK";
    }

    @Override
    public boolean isEnabled() {
        return alertConfig.isEnabled()
                && alertConfig.getChannels().contains("DINGTALK")
                && StringUtils.hasText(alertConfig.getDingtalk().getWebhook());
    }

    /**
     * 构建webhook URL（支持加签）
     */
    private String buildWebhookUrl() throws Exception {
        String webhook = alertConfig.getDingtalk().getWebhook();
        String secret = alertConfig.getDingtalk().getSecret();

        if (!StringUtils.hasText(secret)) {
            return webhook;
        }

        // 加签逻辑
        Long timestamp = System.currentTimeMillis();
        String stringToSign = timestamp + "\n" + secret;

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        String sign = URLEncoder.encode(Base64.getEncoder().encodeToString(signData), "UTF-8");

        return webhook + "&timestamp=" + timestamp + "&sign=" + sign;
    }

    /**
     * 格式化钉钉消息内容（Markdown格式）
     */
    private String formatDingTalkContent(String title, String message, AlertLevel level) {
        String levelEmoji = getLevelEmoji(level);
        String levelColor = getLevelColor(level);

        return String.format(
                "### %s %s\n\n" +
                        "> **告警级别**: <font color='%s'>%s</font>\n\n" +
                        "> **告警时间**: %s\n\n" +
                        "---\n\n" +
                        "**告警内容**:\n\n%s\n\n" +
                        "---\n\n" +
                        "<font color='#999999'>ElecCloud 分布式重试平台</font>",
                levelEmoji, title,
                levelColor, level,
                LocalDateTime.now().format(FORMATTER),
                message.replace("\n", "\n\n")
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
            case INFO: return "#1890FF";
            case WARN: return "#FAAD14";
            case ERROR: return "#FF4D4F";
            case CRITICAL: return "#CF1322";
            default: return "#666666";
        }
    }
}
