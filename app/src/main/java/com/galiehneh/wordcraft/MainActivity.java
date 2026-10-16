package com.galiehneh.wordcraft;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;

import com.galiehneh.wordcraft.print.PdfPrinter;
import com.galiehneh.wordcraft.ui.DocumentPickers;
import com.galiehneh.wordcraft.ui.FileChooser;
import com.galiehneh.wordcraft.ui.InsetsController;
import com.galiehneh.wordcraft.web.CrashLimiter;
import com.galiehneh.wordcraft.web.HostState;
import com.galiehneh.wordcraft.web.WebViewHost;

/** Single-activity host: wires the WebView, document intents, Back and renderer recovery. */
public class MainActivity extends ComponentActivity implements HostController.Callbacks, WebViewHost.Callbacks {
    private static final int MAX_CRASHES = 2;
    private static final long CRASH_WINDOW_MS = 60_000;

    private FrameLayout root;
    private WebView webView;
    private HostController controller;
    private WebViewHost webViewHost;
    private FileChooser fileChooser;
    private DocumentPickers pickers;
    private PdfPrinter printer;
    private OnBackPressedCallback backCallback;
    private final CrashLimiter crashLimiter = new CrashLimiter(MAX_CRASHES, CRASH_WINDOW_MS);
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        root = new FrameLayout(this);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        InsetsController.apply(getWindow(), root);
        setContentView(root);

        controller = new HostController(this, this);
        printer = new PdfPrinter(this);
        fileChooser = new FileChooser(this);
        pickers = new DocumentPickers(this, new DocumentPickers.Listener() {
            @Override
            public void onOpenPicked(Uri uri) {
                controller.onPicked(uri);
            }

            @Override
            public void onCreateResult(Uri uri) {
                controller.onCreateResult(uri);
            }
        });
        webViewHost = new WebViewHost(this, controller.tokens(), this, fileChooser);

        backCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                controller.requestBack();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, backCallback);

        Intent intent = getIntent();
        if (intent != null && IntentRouter.shouldHandleLaunch(intent.getAction(), savedInstanceState != null)) {
            openFromIntent(intent, true);
        }
        createWebView();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && IntentRouter.isDocumentAction(intent.getAction())) {
            openFromIntent(intent, false);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        InsetsController.updateBarAppearance(getWindow(), root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override
    protected void onPause() {
        controller.snapshotNow();
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        controller.shutdown();
        fileChooser.cancelPending();
        destroyWebView();
        super.onDestroy();
    }

    // WebView lifecycle -------------------------------------------------------------------------

    private void createWebView() {
        webView = new WebView(this);
        webView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(webView, 0);
        if (!webViewHost.configure(webView, controller.newBridge())) {
            showFatal(R.string.webview_unsupported);
            return;
        }
        webView.loadUrl(WebViewHost.BASE_URL);
    }

    private void destroyWebView() {
        if (webView == null) return;
        root.removeView(webView);
        webView.destroy();
        webView = null;
    }

    @Override
    public void onRenderGone() {
        // Never destroy a WebView from inside its own callback.
        handler.post(() -> {
            destroyWebView();
            if (crashLimiter.recordAndAllowRetry(System.currentTimeMillis())) {
                createWebView();
            } else {
                showFatal(R.string.renderer_failed);
            }
        });
    }

    private void showFatal(int messageRes) {
        root.removeAllViews();
        TextView message = new TextView(this);
        message.setText(messageRes);
        message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        message.setGravity(Gravity.CENTER);
        message.setPadding(dpToPx(24), dpToPx(24), dpToPx(24), dpToPx(24));
        root.addView(message, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        backCallback.setEnabled(false);
    }

    // Intents -----------------------------------------------------------------------------------

    private void openFromIntent(Intent intent, boolean atLaunch) {
        Uri uri = null;
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            uri = intent.getData();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            uri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
        } else {
            uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        }
        if (uri == null) {
            toast(R.string.open_failed);
            return;
        }
        if (isPrivateFile(uri)) {
            toast(R.string.open_failed);
            return;
        }
        if (atLaunch) controller.expectLaunchOpen();
        controller.openUri(uri, false, IntentRouter.needsSniff(intent.getType()));
    }

    private boolean isPrivateFile(Uri uri) {
        if (!"file".equalsIgnoreCase(uri.getScheme()) || uri.getPath() == null) return false;
        try {
            return IntentRouter.isPrivateFile(uri.getScheme(),
                    new java.io.File(uri.getPath()).getCanonicalPath(), getFilesDir().getCanonicalPath());
        } catch (java.io.IOException e) {
            return true;
        }
    }

    // HostController.Callbacks ----------------------------------------------------------------

    @Override
    public void onStateChanged(HostState state) {
        backCallback.setEnabled(state.wantsBack());
    }

    @Override
    public void exit(boolean dirty) {
        if (dirty) {
            moveTaskToBack(true);
        } else {
            finish();
        }
    }

    @Override
    public void toast(int resId, Object... args) {
        Toast.makeText(this, getString(resId, args), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void openExternal(Uri uri) {
        onExternalUrl(uri);
    }

    @Override
    public void onExternalUrl(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException e) {
            toast(R.string.no_app_for_link);
        }
    }

    @Override
    public void createDocument(String suggestedName, String mime) {
        pickers.create(suggestedName, mime);
    }

    @Override
    public void print(String name, java.nio.ByteBuffer pdf) {
        if (!printer.print(name, pdf)) toast(R.string.print_failed);
    }

    @Override
    public void pickOpen() {
        pickers.open();
    }

    private int dpToPx(int dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics()));
    }
}
