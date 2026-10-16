package com.galiehneh.wordcraft;

import com.galiehneh.wordcraft.web.DocSource;

/** A document waiting to be handed to the editor via an {@code open} event. */
final class PendingOpen {
    final DocSource source;
    final String name;
    final String sourceUri;
    final boolean recovered;

    PendingOpen(DocSource source, String name, String sourceUri, boolean recovered) {
        this.source = source;
        this.name = name;
        this.sourceUri = sourceUri;
        this.recovered = recovered;
    }
}
