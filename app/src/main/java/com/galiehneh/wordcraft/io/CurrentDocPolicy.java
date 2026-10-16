package com.galiehneh.wordcraft.io;

/** Pure rules tying the save target ({@link CurrentDoc}) to the document the editor really shows. */
public final class CurrentDocPolicy {
    private CurrentDocPolicy() {}

    /** The target describes the editor's document only while both carry the same file name. */
    public static boolean matches(CurrentDoc doc, String editorName) {
        return doc != null && editorName != null && !editorName.isEmpty()
                && doc.name.equalsIgnoreCase(editorName);
    }

    /** Source URI for a recovery snapshot of {@code editorName}: empty unless the active target matches. */
    public static String sourceFor(CurrentDoc doc, String editorName) {
        return matches(doc, editorName) ? doc.uri : "";
    }

    /**
     * Save target after restoring a snapshot: the original file again, but only when its write grant
     * survived and the snapshot is still that very file (same name); otherwise null (ask where to save).
     */
    public static CurrentDoc restoredTarget(String sourceUri, String snapshotName,
                                            String sourceDisplayName, boolean writable) {
        if (sourceUri == null || sourceUri.isEmpty() || !writable) return null;
        if (snapshotName == null || !snapshotName.equalsIgnoreCase(sourceDisplayName)) return null;
        return new CurrentDoc(sourceUri, snapshotName, true);
    }
}
