package com.galiehneh.wordcraft.web;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

/** One-time tokens mapping {@code /__host/doc/<token>} to a {@link DocSource}. Thread-safe. */
public final class DocTokenStore {
    private static final int TOKEN_BYTES = 16;
    private static final int MAX_PENDING = 8;

    private final SecureRandom random = new SecureRandom();
    private final Map<String, DocSource> sources = new HashMap<>();

    public synchronized String issue(DocSource source) {
        if (sources.size() >= MAX_PENDING) {
            // Drop the oldest-inserted-ish entry; stale tokens come from editors that never fetched.
            sources.remove(sources.keySet().iterator().next());
        }
        byte[] raw = new byte[TOKEN_BYTES];
        random.nextBytes(raw);
        StringBuilder sb = new StringBuilder(TOKEN_BYTES * 2);
        for (byte b : raw) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        String token = sb.toString();
        sources.put(token, source);
        return token;
    }

    /** @return the source, removing the token; null if unknown or already used. */
    public synchronized DocSource consume(String token) {
        return token == null ? null : sources.remove(token);
    }
}
