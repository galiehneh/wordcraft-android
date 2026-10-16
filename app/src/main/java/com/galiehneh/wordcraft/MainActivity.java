package com.galiehneh.wordcraft;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private static final String TAG = "WordCraft";
    private static final String BASE_URL = "https://appassets.androidplatform.net/index.html";
    private static final int FILE_CHOOSER_REQUEST = 1001;
    private static final int DIRECT_PICKER_REQUEST = 1002;

    private static final String[] SUPPORTED_MIMES = {
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/msword",
            "application/vnd.oasis.opendocument.text",
            "application/rtf",
            "text/rtf",
            "text/plain",
            "text/markdown",
            "text/html",
            "application/pdf",
            "application/octet-stream"
    };

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private String currentDocumentName = null;
    private TextView fab;
    private long backPressedTime = 0;
    private Toast exitToast = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout rootLayout = new FrameLayout(this);
        rootLayout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        applyWindowInsets(rootLayout);

        webView = new WebView(this);
        webView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        rootLayout.addView(webView);

        // Floating Action Button "Buka Dokumen" (WordCraft accent blue)
        fab = createFabButton();
        rootLayout.addView(fab);

        setContentView(rootLayout);

        configureWebView();

        Intent intent = getIntent();
        if (intent != null && hasDocumentAction(intent)) {
            handleIncomingIntent(intent);
        } else if (savedInstanceState == null) {
            webView.loadUrl(BASE_URL);
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

    private boolean hasDocumentAction(Intent intent) {
        String action = intent.getAction();
        return Intent.ACTION_VIEW.equals(action) || Intent.ACTION_SEND.equals(action);
    }

    private TextView createFabButton() {
        TextView fab = new TextView(this);
        fab.setText("Buka Dokumen");
        fab.setTextColor(Color.WHITE);
        fab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        fab.setTypeface(null, android.graphics.Typeface.BOLD);

        int padH = dpToPx(20);
        int padV = dpToPx(14);
        fab.setPadding(padH, padV, padH, padV);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF185ABD); // WordCraft Royal Blue
        bg.setCornerRadius(dpToPx(28));
        fab.setBackground(bg);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            fab.setElevation(dpToPx(8));
        }

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.BOTTOM | Gravity.END;
        params.bottomMargin = dpToPx(24);
        params.rightMargin = dpToPx(20);
        fab.setLayoutParams(params);

        fab.setClickable(true);
        fab.setFocusable(true);
        fab.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            openDirectDocumentPicker();
        });
        return fab;
    }

    private void applyWindowInsets(View view) {
        final int navHeight = getNavigationBarHeight();
        view.setPadding(0, 0, 0, navHeight);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets bars = insets.getInsets(
                        android.view.WindowInsets.Type.navigationBars() | android.view.WindowInsets.Type.displayCutout()
                );
                int bottom = bars.bottom > 0 ? bars.bottom : navHeight;
                v.setPadding(bars.left, 0, bars.right, bottom);
                return insets;
            });
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            view.setOnApplyWindowInsetsListener((v, insets) -> {
                int left = insets.getSystemWindowInsetLeft();
                int right = insets.getSystemWindowInsetRight();
                int bottom = insets.getSystemWindowInsetBottom();
                v.setPadding(left, 0, right, bottom > 0 ? bottom : navHeight);
                return insets;
            });
        }
    }

    private int getNavigationBarHeight() {
        int resId = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        if (resId > 0) {
            return getResources().getDimensionPixelSize(resId);
        }
        return dpToPx(48);
    }

    private int dpToPx(int dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics()
        ));
    }

    private void configureWebView() {
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.requestFocus(View.FOCUS_DOWN);

        webView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                    case MotionEvent.ACTION_UP:
                        if (!v.hasFocus()) {
                            v.requestFocus();
                        }
                        break;
                }
                return false;
            }
        });

        webView.addJavascriptInterface(new AndroidDownloadBridge(), "WordCraftAndroid");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (request == null || request.getUrl() == null) return null;
                Uri uri = request.getUrl();
                String host = uri.getHost();
                if ("appassets.androidplatform.net".equalsIgnoreCase(host) || "localhost".equalsIgnoreCase(host)) {
                    String path = uri.getPath();
                    if (path == null || path.isEmpty() || path.equals("/")) {
                        path = "/index.html";
                    }

                    // Intercept incoming / current document from cache
                    if (path.startsWith("/doc/") || path.contains("current_document")) {
                        File cachedFile = new File(getCacheDir(), "current_document");
                        if (cachedFile.exists()) {
                            try {
                                InputStream is = new FileInputStream(cachedFile);
                                String mime = getMimeType(path);
                                Map<String, String> headers = new HashMap<>();
                                headers.put("Access-Control-Allow-Origin", "*");
                                headers.put("Cache-Control", "no-cache");
                                return new WebResourceResponse(mime, "UTF-8", 200, "OK", headers, is);
                            } catch (IOException e) {
                                Log.e(TAG, "Cannot stream cached document", e);
                            }
                        }
                    }

                    if (path.startsWith("/")) {
                        path = path.substring(1);
                    }
                    try {
                        InputStream stream = getAssets().open("web/" + path);
                        String mime = getMimeType(path);
                        Map<String, String> headers = new HashMap<>();
                        headers.put("Access-Control-Allow-Origin", "*");
                        headers.put("Cache-Control", "no-cache");
                        return new WebResourceResponse(mime, "UTF-8", 200, "OK", headers, stream);
                    } catch (IOException e) {
                        Log.w(TAG, "Asset not found: web/" + path);
                        return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", null, null);
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = callback;

                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_MIME_TYPES, SUPPORTED_MIMES);

                if (params != null && params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) {
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                }

                try {
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                } catch (ActivityNotFoundException e) {
                    try {
                        Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
                        fallback.addCategory(Intent.CATEGORY_OPENABLE);
                        fallback.setType("*/*");
                        fallback.putExtra(Intent.EXTRA_MIME_TYPES, SUPPORTED_MIMES);
                        startActivityForResult(Intent.createChooser(fallback, "Pilih Dokumen"), FILE_CHOOSER_REQUEST);
                    } catch (Exception ex) {
                        Log.e(TAG, "Cannot launch file chooser", ex);
                        if (filePathCallback != null) {
                            filePathCallback.onReceiveValue(null);
                            filePathCallback = null;
                        }
                        return false;
                    }
                }
                return true;
            }
        });
    }

    private void openDirectDocumentPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, SUPPORTED_MIMES);
        try {
            startActivityForResult(intent, DIRECT_PICKER_REQUEST);
        } catch (ActivityNotFoundException e) {
            try {
                Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
                fallback.addCategory(Intent.CATEGORY_OPENABLE);
                fallback.setType("*/*");
                fallback.putExtra(Intent.EXTRA_MIME_TYPES, SUPPORTED_MIMES);
                startActivityForResult(Intent.createChooser(fallback, "Pilih Dokumen"), DIRECT_PICKER_REQUEST);
            } catch (Exception ex) {
                Toast.makeText(this, "Tidak ada aplikasi pemilih berkas", Toast.LENGTH_SHORT).show();
            }
        }
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
            String name = getFileNameFromUri(uri);
            this.currentDocumentName = name;

            File dest = new File(getCacheDir(), "current_document");
            try (InputStream is = getContentResolver().openInputStream(uri);
                 FileOutputStream fos = new FileOutputStream(dest)) {
                byte[] buffer = new byte[32768];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, read);
                }
            }

            String encodedName = Uri.encode(name);
            String url = BASE_URL + "?file=/doc/" + encodedName + "&t=" + System.currentTimeMillis();
            if (webView != null) {
                webView.loadUrl(url);
                if (fab != null) {
                    fab.setVisibility(View.GONE);
                }
                getWindow().getDecorView().performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                Toast.makeText(this, "Membuka: " + name, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling Document URI: " + uri, e);
            Toast.makeText(this, "Gagal memproses berkas dokumen", Toast.LENGTH_SHORT).show();
        }
    }

    private String getFileNameFromUri(Uri uri) {
        String result = "document.docx";
        if (uri == null) return result;
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        String name = cursor.getString(nameIndex);
                        if (name != null && !name.trim().isEmpty()) {
                            result = name;
                        }
                    }
                }
            } catch (Exception ignored) {}
        } else {
            String path = uri.getPath();
            if (path != null) {
                int cut = path.lastIndexOf('/');
                if (cut != -1) {
                    result = path.substring(cut + 1);
                }
            }
        }
        return result;
    }

    private String getMimeType(String path) {
        String normalized = path.toLowerCase(Locale.US);
        if (normalized.endsWith(".html") || normalized.endsWith(".htm")) return "text/html";
        if (normalized.endsWith(".js")) return "text/javascript";
        if (normalized.endsWith(".wasm")) return "application/wasm";
        if (normalized.endsWith(".css")) return "text/css";
        if (normalized.endsWith(".json")) return "application/json";
        if (normalized.endsWith(".png")) return "image/png";
        if (normalized.endsWith(".jpg") || normalized.endsWith(".jpeg")) return "image/jpeg";
        if (normalized.endsWith(".svg")) return "image/svg+xml";
        if (normalized.endsWith(".ico")) return "image/x-icon";
        if (normalized.endsWith(".txt")) return "text/plain";
        if (normalized.endsWith(".md")) return "text/markdown";
        if (normalized.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (normalized.endsWith(".doc")) return "application/msword";
        if (normalized.endsWith(".odt")) return "application/vnd.oasis.opendocument.text";
        if (normalized.endsWith(".rtf")) return "application/rtf";
        if (normalized.endsWith(".pdf")) return "application/pdf";
        if (normalized.endsWith(".woff2")) return "font/woff2";
        if (normalized.endsWith(".ttf")) return "font/ttf";
        return "application/octet-stream";
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (filePathCallback != null) {
                Uri[] results = null;
                if (resultCode == RESULT_OK && data != null) {
                    if (fab != null) {
                        fab.setVisibility(View.GONE);
                    }
                    if (data.getData() != null) {
                        results = new Uri[]{data.getData()};
                    } else if (data.getClipData() != null) {
                        int count = data.getClipData().getItemCount();
                        results = new Uri[count];
                        for (int i = 0; i < count; i++) {
                            results[i] = data.getClipData().getItemAt(i).getUri();
                        }
                    }
                }
                filePathCallback.onReceiveValue(results);
                filePathCallback = null;
            }
        } else if (requestCode == DIRECT_PICKER_REQUEST) {
            if (resultCode == RESULT_OK && data != null) {
                Uri uri = data.getData();
                if (uri != null) {
                    handleDocumentUri(uri);
                }
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null) {
            webView.evaluateJavascript("window.handleAndroidBack && window.handleAndroidBack();", null);
        }
        if (fab != null && fab.getVisibility() == View.GONE) {
            // Currently viewing a document; return to home
            fab.setVisibility(View.VISIBLE);
            this.currentDocumentName = null;
            if (webView != null) {
                webView.loadUrl(BASE_URL);
                webView.clearHistory();
            }
            backPressedTime = 0;
            Toast.makeText(this, "Kembali ke Beranda", Toast.LENGTH_SHORT).show();
            return;
        }

        // Di Beranda: user harus tekan 2x dalam 2 detik untuk keluar
        long currentTime = System.currentTimeMillis();
        if (currentTime - backPressedTime < 2000) {
            if (exitToast != null) {
                exitToast.cancel();
            }
            super.onBackPressed();
        } else {
            backPressedTime = currentTime;
            if (exitToast != null) {
                exitToast.cancel();
            }
            exitToast = Toast.makeText(this, "Tekan sekali lagi untuk keluar", Toast.LENGTH_SHORT);
            exitToast.show();
            getWindow().getDecorView().performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) {
            webView.saveState(outState);
        }
    }

    private class AndroidDownloadBridge {
        @JavascriptInterface
        public void showKeyboard() {
            runOnUiThread(() -> {
                if (webView != null) {
                    webView.requestFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        boolean ok = imm.showSoftInput(webView, InputMethodManager.SHOW_IMPLICIT);
                        if (!ok) {
                            ok = imm.showSoftInput(webView, 0);
                        }
                        if (!ok) {
                            imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
                        }
                    }
                }
            });
        }

        @JavascriptInterface
        public void hideKeyboard() {
            runOnUiThread(() -> {
                if (webView != null) {
                    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(webView.getWindowToken(), 0);
                    }
                }
            });
        }

        @JavascriptInterface
        public void setDocumentOpened(boolean opened) {
            runOnUiThread(() -> {
                if (fab != null) {
                    fab.setVisibility(opened ? View.GONE : View.VISIBLE);
                }
            });
        }

        @JavascriptInterface
        public void saveBlob(String dataUrl, String filename) {
            if (dataUrl == null || !dataUrl.contains(",")) return;
            String base64Data = dataUrl.substring(dataUrl.indexOf(",") + 1);
            try {
                byte[] bytes = Base64.decode(base64Data, Base64.DEFAULT);
                String safeName = (filename != null && !filename.isEmpty()) ? filename : "document.docx";
                boolean saved = saveFileToDownloads(safeName, bytes);
                runOnUiThread(() -> {
                    if (saved) {
                        getWindow().getDecorView().performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                        Toast.makeText(MainActivity.this, "Berhasil disimpan ke Download: " + safeName, Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(MainActivity.this, "Gagal menyimpan berkas", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed saving blob", e);
            }
        }

        private boolean saveFileToDownloads(String filename, byte[] bytes) {
            String mime = getMimeType(filename);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentResolver resolver = getContentResolver();
                ContentValues contentValues = new ContentValues();
                contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
                contentValues.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

                Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues);
                if (uri == null) return false;
                try (OutputStream os = resolver.openOutputStream(uri)) {
                    if (os != null) {
                        os.write(bytes);
                        return true;
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error writing to MediaStore", e);
                }
                return false;
            } else {
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!dir.exists()) dir.mkdirs();
                File file = new File(dir, filename);
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(bytes);
                    return true;
                } catch (IOException e) {
                    Log.e(TAG, "Error writing to file", e);
                    return false;
                }
            }
        }
    }
}
