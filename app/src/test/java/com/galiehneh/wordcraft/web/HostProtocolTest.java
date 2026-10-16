package com.galiehneh.wordcraft.web;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class HostProtocolTest {
    private static byte[] bytes(HostProtocol.Frame f) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try {
            HostProtocol.writeTo(out, f.payload);
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
        return out.toByteArray();
    }

    @Test
    public void payloadIsViewOverSourceFrame() {
        byte[] frame = HostProtocol.encode("{}", new byte[] {5, 6});
        HostProtocol.Frame f = HostProtocol.decode(frame);
        assertSame(frame, f.payload.array());
    }

    @Test
    public void roundTripPreservesHeaderAndPayload() {
        byte[] payload = {0, 1, 2, (byte) 0xFF, 127};
        String header = "{\"type\":\"save\",\"ticket\":7}";

        HostProtocol.Frame f = HostProtocol.decode(HostProtocol.encode(header, payload));

        assertEquals(header, f.headerJson);
        assertArrayEquals(payload, bytes(f));
    }

    @Test
    public void lengthPrefixIsLittleEndian() {
        byte[] frame = HostProtocol.encode("abc", new byte[0]);

        assertEquals(3, frame[0]);
        assertEquals(0, frame[1]);
        assertEquals(0, frame[2]);
        assertEquals(0, frame[3]);
        assertEquals(7, frame.length);
    }

    @Test
    public void emptyPayloadAndNullPayloadDecodeToEmpty() {
        assertEquals(0, HostProtocol.decode(HostProtocol.encode("{}", null)).payload.remaining());
        assertEquals(0, HostProtocol.decode(HostProtocol.encode("{}", new byte[0])).payload.remaining());
    }

    @Test
    public void utf8HeaderSurvivesRoundTrip() {
        String header = "{\"name\":\"é中文.docx\"}";
        byte[] frame = HostProtocol.encode(header, new byte[] {9});

        assertEquals(header, HostProtocol.decode(frame).headerJson);
        assertEquals(header.getBytes(StandardCharsets.UTF_8).length, frame[0] & 0xFF);
    }

    @Test
    public void decodesHandBuiltFrame() {
        byte[] frame = {2, 0, 0, 0, '{', '}', 42};

        HostProtocol.Frame f = HostProtocol.decode(frame);

        assertEquals("{}", f.headerJson);
        assertArrayEquals(new byte[] {42}, bytes(f));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsFrameShorterThanPrefix() {
        HostProtocol.decode(new byte[] {1, 0});
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNull() {
        HostProtocol.decode(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsHeaderLengthBeyondFrame() {
        HostProtocol.decode(new byte[] {10, 0, 0, 0, 'a'});
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativeAsHugeUnsignedLength() {
        HostProtocol.decode(new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 1});
    }
}
