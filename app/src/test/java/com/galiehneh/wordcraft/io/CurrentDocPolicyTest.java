package com.galiehneh.wordcraft.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CurrentDocPolicyTest {
    private static final CurrentDoc REPORT = new CurrentDoc("content://x/1", "Report.docx", true);

    @Test
    public void matchesOnlyTheSameNameIgnoringCase() {
        assertTrue(CurrentDocPolicy.matches(REPORT, "REPORT.docx"));
        assertFalse(CurrentDocPolicy.matches(REPORT, "Document1.docx"));
        assertFalse(CurrentDocPolicy.matches(REPORT, ""));
        assertFalse(CurrentDocPolicy.matches(REPORT, null));
        assertFalse(CurrentDocPolicy.matches(null, "Report.docx"));
    }

    @Test
    public void recoverySourceIsEmptyAfterTheEditorSwitchedDocuments() {
        assertEquals("content://x/1", CurrentDocPolicy.sourceFor(REPORT, "Report.docx"));
        assertEquals("", CurrentDocPolicy.sourceFor(REPORT, "Document1.docx"));
        assertEquals("", CurrentDocPolicy.sourceFor(null, "Report.docx"));
    }

    @Test
    public void restoreTargetsTheSourceOnlyWhenWritableAndSameName() {
        CurrentDoc t = CurrentDocPolicy.restoredTarget("content://x/1", "Report.docx", "Report.docx", true);
        assertEquals("content://x/1", t.uri);
        assertTrue(t.writable);
        assertNull(CurrentDocPolicy.restoredTarget("content://x/1", "Document1.docx", "Report.docx", true));
        assertNull(CurrentDocPolicy.restoredTarget("content://x/1", "Report.docx", "Report.docx", false));
        assertNull(CurrentDocPolicy.restoredTarget("", "Report.docx", "Report.docx", true));
        assertNull(CurrentDocPolicy.restoredTarget(null, "Report.docx", "Report.docx", true));
    }

    @Test
    public void restoredTargetThatDiffersInNameFallsBackToPicker() {
        CurrentDoc t = CurrentDocPolicy.restoredTarget("content://x/1", "Document1.docx", "Report.docx", true);
        assertEquals(SavePlan.Target.PICK_NEW, SavePlan.decide("save", "Document1.docx", t));
    }
}
