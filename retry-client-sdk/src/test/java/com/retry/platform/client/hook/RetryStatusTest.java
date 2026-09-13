package com.retry.platform.client.hook;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RetryStatusTest {

    @Test
    void testEnumValuesExist() {
        assertNotNull(RetryStatus.INIT);
        assertNotNull(RetryStatus.WAIT);
        assertNotNull(RetryStatus.SUCCESS);
    }

    @Test
    void testValueOf() {
        assertEquals(RetryStatus.INIT, RetryStatus.valueOf("INIT"));
        assertEquals(RetryStatus.WAIT, RetryStatus.valueOf("WAIT"));
        assertEquals(RetryStatus.SUCCESS, RetryStatus.valueOf("SUCCESS"));
    }
}
