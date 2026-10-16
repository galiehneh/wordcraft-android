package com.galiehneh.wordcraft;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.galiehneh.wordcraft.io.DocumentRepository;
import com.galiehneh.wordcraft.io.FileNames;
import com.galiehneh.wordcraft.io.MimeMap;
import com.galiehneh.wordcraft.io.RecoveryPolicy;
import com.galiehneh.wordcraft.io.RecoveryStore;
import com.galiehneh.wordcraft.web.DocSource;
import com.galiehneh.wordcraft.web.DocTokenStore;
import com.galiehneh.wordcraft.web.HostBridge;
import com.galiehneh.wordcraft.web.HostState;
import com.galiehneh.wordcraft.web.NavPolicy;
import com.galiehneh.wordcraft.web.WebViewHost;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Host-side logic behind the bridge: pending opens, recovery snapshots, confirmed saves, Back. */
final class HostController implements HostBridge.Listener {
    private static final String TAG = "WordCraft";
    private static final long BACK_TIMEOUT_MS = 4000;
    private static final long OPEN_TIMEOUT_MS = 15_000;

    /** Effects on the activity, always invoked on the UI thread. */
    interface Callbacks {
        void onStateChanged(HostState state);

        /** The editor does not consume Back: leave the app. */
        void exit(boolean dirty);

        void toast(int resId, Object... args);

        void openExternal(Uri uri);

        void pickOpen();
    }

    private final Callbacks callbacks;
    private final DocumentRepository documents;
    private final RecoveryStore recovery;
    private final DocTokenStore tokens = new DocTokenStore();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable backTimeout = this::onBackTimeout;
    private final Runnable openTimeout = this::onOpenTimeout;
    private final RecoveryPolicy policy = new RecoveryPolicy();

    private HostBridge bridge;
    private HostState state = HostState.EMPTY;
    private boolean ready;
    private boolean destroyed;
    private boolean launchOpenExpected;
    private boolean backPending;
    private PendingOpen pending;
    private String currentSource = "";
    private String openingSource = "";

    HostController(Context context, Callbacks callbacks) {
        this.callbacks = callbacks;
        this.documents = new DocumentRepository(context);
        this.recovery = new RecoveryStore(new java.io.File(context.getFilesDir(), "recovery"));
    }

    DocTokenStore tokens() {
        return tokens;
    }

    /** Binds a fresh bridge (new WebView): the page must send {@code ready} again. */
    HostBridge newBridge() {
        main.removeCallbacks(backTimeout);
        main.removeCallbacks(openTimeout);
        backPending = false;
        ready = false;
        state = HostState.EMPTY;
        if (bridge != null) bridge.detach();
        bridge = new HostBridge(this);
        callbacks.onStateChanged(state);
        return bridge;
    }

    void expectLaunchOpen() {
        launchOpenExpected = true;
    }

    void openUri(Uri uri) {
        runIo(() -> {
            try {
                String name = documents.displayName(uri);
                documents.probe(uri);
                DocSource source = () -> documents.openStream(uri);
                main.post(() -> queueOpen(new PendingOpen(source, name, uri.toString(), false)));
            } catch (IOException | SecurityException e) {
                Log.e(TAG, "Cannot open " + uri, e);
                main.post(() -> {
                    if (destroyed) return;
                    launchOpenExpected = false;
                    callbacks.toast(R.string.open_failed);
                    if (ready) restoreRecovery();
                });
            }
        });
    }

    private void runIo(Runnable task) {
        if (!destroyed) io.execute(task);
    }

    /** Runs on the UI thread only while the activity lives and {@code target} is still the bound bridge. */
    private void postIfCurrent(HostBridge target, Runnable task) {
        main.post(() -> {
            if (!destroyed && target == bridge) task.run();
        });
    }

    private void queueOpen(PendingOpen p) {
        if (destroyed) return;
        launchOpenExpected = false;
        pending = p;
        if (ready) flushOpen();
    }

    private void flushOpen() {
        PendingOpen p = pending;
        if (p == null || !policy.canSendOpen()) return;
        pending = null;
        // A dirty document is about to be replaced: persist it first (the editor also prompts).
        if (state.dirty) bridge.sendSnapshotNow();
        openingSource = p.sourceUri;
        policy.onOpenSent();
        main.removeCallbacks(openTimeout);
        main.postDelayed(openTimeout, OPEN_TIMEOUT_MS);
        bridge.sendOpen(WebViewHost.DOC_URL_PREFIX + tokens.issue(p.source), p.name, p.recovered);
    }

