package com.galiehneh.wordcraft.print;

import android.content.Context;

/** Prints PDF bytes through PrintManager. Skeleton: implemented in a later wave. */
public final class PdfPrinter {
    private final Context context;

    public PdfPrinter(Context context) {
        this.context = context;
    }

    Context context() {
        return context;
    }
}
