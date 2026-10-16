package com.galiehneh.wordcraft.web;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.view.View;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import com.galiehneh.wordcraft.BuildConfig;
import com.galiehneh.wordcraft.ui.FileChooser;

import java.util.Collections;

/** Locks down and configures a WebView that serves the editor from the app origin. */
public final class WebViewHost {
    public static final String BASE_URL = "https://" + NavPolicy.APP_HOST + "/index.html";
    public static final String DOC_URL_PREFIX = "/__host/doc/";
    public static final String JS_OBJECT_NAME = "WordCraftHost";
    private static final String ORIGIN = "https://" + NavPolicy.APP_HOST;

    /** Events the host activity must react to (UI thread). */
    public interface Callbacks {
        void onRenderGone();

        void onExternalUrl(Uri uri);
    }

    private final Callbacks callbacks;
    private final FileChooser fileChooser;
    private final WebViewAssetLoader loader;
    private final WordCraftSync sync;

    public WebViewHost(android.content.Context context, DocTokenStore tokens, Callbacks callbacks, FileChooser fileChooser) {
        this.callbacks = callbacks;
        this.fileChooser = fileChooser;
        this.sync = new WordCraftSync(context);
        this.loader = new WebViewAssetLoader.Builder()
                .setDomain(NavPolicy.APP_HOST)
                .addPathHandler(DOC_URL_PREFIX, new HostPathHandlers.Docs(tokens))
                .addPathHandler("/", new HostPathHandlers.Assets(context))
                .build();
    }

    /** @return false if this WebView cannot host the bridge (no WebMessageListener support). */
    @SuppressLint("SetJavaScriptEnabled")
    public boolean configure(WebView webView, HostBridge bridge) {
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setSupportMultipleWindows(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setGeolocationEnabled(false);
        disableForcedDarkening(settings);

        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.requestFocus(View.FOCUS_DOWN);

        webView.setWebViewClient(new AssetClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                return fileChooser.onShowFileChooser(callback, params);
            }
        });

        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            return false;
        }
        webView.addJavascriptInterface(sync, WordCraftSync.JS_OBJECT_NAME);
        WebViewCompat.addWebMessageListener(webView, JS_OBJECT_NAME, Collections.singleton(ORIGIN), bridge);
        return true;
    }

    private final class AssetClient extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if (!NavPolicy.isRequestAllowed(uri.getScheme(), uri.getHost())) {
                return HostPathHandlers.forbidden();
            }
            if (NavPolicy.isLocalScheme(uri.getScheme())) return null;
            WebResourceResponse response = loader.shouldInterceptRequest(uri);
            return response != null ? response : HostPathHandlers.notFound();
        }

        @Override
        public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
            Uri uri = url == null ? Uri.EMPTY : Uri.parse(url);
            sync.setTrusted(uri.getScheme(), uri.getHost());
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            switch (NavPolicy.classify(uri.getScheme(), uri.getHost())) {
                case ALLOW_IN_APP:
                    return false;
                case OPEN_EXTERNAL:
                    // Only a user-initiated navigation; openExternal from the editor uses the bridge.
                    if (request.hasGesture()) callbacks.onExternalUrl(uri);
                    return true;
                default:
                    return true;
            }
        }

        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            callbacks.onRenderGone();
            return true;
        }
    }

    /**
     * The editor has its own light/dark theme and follows {@code prefers-color-scheme} itself; the WebView must
     * not darken it a second time. Only the forced/algorithmic darkening is turned off, the media query is untouched.
     */
    @SuppressWarnings("deprecation")
    private static void disableForcedDarkening(WebSettings settings) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false);
        } else if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            WebSettingsCompat.setForceDark(settings, WebSettingsCompat.FORCE_DARK_OFF);
        }
    }
}
