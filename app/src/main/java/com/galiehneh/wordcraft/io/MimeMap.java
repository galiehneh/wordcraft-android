package com.galiehneh.wordcraft.io;

import java.util.Locale;

/** Maps file names or paths to MIME types by extension. */
public final class MimeMap {
    /** Types offered by the document picker (octet-stream catches providers with generic types). */
    public static final String[] OPEN_TYPES = {
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/msword",
            "application/vnd.oasis.opendocument.text",
            "application/rtf",
            "text/rtf",
            "text/plain",
            "text/markdown",
            "text/html",
            "application/octet-stream"
    };

    private MimeMap() {}

    public static String of(String path) {
        String normalized = path.toLowerCase(Locale.US);
        if (normalized.endsWith(".html") || normalized.endsWith(".htm")) return "text/html";
        if (normalized.endsWith(".js")) return "text/javascript";
        if (normalized.endsWith(".wasm")) return "application/wasm";
        if (normalized.endsWith(".css")) return "text/css";
        if (normalized.endsWith(".json")) return "application/json";
        if (normalized.endsWith(".png")) return "image/png";
        if (normalized.endsWith(".jpg") || normalized.endsWith(".jpeg")) return "image/jpeg";
        if (normalized.endsWith(".svg")) return "image/svg+xml";
        if (normalized.endsWith(".ico")) return "image/x-icon";
        if (normalized.endsWith(".txt")) return "text/plain";
        if (normalized.endsWith(".md")) return "text/markdown";
        if (normalized.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (normalized.endsWith(".doc")) return "application/msword";
        if (normalized.endsWith(".odt")) return "application/vnd.oasis.opendocument.text";
        if (normalized.endsWith(".rtf")) return "application/rtf";
        if (normalized.endsWith(".pdf")) return "application/pdf";
        if (normalized.endsWith(".woff2")) return "font/woff2";
        if (normalized.endsWith(".ttf")) return "font/ttf";
        return "application/octet-stream";
    }
}
