package com.galiehneh.wordcraft.io;

import com.galiehneh.wordcraft.web.HostState;

/**
 * Pure decision logic for when the recovery snapshot may be restored or cleared. Main thread only.
 *
 * <p>An empty editor state never clears anything: recovery is cleared only on a dirty-to-clean
 * transition of the same open document (or after a confirmed save), never while a restore or open
 * is still in flight.
 */
public final class RecoveryPolicy {
    private HostState state = HostState.EMPTY;
    private HostState stateAtOpen = HostState.EMPTY;
    private boolean guardClear;
    private boolean restoreInFlight;
    private boolean opening;
    private boolean savedWhileDirty;
    /** Written on the main thread, read by the IO thread that takes in-place backups. */
    private volatile boolean encrypted;

    /** A fresh page sent {@code ready}: nothing may be cleared until a document shows up. */
    public void onReady() {
        state = HostState.EMPTY;
        guardClear = true;
        restoreInFlight = false;
        opening = false;
        savedWhileDirty = false;
        encrypted = false;
    }

    /** @return true if the caller should read recovery metadata and try to restore. */
    public boolean tryBeginRestore(boolean pendingOpen, boolean launchOpenExpected) {
        if (pendingOpen || launchOpenExpected || restoreInFlight || opening || state.hasDocument) return false;
        restoreInFlight = true;
        return true;
    }

    /** Metadata read finished with nothing to restore. */
    public void onRestoreMissing() {
        restoreInFlight = false;
        if (!opening) guardClear = false;
    }

    /** Metadata read finished; @return true if the restore may still be queued as the open. */
    public boolean acceptRestore(boolean pendingOpen) {
        restoreInFlight = false;
        return !pendingOpen && !opening && !state.hasDocument;
    }

    /** An {@code open} event is about to be sent. */
    public void onOpenSent() {
        guardClear = true;
        opening = true;
        stateAtOpen = state;
    }

    /** A new open must wait while the previous one has not reported its document yet. */
    public boolean canSendOpen() {
        return !opening;
    }

    public boolean isOpening() {
        return opening;
    }

    /** Gives up waiting for the editor to report the opened document. */
    public void onOpenTimeout() {
        opening = false;
    }

    /** A {@code save}/{@code saveAs} (not {@code export}) was confirmed written. */
    public void onSaveOk(String kind) {
        if (!"export".equals(kind)) savedWhileDirty = true;
    }

    /** The open document is encrypted: no snapshot or backup may be written while this holds. */
    public boolean isEncrypted() {
        return encrypted;
    }

    /** @return true if recovery should be cleared now. */
    public boolean onState(HostState next) {
        HostState prev = state;
        state = next;
        encrypted = next.encrypted;
        if (next.encrypted) return true;
        if (next.dirty) savedWhileDirty = false;
        if (guardClear) {
            if (next.hasDocument && (!opening || openCompleted(next))) {
                guardClear = false;
                opening = false;
            }
            return false;
        }
        boolean cleaned = prev.hasDocument && prev.dirty && next.hasDocument && !next.dirty;
        if (cleaned && (prev.name.equals(next.name) || savedWhileDirty)) {
            savedWhileDirty = false;
            return true;
        }
        return false;
    }

    private boolean openCompleted(HostState next) {
        return !stateAtOpen.hasDocument || !stateAtOpen.dirty
                || !next.dirty || !next.name.equals(stateAtOpen.name);
    }
}
