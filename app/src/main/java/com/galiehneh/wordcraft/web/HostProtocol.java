package com.galiehneh.wordcraft.web;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Frame codec for JS to native binary messages: {@code [u32 LE headerLen][header JSON UTF-8][payload]}.
 * Pure Java, no Android dependencies. See docs/host-protocol.md.
 */
public final class HostProtocol {
    private static final int LENGTH_PREFIX = 4;

    private HostProtocol() {}

    /** Decoded frame. */
    public static final class Frame {
        public final String headerJson;
        public final byte[] payload;

        public Frame(String headerJson, byte[] payload) {
            this.headerJson = headerJson;
            this.payload = payload;
        }
    }

    public static byte[] encode(String headerJson, byte[] payload) {
        byte[] header = headerJson.getBytes(StandardCharsets.UTF_8);
        byte[] body = payload == null ? new byte[0] : payload;
        ByteBuffer buf = ByteBuffer.allocate(LENGTH_PREFIX + header.length + body.length)
                .order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(header.length);
        buf.put(header);
        buf.put(body);
        return buf.array();
    }

    /** @throws IllegalArgumentException if the frame is truncated or the header length is invalid. */
    public static Frame decode(byte[] frame) {
        if (frame == null || frame.length < LENGTH_PREFIX) {
            throw new IllegalArgumentException("Frame shorter than length prefix");
        }
        long headerLen = ByteBuffer.wrap(frame, 0, LENGTH_PREFIX)
                .order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xFFFFFFFFL;
        if (headerLen > frame.length - LENGTH_PREFIX) {
            throw new IllegalArgumentException("Header length exceeds frame size");
        }
        int headerEnd = LENGTH_PREFIX + (int) headerLen;
        String header = new String(frame, LENGTH_PREFIX, (int) headerLen, StandardCharsets.UTF_8);
        return new Frame(header, Arrays.copyOfRange(frame, headerEnd, frame.length));
    }
}
