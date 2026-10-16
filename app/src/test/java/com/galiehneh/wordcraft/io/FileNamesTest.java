package com.galiehneh.wordcraft.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FileNamesTest {
    @Test
    public void plainNameUnchanged() {
        assertEquals("Laporan 2026.docx", FileNames.sanitize("Laporan 2026.docx"));
    }

    @Test
    public void pathSeparatorsAndTraversalAreNeutralised() {
        assertEquals("___etc_passwd", FileNames.sanitize("/../etc/passwd"));
        assertEquals(false, FileNames.sanitize("../../x.docx").contains(".."));
        assertEquals(false, FileNames.sanitize("a/b\\c.docx").contains("/"));
    }

    @Test
    public void reservedAndControlCharsReplaced() {
        assertEquals("a_b_c_.docx", FileNames.sanitize("a:b*c\n.docx"));
    }

    @Test
    public void leadingDotsStripped() {
        assertEquals("hidden.docx", FileNames.sanitize("..hidden.docx"));
    }

    @Test
    public void emptyOrJunkFallsBackToDefault() {
        assertEquals(FileNames.DEFAULT_NAME, FileNames.sanitize(null));
        assertEquals(FileNames.DEFAULT_NAME, FileNames.sanitize("   "));
        assertEquals(FileNames.DEFAULT_NAME, FileNames.sanitize("///"));
    }

    @Test
    public void longNameKeepsExtension() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 300; i++) sb.append('a');
        String out = FileNames.sanitize(sb + ".docx");
        assertTrue(out.length() <= 120);
        assertTrue(out.endsWith(".docx"));
    }
}
