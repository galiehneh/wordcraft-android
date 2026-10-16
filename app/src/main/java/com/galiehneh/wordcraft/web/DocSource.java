package com.galiehneh.wordcraft.web;

import java.io.IOException;
import java.io.InputStream;

/** Opens the bytes of a document on demand (called off the main thread). */
public interface DocSource {
    InputStream open() throws IOException;
}
