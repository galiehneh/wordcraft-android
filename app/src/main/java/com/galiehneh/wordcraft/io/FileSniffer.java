package com.galiehneh.wordcraft.io;

import java.util.Locale;

/** Extension plus magic-number check for files that arrive with a generic MIME type. Pure. */
public final class FileSniffer {
    /** How many leading bytes {@link #isSupported} needs. */
    public static final int HEAD_BYTES = 512;

    private FileSniffer() {}

    public static boolean isSupported(String name, byte[] head, int length) {
        if (name == null || head == null) return false;
        String lower = name.toLowerCase(Locale.US);
        int dot = lower.lastIndexOf('.');
        String ext = dot < 0 ? "" : lower.substring(dot + 1);
        switch (ext) {
            case "docx":
            case "odt":
                return startsWith(head, length, 'P', 'K', 0x03, 0x04);
            case "doc":
                return startsWith(head, length, 0xD0, 0xCF, 0x11, 0xE0);
            case "rtf":
                return startsWith(head, length, '{', '\\', 'r', 't', 'f');
            case "txt":
            case "md":
            case "html":
            case "htm":
                return isText(head, length);
            default:
                return false;
        }
    }

    private static boolean startsWith(byte[] head, int length, int... magic) {
        if (length < magic.length) return false;
        for (int i = 0; i < magic.length; i++) {
            if ((head[i] & 0xFF) != magic[i]) return false;
        }
        return true;
    }

    /** No NUL bytes in the sampled head: good enough to tell text from a binary blob. */
    private static boolean isText(byte[] head, int length) {
        for (int i = 0; i < length; i++) {
            if (head[i] == 0) return false;
        }
        return true;
    }
}
