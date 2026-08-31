package com.retry.platform.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 告警配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "retry.alert")
public class AlertConfig {

    /**
     * 是否启用告警
     */
    private boolean enabled = false;

    /**
     * 告警渠道（支持多个）：EMAIL, DINGTALK, WECHAT
     */
    private List<String> channels = new ArrayList<>();

    /**
     * 邮件配置
     */
    private EmailConfig email = new EmailConfig();

    /**
     * 钉钉配置
     */
    private DingTalkConfig dingtalk = new DingTalkConfig();

    /**
     * 企业微信配置
     */
    private WeChatConfig wechat = new WeChatConfig();

    /**
     * 失败任务告警阈值配置
     */
    private ThresholdConfig threshold = new ThresholdConfig();

    @Data
    public static class EmailConfig {
        /**
         * 收件人列表
         */
        private List<String> recipients = new ArrayList<>();

        /**
         * 发件人显示名称
         */
        private String fromName = "ElecCloud Alert";
    }

    @Data
    public static class DingTalkConfig {
        /**
         * 钉钉机器人webhook地址
         */
        private String webhook;

        /**
         * 安全设置-关键词
         */
        private String keyword = "告警";

        /**
         * 安全设置-加签密钥
         */
        private String secret;

        /**
         * @指定人的手机号（可选）
         */
        private List<String> atMobiles = new ArrayList<>();

        /**
         * 是否@所有人
         */
        private boolean atAll = false;
    }

    @Data
    public static class WeChatConfig {
        /**
         * 企业微信机器人webhook地址
         */
        private String webhook;

        /**
         * @指定人的userid（可选）
         */
        private List<String> mentionedList = new ArrayList<>();

        /**
         * @指定人的手机号（可选）
         */
        private List<String> mentionedMobileList = new ArrayList<>();
    }

    @Data
    public static class ThresholdConfig {
        /**
         * 死信任务突增阈值（单位：个/小时）
         */
        private int failedTasksPerHour = 10;

        /**
         * 执行失败率阈值（百分比）
         */
        private int failureRatePercent = 20;

        /**
         * 统计时间窗口（分钟）
         */
        private int timeWindowMinutes = 60;

        /**
         * 是否启用死信突增告警
         */
        private boolean enableFailedTaskAlert = true;

        /**
         * 是否启用失败率告警
         */
        private boolean enableFailureRateAlert = true;
    }
}
