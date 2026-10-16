package com.galiehneh.wordcraft;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.galiehneh.wordcraft.io.CurrentDoc;
import com.galiehneh.wordcraft.io.DocumentRepository;
import com.galiehneh.wordcraft.io.RecoveryPolicy;
import com.galiehneh.wordcraft.io.SavePlan;
import com.galiehneh.wordcraft.io.RecoveryStore;
import com.galiehneh.wordcraft.io.UnsupportedFileException;
import com.galiehneh.wordcraft.web.DocSource;
import com.galiehneh.wordcraft.web.DocTokenStore;
import com.galiehneh.wordcraft.web.HostBridge;
import com.galiehneh.wordcraft.web.HostState;
import com.galiehneh.wordcraft.web.NavPolicy;
import com.galiehneh.wordcraft.web.WebViewHost;

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

        void onEditorReady();
        /** The editor does not consume Back: leave the app. */
        void exit(boolean dirty);

        void toast(int resId, Object... args);

        void openExternal(Uri uri);
        void pickOpen();
        void ime(boolean visible);
        /** Shows the SAF "create document" picker; the answer comes back via onCreateResult. */
        void createDocument(String suggestedName, String mime);
        void print(String name, ByteBuffer pdf);
    }

    private final Context context;
    private final Callbacks callbacks;
    private final DocumentRepository documents;
    private final RecoveryCoordinator recovery;
    private final DocTokenStore tokens = new DocTokenStore();
    /** Save, open and probe work; recovery snapshots have their own thread. */
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable openTimeout = this::onOpenTimeout;
    private final RecoveryPolicy policy = new RecoveryPolicy();
    private final SaveCoordinator saves;
    private final BackGate back;

    private HostBridge bridge;
    private HostState state = HostState.EMPTY;
    private boolean ready, destroyed, launchOpenExpected;
    private PendingOpen pending;
    private CurrentDoc openingDoc;

    HostController(Context context, Callbacks callbacks) {
        this.context = context.getApplicationContext();
        this.callbacks = callbacks;
        this.documents = new DocumentRepository(context);
        this.recovery = new RecoveryCoordinator(
                new RecoveryStore(new java.io.File(context.getFilesDir(), "recovery")));
        this.saves = new SaveCoordinator(documents, this);
        this.back = new BackGate(main, () -> callbacks.exit(state.dirty));
    }

    DocTokenStore tokens() { return tokens; }

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

    void expectLaunchOpen() { launchOpenExpected = true; }

    /** Opens a document chosen in the SAF picker: keeps its grant across restarts. */
    void onPicked(Uri uri) {
        openUri(uri, true, false);
    }

    /** @param sniff validate extension and content first (generic MIME type from an external app) */
    void openUri(Uri uri, boolean persist, boolean sniff) {
        runIo(() -> {
            try {
                CurrentDoc doc = documents.describeForOpen(uri, persist, sniff);
                DocSource source = () -> documents.openStream(uri);
                main.post(() -> queueOpen(new PendingOpen(source, doc.name, false, doc)));
            } catch (IOException | RuntimeException e) {
                Log.e(TAG, "Cannot open " + uri, e);
                main.post(() -> {
                    if (destroyed) return;
                    launchOpenExpected = false;
                    callbacks.toast(e instanceof UnsupportedFileException
                            ? R.string.unsupported_file : R.string.open_failed);
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
        openingDoc = p.doc;
        policy.onOpenSent();
        main.removeCallbacks(openTimeout);
        main.postDelayed(openTimeout, OPEN_TIMEOUT_MS);
        bridge.sendOpen(WebViewHost.DOC_URL_PREFIX + tokens.issue(p.source), p.name, p.recovered);
    }

    /** The editor never confirmed the open: it may not show that file, so it is not a save target. */
    private void onOpenTimeout() {
        policy.onOpenTimeout();
        saves.setCurrent(null);
        flushOpen();
    }

    private void restoreRecovery() {
        if (!policy.tryBeginRestore(pending != null, launchOpenExpected)) return;
        recovery.read(this::runIo, documents, restore -> main.post(() -> {
            if (restore == null) {
                policy.onRestoreMissing();
            } else if (!destroyed && policy.acceptRestore(pending != null)) {
                queueOpen(new PendingOpen(restore.source, restore.meta.name, true, restore.target));
            }
        }));
    }

    // Lifecycle -------------------------------------------------------------------------------

    /** Ask the editor for a snapshot; call before WebView.onPause(). */
    void snapshotNow() {
        if (ready && state.dirty) bridge.sendSnapshotNow();
    }

    void shutdown() {
        destroyed = true;
        recovery.shutdown();
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
        callbacks.onEditorReady();
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
        if (policy.onState(s)) recovery.clear();
        if (wasOpening && !policy.isOpening()) {
            main.removeCallbacks(openTimeout);
            // Only a confirmed open (the editor reports a document) makes the file a save target.
            saves.setCurrent(s.hasDocument ? openingDoc : null);
            flushOpen();
        }
        if (!policy.isOpening()) saves.onEditorState(s.hasDocument ? s.name : null);
        callbacks.onStateChanged(s);
    }

    @Override
    public void onSnapshot(String name, long rev, ByteBuffer docx) {
        if (!state.dirty) return;
        String docName = name.isEmpty() ? state.name : name;
        RecoveryStore.Meta meta = new RecoveryStore.Meta(
                docName, saves.sourceUriFor(docName), rev);
        recovery.snapshot(meta, docx);
    }

    @Override
    public void onSave(long ticket, String kind, String name, String mime, ByteBuffer bytes) {
        saves.onSave(bridge, ticket, kind, name, mime, bytes);
    }

    void onCreateResult(Uri uri) { saves.onCreateResult(uri); }

    // SaveCoordinator.Host --------------------------------------------------------------------

    @Override
    public void toast(int resId, Object... args) {
        callbacks.toast(resId, args);
    }

    @Override
    public String text(int resId) { return context.getString(resId); }

    @Override
    public void createDocument(String suggestedName, String mime) {
        callbacks.createDocument(suggestedName, mime);
    }

    @Override
    public void saved(String kind) {
        policy.onSaveOk(kind);
        // The in-place backup is obsolete now; a still-dirty editor clears it on its clean transition.
        if (SavePlan.retargetsCurrent(kind) && !state.dirty && !policy.isOpening()) recovery.clear();
    }

    @Override
    public void backup(CurrentDoc doc, ByteBuffer bytes) {
        recovery.backup(doc, bytes);
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
        if (!destroyed) callbacks.ime(visible);
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
