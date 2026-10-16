package com.galiehneh.wordcraft;

import android.net.Uri;
import android.util.Log;

import com.galiehneh.wordcraft.io.CurrentDoc;
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

        /** A save/saveAs/export was confirmed written; {@code retargeted} is the new open document, if any. */
        void saved(String kind, CurrentDoc retargeted);
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
    private Pending pending;

    SaveCoordinator(DocumentRepository documents, Host host) {
        this.documents = documents;
        this.host = host;
    }

    void setCurrent(CurrentDoc doc) {
        current = doc;
    }

    void onSave(HostBridge target, long ticket, String kind, String name, String mime, ByteBuffer bytes) {
        String fileName = FileNames.sanitize(name);
        if (pending != null) {
            fail(target, ticket, "another save is waiting for a target");
            return;
        }
        if (SavePlan.decide(kind, fileName, current) == SavePlan.Target.IN_PLACE) {
            writeInPlace(target, ticket, kind, current, bytes);
            return;
        }
        pending = new Pending(target, ticket, kind, bytes);
        host.createDocument(fileName, mime.isEmpty() ? MimeMap.of(fileName) : mime);
    }

    /** Result of the CreateDocument picker; {@code uri == null} means the user cancelled. */
    void onCreateResult(Uri uri) {
        Pending p = pending;
        pending = null;
        if (p == null) return;
        if (uri == null) {
            host.postIfCurrent(p.target, () -> p.target.sendSaveResult(p.ticket, "cancelled", ""));
            return;
        }
        boolean retarget = SavePlan.retargetsCurrent(p.kind);
        host.runIo(() -> {
            try {
                if (retarget) documents.persistAccess(uri);
                documents.write(uri, p.bytes);
                String saved = documents.displayName(uri);
                CurrentDoc doc = retarget ? new CurrentDoc(uri.toString(), saved, documents.canWrite(uri)) : null;
                host.postIfCurrent(p.target, () -> {
                    if (doc != null) current = doc;
                    succeed(p.target, p.ticket, p.kind, saved, doc);
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

    private void writeInPlace(HostBridge target, long ticket, String kind, CurrentDoc doc, ByteBuffer bytes) {
        Uri uri = Uri.parse(doc.uri);
        host.runIo(() -> {
            try {
                documents.write(uri, bytes);
                host.postIfCurrent(target, () -> succeed(target, ticket, kind, doc.name, doc));
            } catch (java.io.IOException | RuntimeException | OutOfMemoryError e) {
                failed(target, ticket, e);
            }
        });
    }

    private void succeed(HostBridge target, long ticket, String kind, String name, CurrentDoc doc) {
        host.saved(kind, doc);
        target.sendSaveResult(ticket, "ok", name);
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
        target.sendSaveResult(ticket, "failed", reason);
        host.toast(R.string.save_failed);
    }
}
