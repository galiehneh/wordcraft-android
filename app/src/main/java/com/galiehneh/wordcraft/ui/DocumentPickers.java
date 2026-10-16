package com.galiehneh.wordcraft.ui;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;

import com.galiehneh.wordcraft.R;
import com.galiehneh.wordcraft.io.MimeMap;

/** SAF pickers asking for persistable read+write grants. Construct before the activity is STARTED. */
public final class DocumentPickers {
    /** Receives picker results on the UI thread; {@code null} means cancelled. */
    public interface Listener {
        void onOpenPicked(Uri uri);
    }

    private static final int GRANT_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION
            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION;

    private final Context context;
    private final ActivityResultLauncher<String[]> openLauncher;

    public DocumentPickers(ComponentActivity activity, Listener listener) {
        this.context = activity;
        this.openLauncher = activity.registerForActivityResult(new OpenWithGrants(), uri -> {
            if (uri != null) listener.onOpenPicked(uri);
        });
    }

    public void open() {
        try {
            openLauncher.launch(MimeMap.OPEN_TYPES);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, R.string.no_file_picker, Toast.LENGTH_SHORT).show();
        }
    }

    private static final class OpenWithGrants extends ActivityResultContracts.OpenDocument {
        @NonNull
        @Override
        public Intent createIntent(@NonNull Context context, @NonNull String[] input) {
            return super.createIntent(context, input).addFlags(GRANT_FLAGS);
        }
    }
}
