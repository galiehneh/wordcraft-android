package com.galiehneh.wordcraft;

import android.os.Handler;

/** Tracks one in-flight Back request to the editor and its no-reply timeout. Main thread only. */
final class BackGate {
    private static final long TIMEOUT_MS = 4000;

    /** What to do when Back is not consumed by the editor. */
    interface OnLeave {
        void leave();
    }

    private final Handler main;
    private final OnLeave onLeave;
    private final Runnable timeout = this::onTimeout;
    private boolean pending;

    BackGate(Handler main, OnLeave onLeave) {
        this.main = main;
        this.onLeave = onLeave;
    }

    /** @return true if the request should be sent to the editor now. */
    boolean begin() {
        if (pending) return false;
        pending = true;
        main.postDelayed(timeout, TIMEOUT_MS);
        return true;
    }

    void reset() {
        pending = false;
        main.removeCallbacks(timeout);
    }

    void onResult(boolean handled) {
        if (!pending) return;
        reset();
        if (!handled) onLeave.leave();
    }

    /** No reply at all within the timeout: the page is unresponsive, leave. */
    private void onTimeout() {
        if (!pending) return;
        pending = false;
        onLeave.leave();
    }
}
