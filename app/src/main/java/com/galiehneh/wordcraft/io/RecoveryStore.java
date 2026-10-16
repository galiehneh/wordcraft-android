package com.galiehneh.wordcraft.io;

import android.util.AtomicFile;
import android.util.Log;

import com.galiehneh.wordcraft.web.HostProtocol;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/** Crash-safe snapshot of the current document: {@code current.docx} + {@code meta.json}. */
public final class RecoveryStore {
    /** Metadata describing a stored snapshot. */
    public static final class Meta {
        public final String name;
        public final String sourceUri;
        public final long rev;

        public Meta(String name, String sourceUri, long rev) {
            this.name = name;
            this.sourceUri = sourceUri;
            this.rev = rev;
        }
    }

    private final AtomicFile docFile;
    private final AtomicFile metaFile;

    public RecoveryStore(File dir) {
        if (!dir.isDirectory() && !dir.mkdirs()) {
            // write() will surface the failure as IOException.
        }
        this.docFile = new AtomicFile(new File(dir, "current.docx"));
        this.metaFile = new AtomicFile(new File(dir, "meta.json"));
    }

    public File docFile() {
        return docFile.getBaseFile();
    }

    public synchronized void write(Meta meta, ByteBuffer docx) throws IOException {
        writeAtomic(docFile, docx.duplicate());
        try {
            JSONObject json = new JSONObject()
                    .put("name", meta.name)
                    .put("sourceUri", meta.sourceUri)
                    .put("rev", meta.rev);
            writeAtomic(metaFile, ByteBuffer.wrap(json.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    /** Snapshot write that logs instead of throwing (low memory, full disk): recovery is best effort. */
    public void writeQuietly(Meta meta, ByteBuffer docx) {
        tryWrite(meta, docx);
    }

    /** @return false if the snapshot could not be stored (logged without the document location). */
    public boolean tryWrite(Meta meta, ByteBuffer docx) {
        try {
            write(meta, docx);
            return true;
        } catch (IOException | OutOfMemoryError e) {
            Log.e("WordCraft", "Recovery write failed: " + e.getClass().getSimpleName());
            return false;
        }
    }

    /** @return the stored metadata, or null if there is no complete snapshot. */
    public synchronized Meta readMeta() {
        if (!docFile.getBaseFile().isFile() || !metaFile.getBaseFile().isFile()) return null;
        try {
            JSONObject json = new JSONObject(new String(metaFile.readFully(), StandardCharsets.UTF_8));
            return new Meta(json.optString("name", FileNames.DEFAULT_NAME),
                    json.optString("sourceUri", ""), json.optLong("rev", 0));
        } catch (IOException | JSONException e) {
            return null;
        }
    }

    public synchronized void clear() {
        docFile.delete();
        metaFile.delete();
    }

    private static void writeAtomic(AtomicFile file, ByteBuffer bytes) throws IOException {
        FileOutputStream out = file.startWrite();
        try {
            HostProtocol.writeTo(out, bytes);
            file.finishWrite(out);
        } catch (IOException e) {
            file.failWrite(out);
            throw e;
        }
    }
}
