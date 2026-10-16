package com.galiehneh.wordcraft;

import android.content.Intent;

/** Decides which incoming intents carry a document to open. */
final class IntentRouter {
    private IntentRouter() {}

    static boolean isDocumentAction(String action) {
        return Intent.ACTION_VIEW.equals(action) || Intent.ACTION_SEND.equals(action);
    }

    /** Generic or missing MIME types say nothing about the content: validate extension and magic. */
    static boolean needsSniff(String mimeType) {
        return mimeType == null || mimeType.isEmpty() || "application/octet-stream".equalsIgnoreCase(mimeType);
    }

    /** A recreated activity (process death) must not re-handle the intent that started it. */
    static boolean shouldHandleLaunch(String action, boolean hasSavedState) {
        return !hasSavedState && isDocumentAction(action);
    }

    /** A file:// URI must never expose the app's private storage (recovery snapshots, prefs). */
    static boolean isPrivateFile(String scheme, String canonicalPath, String canonicalFilesDir) {
        if (!"file".equalsIgnoreCase(scheme) || canonicalPath == null) return false;
        String dataRoot = canonicalFilesDir.substring(0, Math.max(0, canonicalFilesDir.lastIndexOf('/')));
        return canonicalPath.equals(dataRoot) || canonicalPath.startsWith(dataRoot + "/");
    }
}
