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
    public boolean print(String jobName, ByteBuffer pdf) {
        PrintManager manager = (PrintManager) context.getSystemService(Context.PRINT_SERVICE);
        if (manager == null || !pdf.hasRemaining()) return false;
        byte[] bytes = new byte[pdf.remaining()];
        pdf.duplicate().get(bytes);
        try {
            manager.print(jobName, new PdfAdapter(jobName, bytes), new PrintAttributes.Builder().build());
            return true;
        } catch (RuntimeException e) {
            Log.e(TAG, "Print failed", e);
            Arrays.fill(bytes, (byte) 0);
            return false;
        }
    }

    private static final class PdfAdapter extends PrintDocumentAdapter {
        private final String name;
        private final ExecutorService worker = Executors.newSingleThreadExecutor();
        private volatile byte[] bytes;

        PdfAdapter(String name, byte[] bytes) {
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
            byte[] data = bytes;
            if (data == null) {
                callback.onWriteFailed(null);
                return;
            }
            worker.execute(() -> {
                try (FileOutputStream out = new FileOutputStream(destination.getFileDescriptor())) {
                    for (int off = 0; off < data.length; off += CHUNK) {
                        if (cancel.isCanceled()) {
                            callback.onWriteCancelled();
                            return;
                        }
                        out.write(data, off, Math.min(CHUNK, data.length - off));
                    }
                    callback.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});
                } catch (IOException e) {
                    Log.e(TAG, "Print write failed", e);
                    callback.onWriteFailed(e.getMessage());
                }
            });
        }

        @Override
        public void onFinish() {
            worker.execute(() -> {
                byte[] data = bytes;
                bytes = null;
                if (data != null) Arrays.fill(data, (byte) 0);
            });
            worker.shutdown();
        }
    }
}
