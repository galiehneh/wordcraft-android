package com.galiehneh.wordcraft.io;

/** Log-safe description of a document URI: scheme and authority only, never path or name. Pure. */
public final class UriLog {
    private UriLog() {}

    public static String describe(String scheme, String authority) {
        return (scheme == null ? "?" : scheme) + "://" + (authority == null ? "?" : authority);
    }
}
