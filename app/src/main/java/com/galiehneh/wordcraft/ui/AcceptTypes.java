package com.galiehneh.wordcraft.ui;

import com.galiehneh.wordcraft.io.MimeMap;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Maps {@code <input accept>} tokens (MIME types and extensions) to SAF MIME filters. Pure. */
public final class AcceptTypes {
    private static final String GENERIC = "application/octet-stream";
    private static final Map<String, String[]> BY_EXTENSION = new LinkedHashMap<>();

    static {
        put("png", "image/png");
        put("jpg", "image/jpeg");
        put("jpeg", "image/jpeg");
        put("gif", "image/gif");
        put("bmp", "image/bmp");
        put("webp", "image/webp");
        put("svg", "image/svg+xml");
        // Providers often report fonts with a generic type, so keep octet-stream selectable.
        put("ttf", "font/ttf", "font/sfnt", "application/x-font-ttf", GENERIC);
        put("otf", "font/otf", "font/sfnt", "application/x-font-otf", "application/vnd.ms-opentype", GENERIC);
        put("woff", "font/woff", GENERIC);
        put("woff2", "font/woff2", GENERIC);
        put("csv", "text/csv", "text/comma-separated-values", "application/csv", "application/vnd.ms-excel");
        put("tsv", "text/tab-separated-values");
        put("txt", "text/plain");
        put("md", "text/markdown");
        put("html", "text/html");
        put("htm", "text/html");
        put("json", "application/json");
        put("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        put("doc", "application/msword");
        put("odt", "application/vnd.oasis.opendocument.text");
        put("rtf", "application/rtf", "text/rtf");
    }

    private AcceptTypes() {}

    private static void put(String extension, String... mimes) {
        BY_EXTENSION.put(extension, mimes);
    }

    /** @return MIME filters for the page's accept list; the document types when it names nothing usable. */
    public static String[] resolve(String[] acceptTypes) {
        Set<String> mimes = new LinkedHashSet<>();
        if (acceptTypes != null) {
            for (String entry : acceptTypes) {
                if (entry == null) continue;
                for (String token : entry.split(",")) addToken(mimes, token.trim().toLowerCase(Locale.US));
            }
        }
        return mimes.isEmpty() ? MimeMap.OPEN_TYPES.clone() : mimes.toArray(new String[0]);
    }

    private static void addToken(Set<String> out, String token) {
        if (token.isEmpty()) return;
        if (token.startsWith(".")) {
            String[] mapped = BY_EXTENSION.get(token.substring(1));
            if (mapped == null) {
                out.add(GENERIC);
            } else {
                for (String mime : mapped) out.add(mime);
            }
        } else if (token.indexOf('/') > 0) {
            out.add(token);
        }
    }
}
