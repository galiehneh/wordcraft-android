package com.galiehneh.wordcraft.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;

public class MimeMapTest {
    @Test
    public void documentExtensionsMapCaseInsensitively() {
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", MimeMap.of("A.DOCX"));
        assertEquals("application/msword", MimeMap.of("a.doc"));
        assertEquals("application/vnd.oasis.opendocument.text", MimeMap.of("a.odt"));
        assertEquals("application/rtf", MimeMap.of("a.rtf"));
        assertEquals("text/markdown", MimeMap.of("a.md"));
        assertEquals("text/plain", MimeMap.of("a.txt"));
        assertEquals("text/html", MimeMap.of("a.htm"));
        assertEquals("application/pdf", MimeMap.of("a.pdf"));
        assertEquals("image/png", MimeMap.of("a.png"));
        assertEquals("image/jpeg", MimeMap.of("a.jpeg"));
    }

    @Test
    public void unknownOrMissingExtensionIsOctetStream() {
        assertEquals("application/octet-stream", MimeMap.of("noext"));
        assertEquals("application/octet-stream", MimeMap.of("a.xyz"));
    }

    @Test
    public void pickerOffersDocumentTypesWithoutPdf() {
        assertTrue(Arrays.asList(MimeMap.OPEN_TYPES).contains("application/octet-stream"));
        assertFalse(Arrays.asList(MimeMap.OPEN_TYPES).contains("application/pdf"));
    }
}
