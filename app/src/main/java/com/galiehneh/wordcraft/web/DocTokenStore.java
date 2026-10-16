package com.galiehneh.wordcraft.web;

import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;

/** One-time tokens mapping {@code /__host/doc/<token>} to a {@link DocSource}. Thread-safe. */
public final class DocTokenStore {
    private static final int TOKEN_BYTES = 16;
    private static final int MAX_PENDING = 8;
    /** A token the page has not fetched within this time is dead. */
    public static final long TTL_MS = 60_000;

    /** Monotonic milliseconds; injected so expiry is testable. */
    public interface Clock {
        long nowMs();
    }

    private static final class Entry {
        final DocSource source;
        final long expiresAt;

        Entry(DocSource source, long expiresAt) {
            this.source = source;
            this.expiresAt = expiresAt;
        }
    }

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Entry> sources = new LinkedHashMap<>();
    private final Clock clock;

    public DocTokenStore() {
        this(() -> System.nanoTime() / 1_000_000);
    }

    public DocTokenStore(Clock clock) {
        this.clock = clock;
    }

    public synchronized String issue(DocSource source) {
        long now = clock.nowMs();
        sources.values().removeIf(e -> e.expiresAt <= now);
        if (sources.size() >= MAX_PENDING) {
            // LinkedHashMap iterates in insertion order: drop the oldest (never fetched) token.
            sources.remove(sources.keySet().iterator().next());
        }
        byte[] raw = new byte[TOKEN_BYTES];
        random.nextBytes(raw);
        StringBuilder sb = new StringBuilder(TOKEN_BYTES * 2);
        for (byte b : raw) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        String token = sb.toString();
        sources.put(token, new Entry(source, now + TTL_MS));
        return token;
    }

    /** @return the source, removing the token; null if unknown, already used or expired. */
    public synchronized DocSource consume(String token) {
        Entry e = token == null ? null : sources.remove(token);
        return e == null || e.expiresAt <= clock.nowMs() ? null : e.source;
    }
}
