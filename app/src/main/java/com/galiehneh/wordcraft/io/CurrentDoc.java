package com.galiehneh.wordcraft.io;

/** The document the editor currently has open: where it came from and whether we may write back. */
public final class CurrentDoc {
    public final String uri;
    public final String name;
    public final boolean writable;

    public CurrentDoc(String uri, String name, boolean writable) {
        this.uri = uri;
        this.name = name;
        this.writable = writable;
    }
}
