package com.galiehneh.wordcraft.io;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class UriLogTest {
    @Test
    public void keepsOnlySchemeAndAuthority() {
        assertEquals("content://com.android.providers", UriLog.describe("content", "com.android.providers"));
        assertEquals("?://?", UriLog.describe(null, null));
    }
}
