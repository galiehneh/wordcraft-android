package com.galiehneh.wordcraft.web;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CrashLimiterTest {
    @Test
    public void thirdCrashInWindowIsRefused() {
        CrashLimiter l = new CrashLimiter(2, 60_000);
        assertTrue(l.recordAndAllowRetry(0));
        assertTrue(l.recordAndAllowRetry(10_000));
        assertFalse(l.recordAndAllowRetry(20_000));
    }

    @Test
    public void oldCrashesExpire() {
        CrashLimiter l = new CrashLimiter(2, 60_000);
        l.recordAndAllowRetry(0);
        l.recordAndAllowRetry(1_000);
        assertTrue(l.recordAndAllowRetry(61_001 + 1_000));
    }
}
