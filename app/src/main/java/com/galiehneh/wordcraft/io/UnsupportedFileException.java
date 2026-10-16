package com.galiehneh.wordcraft.io;

import java.io.IOException;

/** The file's extension or content is not a document WordCraft can open. */
public final class UnsupportedFileException extends IOException {
    public UnsupportedFileException(String name) {
        super("Unsupported file: " + name);
    }
}
