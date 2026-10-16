package com.galiehneh.wordcraft.web;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

/** Reassembles base64-fallback chunks (already decoded) per message id. See docs/host-protocol.md. */
public final class ChunkAssembler {
    private static final int MAX_CHUNKS = 4096;
    private static final int MAX_IN_FLIGHT = 4;

    private static final class Partial {
        final String headerJson;
        final byte[][] parts;
        int received;

        Partial(String headerJson, int chunks) {
            this.headerJson = headerJson;
            this.parts = new byte[chunks][];
        }
    }

    private final Map<Long, Partial> inFlight = new HashMap<>();

    /**
     * @return the complete frame once every chunk of {@code id} arrived, else null.
     * @throws IllegalArgumentException on malformed chunk metadata.
     */
    public HostProtocol.Frame add(long id, int index, int chunks, String headerJson, byte[] data) {
        if (chunks < 1 || chunks > MAX_CHUNKS || index < 0 || index >= chunks || data == null) {
            throw new IllegalArgumentException("Invalid chunk " + index + "/" + chunks);
        }
        Partial p = inFlight.get(id);
        if (p == null) {
            if (inFlight.size() >= MAX_IN_FLIGHT) {
                inFlight.remove(inFlight.keySet().iterator().next());
            }
            p = new Partial(headerJson, chunks);
            inFlight.put(id, p);
        } else if (p.parts.length != chunks) {
            inFlight.remove(id);
            throw new IllegalArgumentException("Chunk count changed for id " + id);
        }
        if (p.parts[index] == null) {
            p.received++;
        }
        p.parts[index] = data;
        if (p.received < chunks) {
            return null;
        }
        inFlight.remove(id);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : p.parts) {
            out.write(part, 0, part.length);
        }
        return new HostProtocol.Frame(p.headerJson, out.toByteArray());
    }
}
