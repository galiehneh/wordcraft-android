package com.galiehneh.wordcraft.web;

/** Editor state reported through the {@code state} message. Immutable. */
public final class HostState {
    public static final HostState EMPTY = new HostState(false, false, false, false, false, "", 0);

    public final boolean dirty;
    public final boolean hasDocument;
    public final boolean editMode;
    public final boolean backstage;
    public final boolean dialog;
    public final String name;
    public final long rev;
    /** Document is password-encrypted: its plaintext must never be written to recovery. */
    public final boolean encrypted;

    public HostState(boolean dirty, boolean hasDocument, boolean editMode, boolean backstage,
                     boolean dialog, String name, long rev) {
        this(dirty, hasDocument, editMode, backstage, dialog, name, rev, false);
    }

    public HostState(boolean dirty, boolean hasDocument, boolean editMode, boolean backstage,
                     boolean dialog, String name, long rev, boolean encrypted) {
        this.dirty = dirty;
        this.hasDocument = hasDocument;
        this.editMode = editMode;
        this.backstage = backstage;
        this.dialog = dialog;
        this.name = name == null ? "" : name;
        this.rev = rev;
        this.encrypted = encrypted;
    }

    /** Back must be routed to the editor instead of the system default. */
    public boolean wantsBack() {
        return hasDocument || dialog || backstage || editMode;
    }
}
