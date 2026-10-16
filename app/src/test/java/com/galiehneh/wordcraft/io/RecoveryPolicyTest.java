package com.galiehneh.wordcraft.io;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.galiehneh.wordcraft.web.HostState;

import org.junit.Test;

public class RecoveryPolicyTest {
    private static HostState st(boolean dirty, boolean doc, String name) {
        return new HostState(dirty, doc, false, false, false, name, 0);
    }

    private static final HostState EMPTY = HostState.EMPTY;

    @Test
    public void emptyStateAfterReadyNeverClears() {
        RecoveryPolicy p = new RecoveryPolicy();
        p.onReady();
        assertTrue(p.tryBeginRestore(false, false));
        assertFalse(p.onState(EMPTY));
        assertFalse(p.onState(EMPTY));
    }

    @Test
    public void emptyStateWhileLaunchIntentOpenPendingDoesNotClear() {
        RecoveryPolicy p = new RecoveryPolicy();
        p.onReady();
        assertFalse(p.tryBeginRestore(false, true));
        assertFalse(p.onState(EMPTY));
        p.onOpenSent();
        assertFalse(p.onState(EMPTY));
    }

    @Test
    public void restoreThenRecoveredDocumentKeepsRecoveryUntilSaved() {
        RecoveryPolicy p = new RecoveryPolicy();
        p.onReady();
        assertTrue(p.tryBeginRestore(false, false));
        assertFalse(p.onState(EMPTY));
        assertTrue(p.acceptRestore(false));
        p.onOpenSent();
        assertFalse(p.onState(EMPTY));
        assertFalse(p.onState(st(true, true, "a.docx")));
        assertTrue(p.onState(st(false, true, "a.docx")));
    }

    @Test
    public void restoreRejectedWhenUserOpenedFileFirst() {
        RecoveryPolicy p = new RecoveryPolicy();
        p.onReady();
        assertTrue(p.tryBeginRestore(false, false));
        assertFalse(p.acceptRestore(true));
    }

    @Test
    public void secondRestoreNotStartedWhileInFlight() {
        RecoveryPolicy p = new RecoveryPolicy();
        p.onReady();
        assertTrue(p.tryBeginRestore(false, false));
        assertFalse(p.tryBeginRestore(false, false));
    }

    @Test
    public void openOfCleanDocOverDirtyDocDoesNotClear() {
        RecoveryPolicy p = new RecoveryPolicy();
        p.onReady();
        p.onState(st(true, true, "a.docx"));
        p.onOpenSent();
        assertFalse(p.canSendOpen());
        assertFalse(p.onState(st(true, true, "a.docx")));
        assertFalse(p.onState(st(false, true, "b.docx")));
        assertTrue(p.canSendOpen());
        assertFalse(p.onState(st(false, true, "b.docx")));
    }

    @Test
    public void dirtyToCleanSameDocClears() {
        RecoveryPolicy p = new RecoveryPolicy();
        p.onReady();
        assertFalse(p.onState(st(false, true, "a.docx")));
        assertFalse(p.onState(st(true, true, "a.docx")));
        assertTrue(p.onState(st(false, true, "a.docx")));
        assertFalse(p.onState(st(false, true, "a.docx")));
    }

    @Test
    public void saveAsRenameClearsOnlyAfterConfirmedSave() {
        RecoveryPolicy p = new RecoveryPolicy();
        p.onReady();
        p.onState(st(false, true, "a.docx"));
        p.onState(st(true, true, "a.docx"));
        p.onSaveOk("saveAs");
        assertTrue(p.onState(st(false, true, "b.docx")));
    }

    @Test
    public void exportDoesNotAuthorizeClearOnRename() {
        RecoveryPolicy p = new RecoveryPolicy();
        p.onReady();
        p.onState(st(false, true, "a.docx"));
        p.onState(st(true, true, "a.docx"));
        p.onSaveOk("export");
        assertFalse(p.onState(st(false, true, "b.docx")));
    }

    @Test
    public void openTimeoutUnblocksQueuedOpen() {
        RecoveryPolicy p = new RecoveryPolicy();
        p.onReady();
        p.onOpenSent();
        p.onOpenTimeout();
        assertTrue(p.canSendOpen());
        assertFalse(p.onState(EMPTY));
    }

    @Test
    public void missingRecoveryReleasesGuard() {
        RecoveryPolicy p = new RecoveryPolicy();
        p.onReady();
        p.tryBeginRestore(false, false);
        p.onRestoreMissing();
        p.onState(st(false, true, "a.docx"));
        p.onState(st(true, true, "a.docx"));
        assertTrue(p.onState(st(false, true, "a.docx")));
    }

    @Test
    public void encryptedStateClearsRecoveryAndBlocksSnapshots() {
        RecoveryPolicy p = new RecoveryPolicy();
        p.onReady();
        p.onState(st(false, true, "a.docx"));
        assertFalse(p.isEncrypted());
        assertTrue(p.onState(new HostState(true, true, false, false, false, "a.docx", 1, true)));
        assertTrue(p.isEncrypted());
    }

    @Test
    public void encryptedClearsEvenWhileGuardedAndLiftsWhenPlain() {
        RecoveryPolicy p = new RecoveryPolicy();
        p.onReady();
        assertTrue(p.onState(new HostState(true, true, false, false, false, "a.docx", 1, true)));
        assertFalse(p.onState(st(true, true, "b.docx")));
        assertFalse(p.isEncrypted());
    }
}
