package com.galiehneh.wordcraft;

import com.galiehneh.wordcraft.io.CurrentDoc;
import com.galiehneh.wordcraft.web.DocSource;

/** A document waiting to be handed to the editor via an {@code open} event. */
final class PendingOpen {
    final DocSource source;
    final String name;
    final boolean recovered;
    /** Save target once this open completes; null when it cannot be written back. */
    final CurrentDoc doc;

    PendingOpen(DocSource source, String name, boolean recovered, CurrentDoc doc) {
        this.source = source;
        this.name = name;
        this.recovered = recovered;
        this.doc = doc;
    }
}
