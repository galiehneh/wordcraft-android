package com.galiehneh.wordcraft;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;

import com.galiehneh.wordcraft.io.DocumentRepository;
import com.galiehneh.wordcraft.ui.FileChooser;
import com.galiehneh.wordcraft.ui.InsetsController;
import com.galiehneh.wordcraft.web.HostBridge;
import com.galiehneh.wordcraft.web.WebViewHost;

/** Single-activity host: wires the WebView, document intents, FAB and double-back exit. */
public class MainActivity extends ComponentActivity {
    private static final String TAG = "WordCraft";
    private static final long EXIT_WINDOW_MS = 2000;

    private WebView webView;
    private TextView fab;
    private DocumentRepository documents;
    private long backPressedTime = 0;
    private Toast exitToast = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout rootLayout = new FrameLayout(this);
        rootLayout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        InsetsController.apply(rootLayout);

        webView = new WebView(this);
        webView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        rootLayout.addView(webView);

        fab = createFabButton();
        rootLayout.addView(fab);
        setContentView(rootLayout);

        documents = new DocumentRepository(this);
        FileChooser fileChooser = new FileChooser(this, new FileChooser.Listener() {
            @Override
            public void onChooserSelected() {
                fab.setVisibility(View.GONE);
            }

            @Override
            public void onDocumentPicked(Uri uri) {
                handleDocumentUri(uri);
            }
        });
        HostBridge bridge = new HostBridge(this, webView, documents,
                opened -> fab.setVisibility(opened ? View.GONE : View.VISIBLE));
        new WebViewHost(this, webView, fileChooser).configure(bridge);
        fab.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            fileChooser.openDirect();
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                onBack(this);
            }
        });

        Intent intent = getIntent();
        if (intent != null && hasDocumentAction(intent)) {
            handleIncomingIntent(intent);
        } else if (savedInstanceState == null) {
            webView.loadUrl(WebViewHost.BASE_URL);
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && hasDocumentAction(intent)) {
            handleIncomingIntent(intent);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) {
            webView.saveState(outState);
        }
    }

    private boolean hasDocumentAction(Intent intent) {
        String action = intent.getAction();
        return Intent.ACTION_VIEW.equals(action) || Intent.ACTION_SEND.equals(action);
    }

    private void handleIncomingIntent(Intent intent) {
        Uri targetUri = null;
        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action)) {
            targetUri = intent.getData();
        } else if (Intent.ACTION_SEND.equals(action)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                targetUri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
            } else {
                targetUri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
            }
        }
        if (targetUri != null) {
            handleDocumentUri(targetUri);
        }
    }

    private void handleDocumentUri(Uri uri) {
        try {
            String name = documents.displayName(uri);
            documents.copyToCache(uri);
            String url = WebViewHost.BASE_URL + "?file=/doc/" + Uri.encode(name) + "&t=" + System.currentTimeMillis();
            webView.loadUrl(url);
            fab.setVisibility(View.GONE);
            getWindow().getDecorView().performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            Toast.makeText(this, "Membuka: " + name, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Error handling Document URI: " + uri, e);
            Toast.makeText(this, "Gagal memproses berkas dokumen", Toast.LENGTH_SHORT).show();
        }
    }

    private void onBack(OnBackPressedCallback callback) {
        webView.evaluateJavascript("window.handleAndroidBack && window.handleAndroidBack();", null);
        if (fab.getVisibility() == View.GONE) {
            fab.setVisibility(View.VISIBLE);
            webView.loadUrl(WebViewHost.BASE_URL);
            webView.clearHistory();
            backPressedTime = 0;
            Toast.makeText(this, "Kembali ke Beranda", Toast.LENGTH_SHORT).show();
            return;
        }

        long now = System.currentTimeMillis();
        if (now - backPressedTime < EXIT_WINDOW_MS) {
            if (exitToast != null) {
                exitToast.cancel();
            }
            callback.setEnabled(false);
            getOnBackPressedDispatcher().onBackPressed();
        } else {
            backPressedTime = now;
            if (exitToast != null) {
                exitToast.cancel();
            }
            exitToast = Toast.makeText(this, "Tekan sekali lagi untuk keluar", Toast.LENGTH_SHORT);
            exitToast.show();
            getWindow().getDecorView().performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        }
    }

    private TextView createFabButton() {
        TextView button = new TextView(this);
        button.setText("Buka Dokumen");
        button.setTextColor(Color.WHITE);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        button.setTypeface(null, android.graphics.Typeface.BOLD);
        int padH = dpToPx(20);
        int padV = dpToPx(14);
        button.setPadding(padH, padV, padH, padV);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF185ABD);
        bg.setCornerRadius(dpToPx(28));
        button.setBackground(bg);
        button.setElevation(dpToPx(8));

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.BOTTOM | Gravity.END;
        params.bottomMargin = dpToPx(24);
        params.rightMargin = dpToPx(20);
        button.setLayoutParams(params);
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private int dpToPx(int dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics()));
    }
}
