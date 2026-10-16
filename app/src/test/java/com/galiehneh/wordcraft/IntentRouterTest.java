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
}
