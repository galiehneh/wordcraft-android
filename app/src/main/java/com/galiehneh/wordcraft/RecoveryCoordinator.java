package com.galiehneh.wordcraft;

import com.galiehneh.wordcraft.io.CurrentDoc;
import com.galiehneh.wordcraft.io.DocumentRepository;
import com.galiehneh.wordcraft.io.RecoveryStore;
import com.galiehneh.wordcraft.web.DocSource;

import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Recovery snapshots on their own thread, so a stuck document write never delays them. */
final class RecoveryCoordinator {
    /** A snapshot ready to be reopened. */
    static final class Restore {
        final RecoveryStore.Meta meta;
        final DocSource source;
        /** Save target, or null when the snapshot can no longer be written back to its file. */
        final CurrentDoc target;

        Restore(RecoveryStore.Meta meta, DocSource source, CurrentDoc target) {
            this.meta = meta;
            this.source = source;
            this.target = target;
        }
    }

    /** Called off the main thread; {@code restore} is null when there is nothing to restore. */
    interface RestoreSink {
        void onRead(Restore restore);
    }

    private final RecoveryStore store;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    RecoveryCoordinator(RecoveryStore store) {
        this.store = store;
    }

    void snapshot(RecoveryStore.Meta meta, ByteBuffer docx) {
        execute(() -> store.writeQuietly(meta, docx));
    }

    void clear() {
        execute(store::clear);
    }

    /** Synchronous (caller is on an IO thread): the bytes about to overwrite a file in place. */
    void backup(CurrentDoc doc, ByteBuffer docx) {
        store.writeQuietly(new RecoveryStore.Meta(doc.name, doc.uri, 0), docx);
    }

    void read(Executor documentIo, DocumentRepository documents, RestoreSink sink) {
        execute(() -> {
            RecoveryStore.Meta meta = store.readMeta();
            if (meta == null) {
                sink.onRead(null);
                return;
            }
            DocSource source = () -> new FileInputStream(store.docFile());
            // Provider queries (display name, grants) may block: keep them off the snapshot thread.
            documentIo.execute(() -> sink.onRead(
                    new Restore(meta, source, documents.restoredTarget(meta.sourceUri, meta.name))));
        });
    }

    void shutdown() {
        io.shutdown();
    }

    private void execute(Runnable task) {
        try {
            io.execute(task);
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // Shut down with the activity.
        }
    }
}
