package com.galiehneh.wordcraft.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NavPolicyTest {
    @Test
    public void appOriginStaysInApp() {
        assertEquals(NavPolicy.Action.ALLOW_IN_APP, NavPolicy.classify("https", "appassets.androidplatform.net"));
        assertEquals(NavPolicy.Action.ALLOW_IN_APP, NavPolicy.classify("HTTPS", "AppAssets.AndroidPlatform.net"));
    }

    @Test
    public void legacyLocalhostIsNotTheAppOrigin() {
        assertEquals(NavPolicy.Action.OPEN_EXTERNAL, NavPolicy.classify("https", "localhost"));
        assertEquals(NavPolicy.Action.OPEN_EXTERNAL, NavPolicy.classify("http", "appassets.androidplatform.net"));
    }

    @Test
    public void webAndMailGoExternal() {
        assertEquals(NavPolicy.Action.OPEN_EXTERNAL, NavPolicy.classify("https", "example.com"));
        assertEquals(NavPolicy.Action.OPEN_EXTERNAL, NavPolicy.classify("http", "example.com"));
        assertEquals(NavPolicy.Action.OPEN_EXTERNAL, NavPolicy.classify("mailto", null));
    }

    @Test
    public void otherSchemesAreBlocked() {
        assertEquals(NavPolicy.Action.BLOCK, NavPolicy.classify("file", null));
        assertEquals(NavPolicy.Action.BLOCK, NavPolicy.classify("intent", "x"));
        assertEquals(NavPolicy.Action.BLOCK, NavPolicy.classify("javascript", null));
        assertEquals(NavPolicy.Action.BLOCK, NavPolicy.classify(null, null));
    }

    @Test
    public void openExternalRejectsAppOriginAndBlocked() {
        assertTrue(NavPolicy.canOpenExternally("https", "example.com"));
        assertFalse(NavPolicy.canOpenExternally("https", "appassets.androidplatform.net"));
        assertFalse(NavPolicy.canOpenExternally("content", "x"));
    }

    @Test
    public void onlyAppOriginAndLocalSchemesAreFetchable() {
        assertTrue(NavPolicy.isRequestAllowed("https", NavPolicy.APP_HOST));
        assertTrue(NavPolicy.isRequestAllowed("blob", null));
        assertTrue(NavPolicy.isRequestAllowed("DATA", null));
        assertFalse(NavPolicy.isRequestAllowed("https", "example.com"));
        assertFalse(NavPolicy.isRequestAllowed("http", NavPolicy.APP_HOST));
        assertFalse(NavPolicy.isRequestAllowed("file", null));
        assertFalse(NavPolicy.isRequestAllowed(null, null));
    }
}
