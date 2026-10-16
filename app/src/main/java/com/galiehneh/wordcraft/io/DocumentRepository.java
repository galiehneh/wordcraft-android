package com.galiehneh.wordcraft.io;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Process;
import android.provider.MediaStore;
import android.provider.OpenableColumns;

import com.galiehneh.wordcraft.web.HostProtocol;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/** Document IO: display names, probing readable URIs, saving to Downloads. Call off the main thread. */
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

    /** Legacy behaviour kept until W4: always a new file in Downloads. */
    public void saveToDownloads(String filename, String mime, ByteBuffer bytes) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = context.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
            values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IOException("MediaStore insert failed");
            try (OutputStream os = resolver.openOutputStream(uri)) {
                if (os == null) throw new IOException("No output stream for " + uri);
                HostProtocol.writeTo(os, bytes);
            } catch (IOException e) {
                resolver.delete(uri, null, null);
                throw e;
            }
            return;
        }
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
        try (FileOutputStream fos = new FileOutputStream(new File(dir, filename))) {
            HostProtocol.writeTo(fos, bytes);
        }
    }
}
