package com.retry.platform.server.strategy;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 退避策略单元测试
 * 覆盖 CUSTOM / FIXED / LINEAR / EXPONENTIAL 四种策略的间隔计算，
 * 以及 {@link BackoffStrategy#fromName(String)} 的安全解析与 24h 上限保护。
 */
class BackoffStrategyTest {

    private static final long MIN = 60_000L;

    @Test
    void customUsesIntervalListInOrder() {
        List<Integer> intervals = Arrays.asList(1, 5, 10, 30);
        assertEquals(1 * MIN, BackoffStrategy.CUSTOM.calculateIntervalMs(0, 1, intervals));
        assertEquals(5 * MIN, BackoffStrategy.CUSTOM.calculateIntervalMs(1, 1, intervals));
        assertEquals(10 * MIN, BackoffStrategy.CUSTOM.calculateIntervalMs(2, 1, intervals));
        assertEquals(30 * MIN, BackoffStrategy.CUSTOM.calculateIntervalMs(3, 1, intervals));
    }

    @Test
    void customClampsToLastIntervalWhenRetryCountExceedsList() {
        List<Integer> intervals = Arrays.asList(1, 5, 10, 30);
        assertEquals(30 * MIN, BackoffStrategy.CUSTOM.calculateIntervalMs(10, 1, intervals));
        assertEquals(30 * MIN, BackoffStrategy.CUSTOM.calculateIntervalMs(999, 1, intervals));
    }

    @Test
    void customFallsBackToOneMinuteWhenNoIntervals() {
        assertEquals(MIN, BackoffStrategy.CUSTOM.calculateIntervalMs(0, 1, null));
        assertEquals(MIN, BackoffStrategy.CUSTOM.calculateIntervalMs(0, 1, Arrays.asList()));
    }

    @Test
    void fixedIgnoresRetryCountAndBase() {
        assertEquals(5 * MIN, BackoffStrategy.FIXED.calculateIntervalMs(0, 5, null));
        assertEquals(5 * MIN, BackoffStrategy.FIXED.calculateIntervalMs(100, 5, null));
    }

    @Test
    void fixedDefaultsBaseToOneWhenZeroOrNegative() {
        assertEquals(MIN, BackoffStrategy.FIXED.calculateIntervalMs(3, 0, null));
        assertEquals(MIN, BackoffStrategy.FIXED.calculateIntervalMs(3, -5, null));
    }

    @Test
    void linearIncreasesWithRetryCount() {
        assertEquals(2 * MIN, BackoffStrategy.LINEAR.calculateIntervalMs(0, 2, null));
        assertEquals(4 * MIN, BackoffStrategy.LINEAR.calculateIntervalMs(1, 2, null));
        assertEquals(6 * MIN, BackoffStrategy.LINEAR.calculateIntervalMs(2, 2, null));
    }

    @Test
    void linearDefaultsBaseToOneWhenZeroOrNegative() {
        assertEquals(3 * MIN, BackoffStrategy.LINEAR.calculateIntervalMs(2, 0, null));
    }

    @Test
    void exponentialDoublesEachRetry() {
        assertEquals(1 * MIN, BackoffStrategy.EXPONENTIAL.calculateIntervalMs(0, 1, null));
        assertEquals(2 * MIN, BackoffStrategy.EXPONENTIAL.calculateIntervalMs(1, 1, null));
        assertEquals(4 * MIN, BackoffStrategy.EXPONENTIAL.calculateIntervalMs(2, 1, null));
        assertEquals(8 * MIN, BackoffStrategy.EXPONENTIAL.calculateIntervalMs(3, 1, null));
    }

    @Test
    void exponentialCapsAtTwentyFourHours() {
        assertEquals(24 * 60 * MIN, BackoffStrategy.EXPONENTIAL.calculateIntervalMs(30, 1, null));
        assertEquals(24 * 60 * MIN, BackoffStrategy.EXPONENTIAL.calculateIntervalMs(100, 1, null));
    }

    @Test
    void fromNameResolvesCaseInsensitivelyAndDefaultsToCustom() {
        assertEquals(BackoffStrategy.EXPONENTIAL, BackoffStrategy.fromName("exponential"));
        assertEquals(BackoffStrategy.EXPONENTIAL, BackoffStrategy.fromName("EXPONENTIAL"));
        assertEquals(BackoffStrategy.CUSTOM, BackoffStrategy.fromName(null));
        assertEquals(BackoffStrategy.CUSTOM, BackoffStrategy.fromName(""));
        assertEquals(BackoffStrategy.CUSTOM, BackoffStrategy.fromName("unknown-strategy"));
    }

    @Test
    void nextRetryTimeIsInTheFuture() {
        long next = BackoffStrategy.FIXED.calculateNextRetryTime(0, 5, null);
        assertTrue(next > System.currentTimeMillis(), "next retry time should be in the future");
    }
}
