package com.galiehneh.wordcraft.web;

import android.app.Activity;
import android.content.Context;
import android.util.Base64;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import com.galiehneh.wordcraft.io.DocumentRepository;

/** Legacy JS bridge exposed as {@code WordCraftAndroid}. */
public final class HostBridge {
    private static final String TAG = "WordCraft";

    /** Notifications to the host activity (invoked on the UI thread). */
    public interface Listener {
        void onDocumentOpened(boolean opened);
    }

    private final Activity activity;
    private final WebView webView;
    private final DocumentRepository documents;
    private final Listener listener;

    public HostBridge(Activity activity, WebView webView, DocumentRepository documents, Listener listener) {
        this.activity = activity;
        this.webView = webView;
        this.documents = documents;
        this.listener = listener;
    }

    @JavascriptInterface
    public void showKeyboard() {
        activity.runOnUiThread(() -> {
            webView.requestFocus();
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                boolean ok = imm.showSoftInput(webView, InputMethodManager.SHOW_IMPLICIT);
                if (!ok) {
                    ok = imm.showSoftInput(webView, 0);
                }
                if (!ok) {
                    imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
                }
            }
        });
    }

    @JavascriptInterface
    public void hideKeyboard() {
        activity.runOnUiThread(() -> {
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(webView.getWindowToken(), 0);
            }
        });
    }

    @JavascriptInterface
    public void setDocumentOpened(boolean opened) {
        activity.runOnUiThread(() -> listener.onDocumentOpened(opened));
    }

    @JavascriptInterface
    public void saveBlob(String dataUrl, String filename) {
        if (dataUrl == null || !dataUrl.contains(",")) return;
        String base64Data = dataUrl.substring(dataUrl.indexOf(",") + 1);
        try {
            byte[] bytes = Base64.decode(base64Data, Base64.DEFAULT);
            String safeName = (filename != null && !filename.isEmpty()) ? filename : "document.docx";
            boolean saved = documents.saveToDownloads(safeName, bytes);
            activity.runOnUiThread(() -> {
                if (saved) {
                    activity.getWindow().getDecorView().performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    Toast.makeText(activity, "Berhasil disimpan ke Download: " + safeName, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(activity, "Gagal menyimpan berkas", Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed saving blob", e);
        }
    }
}
