package com.galiehneh.wordcraft.io;

import android.util.AtomicFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** Crash-safe snapshot of the current document. Skeleton: not wired into MainActivity yet. */
public final class RecoveryStore {
    private final AtomicFile file;

    public RecoveryStore(File dir) {
        this.file = new AtomicFile(new File(dir, "current.docx"));
    }

    public void write(byte[] docx) throws IOException {
        FileOutputStream out = file.startWrite();
        try {
            out.write(docx);
            file.finishWrite(out);
        } catch (IOException e) {
            file.failWrite(out);
            throw e;
        }
    }

    /** @return snapshot bytes, or null if none exists. */
    public byte[] read() throws IOException {
        if (!file.getBaseFile().exists()) return null;
        return file.readFully();
    }

    public void clear() {
        file.delete();
    }
}
