package com.galiehneh.wordcraft.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

public class DocTokenStoreTest {
    private static final DocSource SRC = () -> null;

    @Test
    public void tokenIsSingleUse() {
        DocTokenStore store = new DocTokenStore();
        String token = store.issue(SRC);

        assertSame(SRC, store.consume(token));
        assertNull(store.consume(token));
    }

    @Test
    public void tokensAreUniqueHex() {
        DocTokenStore store = new DocTokenStore();
        String a = store.issue(SRC);
        String b = store.issue(SRC);

        assertNotEquals(a, b);
        assertEquals(32, a.length());
        assertEquals(true, a.matches("[0-9a-f]{32}"));
    }

    @Test
    public void unknownOrNullTokenReturnsNull() {
        DocTokenStore store = new DocTokenStore();
        assertNull(store.consume("nope"));
        assertNull(store.consume(null));
    }

    @Test
    public void oldestPendingTokenIsEvictedBeyondLimit() {
        DocTokenStore store = new DocTokenStore();
        String last = null;
        for (int i = 0; i < 20; i++) last = store.issue(SRC);
        assertNotNull(store.consume(last));
    }

    @Test
    public void tokenExpiresAfterTtl() {
        long[] now = {1_000};
        DocTokenStore store = new DocTokenStore(() -> now[0]);
        String fresh = store.issue(SRC);
        String stale = store.issue(SRC);

        now[0] += DocTokenStore.TTL_MS - 1;
        assertSame(SRC, store.consume(fresh));
        now[0] += 1;
        assertNull(store.consume(stale));
    }
}
