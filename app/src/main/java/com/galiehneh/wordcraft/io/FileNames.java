package com.galiehneh.wordcraft.io;

/** File name sanitising for names supplied by the editor. Pure. */
public final class FileNames {
    public static final String DEFAULT_NAME = "document.docx";
    private static final String DEFAULT_BASE = "document";
    private static final int MAX_LENGTH = 120;

    private FileNames() {}

    public static String sanitize(String name) {
        if (name == null) return DEFAULT_NAME;
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean bad = c < 0x20 || c == 0x7F || "\\/:*?\"<>|".indexOf(c) >= 0;
            sb.append(bad ? '_' : c);
        }
        String s = sb.toString().trim();
        while (s.startsWith(".")) {
            s = s.substring(1);
        }
        s = s.replace("..", "_").trim();
        if (s.isEmpty() || s.replace("_", "").isEmpty()) return DEFAULT_NAME;
        if (s.length() > MAX_LENGTH) {
            int dot = s.lastIndexOf('.');
            String ext = dot > 0 && s.length() - dot <= 10 ? s.substring(dot) : "";
            s = s.substring(0, MAX_LENGTH - ext.length()) + ext;
        }
        return s;
    }

    /** Print job / file name for editor-supplied {@code name} (no Word extension): {@code <name>.pdf}. */
    public static String pdfName(String name) {
        String base = name == null ? "" : name.trim();
        if (base.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")) base = base.substring(0, base.length() - 4);
        String clean = base.isEmpty() ? DEFAULT_BASE : sanitize(base);
        if (DEFAULT_NAME.equals(clean)) clean = DEFAULT_BASE;
        return clean + ".pdf";
    }
}
