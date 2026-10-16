package com.galiehneh.wordcraft.web;

import java.util.ArrayDeque;

/** Allows at most {@code maxCrashes} renderer crashes inside a sliding window. Pure. */
public final class CrashLimiter {
    private final int maxCrashes;
    private final long windowMs;
    private final ArrayDeque<Long> crashes = new ArrayDeque<>();

    public CrashLimiter(int maxCrashes, long windowMs) {
        this.maxCrashes = maxCrashes;
        this.windowMs = windowMs;
    }

    /** @return true if the WebView may be rebuilt, false if the crash budget is exhausted. */
    public boolean recordAndAllowRetry(long nowMs) {
        crashes.addLast(nowMs);
        while (!crashes.isEmpty() && nowMs - crashes.peekFirst() > windowMs) {
            crashes.removeFirst();
        }
        return crashes.size() <= maxCrashes;
    }
}
