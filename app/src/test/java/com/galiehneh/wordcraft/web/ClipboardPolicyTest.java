package com.galiehneh.wordcraft.web;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ClipboardPolicyTest {
    @Test
    public void nullBecomesEmpty() {
        assertEquals("", ClipboardPolicy.limit(null));
    }

    @Test
    public void normalTextPassesThrough() {
        assertEquals("halo <b>dunia</b>", ClipboardPolicy.limit("halo <b>dunia</b>"));
    }

    @Test
    public void textAtLimitPassesAndOverLimitIsDropped() {
        String atLimit = new String(new char[ClipboardPolicy.MAX_CHARS]).replace('\0', 'a');
        assertEquals(ClipboardPolicy.MAX_CHARS, ClipboardPolicy.limit(atLimit).length());
        assertEquals("", ClipboardPolicy.limit(atLimit + "a"));
    }
}
