package com.galiehneh.wordcraft.ui;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.galiehneh.wordcraft.io.MimeMap;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class AcceptTypesTest {
    private static List<String> resolve(String... accept) {
        return Arrays.asList(AcceptTypes.resolve(accept));
    }

    @Test
    public void emptyOrNullFallsBackToDocumentTypesWithoutPdf() {
        assertArrayEquals(MimeMap.OPEN_TYPES, AcceptTypes.resolve(null));
        assertArrayEquals(MimeMap.OPEN_TYPES, AcceptTypes.resolve(new String[0]));
        assertArrayEquals(MimeMap.OPEN_TYPES, AcceptTypes.resolve(new String[]{"", " "}));
        assertFalse(resolve().contains("application/pdf"));
    }

    @Test
    public void imageWildcardIsKept() {
        assertEquals(Arrays.asList("image/*"), resolve("image/*"));
    }

    @Test
    public void extensionsMapToMimes() {
        assertEquals(Arrays.asList("image/png", "image/jpeg"), resolve(".png", ".jpg"));
        assertTrue(resolve(".ttf").contains("font/ttf"));
        assertTrue(resolve(".otf").contains("application/octet-stream"));
        assertTrue(resolve(".csv").contains("text/csv"));
        assertTrue(resolve(".csv").contains("application/vnd.ms-excel"));
    }

    @Test
    public void commaJoinedEntriesAreSplitAndCaseIgnored() {
        List<String> mimes = resolve("IMAGE/PNG, .TTF");
        assertTrue(mimes.contains("image/png"));
        assertTrue(mimes.contains("font/ttf"));
    }

    @Test
    public void unknownExtensionStaysSelectable() {
        assertEquals(Arrays.asList("application/octet-stream"), resolve(".xyz"));
    }

    @Test
    public void duplicatesAreRemoved() {
        assertEquals(Arrays.asList("text/csv", "text/comma-separated-values", "application/csv",
                "application/vnd.ms-excel"), resolve("text/csv", ".csv"));
    }

    @Test
    public void junkTokensAreIgnored() {
        assertArrayEquals(MimeMap.OPEN_TYPES, AcceptTypes.resolve(new String[]{"png", null}));
    }
}