    private void onOpenTimeout() {
        policy.onOpenTimeout();
        currentSource = openingSource;
        flushOpen();
    }

    private void restoreRecovery() {
        if (!policy.tryBeginRestore(pending != null, launchOpenExpected)) return;
        runIo(() -> {
            RecoveryStore.Meta meta = recovery.readMeta();
            if (meta == null) {
                main.post(policy::onRestoreMissing);
                return;
            }
            DocSource source = () -> new FileInputStream(recovery.docFile());
            main.post(() -> {
                if (!destroyed && policy.acceptRestore(pending != null)) {
                    queueOpen(new PendingOpen(source, meta.name, meta.sourceUri, true));
                }
            });
        });
    }

    // Lifecycle -------------------------------------------------------------------------------

    /** Ask the editor for a snapshot; call before WebView.onPause(). */
    void snapshotNow() {
        if (ready && state.dirty) bridge.sendSnapshotNow();
    }

    void shutdown() {
        destroyed = true;
        if (bridge != null) bridge.detach();
        bridge = null;
        main.removeCallbacksAndMessages(null);
        io.shutdown();
    }

    // Back ------------------------------------------------------------------------------------

    void requestBack() {
        if (!ready || backPending) return;
        backPending = true;
        main.postDelayed(backTimeout, BACK_TIMEOUT_MS);
        bridge.sendBack();
    }

    /** No reply at all within the timeout: the page is unresponsive, leave the app. */
    private void onBackTimeout() {
        if (!backPending) return;
        backPending = false;
        callbacks.exit(state.dirty);
    }

    @Override
    public void onBackResult(boolean handled) {
        if (!backPending) return;
        backPending = false;
        main.removeCallbacks(backTimeout);
        if (!handled) callbacks.exit(state.dirty);
    }

    // Bridge listener ---------------------------------------------------------------------------

    @Override
    public void onReady() {
        ready = true;
        policy.onReady();
        if (pending != null) {
            flushOpen();
        } else {
            restoreRecovery();
        }
    }

    @Override
    public void onState(HostState s) {
        boolean wasOpening = policy.isOpening();
        state = s;
        if (policy.onState(s)) runIo(recovery::clear);
        if (wasOpening && !policy.isOpening()) {
            main.removeCallbacks(openTimeout);
            currentSource = openingSource;
            flushOpen();
        }
        callbacks.onStateChanged(s);
    }

    @Override
    public void onSnapshot(String name, long rev, ByteBuffer docx) {
        if (!state.dirty) return;
        RecoveryStore.Meta meta = new RecoveryStore.Meta(
                name.isEmpty() ? state.name : name, currentSource, rev);
        runIo(() -> {
            try {
                recovery.write(meta, docx);
            } catch (IOException | OutOfMemoryError e) {
                Log.e(TAG, "Recovery snapshot failed", e);
            }
        });
    }

    @Override
    public void onSave(long ticket, String kind, String name, String mime, ByteBuffer bytes) {
        String fileName = FileNames.sanitize(name);
        String type = mime.isEmpty() ? MimeMap.of(fileName) : mime;
        HostBridge target = bridge;
        runIo(() -> {
            try {
                documents.saveToDownloads(fileName, type, bytes);
                postIfCurrent(target, () -> {
                    policy.onSaveOk(kind);
                    target.sendSaveResult(ticket, "ok", fileName);
                    callbacks.toast(R.string.saved_to_downloads, fileName);
                });
            } catch (IOException | OutOfMemoryError e) {
                Log.e(TAG, "Save failed", e);
                postIfCurrent(target, () -> {
                    target.sendSaveResult(ticket, "failed", String.valueOf(e.getMessage()));
                    callbacks.toast(R.string.save_failed);
                });
            }
        });
    }

    @Override
    public void onSaveRejected(long ticket, String reason) {
        if (destroyed || bridge == null) return;
        bridge.sendSaveResult(ticket, "failed", reason);
        callbacks.toast(R.string.save_failed);
    }

    @Override
    public void onPrint(String name, ByteBuffer pdf) {
        Log.i(TAG, "print not implemented yet (" + pdf.remaining() + " bytes)");
    }

    @Override
    public void onIme(boolean visible) {
        // Handled in a later wave (P3.1).
    }

    @Override
    public void onPickOpen() {
        callbacks.pickOpen();
    }

    @Override
    public void onOpenExternal(String url) {
        Uri uri = Uri.parse(url);
        if (NavPolicy.canOpenExternally(uri.getScheme(), uri.getHost())) callbacks.openExternal(uri);
    }
}
