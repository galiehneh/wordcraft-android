package com.galiehneh.wordcraft.print;

import android.content.Context;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintManager;
import android.util.Log;

import com.galiehneh.wordcraft.io.FileNames;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Prints editor-rendered PDF bytes through the system print framework. */
public final class PdfPrinter {
    private static final String TAG = "WordCraft";
    private static final int CHUNK = 64 * 1024;

    private final Context context;

    /** @param context an Activity: the print dialog needs a window. */
    public PdfPrinter(Context context) {
        this.context = context;
    }

    /** @return false when there is nothing to print or the print service is unavailable. */
    public boolean print(String documentName, ByteBuffer pdf) {
        String jobName = FileNames.pdfName(documentName);
        PrintManager manager = (PrintManager) context.getSystemService(Context.PRINT_SERVICE);
        if (manager == null || !pdf.hasRemaining()) return false;
        // No copy here (main thread): the adapter streams from a view of the editor's own buffer.
        try {
            manager.print(jobName, new PdfAdapter(jobName, pdf.duplicate()), new PrintAttributes.Builder().build());
            return true;
        } catch (RuntimeException | OutOfMemoryError e) {
            Log.e(TAG, "Print failed", e);
            return false;
        }
    }

    private static final class PdfAdapter extends PrintDocumentAdapter {
        private final String name;
        private final ExecutorService worker = Executors.newSingleThreadExecutor();
        private volatile ByteBuffer bytes;

        PdfAdapter(String name, ByteBuffer bytes) {
            this.name = name;
            this.bytes = bytes;
        }

        @Override
        public void onLayout(PrintAttributes oldAttributes, PrintAttributes newAttributes,
                CancellationSignal cancel, LayoutResultCallback callback, Bundle extras) {
            if (cancel.isCanceled()) {
                callback.onLayoutCancelled();
                return;
            }
            callback.onLayoutFinished(new PrintDocumentInfo.Builder(name)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                    .build(), true);
        }

        @Override
        public void onWrite(PageRange[] pages, ParcelFileDescriptor destination,
                CancellationSignal cancel, WriteResultCallback callback) {
            ByteBuffer data = bytes;
            if (data == null) {
                callback.onWriteFailed(null);
                return;
            }
            worker.execute(() -> {
                try (FileOutputStream out = new FileOutputStream(destination.getFileDescriptor())) {
                    ByteBuffer src = data.duplicate();
                    byte[] chunk = new byte[CHUNK];
                    while (src.hasRemaining()) {
                        if (cancel.isCanceled()) {
                            callback.onWriteCancelled();
                            return;
                        }
                        int n = Math.min(CHUNK, src.remaining());
                        src.get(chunk, 0, n);
                        out.write(chunk, 0, n);
                    }
                    callback.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});
                } catch (IOException | RuntimeException | OutOfMemoryError e) {
                    Log.e(TAG, "Print write failed", e);
                    callback.onWriteFailed(e.getMessage());
                }
            });
        }

        @Override
        public void onFinish() {
            worker.execute(() -> {
                ByteBuffer data = bytes;
                bytes = null;
                if (data != null && data.hasArray()) {
                    Arrays.fill(data.array(), data.arrayOffset() + data.position(),
                            data.arrayOffset() + data.limit(), (byte) 0);
                }
            });
            worker.shutdown();
        }
    }
}
