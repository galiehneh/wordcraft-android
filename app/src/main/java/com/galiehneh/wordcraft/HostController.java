package com.galiehneh.wordcraft;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.galiehneh.wordcraft.io.CurrentDoc;
import com.galiehneh.wordcraft.io.DocumentRepository;
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
final class HostController implements HostBridge.Listener, SaveCoordinator.Host {
    private static final String TAG = "WordCraft";
    private static final long OPEN_TIMEOUT_MS = 15_000;

    /** Effects on the activity, always invoked on the UI thread. */
    interface Callbacks {
        void onStateChanged(HostState state);

        /** The editor does not consume Back: leave the app. */
        void exit(boolean dirty);

        void toast(int resId, Object... args);

        void openExternal(Uri uri);

        void pickOpen();

        /** Shows the SAF "create document" picker; the answer comes back via onCreateResult. */
        void createDocument(String suggestedName, String mime);

        void print(String name, ByteBuffer pdf);
    }

    private final Callbacks callbacks;
    private final DocumentRepository documents;
    private final RecoveryStore recovery;
    private final DocTokenStore tokens = new DocTokenStore();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable openTimeout = this::onOpenTimeout;
    private final RecoveryPolicy policy = new RecoveryPolicy();
    private final SaveCoordinator saves;
    private final BackGate back;

    private HostBridge bridge;
    private HostState state = HostState.EMPTY;
    private boolean ready;
    private boolean destroyed;
    private boolean launchOpenExpected;
    private PendingOpen pending;
    private String currentSource = "";
    private String openingSource = "";
    private CurrentDoc openingDoc;

    HostController(Context context, Callbacks callbacks) {
        this.callbacks = callbacks;
        this.documents = new DocumentRepository(context);
        this.recovery = new RecoveryStore(new java.io.File(context.getFilesDir(), "recovery"));
        this.saves = new SaveCoordinator(documents, this);
        this.back = new BackGate(main, () -> callbacks.exit(state.dirty));
    }

    DocTokenStore tokens() {
        return tokens;
    }

    /** Binds a fresh bridge (new WebView): the page must send {@code ready} again. */
    HostBridge newBridge() {
        main.removeCallbacks(openTimeout);
        back.reset();
        saves.dropPending();
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

    /** Opens a document chosen in the SAF picker: keeps its grant across restarts. */
    void onPicked(Uri uri) {
        openUri(uri, true);
    }

    void openUri(Uri uri, boolean persist) {
        runIo(() -> {
            try {
                CurrentDoc doc = documents.describeForOpen(uri, persist);
                DocSource source = () -> documents.openStream(uri);
                main.post(() -> queueOpen(new PendingOpen(source, doc.name, doc.uri, false, doc)));
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

    @Override
    public void runIo(Runnable task) {
        if (!destroyed) io.execute(task);
    }

    /** Runs on the UI thread only while the activity lives and {@code target} is still the bound bridge. */
    @Override
    public void postIfCurrent(HostBridge target, Runnable task) {
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
        openingDoc = p.doc;
        policy.onOpenSent();
        main.removeCallbacks(openTimeout);
        main.postDelayed(openTimeout, OPEN_TIMEOUT_MS);
        bridge.sendOpen(WebViewHost.DOC_URL_PREFIX + tokens.issue(p.source), p.name, p.recovered);
    }

    private void onOpenTimeout() {
        policy.onOpenTimeout();
        currentSource = openingSource;
        saves.setCurrent(openingDoc);
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
            CurrentDoc doc = documents.restoredTarget(meta.sourceUri, meta.name);
            main.post(() -> {
                if (!destroyed && policy.acceptRestore(pending != null)) {
                    queueOpen(new PendingOpen(source, meta.name, meta.sourceUri, true, doc));
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
        if (ready && back.begin()) bridge.sendBack();
    }

    @Override
    public void onBackResult(boolean handled) {
        back.onResult(handled);
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
            saves.setCurrent(openingDoc);
            flushOpen();
        }
        if (!s.hasDocument && !policy.isOpening()) saves.setCurrent(null);
        callbacks.onStateChanged(s);
    }

    @Override
    public void onSnapshot(String name, long rev, ByteBuffer docx) {
        if (!state.dirty) return;
        RecoveryStore.Meta meta = new RecoveryStore.Meta(
                name.isEmpty() ? state.name : name, currentSource, rev);
        runIo(() -> recovery.writeQuietly(meta, docx));
    }

    @Override
    public void onSave(long ticket, String kind, String name, String mime, ByteBuffer bytes) {
        saves.onSave(bridge, ticket, kind, name, mime, bytes);
    }

    void onCreateResult(Uri uri) {
        saves.onCreateResult(uri);
    }

    // SaveCoordinator.Host --------------------------------------------------------------------

    @Override
    public void toast(int resId, Object... args) {
        callbacks.toast(resId, args);
    }

    @Override
    public void createDocument(String suggestedName, String mime) {
        callbacks.createDocument(suggestedName, mime);
    }

    @Override
    public void saved(String kind, CurrentDoc retargeted) {
        policy.onSaveOk(kind);
        if (retargeted != null) currentSource = retargeted.uri;
    }

    @Override
    public void onSaveRejected(long ticket, String reason) {
        if (!destroyed) saves.reject(bridge, ticket, reason);
    }

    @Override
    public void onPrint(String name, ByteBuffer pdf) {
        if (!destroyed) callbacks.print(name, pdf);
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
