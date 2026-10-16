package com.galiehneh.wordcraft;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class IntentRouterTest {
    @Test
    public void viewAndSendCarryDocuments() {
        assertTrue(IntentRouter.isDocumentAction("android.intent.action.VIEW"));
        assertTrue(IntentRouter.isDocumentAction("android.intent.action.SEND"));
        assertFalse(IntentRouter.isDocumentAction("android.intent.action.MAIN"));
        assertFalse(IntentRouter.isDocumentAction(null));
    }

    @Test
    public void launchIntentHandledOnlyOnFreshStart() {
        assertTrue(IntentRouter.shouldHandleLaunch("android.intent.action.VIEW", false));
        assertFalse(IntentRouter.shouldHandleLaunch("android.intent.action.VIEW", true));
        assertFalse(IntentRouter.shouldHandleLaunch("android.intent.action.MAIN", false));
    }

    @Test
    public void fileUrisInsideAppDataAreRefused() {
        String files = "/data/user/0/com.galiehneh.wordcraft/files";
        assertTrue(IntentRouter.isPrivateFile("file", files + "/recovery/current.docx", files));
        assertTrue(IntentRouter.isPrivateFile("file", "/data/user/0/com.galiehneh.wordcraft/shared_prefs/x.xml", files));
        assertFalse(IntentRouter.isPrivateFile("file", "/storage/emulated/0/a.docx", files));
        assertFalse(IntentRouter.isPrivateFile("file", "/data/user/0/com.galiehneh.wordcraft2/a", files));
        assertFalse(IntentRouter.isPrivateFile("content", files + "/x", files));
    }

    @Test
    public void onlyGenericMimeTypesAreSniffed() {
        assertTrue(IntentRouter.needsSniff(null));
        assertTrue(IntentRouter.needsSniff(""));
        assertTrue(IntentRouter.needsSniff("application/octet-stream"));
        assertFalse(IntentRouter.needsSniff("application/msword"));
        assertFalse(IntentRouter.needsSniff("text/plain"));
    }
}
