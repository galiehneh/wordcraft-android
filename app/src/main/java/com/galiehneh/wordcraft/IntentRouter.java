package com.galiehneh.wordcraft;

import android.content.Intent;

/** Decides which incoming intents carry a document to open. */
final class IntentRouter {
    private IntentRouter() {}

    static boolean isDocumentAction(String action) {
        return Intent.ACTION_VIEW.equals(action) || Intent.ACTION_SEND.equals(action);
    }

    /** A recreated activity (process death) must not re-handle the intent that started it. */
    static boolean shouldHandleLaunch(String action, boolean hasSavedState) {
        return !hasSavedState && isDocumentAction(action);
    }
}
