package com.galiehneh.wordcraft.web;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ChunkAssemblerTest {
    private static byte[] arr(HostProtocol.Frame f) {
        return java.util.Arrays.copyOfRange(f.payload.array(), f.payload.arrayOffset(),
                f.payload.arrayOffset() + f.payload.remaining());
    }

    @Test
    public void oldestPartialEvictedBeyondInFlightLimit() {
        ChunkAssembler a = new ChunkAssembler();
        for (long id = 1; id <= 5; id++) a.add(id, 0, 2, "h" + id, new byte[]{1});
        assertEquals(java.util.Collections.singletonList("h1"), a.takeDropped());
        assertNull(a.add(1, 1, 2, "h1", new byte[]{2}));
    }

    @Test
    public void totalByteCapEvictsOldestThenRejectsOversize() {
        ChunkAssembler a = new ChunkAssembler(10);
        a.add(1, 0, 2, "h1", new byte[6]);
        a.add(2, 0, 2, "h2", new byte[6]);
        assertEquals(java.util.Collections.singletonList("h1"), a.takeDropped());
        try {
            a.add(2, 1, 2, "h2", new byte[6]);
            org.junit.Assert.fail();
        } catch (IllegalArgumentException expected) {
            assertEquals(java.util.Collections.singletonList("h2"), a.takeDropped());
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsTooManyChunks() {
        new ChunkAssembler().add(1, 0, ChunkAssembler.MAX_CHUNKS + 1, "h", new byte[0]);
    }

    @Test
    public void assemblesOutOfOrderChunks() {
        ChunkAssembler a = new ChunkAssembler();

        assertNull(a.add(1, 1, 3, "{\"type\":\"save\"}", new byte[]{3, 4}));
        assertNull(a.add(1, 2, 3, "{\"type\":\"save\"}", new byte[]{5}));
        HostProtocol.Frame f = a.add(1, 0, 3, "{\"type\":\"save\"}", new byte[]{1, 2});

        assertEquals("{\"type\":\"save\"}", f.headerJson);
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5}, arr(f));
    }

    @Test
    public void interleavedIdsStaySeparate() {
        ChunkAssembler a = new ChunkAssembler();
        a.add(1, 0, 2, "h1", new byte[]{1});
        a.add(2, 0, 2, "h2", new byte[]{9});

        assertArrayEquals(new byte[]{9, 8}, arr(a.add(2, 1, 2, "h2", new byte[]{8})));
        assertArrayEquals(new byte[]{1, 2}, arr(a.add(1, 1, 2, "h1", new byte[]{2})));
    }

    @Test
    public void duplicateChunkDoesNotCompleteEarly() {
        ChunkAssembler a = new ChunkAssembler();
        assertNull(a.add(1, 0, 2, "h", new byte[]{1}));
        assertNull(a.add(1, 0, 2, "h", new byte[]{1}));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsIndexOutOfRange() {
        new ChunkAssembler().add(1, 2, 2, "h", new byte[0]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsChangedChunkCount() {
        ChunkAssembler a = new ChunkAssembler();
        a.add(1, 0, 2, "h", new byte[0]);
        a.add(1, 1, 3, "h", new byte[0]);
    }
}
