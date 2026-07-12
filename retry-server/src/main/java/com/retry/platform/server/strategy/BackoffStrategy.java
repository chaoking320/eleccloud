package com.retry.platform.server.strategy;

import java.util.List;

/**
 * 退避策略枚举
 * 定义重试任务下次执行时间的计算策略
 *
 * <pre>
 * 策略说明：
 *   CUSTOM      - 自定义间隔列表（默认），从 retry_intervals 字段读取逗号分隔的分钟数
 *                 示例：retry_intervals="1,5,10,30" 表示4次重试间隔依次为1/5/10/30分钟
 *
 *   FIXED       - 固定间隔，每次重试间隔相同（= backoff_base 分钟）
 *                 示例：backoff_base=5，重试间隔始终为 5分钟
 *
 *   LINEAR      - 线性递增，每次重试间隔 = retryCount * backoff_base 分钟
 *                 示例：backoff_base=2，间隔依次为 2、4、6、8、10 分钟
 *
 *   EXPONENTIAL - 指数退避，每次重试间隔 = backoff_base * 2^(retryCount-1) 分钟
 *                 示例：backoff_base=1，间隔依次为 1、2、4、8、16 分钟
 *                 与专利 CN116662445A 中"2^(n-1)分钟"策略对齐
 * </pre>
 */
public enum BackoffStrategy {

    /**
     * 自定义间隔列表（默认）
     * 从 scene_config.retry_intervals 字段读取固定间隔列表
     */
    CUSTOM {
        @Override
        public long calculateIntervalMs(int retryCount, int baseMins, List<Integer> customIntervals) {
            if (customIntervals == null || customIntervals.isEmpty()) {
                // 兜底：若无自定义配置，退化为固定1分钟
                return DEFAULT_FALLBACK_MS;
            }
            // retryCount 从 0 开始，取对应索引的间隔
            int idx = Math.min(retryCount, customIntervals.size() - 1);
            return customIntervals.get(idx) * MINUTE_MS;
        }
    },

    /**
     * 固定间隔
     * 每次重试间隔固定为 baseMins 分钟
     */
    FIXED {
        @Override
        public long calculateIntervalMs(int retryCount, int baseMins, List<Integer> customIntervals) {
            int base = baseMins > 0 ? baseMins : 1;
            return base * MINUTE_MS;
        }
    },

    /**
     * 线性递增
     * 每次重试间隔 = (retryCount + 1) * baseMins 分钟
     * retryCount=0 时间隔=1*base，retryCount=1 时间隔=2*base，以此类推
     */
    LINEAR {
        @Override
        public long calculateIntervalMs(int retryCount, int baseMins, List<Integer> customIntervals) {
            int base = baseMins > 0 ? baseMins : 1;
            return (long) (retryCount + 1) * base * MINUTE_MS;
        }
    },

    /**
     * 指数退避（对应专利 CN116662445A 中的 2^(n-1) 策略）
     * 每次重试间隔 = baseMins * 2^retryCount 分钟
     * retryCount=0 时间隔=1*base，retryCount=1 时间隔=2*base，retryCount=2 时间隔=4*base
     * 上限：最大不超过 24 小时，防止间隔无限增大
     */
    EXPONENTIAL {
        private static final long MAX_INTERVAL_MS = 24 * 60 * MINUTE_MS; // 24小时上限

        @Override
        public long calculateIntervalMs(int retryCount, int baseMins, List<Integer> customIntervals) {
            int base = baseMins > 0 ? baseMins : 1;
            // 防止 2^retryCount 溢出，上限为 30
            int exponent = Math.min(retryCount, 30);
            long intervalMs = base * (1L << exponent) * MINUTE_MS;
            return Math.min(intervalMs, MAX_INTERVAL_MS);
        }
    };

    /** 1分钟毫秒数 */
    protected static final long MINUTE_MS = 60 * 1000L;

    /** 兜底间隔：1分钟 */
    protected static final long DEFAULT_FALLBACK_MS = MINUTE_MS;

    /**
     * 计算下次重试间隔（毫秒）
     *
     * @param retryCount     当前已执行的重试次数（从0开始）
     * @param baseMins       退避基数（分钟），CUSTOM策略忽略此值
     * @param customIntervals CUSTOM策略下的自定义间隔列表（其他策略传null即可）
     * @return 距离下次重试的毫秒数
     */
    public abstract long calculateIntervalMs(int retryCount, int baseMins, List<Integer> customIntervals);

    /**
     * 计算下次重试时间戳（毫秒）
     *
     * @param retryCount     当前已执行的重试次数
     * @param baseMins       退避基数（分钟）
     * @param customIntervals 自定义间隔列表（CUSTOM策略用）
     * @return 下次重试的 Unix 时间戳（毫秒）
     */
    public long calculateNextRetryTime(int retryCount, int baseMins, List<Integer> customIntervals) {
        return System.currentTimeMillis() + calculateIntervalMs(retryCount, baseMins, customIntervals);
    }

    /**
     * 根据字符串名称安全解析枚举，未知策略默认返回 CUSTOM
     */
    public static BackoffStrategy fromName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return CUSTOM;
        }
        try {
            return BackoffStrategy.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return CUSTOM;
        }
    }
}
