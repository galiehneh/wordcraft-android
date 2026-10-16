package com.galiehneh.wordcraft.web;

import android.net.Uri;
import android.util.Base64;
import android.util.Log;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.webkit.JavaScriptReplyProxy;
import androidx.webkit.WebMessageCompat;
import androidx.webkit.WebViewCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;

/** Receives {@code WordCraftHost} messages (docs/host-protocol.md) and posts events back to JS. */
public final class HostBridge implements WebViewCompat.WebMessageListener {
    private static final String TAG = "WordCraft";

    /** Parsed JS messages, delivered on the UI thread. {@code bytes} is never null (array-backed view). */
    public interface Listener {
        void onReady();

        void onState(HostState state);

        void onSave(long ticket, String kind, String name, String mime, ByteBuffer bytes);

        /** A {@code save} could not be processed; the ticket must be answered with failed. */
        void onSaveRejected(long ticket, String reason);

        void onSnapshot(String name, long rev, ByteBuffer docx);

        void onPrint(String name, ByteBuffer pdf);

        void onIme(boolean visible);

        void onPickOpen();

        void onOpenExternal(String url);

        void onBackResult(boolean handled);
    }

    private final Listener listener;
    private final ChunkAssembler chunks = new ChunkAssembler();
    private JavaScriptReplyProxy proxy;
    private String currentHeader;
    private volatile boolean detached;

    public HostBridge(Listener listener) {
        this.listener = listener;
    }

    @Override
    public void onPostMessage(@NonNull WebView view, @NonNull WebMessageCompat message,
                              @NonNull Uri sourceOrigin, boolean isMainFrame, @NonNull JavaScriptReplyProxy replyProxy) {
        if (!isMainFrame || detached) return;
        proxy = replyProxy;
        currentHeader = null;
        try {
            if (message.getType() == WebMessageCompat.TYPE_ARRAY_BUFFER) {
                HostProtocol.Frame f = HostProtocol.decode(message.getArrayBuffer());
                currentHeader = f.headerJson;
                dispatch(f.headerJson, f.payload);
            } else if (message.getType() == WebMessageCompat.TYPE_STRING) {
                handleText(message.getData());
            }
        } catch (JSONException | IllegalArgumentException | OutOfMemoryError e) {
            Log.w(TAG, "Dropping host message", e);
            if (e instanceof OutOfMemoryError) chunks.clear();
            rejectSave(currentHeader, String.valueOf(e.getMessage()));
        } finally {
            for (String dropped : chunks.takeDropped()) rejectSave(dropped, "incomplete chunks");
        }
    }

    /** Answers a {@code save} header with a failure when its ticket is readable; otherwise just logs. */
    private void rejectSave(String headerJson, String reason) {
        if (headerJson == null) return;
        try {
            JSONObject h = new JSONObject(headerJson);
            if ("save".equals(h.optString("type")) && h.has("ticket")) {
                listener.onSaveRejected(h.getLong("ticket"), reason);
                return;
            }
        } catch (JSONException ignored) {
            // Falls through to the log below.
        }
        Log.w(TAG, "Unanswerable malformed message: " + reason);
    }

    private void handleText(String text) throws JSONException {
        JSONObject json = new JSONObject(text);
        if (!"chunk".equals(json.optString("type"))) {
            currentHeader = text;
            dispatch(text, ByteBuffer.allocate(0));
            return;
        }
        String header = json.getJSONObject("header").toString();
        currentHeader = header;
        byte[] data = Base64.decode(json.getString("data"), Base64.DEFAULT);
        HostProtocol.Frame frame = chunks.add(json.getLong("id"), json.getInt("index"),
                json.getInt("chunks"), header, data);
        if (frame != null) dispatch(frame.headerJson, frame.payload);
    }

    private void dispatch(String headerJson, ByteBuffer bytes) throws JSONException {
        JSONObject h = new JSONObject(headerJson);
        switch (h.optString("type")) {
            case "ready":
                listener.onReady();
                break;
            case "state":
                listener.onState(new HostState(h.optBoolean("dirty"), h.optBoolean("hasDocument"),
                        h.optBoolean("editMode"), h.optBoolean("backstage"), h.optBoolean("dialog"),
                        h.optString("name", ""), h.optLong("rev", 0)));
                break;
            case "save":
                listener.onSave(h.getLong("ticket"), h.optString("kind", ""), h.optString("name", ""),
                        h.optString("mime", ""), bytes);
                break;
            case "snapshot":
                listener.onSnapshot(h.optString("name", ""), h.optLong("rev", 0), bytes);
                break;
            case "print":
                listener.onPrint(h.optString("name", ""), bytes);
                break;
            case "ime":
                listener.onIme(h.optBoolean("visible"));
                break;
            case "pickOpen":
                listener.onPickOpen();
                break;
            case "openExternal":
                listener.onOpenExternal(h.optString("url", ""));
                break;
            case "backResult":
                listener.onBackResult(h.optBoolean("handled"));
                break;
            default:
                Log.w(TAG, "Unknown host message: " + h.optString("type"));
        }
    }

    /** Stops delivering and sending: this bridge belongs to a WebView that is gone. */
    public void detach() {
        detached = true;
    }

    public void sendOpen(String url, String name, boolean recovered) {
        send("open", "url", url, "name", name, "recovered", recovered);
    }

    public void sendSaveResult(long ticket, String outcome, String message) {
        send("saveResult", "ticket", ticket, "outcome", outcome, "message", message);
    }

    public void sendBack() {
        send("back");
    }

    public void sendSnapshotNow() {
        send("snapshotNow");
    }

    /** Posts {@code {"type":type, k1:v1, ...}} to the page; dropped (logged) before the first message. */
    private void send(String type, Object... keyValues) {
        if (detached) return;
        if (proxy == null) {
            Log.w(TAG, "Host event dropped before ready: " + type);
            return;
        }
        try {
            JSONObject json = new JSONObject().put("type", type);
            for (int i = 0; i < keyValues.length; i += 2) {
                json.put((String) keyValues[i], keyValues[i + 1]);
            }
            proxy.postMessage(json.toString());
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }
}
