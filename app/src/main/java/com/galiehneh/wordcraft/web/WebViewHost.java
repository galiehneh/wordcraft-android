package com.galiehneh.wordcraft.web;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.galiehneh.wordcraft.io.DocumentRepository;
import com.galiehneh.wordcraft.io.MimeMap;
import com.galiehneh.wordcraft.ui.FileChooser;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/** Configures the WebView and serves bundled assets from the app origin. */
public final class WebViewHost {
    private static final String TAG = "WordCraft";
    public static final String BASE_URL = "https://appassets.androidplatform.net/index.html";
    public static final String JS_BRIDGE_NAME = "WordCraftAndroid";

    private final Context context;
    private final WebView webView;
    private final FileChooser fileChooser;

    public WebViewHost(Context context, WebView webView, FileChooser fileChooser) {
        this.context = context;
        this.webView = webView;
        this.fileChooser = fileChooser;
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    public void configure(HostBridge bridge) {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.requestFocus(View.FOCUS_DOWN);
        webView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int action = event.getAction();
                if ((action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_UP) && !v.hasFocus()) {
                    v.requestFocus();
                }
                return false;
            }
        });

        webView.addJavascriptInterface(bridge, JS_BRIDGE_NAME);
        webView.setWebViewClient(new AssetClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                return fileChooser.onShowFileChooser(callback, params);
            }
        });
    }

    private final class AssetClient extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            if (request == null || request.getUrl() == null) return null;
            Uri uri = request.getUrl();
            String host = uri.getHost();
            if (!"appassets.androidplatform.net".equalsIgnoreCase(host) && !"localhost".equalsIgnoreCase(host)) {
                return super.shouldInterceptRequest(view, request);
            }
            String path = uri.getPath();
            if (path == null || path.isEmpty() || path.equals("/")) {
                path = "/index.html";
            }

            if (path.startsWith("/doc/") || path.contains("current_document")) {
                File cachedFile = new File(context.getCacheDir(), DocumentRepository.CURRENT_DOCUMENT_FILE);
                if (cachedFile.exists()) {
                    try {
                        return response(MimeMap.of(path), new FileInputStream(cachedFile));
                    } catch (IOException e) {
                        Log.e(TAG, "Cannot stream cached document", e);
                    }
                }
            }

            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            try {
                return response(MimeMap.of(path), context.getAssets().open("web/" + path));
            } catch (IOException e) {
                Log.w(TAG, "Asset not found: web/" + path);
                return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", null, null);
            }
        }

        private WebResourceResponse response(String mime, InputStream stream) {
            Map<String, String> headers = new HashMap<>();
            headers.put("Access-Control-Allow-Origin", "*");
            headers.put("Cache-Control", "no-cache");
            return new WebResourceResponse(mime, "UTF-8", 200, "OK", headers, stream);
        }
    }
}
