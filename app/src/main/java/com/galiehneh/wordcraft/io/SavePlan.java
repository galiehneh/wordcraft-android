package com.galiehneh.wordcraft.io;

import java.io.FileNotFoundException;
import java.nio.ByteBuffer;
import java.util.Locale;

/** Pure decision: where does a {@code save} message from the editor go. */
public final class SavePlan {
    public static final String KIND_SAVE = "save";
    public static final String KIND_SAVE_AS = "saveAs";
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

    /** Only the three kinds of docs/host-protocol.md are accepted; anything else is never written. */
    public static boolean isKnownKind(String kind) {
        return KIND_SAVE.equals(kind) || KIND_SAVE_AS.equals(kind) || KIND_EXPORT.equals(kind);
    }

    /** An export must never be written over the document that is open. */
    public static boolean exportOverwritesCurrent(String kind, String targetUri, CurrentDoc current) {
        return KIND_EXPORT.equals(kind) && current != null && current.uri.equals(targetUri);
    }

    /** In-place saves replace a user file: refuse empty bytes or anything that is not a ZIP/.docx container. */
    public static boolean canWriteInPlace(ByteBuffer bytes) {
        return FileSniffer.hasZipMagic(bytes);
    }

    /** Failures that mean "this file cannot be written back here": ask for a new target instead. */
    public static boolean shouldRepick(Throwable e) {
        return e instanceof SecurityException || e instanceof FileNotFoundException
                || e instanceof UnsupportedOperationException;
    }

    private static boolean isDocx(String name) {
        return name != null && name.toLowerCase(Locale.US).endsWith(".docx");
    }
}
