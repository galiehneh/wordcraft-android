package com.galiehneh.wordcraft;

import android.net.Uri;
import android.util.Log;

import com.galiehneh.wordcraft.io.CurrentDoc;
import com.galiehneh.wordcraft.io.CurrentDocPolicy;
import com.galiehneh.wordcraft.io.DocumentRepository;
import com.galiehneh.wordcraft.io.FileNames;
import com.galiehneh.wordcraft.io.MimeMap;
import com.galiehneh.wordcraft.io.SavePlan;
import com.galiehneh.wordcraft.web.HostBridge;

import java.nio.ByteBuffer;

/** Save, Save As and Export: in-place write or SAF CreateDocument. Main-thread API, IO off-thread. */
final class SaveCoordinator {
    private static final String TAG = "WordCraft";

    interface Host {
        void runIo(Runnable task);

        void postIfCurrent(HostBridge target, Runnable task);

        void toast(int resId, Object... args);

        void createDocument(String suggestedName, String mime);

        /** A save/saveAs/export was confirmed written. */
        void saved(String kind);

        /** IO thread, before an in-place overwrite: keep the new bytes recoverable if the write dies midway. */
        void backup(CurrentDoc doc, ByteBuffer bytes);
    }

    private static final class Pending {
        final HostBridge target;
        final long ticket;
        final String kind;
        final ByteBuffer bytes;

        Pending(HostBridge target, long ticket, String kind, ByteBuffer bytes) {
            this.target = target;
            this.ticket = ticket;
            this.kind = kind;
            this.bytes = bytes;
        }
    }

    private final DocumentRepository documents;
    private final Host host;
    private CurrentDoc current;
    /** Bumped whenever {@code current} is replaced: an async save must not overwrite a newer target. */
    private int generation;
    private Pending pending;

    SaveCoordinator(DocumentRepository documents, Host host) {
        this.documents = documents;
        this.host = host;
    }

    void setCurrent(CurrentDoc doc) {
        current = doc;
        generation++;
    }

    /** The editor reports its document name ({@code null}: none): a different one is not the open file. */
    void onEditorState(String editorName) {
        if (current != null && !CurrentDocPolicy.matches(current, editorName)) setCurrent(null);
    }

    /** Source URI worth recording in a recovery snapshot of the document called {@code editorName}. */
    String sourceUriFor(String editorName) {
        return CurrentDocPolicy.sourceFor(current, editorName);
    }

    void onSave(HostBridge target, long ticket, String kind, String name, String mime, ByteBuffer bytes) {
        if (!SavePlan.isKnownKind(kind)) {
            fail(target, ticket, "unknown save kind");
            return;
        }
        String fileName = FileNames.sanitize(name);
        if (pending != null) {
            fail(target, ticket, "another save is waiting for a target");
            return;
        }
        if (SavePlan.decide(kind, fileName, current) == SavePlan.Target.IN_PLACE) {
            if (!SavePlan.canWriteInPlace(bytes)) {
                fail(target, ticket, "refusing to overwrite the file with an invalid document");
                return;
            }
            writeInPlace(target, ticket, kind, fileName, mime, current, bytes);
            return;
        }
        pick(target, ticket, kind, fileName, mime, bytes);
    }

    private void pick(HostBridge target, long ticket, String kind, String fileName, String mime,
                      ByteBuffer bytes) {
        pending = new Pending(target, ticket, kind, bytes);
        host.createDocument(fileName, mime.isEmpty() ? MimeMap.of(fileName) : mime);
    }

    /** Result of the CreateDocument picker; {@code uri == null} means the user cancelled. */
    void onCreateResult(Uri uri) {
        Pending p = pending;
        pending = null;
        if (p == null) return;
        if (uri == null) {
            host.postIfCurrent(p.target, () -> p.target.sendSaveResult(p.ticket, "cancelled", "", null));
            return;
        }
        if (SavePlan.exportOverwritesCurrent(p.kind, uri.toString(), current)) {
            fail(p.target, p.ticket, "an export cannot replace the open document");
            return;
        }
        boolean retarget = SavePlan.retargetsCurrent(p.kind);
        int gen = generation;
        host.runIo(() -> {
            try {
                if (retarget) documents.persistAccess(uri);
                documents.write(uri, p.bytes);
                String saved = documents.displayName(uri);
                CurrentDoc doc = retarget ? new CurrentDoc(uri.toString(), saved, documents.canWrite(uri)) : null;
                host.postIfCurrent(p.target, () -> {
                    if (doc != null && gen == generation) setCurrent(doc);
                    succeed(p.target, p.ticket, p.kind, saved);
                });
            } catch (java.io.IOException | RuntimeException | OutOfMemoryError e) {
                failed(p.target, p.ticket, e);
            }
        });
    }

    /** The editor page went away: a target pick can no longer be answered. */
    void dropPending() {
        pending = null;
    }

    private void writeInPlace(HostBridge target, long ticket, String kind, String fileName, String mime,
                              CurrentDoc doc, ByteBuffer bytes) {
        Uri uri = Uri.parse(doc.uri);
        host.runIo(() -> {
            try {
                host.backup(doc, bytes);
                documents.write(uri, bytes);
                host.postIfCurrent(target, () -> succeed(target, ticket, kind, doc.name));
            } catch (java.io.IOException | RuntimeException | OutOfMemoryError e) {
                if (SavePlan.shouldRepick(e)) {
                    Log.w(TAG, "In-place save not possible, asking for a target", e);
                    host.postIfCurrent(target, () -> repick(target, ticket, kind, fileName, mime, doc, bytes));
                } else {
                    failed(target, ticket, e);
                }
            }
        });
    }

    /** The file turned out not to be writable: stop offering it and answer the same ticket via the picker. */
    private void repick(HostBridge target, long ticket, String kind, String fileName, String mime,
                        CurrentDoc doc, ByteBuffer bytes) {
        if (current == doc) setCurrent(new CurrentDoc(doc.uri, doc.name, false));
        if (pending != null) {
            fail(target, ticket, "another save is waiting for a target");
            return;
        }
        pick(target, ticket, kind, fileName, mime, bytes);
    }

    private void succeed(HostBridge target, long ticket, String kind, String name) {
        host.saved(kind);
        target.sendSaveResult(ticket, "ok", "", name);
        host.toast(R.string.saved_file, name);
    }

    private void failed(HostBridge target, long ticket, Throwable e) {
        Log.e(TAG, "Save failed", e);
        host.postIfCurrent(target, () -> fail(target, ticket, String.valueOf(e.getMessage())));
    }

    /** The bridge refused the message (malformed or oversized): still answer the ticket. */
    void reject(HostBridge target, long ticket, String reason) {
        if (target != null) fail(target, ticket, reason);
    }

    private void fail(HostBridge target, long ticket, String reason) {
        target.sendSaveResult(ticket, "failed", reason, null);
        host.toast(R.string.save_failed);
    }
}
