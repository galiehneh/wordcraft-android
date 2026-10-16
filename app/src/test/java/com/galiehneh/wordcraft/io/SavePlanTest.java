package com.galiehneh.wordcraft.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
}
