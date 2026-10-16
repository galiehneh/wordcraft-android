package com.galiehneh.wordcraft.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.junit.Test;

public class SavePlanTest {
    private static final CurrentDoc WRITABLE = new CurrentDoc("content://x/1", "Laporan.docx", true);
    private static final CurrentDoc READ_ONLY = new CurrentDoc("content://x/1", "Laporan.docx", false);

    @Test
    public void saveOfWritableSameDocxIsInPlace() {
        assertEquals(SavePlan.Target.IN_PLACE, SavePlan.decide("save", "Laporan.docx", WRITABLE));
        assertEquals(SavePlan.Target.IN_PLACE, SavePlan.decide("save", "LAPORAN.DOCX", WRITABLE));
    }

    @Test
    public void saveWithoutWritableDocumentAsksForTarget() {
        assertEquals(SavePlan.Target.PICK_NEW, SavePlan.decide("save", "Laporan.docx", null));
        assertEquals(SavePlan.Target.PICK_NEW, SavePlan.decide("save", "Laporan.docx", READ_ONLY));
    }

    @Test
    public void saveAsAndExportNeverWriteInPlace() {
        assertEquals(SavePlan.Target.PICK_NEW, SavePlan.decide("saveAs", "Laporan.docx", WRITABLE));
        assertEquals(SavePlan.Target.PICK_NEW, SavePlan.decide("export", "Laporan.docx", WRITABLE));
        assertEquals(SavePlan.Target.PICK_NEW, SavePlan.decide("export", "Laporan.pdf", WRITABLE));
    }

    @Test
    public void saveOfDifferentDocumentDoesNotOverwriteTheOpenFile() {
        assertEquals(SavePlan.Target.PICK_NEW, SavePlan.decide("save", "Document1.docx", WRITABLE));
    }

    @Test
    public void docxBytesNeverOverwriteANonDocxSource() {
        CurrentDoc odt = new CurrentDoc("content://x/2", "notes.odt", true);
        assertEquals(SavePlan.Target.PICK_NEW, SavePlan.decide("save", "notes.docx", odt));
        assertEquals(SavePlan.Target.PICK_NEW, SavePlan.decide("save", "notes.odt", odt));
    }

    @Test
    public void exportDoesNotRetargetTheOpenDocument() {
        assertFalse(SavePlan.retargetsCurrent("export"));
        assertTrue(SavePlan.retargetsCurrent("save"));
        assertTrue(SavePlan.retargetsCurrent("saveAs"));
    }

    @Test
    public void onlyDocumentedKindsAreKnown() {
        assertTrue(SavePlan.isKnownKind("save"));
        assertTrue(SavePlan.isKnownKind("saveAs"));
        assertTrue(SavePlan.isKnownKind("export"));
        assertFalse(SavePlan.isKnownKind(""));
        assertFalse(SavePlan.isKnownKind(null));
        assertFalse(SavePlan.isKnownKind("SAVE"));
        assertFalse(SavePlan.isKnownKind("delete"));
    }

    @Test
    public void exportToTheOpenFileIsRefused() {
        assertTrue(SavePlan.exportOverwritesCurrent("export", "content://x/1", WRITABLE));
        assertFalse(SavePlan.exportOverwritesCurrent("export", "content://x/2", WRITABLE));
        assertFalse(SavePlan.exportOverwritesCurrent("export", "content://x/1", null));
        assertFalse(SavePlan.exportOverwritesCurrent("saveAs", "content://x/1", WRITABLE));
    }

    @Test
    public void inPlaceNeedsNonEmptyZipBytes() {
        assertTrue(SavePlan.canWriteInPlace(ByteBuffer.wrap(new byte[]{'P', 'K', 3, 4, 0})));
        assertFalse(SavePlan.canWriteInPlace(ByteBuffer.allocate(0)));
        assertFalse(SavePlan.canWriteInPlace(ByteBuffer.wrap(new byte[]{'P', 'K', 3})));
        assertFalse(SavePlan.canWriteInPlace(ByteBuffer.wrap(new byte[]{'P', 'K', 5, 6})));
        assertFalse(SavePlan.canWriteInPlace(ByteBuffer.wrap("{\\rtf1".getBytes())));
        assertFalse(SavePlan.canWriteInPlace(null));
    }

    @Test
    public void inPlaceMagicIsReadFromTheBufferPosition() {
        ByteBuffer b = ByteBuffer.wrap(new byte[]{0, 'P', 'K', 3, 4});
        b.position(1);
        assertTrue(SavePlan.canWriteInPlace(b));
    }

    @Test
    public void unwritableTargetsAreRepickedButIoErrorsFail() {
        assertTrue(SavePlan.shouldRepick(new SecurityException()));
        assertTrue(SavePlan.shouldRepick(new FileNotFoundException()));
        assertTrue(SavePlan.shouldRepick(new UnsupportedOperationException()));
        assertFalse(SavePlan.shouldRepick(new IOException("disk full")));
        assertFalse(SavePlan.shouldRepick(new IllegalStateException()));
    }

    @Test
    public void failedBackupCancelsInPlaceWrite() {
        assertEquals(SavePlan.Target.IN_PLACE, SavePlan.afterBackup(true));
        assertEquals(SavePlan.Target.PICK_NEW, SavePlan.afterBackup(false));
    }
}
