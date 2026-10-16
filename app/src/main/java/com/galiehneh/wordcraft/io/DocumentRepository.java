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
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Document IO: resolve display names, stage incoming documents, save to Downloads. */
public final class DocumentRepository {
    private static final String TAG = "WordCraft";
    public static final String CURRENT_DOCUMENT_FILE = "current_document";
    private static final String DEFAULT_NAME = "document.docx";

    private final Context context;

    public DocumentRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    public String displayName(Uri uri) {
        String result = DEFAULT_NAME;
        if (uri == null) return result;
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        String name = cursor.getString(nameIndex);
                        if (name != null && !name.trim().isEmpty()) {
                            result = name;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        } else {
            String path = uri.getPath();
            if (path != null) {
                int cut = path.lastIndexOf('/');
                if (cut != -1) {
                    result = path.substring(cut + 1);
                }
            }
        }
        return result;
    }

    /** Copies the document into cacheDir/current_document. */
    public void copyToCache(Uri uri) throws IOException {
        File dest = new File(context.getCacheDir(), CURRENT_DOCUMENT_FILE);
        try (InputStream is = context.getContentResolver().openInputStream(uri);
             FileOutputStream fos = new FileOutputStream(dest)) {
            byte[] buffer = new byte[32768];
            int read;
            while ((read = is.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }
        }
    }

    public boolean saveToDownloads(String filename, byte[] bytes) {
        String mime = MimeMap.of(filename);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = context.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
            values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

            Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) return false;
            try (OutputStream os = resolver.openOutputStream(uri)) {
                if (os != null) {
                    os.write(bytes);
                    return true;
                }
            } catch (IOException e) {
                Log.e(TAG, "Error writing to MediaStore", e);
            }
            return false;
        }
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!dir.exists()) dir.mkdirs();
        try (FileOutputStream fos = new FileOutputStream(new File(dir, filename))) {
            fos.write(bytes);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Error writing to file", e);
            return false;
        }
    }
}
