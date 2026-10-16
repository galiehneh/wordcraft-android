package com.galiehneh.wordcraft.web;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reassembles base64-fallback chunks (already decoded) per message id. See docs/host-protocol.md. */
public final class ChunkAssembler {
    static final int MAX_CHUNKS = 1024;
    static final long DEFAULT_MAX_TOTAL_BYTES = 256L * 1024 * 1024;
    private static final int MAX_IN_FLIGHT = 4;

    private static final class Partial {
        final String headerJson;
        final byte[][] parts;
        int received;
        long bytes;

        Partial(String headerJson, int chunks) {
            this.headerJson = headerJson;
            this.parts = new byte[chunks][];
        }
    }

    private final long maxTotalBytes;
    /** Insertion-ordered: the eldest entry is evicted first. */
    private final Map<Long, Partial> inFlight = new LinkedHashMap<>();
    private final List<String> dropped = new ArrayList<>();
    private long totalBytes;

    public ChunkAssembler() {
        this(DEFAULT_MAX_TOTAL_BYTES);
    }

    ChunkAssembler(long maxTotalBytes) {
        this.maxTotalBytes = maxTotalBytes;
    }

    /**
     * @return the complete frame once every chunk of {@code id} arrived, else null.
     * @throws IllegalArgumentException on malformed chunk metadata or when limits are exceeded.
     */
    public HostProtocol.Frame add(long id, int index, int chunks, String headerJson, byte[] data) {
        if (chunks < 1 || chunks > MAX_CHUNKS || index < 0 || index >= chunks || data == null) {
            throw new IllegalArgumentException("Invalid chunk " + index + "/" + chunks);
        }
        Partial p = inFlight.get(id);
        if (p == null) {
            if (inFlight.size() >= MAX_IN_FLIGHT) {
                evict(inFlight.keySet().iterator().next());
            }
            p = new Partial(headerJson, chunks);
            inFlight.put(id, p);
        } else if (p.parts.length != chunks) {
            evict(id);
            throw new IllegalArgumentException("Chunk count changed for id " + id);
        }
        byte[] old = p.parts[index];
        long delta = data.length - (old == null ? 0 : old.length);
        while (totalBytes + delta > maxTotalBytes) {
            Long victim = eldestOtherThan(id);
            if (victim == null) {
                evict(id);
                throw new IllegalArgumentException("Message " + id + " exceeds " + maxTotalBytes + " bytes");
            }
            evict(victim);
        }
        if (old == null) p.received++;
        p.parts[index] = data;
        p.bytes += delta;
        totalBytes += delta;
        if (p.received < chunks) {
            return null;
        }
        inFlight.remove(id);
        totalBytes -= p.bytes;
        byte[] out = new byte[(int) p.bytes];
        int pos = 0;
        for (byte[] part : p.parts) {
            System.arraycopy(part, 0, out, pos, part.length);
            pos += part.length;
        }
        return new HostProtocol.Frame(p.headerJson, ByteBuffer.wrap(out));
    }

    /** Headers of partial messages discarded since the last call (never completed). */
    public List<String> takeDropped() {
        List<String> out = new ArrayList<>(dropped);
        dropped.clear();
        return out;
    }

    public void clear() {
        for (Iterator<Partial> it = inFlight.values().iterator(); it.hasNext(); ) {
            dropped.add(it.next().headerJson);
            it.remove();
        }
        totalBytes = 0;
    }

    private Long eldestOtherThan(long id) {
        for (Long key : inFlight.keySet()) {
            if (key != id) return key;
        }
        return null;
    }

    private void evict(long id) {
        Partial p = inFlight.remove(id);
        if (p == null) return;
        totalBytes -= p.bytes;
        dropped.add(p.headerJson);
    }
}
