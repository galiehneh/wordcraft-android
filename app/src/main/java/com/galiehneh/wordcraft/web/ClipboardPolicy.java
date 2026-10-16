package com.galiehneh.wordcraft.web;

/** Size limit for clipboard text handed to the page. Pure. */
public final class ClipboardPolicy {
    /** Upper bound in UTF-16 chars (about 5 MB of mostly-ASCII text). */
    public static final int MAX_CHARS = 5 * 1024 * 1024;

    private ClipboardPolicy() {}

    /** @return the text, or "" when null or too large (truncating would corrupt HTML). */
    public static String limit(CharSequence text) {
        if (text == null || text.length() > MAX_CHARS) return "";
        return text.toString();
    }
}
