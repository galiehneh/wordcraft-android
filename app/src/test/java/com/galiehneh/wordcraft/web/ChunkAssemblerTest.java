package com.galiehneh.wordcraft.web;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ChunkAssemblerTest {
    @Test
    public void assemblesOutOfOrderChunks() {
        ChunkAssembler a = new ChunkAssembler();

        assertNull(a.add(1, 1, 3, "{\"type\":\"save\"}", new byte[]{3, 4}));
        assertNull(a.add(1, 2, 3, "{\"type\":\"save\"}", new byte[]{5}));
        HostProtocol.Frame f = a.add(1, 0, 3, "{\"type\":\"save\"}", new byte[]{1, 2});

        assertEquals("{\"type\":\"save\"}", f.headerJson);
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5}, f.payload);
    }

    @Test
    public void interleavedIdsStaySeparate() {
        ChunkAssembler a = new ChunkAssembler();
        a.add(1, 0, 2, "h1", new byte[]{1});
        a.add(2, 0, 2, "h2", new byte[]{9});

        assertArrayEquals(new byte[]{9, 8}, a.add(2, 1, 2, "h2", new byte[]{8}).payload);
        assertArrayEquals(new byte[]{1, 2}, a.add(1, 1, 2, "h1", new byte[]{2}).payload);
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
