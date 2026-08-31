package com.retry.platform.server.service.channel;

import com.retry.platform.server.config.AlertConfig;
import com.retry.platform.server.service.AlertService.AlertLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 邮件告警渠道
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "spring.mail", name = "host")
public class EmailAlertChannel implements AlertChannel {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Autowired
    private AlertConfig alertConfig;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void send(String title, String message, AlertLevel level) {
        if (!isEnabled()) {
            log.debug("[EmailAlert] Email alert is disabled or not configured");
            return;
        }

        try {
            SimpleMailMessage mailMessage = new SimpleMailMessage();
            mailMessage.setFrom(alertConfig.getEmail().getFromName());
            mailMessage.setTo(alertConfig.getEmail().getRecipients().toArray(new String[0]));
            mailMessage.setSubject(String.format("[%s] %s", level, title));
            mailMessage.setText(formatEmailContent(title, message, level));

            mailSender.send(mailMessage);
            log.info("[EmailAlert] Alert sent successfully: {}", title);
        } catch (Exception e) {
            log.error("[EmailAlert] Failed to send email alert: {}", title, e);
        }
    }

    @Override
    public String getChannelName() {
        return "EMAIL";
    }

    @Override
    public boolean isEnabled() {
        return alertConfig.isEnabled()
                && alertConfig.getChannels().contains("EMAIL")
                && mailSender != null
                && !CollectionUtils.isEmpty(alertConfig.getEmail().getRecipients());
    }

    private String formatEmailContent(String title, String message, AlertLevel level) {
        return String.format(
                "【ElecCloud 重试平台告警】\n\n" +
                        "告警标题: %s\n" +
                        "告警级别: %s\n" +
                        "告警时间: %s\n" +
                        "告警内容:\n%s\n\n" +
                        "---\n" +
                        "此邮件由 ElecCloud 分布式重试平台自动发送",
                title,
                level,
                LocalDateTime.now().format(FORMATTER),
                message
        );
    }
}
