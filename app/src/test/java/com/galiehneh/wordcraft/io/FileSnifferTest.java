package com.galiehneh.wordcraft.io;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class FileSnifferTest {
    private static boolean ok(String name, byte[] b) {
        return FileSniffer.isSupported(name, b, b.length);
    }

    private static byte[] bytes(int... v) {
        byte[] b = new byte[v.length];
        for (int i = 0; i < v.length; i++) b[i] = (byte) v[i];
        return b;
    }

    @Test
    public void zipBasedFormatsNeedPkMagic() {
        byte[] zip = bytes('P', 'K', 3, 4, 0, 0);
        assertTrue(ok("a.docx", zip));
        assertTrue(ok("A.ODT", zip));
        assertFalse(ok("a.docx", "hello".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void legacyDocNeedsOleMagic() {
        assertTrue(ok("a.doc", bytes(0xD0, 0xCF, 0x11, 0xE0, 0xA1)));
        assertFalse(ok("a.doc", bytes('P', 'K', 3, 4)));
    }

    @Test
    public void rtfNeedsHeader() {
        assertTrue(ok("a.rtf", "{\\rtf1\\ansi".getBytes(StandardCharsets.UTF_8)));
        assertFalse(ok("a.rtf", "plain".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void textFormatsRejectBinary() {
        assertTrue(ok("a.txt", "halo dunia".getBytes(StandardCharsets.UTF_8)));
        assertTrue(ok("a.md", new byte[0]));
        assertTrue(ok("a.html", "<p>x</p>".getBytes(StandardCharsets.UTF_8)));
        assertFalse(ok("a.txt", bytes('a', 0, 'b')));
    }

    @Test
    public void unknownOrMissingExtensionIsRejected() {
        byte[] zip = bytes('P', 'K', 3, 4);
        assertFalse(ok("a.exe", zip));
        assertFalse(ok("a.pdf", zip));
        assertFalse(ok("noext", zip));
        assertFalse(FileSniffer.isSupported(null, zip, 4));
    }

    @Test
    public void truncatedHeadIsRejected() {
        assertFalse(FileSniffer.isSupported("a.docx", bytes('P'), 1));
    }
}
