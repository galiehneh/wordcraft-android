package com.galiehneh.wordcraft.io;

import java.util.Locale;

/** Pure decision: where does a {@code save} message from the editor go. */
public final class SavePlan {
    public static final String KIND_SAVE = "save";
    public static final String KIND_EXPORT = "export";

    /** Where the bytes are written. */
    public enum Target { IN_PLACE, PICK_NEW }

    private SavePlan() {}

    /**
     * Writes back to the open file only for a plain {@code save} of the same, writable .docx
     * document. {@code saveAs} and {@code export} always ask for a new target; an export never
     * overwrites the source.
     */
    public static Target decide(String kind, String name, CurrentDoc current) {
        if (!KIND_SAVE.equals(kind) || current == null || !current.writable) return Target.PICK_NEW;
        if (!isDocx(current.name) || !isDocx(name)) return Target.PICK_NEW;
        // The editor may have switched to another document (File > New): never overwrite the old one.
        return current.name.equalsIgnoreCase(name) ? Target.IN_PLACE : Target.PICK_NEW;
    }

    /** Only save/saveAs retarget the open document; an export leaves it untouched. */
    public static boolean retargetsCurrent(String kind) {
        return !KIND_EXPORT.equals(kind);
    }

    private static boolean isDocx(String name) {
        return name != null && name.toLowerCase(Locale.US).endsWith(".docx");
    }
}
