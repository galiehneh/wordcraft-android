package com.galiehneh.wordcraft.io;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Process;
import android.provider.OpenableColumns;

import com.galiehneh.wordcraft.web.HostProtocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/** Document IO: display names, probing readable URIs, writing back. Call off the main thread. */
public final class DocumentRepository {
    private final Context context;

    public DocumentRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    public String displayName(Uri uri) {
        if (uri == null) return FileNames.DEFAULT_NAME;
        String result = null;
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            try (Cursor cursor = context.getContentResolver().query(
                    uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(0);
                }
            } catch (RuntimeException ignored) {
                // Provider failure: fall back to the path segment below.
            }
        }
        if (result == null || result.trim().isEmpty()) {
            result = uri.getLastPathSegment();
        }
        return FileNames.sanitize(result);
    }

    public InputStream openStream(Uri uri) throws IOException {
        InputStream in = context.getContentResolver().openInputStream(uri);
        if (in == null) throw new IOException("Provider returned no stream for " + uri);
        return in;
    }

    /** Fails fast when the URI cannot be read (revoked grant, deleted file). */
    public void probe(Uri uri) throws IOException {
        openStream(uri).close();
    }

    /** Keeps read (and write when offered) access across restarts; failures are not fatal. */
    public void persistAccess(Uri uri) {
        ContentResolver resolver = context.getContentResolver();
        int rw = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        try {
            resolver.takePersistableUriPermission(uri, rw);
            return;
        } catch (RuntimeException ignored) {
            // Provider offers no write grant (or none persistable): try read only.
        }
        try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (RuntimeException ignored) {
            // Transient grant only: valid while this process lives.
        }
    }

    /** True when a content URI currently carries a write grant for this app. */
    public boolean canWrite(Uri uri) {
        if (uri == null || !"content".equalsIgnoreCase(uri.getScheme())) return false;
        return context.checkUriPermission(uri, Process.myPid(), Process.myUid(),
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == PackageManager.PERMISSION_GRANTED;
    }

    /** Probes {@code uri} and describes it as a save target; optionally keeps its grant. */
    public CurrentDoc describeForOpen(Uri uri, boolean persist) throws IOException {
        String name = displayName(uri);
        probe(uri);
        if (persist) persistAccess(uri);
        return new CurrentDoc(uri.toString(), name, canWrite(uri));
    }

    /** The original file is a save target again only while its write grant survived. */
    public CurrentDoc restoredTarget(String sourceUri, String name) {
        if (sourceUri.isEmpty() || !canWrite(Uri.parse(sourceUri))) return null;
        return new CurrentDoc(sourceUri, name, true);
    }

    /** Replaces the content of {@code uri} ("wt" truncates first). Call off the main thread. */
    public void write(Uri uri, ByteBuffer bytes) throws IOException {
        try (OutputStream os = context.getContentResolver().openOutputStream(uri, "wt")) {
            if (os == null) throw new IOException("No output stream for " + uri);
            HostProtocol.writeTo(os, bytes.duplicate());
        }
    }
}
