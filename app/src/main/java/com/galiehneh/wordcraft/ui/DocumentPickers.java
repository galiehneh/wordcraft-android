package com.galiehneh.wordcraft.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;

import com.galiehneh.wordcraft.R;
import com.galiehneh.wordcraft.io.MimeMap;

/** SAF pickers asking for persistable read+write grants. Construct before the activity is STARTED. */
public final class DocumentPickers {
    /** Receives picker results on the UI thread; {@code null} means cancelled. */
    public interface Listener {
        void onOpenPicked(Uri uri);

        /** @param uri the new document, or {@code null} when cancelled */
        void onCreateResult(Uri uri);
    }

    private static final int GRANT_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION
            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION;

    private final Context context;
    private final Listener listener;
    private final ActivityResultLauncher<String[]> openLauncher;
    private final ActivityResultLauncher<String[]> createLauncher;

    public DocumentPickers(ComponentActivity activity, Listener listener) {
        this.context = activity;
        this.listener = listener;
        this.openLauncher = activity.registerForActivityResult(new OpenWithGrants(), uri -> {
            if (uri != null) listener.onOpenPicked(uri);
        });
        this.createLauncher = activity.registerForActivityResult(new CreateWithGrants(), listener::onCreateResult);
    }

    /** Asks the user where to put a new file. */
    public void create(String suggestedName, String mime) {
        try {
            createLauncher.launch(new String[]{suggestedName, mime});
        } catch (RuntimeException e) {
            // No picker (ActivityNotFoundException) or a launch failure: clear the pending target
            // and answer the editor's ticket as cancelled.
            listener.onCreateResult(null);
            Toast.makeText(context, R.string.no_file_picker, Toast.LENGTH_SHORT).show();
        }
    }

    public void open() {
        try {
            openLauncher.launch(MimeMap.OPEN_TYPES);
        } catch (RuntimeException e) {
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

    /** Input is {suggestedName, mime}. */
    private static final class CreateWithGrants extends ActivityResultContract<String[], Uri> {
        @NonNull
        @Override
        public Intent createIntent(@NonNull Context context, @NonNull String[] input) {
            return new Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType(input[1])
                    .putExtra(Intent.EXTRA_TITLE, input[0])
                    .addFlags(GRANT_FLAGS);
        }

        @Override
        public Uri parseResult(int resultCode, Intent intent) {
            return resultCode == Activity.RESULT_OK && intent != null ? intent.getData() : null;
        }
    }
}
