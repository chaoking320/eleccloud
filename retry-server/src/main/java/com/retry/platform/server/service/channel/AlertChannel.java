package com.retry.platform.server.service.channel;

import com.retry.platform.server.service.AlertService.AlertLevel;

/**
 * 告警渠道接口
 */
public interface AlertChannel {

    /**
     * 发送告警
     *
     * @param title   告警标题
     * @param message 告警内容
     * @param level   告警级别
     */
    void send(String title, String message, AlertLevel level);

    /**
     * 获取渠道名称
     */
    String getChannelName();

    /**
     * 是否启用
     */
    boolean isEnabled();
}
