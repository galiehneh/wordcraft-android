package com.galiehneh.wordcraft.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HostStateTest {
    private static HostState of(boolean doc, boolean edit, boolean backstage, boolean dialog) {
        return new HostState(false, doc, edit, backstage, dialog, null, 0);
    }

    @Test
    public void startScreenLeavesBackToSystem() {
        assertFalse(HostState.EMPTY.wantsBack());
    }

    @Test
    public void anyEditorSurfaceWantsBack() {
        assertTrue(of(true, false, false, false).wantsBack());
        assertTrue(of(false, true, false, false).wantsBack());
        assertTrue(of(false, false, true, false).wantsBack());
        assertTrue(of(false, false, false, true).wantsBack());
    }

    @Test
    public void nullNameBecomesEmpty() {
        assertEquals("", HostState.EMPTY.name);
    }
}
