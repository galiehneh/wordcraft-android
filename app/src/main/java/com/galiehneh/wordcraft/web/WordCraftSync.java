package com.galiehneh.wordcraft.web;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.webkit.JavascriptInterface;

/**
 * Synchronous clipboard reads for the editor's paste (the async Clipboard API is not usable
 * from a WebView). This is the ONLY object added with addJavascriptInterface.
 *
 * addJavascriptInterface exposes the object to every frame of the page, and cannot filter by
 * origin. Two guards: (1) WebViewHost refuses every request outside the app origin, so no
 * foreign frame can load; (2) the page's main-frame origin is checked on each onPageStarted and
 * the getters return "" while it is not the app origin (see {@link #setTrusted}).
 * Calls run on a WebView binder thread, not the UI thread.
 */
public final class WordCraftSync {
    public static final String JS_OBJECT_NAME = "WordCraftSync";

    private final ClipboardManager clipboard;
    private volatile boolean trusted;

    public WordCraftSync(Context context) {
        this.clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
    }

    /** Called from onPageStarted with the new main-frame URL parts. */
    public void setTrusted(String scheme, String host) {
        trusted = NavPolicy.classify(scheme, host) == NavPolicy.Action.ALLOW_IN_APP;
    }

    @JavascriptInterface
    public String getClipboardText() {
        ClipData.Item item = firstItem(ClipDescription.MIMETYPE_TEXT_PLAIN, ClipDescription.MIMETYPE_TEXT_HTML);
        try {
            return item == null ? "" : ClipboardPolicy.limit(item.getText());
        } catch (RuntimeException e) {
            return "";
        }
    }

    @JavascriptInterface
    public String getClipboardHtml() {
        ClipData.Item item = firstItem(ClipDescription.MIMETYPE_TEXT_HTML);
        try {
            return item == null ? "" : ClipboardPolicy.limit(item.getHtmlText());
        } catch (RuntimeException e) {
            return "";
        }
    }

    private ClipData.Item firstItem(String... mimes) {
        if (!trusted || clipboard == null) return null;
        try {
            ClipDescription description = clipboard.getPrimaryClipDescription();
            ClipData clip = clipboard.getPrimaryClip();
            if (description == null || clip == null || clip.getItemCount() == 0) return null;
            for (String mime : mimes) {
                if (description.hasMimeType(mime)) return clip.getItemAt(0);
            }
        } catch (RuntimeException e) {
            // SecurityException when the app may not read the clipboard (background, restricted).
        }
        return null;
    }
}
