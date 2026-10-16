package com.galiehneh.wordcraft;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.galiehneh.wordcraft.io.DocumentRepository;
import com.galiehneh.wordcraft.io.FileNames;
import com.galiehneh.wordcraft.io.MimeMap;
import com.galiehneh.wordcraft.io.RecoveryStore;
import com.galiehneh.wordcraft.web.DocSource;
import com.galiehneh.wordcraft.web.DocTokenStore;
import com.galiehneh.wordcraft.web.HostBridge;
import com.galiehneh.wordcraft.web.HostState;
import com.galiehneh.wordcraft.web.NavPolicy;
import com.galiehneh.wordcraft.web.WebViewHost;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Host-side logic behind the bridge: pending opens, recovery snapshots, confirmed saves, Back. */
final class HostController implements HostBridge.Listener {
    private static final String TAG = "WordCraft";
    private static final long BACK_TIMEOUT_MS = 1500;

    /** Effects on the activity, always invoked on the UI thread. */
    interface Callbacks {
        void onStateChanged(HostState state);

        /** The editor does not consume Back: leave the app. */
        void exit(boolean dirty);

        void toast(int resId, Object... args);

        void openExternal(Uri uri);

        void pickOpen();
    }

    private static final class Pending {
        final DocSource source;
        final String name;
        final String sourceUri;
        final boolean recovered;

        Pending(DocSource source, String name, String sourceUri, boolean recovered) {
            this.source = source;
            this.name = name;
            this.sourceUri = sourceUri;
            this.recovered = recovered;
        }
    }

    private final Callbacks callbacks;
    private final DocumentRepository documents;
    private final RecoveryStore recovery;
    private final DocTokenStore tokens = new DocTokenStore();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable backTimeout = this::onBackTimeout;

    private HostBridge bridge;
    private HostState state = HostState.EMPTY;
    private boolean ready;
    private boolean launchOpenExpected;
    private boolean guardClear;
    private boolean recoveryMaybeStale = true;
    private boolean backPending;
    private Pending pending;
    private String currentSource = "";

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
        backPending = false;
        ready = false;
        state = HostState.EMPTY;
        bridge = new HostBridge(this);
        callbacks.onStateChanged(state);
        return bridge;
    }

    void expectLaunchOpen() {
        launchOpenExpected = true;
    }

    void openUri(Uri uri) {
        io.execute(() -> {
            try {
                String name = documents.displayName(uri);
                documents.probe(uri);
                DocSource source = () -> documents.openStream(uri);
                main.post(() -> queueOpen(new Pending(source, name, uri.toString(), false)));
            } catch (IOException | SecurityException e) {
                Log.e(TAG, "Cannot open " + uri, e);
                main.post(() -> {
                    launchOpenExpected = false;
                    callbacks.toast(R.string.open_failed);
                });
            }
        });
    }

    private void queueOpen(Pending p) {
        launchOpenExpected = false;
        pending = p;
        if (ready) flushOpen();
    }

    private void flushOpen() {
        Pending p = pending;
        if (p == null) return;
        pending = null;
        currentSource = p.sourceUri;
        guardClear = true;
        bridge.sendOpen(WebViewHost.DOC_URL_PREFIX + tokens.issue(p.source), p.name, p.recovered);
    }

    private void restoreRecovery() {
        io.execute(() -> {
            RecoveryStore.Meta meta = recovery.readMeta();
            if (meta == null) return;
            DocSource source = () -> new FileInputStream(recovery.docFile());
            main.post(() -> {
                if (pending == null && !state.hasDocument) {
                    queueOpen(new Pending(source, meta.name, meta.sourceUri, true));
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

    private void onBackTimeout() {
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
        if (pending != null) {
            flushOpen();
        } else if (!launchOpenExpected) {
            restoreRecovery();
        }
    }

    @Override
    public void onState(HostState s) {
        state = s;
        if (s.hasDocument) guardClear = false;
        if (!s.dirty && !guardClear && recoveryMaybeStale) {
            recoveryMaybeStale = false;
            io.execute(recovery::clear);
        }
        callbacks.onStateChanged(s);
    }

    @Override
    public void onSnapshot(String name, long rev, byte[] docx) {
        if (!state.dirty) return;
        recoveryMaybeStale = true;
        RecoveryStore.Meta meta = new RecoveryStore.Meta(
                name.isEmpty() ? state.name : name, currentSource, rev);
        io.execute(() -> {
            try {
                recovery.write(meta, docx);
            } catch (IOException | OutOfMemoryError e) {
                Log.e(TAG, "Recovery snapshot failed", e);
            }
        });
    }

    @Override
    public void onSave(long ticket, String kind, String name, String mime, byte[] bytes) {
        String fileName = FileNames.sanitize(name);
        String type = mime.isEmpty() ? MimeMap.of(fileName) : mime;
        HostBridge target = bridge;
        io.execute(() -> {
            try {
                documents.saveToDownloads(fileName, type, bytes);
                main.post(() -> {
                    target.sendSaveResult(ticket, "ok", fileName);
                    callbacks.toast(R.string.saved_to_downloads, fileName);
                });
            } catch (IOException | OutOfMemoryError e) {
                Log.e(TAG, "Save failed", e);
                main.post(() -> {
                    target.sendSaveResult(ticket, "failed", String.valueOf(e.getMessage()));
                    callbacks.toast(R.string.save_failed);
                });
            }
        });
    }

    @Override
    public void onPrint(String name, byte[] pdf) {
        Log.i(TAG, "print not implemented yet (" + pdf.length + " bytes)");
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
