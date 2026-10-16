package com.galiehneh.wordcraft.io;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

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

    /** Legacy behaviour kept until W4: always a new file in Downloads. */
    public void saveToDownloads(String filename, String mime, byte[] bytes) throws IOException {
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
                os.write(bytes);
            } catch (IOException e) {
                resolver.delete(uri, null, null);
                throw e;
            }
            return;
        }
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
        try (FileOutputStream fos = new FileOutputStream(new File(dir, filename))) {
            fos.write(bytes);
        }
    }
}
