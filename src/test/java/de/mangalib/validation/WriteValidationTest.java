package de.mangalib.validation;

import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Keine Spring-Kontexte, JDBC, DNS oder Netzwerkanfragen. */
class WriteValidationTest {
    @ParameterizedTest @ValueSource(ints = {1, 10_000})
    void acceptsBothVolumeBoundaries(int value) { WriteValidation.volumes(value); }

    @ParameterizedTest @ValueSource(ints = {-1, 0, 10_001, Integer.MAX_VALUE})
    void rejectsUnboundedVolumeLoops(int value) {
        assertThrows(IllegalArgumentException.class, () -> WriteValidation.volumes(value));
    }

    @Test void rejectsMissingCount() { assertThrows(IllegalArgumentException.class, () -> WriteValidation.volumes(null)); }

    @ParameterizedTest @ValueSource(ints = {0, 10_000})
    void acceptsRereadBoundaries(int value) { WriteValidation.reread(value); }

    @ParameterizedTest @ValueSource(ints = {-1, 10_001, Integer.MAX_VALUE})
    void rejectsInvalidReread(int value) {
        assertThrows(IllegalArgumentException.class, () -> WriteValidation.reread(value));
    }

    @Test void preservesNullReread() { WriteValidation.reread(null); }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {" ", "\t\n", "　"})
    void rejectsMissingOrBlankTitles(String title) {
        assertThrows(IllegalArgumentException.class, () -> WriteValidation.title(title));
    }

    @Test void preservesUnicodeAndMarkupAsTextAndChecksStorageLength() {
        WriteValidation.title("ÄÖÜ 漫画 & <img src=x onerror='alert(1)'>");
        WriteValidation.title("x".repeat(255));
        WriteValidation.title("📚".repeat(255));
        assertThrows(IllegalArgumentException.class, () -> WriteValidation.title("x".repeat(256)));
    }

    @ParameterizedTest @ValueSource(strings = {"https://example.invalid/a?b=1&c=2", "http://localhost:18080/cover.svg",
            "https://anilist.co/anything", "HTTP://example.invalid/a", "https://example.invalid/漫画",
            "https://bücher.example/漫画"})
    void acceptsHttpWithoutResolvingHost(String url) { assertEquals(url, WriteValidation.optionalUrl(url)); }

    @ParameterizedTest @NullAndEmptySource
    void preservesEmptyOptionalUrls(String url) { assertEquals(url, WriteValidation.optionalUrl(url)); }

    @ParameterizedTest @ValueSource(strings = {"javascript:alert(1)", "data:image/png;base64,x", "file:///tmp/a", "ftp://example.invalid/a",
            "/image.png", "//example.invalid/a", "https://user:password@example.invalid/a", "https://user@example.invalid",
            "https:///a", "http://", "https://example.invalid/a b", "https://example.invalid:65536/a", "https://example.invalid:0/a",
            "https://example.invalid:-1/a", "https://invalid%2ehost/a"})
    void rejectsUnsafeOrMalformedUrls(String url) {
        assertThrows(IllegalArgumentException.class, () -> WriteValidation.optionalUrl(url));
    }

    @Test void comparisonCannotCallUrlEqualityHashOrOpenConnection() throws Exception {
        URLStreamHandler failOnNetwork = new URLStreamHandler() {
            protected URLConnection openConnection(URL url) { throw new AssertionError("Netzwerkzugriff"); }
            protected boolean equals(URL first, URL second) { throw new AssertionError("URL.equals"); }
            protected int hashCode(URL url) { throw new AssertionError("URL.hashCode"); }
            protected java.net.InetAddress getHostAddress(URL url) { throw new AssertionError("DNS"); }
        };
        var first = URL.of(new java.net.URI("https://example.invalid/a"), failOnNetwork);
        var same = URL.of(new java.net.URI("https://example.invalid/a"), failOnNetwork);
        var other = URL.of(new java.net.URI("https://example.invalid/b"), failOnNetwork);
        assertTrue(WriteValidation.sameUrl(first, same));
        assertFalse(WriteValidation.sameUrl(first, other));
        assertFalse(WriteValidation.sameUrl(first, null));
        assertTrue(WriteValidation.sameUrl(null, null));
    }

    @ParameterizedTest @ValueSource(strings = {"-6.99", "-0.02", "0", "5.99", "99999999.99", "1e2"})
    void acceptsSignedMoneyWithoutChangingIt(String text) {
        assertEquals(new java.math.BigDecimal(text), WriteValidation.money(text));
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"NaN", "Infinity", "-Infinity", "1e999999", "100000000", "1e-999999", "five", "{}"})
    void rejectsNonFiniteAndPathologicalMoney(String text) {
        assertThrows(IllegalArgumentException.class, () -> WriteValidation.money(text));
    }

    @Test void optionalMissingImportFieldsAreOmittedAndInputIsNotMutated() {
        var source = new HashMap<String, String>(); source.put("Band 1 Bild Url", null);
        source.put("Erstveröffentlichung Herkunft", null); source.put("Band 1 Preis", "6,99 €");
        var result = WriteValidation.scrapedData(source);
        assertFalse(result.containsKey("Band 1 Bild Url"));
        assertEquals("6,99 €", result.get("Band 1 Preis"));
        assertTrue(source.containsKey("Band 1 Bild Url"));
        assertTrue(WriteValidation.scrapedData(null).isEmpty());
    }

    @Test void importObjectsAndUnsafeUrlsAreNotTrusted() {
        for (var value : new Object[]{Map.of("Band 1 Bild Url", "javascript:alert(1)"),
                Map.of("Cover URL", "file:///x"), Map.of("Titel", Map.of("x", "y")),
                Map.of("Band 1 Preis", "NaN"), Map.of("Band 1 href", "https://example.invalid/volumes/999999999999")}) {
            assertThrows(IllegalArgumentException.class, () -> WriteValidation.scrapedData(value));
        }
    }
}
