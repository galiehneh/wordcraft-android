package com.galiehneh.wordcraft.web;

import android.content.Context;
import android.webkit.WebResourceResponse;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.webkit.WebViewAssetLoader;

import com.galiehneh.wordcraft.io.MimeMap;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

/** PathHandlers for the single app origin. Both run on WebView IO threads, never the main thread. */
final class HostPathHandlers {
    private HostPathHandlers() {}

    static WebResourceResponse notFound() {
        return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", null, null);
    }

    static WebResourceResponse forbidden() {
        return new WebResourceResponse("text/plain", "UTF-8", 403, "Forbidden", null, null);
    }

    /** Serves the bundled editor from {@code assets/web/}. */
    static final class Assets implements WebViewAssetLoader.PathHandler {
        private final WebViewAssetLoader.AssetsPathHandler delegate;

        Assets(Context context) {
            this.delegate = new WebViewAssetLoader.AssetsPathHandler(context);
        }

        @Nullable
        @Override
        public WebResourceResponse handle(@NonNull String path) {
            String rel = path.isEmpty() ? "index.html" : path;
            if (rel.contains("..")) return notFound();
            WebResourceResponse raw = delegate.handle("web/" + rel);
            if (raw == null) return notFound();
            String mime = MimeMap.of(rel);
            boolean text = mime.startsWith("text/") || mime.equals("application/json");
            return new WebResourceResponse(mime, text ? "UTF-8" : null, raw.getData());
        }
    }

    /** Streams a document by one-time token: {@code /__host/doc/<token>}. */
    static final class Docs implements WebViewAssetLoader.PathHandler {
        private final DocTokenStore tokens;

        Docs(DocTokenStore tokens) {
            this.tokens = tokens;
        }

        @Nullable
        @Override
        public WebResourceResponse handle(@NonNull String path) {
            DocSource source = tokens.consume(path);
            if (source == null) return notFound();
            try {
                InputStream in = source.open();
                return new WebResourceResponse("application/octet-stream", null, 200, "OK",
                        Collections.singletonMap("Cache-Control", "no-store"), in);
            } catch (IOException | SecurityException e) {
                return notFound();
            }
        }
    }
}
