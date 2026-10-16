package com.galiehneh.wordcraft.ui;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.galiehneh.wordcraft.R;

/** WebView file chooser requests ({@code <input type=file>}) served through SAF. */
public final class FileChooser {
    private static final String TAG = "WordCraft";

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

    private final ComponentActivity activity;
    private final ActivityResultLauncher<Intent> chooserLauncher;
    private ValueCallback<Uri[]> filePathCallback;

    /** Must be constructed before the activity reaches STARTED (e.g. in onCreate). */
    public FileChooser(ComponentActivity activity) {
        this.activity = activity;
        this.chooserLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> onChooserResult(result.getResultCode(), result.getData()));
    }

    public boolean onShowFileChooser(ValueCallback<Uri[]> callback, WebChromeClient.FileChooserParams params) {
        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(null);
        }
        filePathCallback = callback;

        Intent intent = openIntent(Intent.ACTION_OPEN_DOCUMENT);
        if (params != null && params.getMode() == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }

        try {
            chooserLauncher.launch(intent);
        } catch (ActivityNotFoundException e) {
            try {
                chooserLauncher.launch(Intent.createChooser(openIntent(Intent.ACTION_GET_CONTENT), activity.getString(R.string.choose_document)));
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

    /** Releases a pending WebView chooser request so the page is not left waiting. */
    public void cancelPending() {
        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(null);
            filePathCallback = null;
        }
    }

    private void onChooserResult(int resultCode, Intent data) {
        if (filePathCallback == null) return;
        Uri[] results = null;
        if (resultCode == Activity.RESULT_OK && data != null) {
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

    private static Intent openIntent(String action) {
        Intent intent = new Intent(action);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, SUPPORTED_MIMES);
        return intent;
    }
}
