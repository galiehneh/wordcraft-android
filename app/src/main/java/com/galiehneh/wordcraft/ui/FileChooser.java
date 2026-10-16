package com.galiehneh.wordcraft.ui;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.galiehneh.wordcraft.R;

/** SAF document picker: WebView file chooser requests and the direct "open document" button. */
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

    /** Callbacks to the host activity. */
    public interface Listener {
        /** The WebView chooser returned a selection. */
        void onChooserSelected();

        /** The direct picker returned a document. */
        void onDocumentPicked(Uri uri);
    }

    private final ComponentActivity activity;
    private final Listener listener;
    private final ActivityResultLauncher<Intent> chooserLauncher;
    private final ActivityResultLauncher<Intent> directLauncher;
    private ValueCallback<Uri[]> filePathCallback;

    /** Must be constructed before the activity reaches STARTED (e.g. in onCreate). */
    public FileChooser(ComponentActivity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
        this.chooserLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> onChooserResult(result.getResultCode(), result.getData()));
        this.directLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Intent data = result.getData();
                    if (result.getResultCode() == Activity.RESULT_OK && data != null && data.getData() != null) {
                        listener.onDocumentPicked(data.getData());
                    }
                });
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

    public void openDirect() {
        try {
            directLauncher.launch(openIntent(Intent.ACTION_OPEN_DOCUMENT));
        } catch (ActivityNotFoundException e) {
            try {
                directLauncher.launch(Intent.createChooser(openIntent(Intent.ACTION_GET_CONTENT), activity.getString(R.string.choose_document)));
            } catch (Exception ex) {
                Toast.makeText(activity, R.string.no_file_picker, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void onChooserResult(int resultCode, Intent data) {
        if (filePathCallback == null) return;
        Uri[] results = null;
        if (resultCode == Activity.RESULT_OK && data != null) {
            listener.onChooserSelected();
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
