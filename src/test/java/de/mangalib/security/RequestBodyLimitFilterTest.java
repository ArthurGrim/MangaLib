package de.mangalib.security;

import jakarta.servlet.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.core.Ordered;
import org.springframework.mock.web.*;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.apache.catalina.connector.Connector;
import org.apache.coyote.http11.AbstractHttp11Protocol;
import static org.junit.jupiter.api.Assertions.*;

/** Servlet- und Connector-Doubles/Objekte, kein Boot, JDBC, Socket oder HTTP-Server.
 * Simuliertes chunked ist bereits dekodierter Servletinhalt, kein Wire-Protokollnachweis.
 */
class RequestBodyLimitFilterTest {
    private MockHttpServletRequest request(String body, String type) {
        var request = new MockHttpServletRequest("POST", "/addMangaReihe");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        if (type != null) request.setContentType(type);
        return request;
    }

    private MockHttpServletResponse run(RequestBodyLimitFilter filter, MockHttpServletRequest request,
            int expected, boolean accepted) throws Exception {
        var response = new MockHttpServletResponse(); var called = new AtomicInteger();
        filter.doFilter(request, response, (r, s) -> {
            called.incrementAndGet(); r.getInputStream().transferTo(OutputStream.nullOutputStream());
        });
        assertEquals(expected, response.getStatus()); assertEquals(accepted ? 1 : 0, called.get());
        if (!accepted) {
            assertFalse(response.getContentAsString().contains("Exception"));
            assertFalse(response.getContentAsString().contains("synthetic-secret"));
            assertEquals("close", response.getHeader("Connection"));
        }
        return response;
    }

    @ParameterizedTest @CsvSource({"63,200,true", "64,200,true", "65,413,false"})
    void byteBoundaryIsEnforcedOnActualStream(int size, int status, boolean accepted) throws Exception {
        var stream = new GeneratedStream(size, 1);
        run(new RequestBodyLimitFilter(64), streaming(stream, "text/plain"), status, accepted);
        assertEquals(size, stream.readCount);
    }

    @ParameterizedTest @ValueSource(ints = {-1, 0, 1})
    void productionByteBoundaryIsAlsoExercisedWithoutAnUnboundedProducerBuffer(int delta) throws Exception {
        assertEquals(32 * 1024 * 1024, RequestBodyLimitFilter.MAX_BODY_BYTES);
        var stream = new GeneratedStream(RequestBodyLimitFilter.MAX_BODY_BYTES + delta, 8192);
        run(new RequestBodyLimitFilter(), streaming(stream, "text/plain"), delta > 0 ? 413 : 200, delta <= 0);
        assertEquals(RequestBodyLimitFilter.MAX_BODY_BYTES + delta, stream.readCount);
    }

    @Test void limitCountsUtf8BytesRatherThanCharacters() throws Exception {
        run(new RequestBodyLimitFilter(64), request("é".repeat(33), "text/plain"), 413, false);
    }

    @Test void unendingOrChunkedBodyStopsAfterLimitPlusOneByteAndNeverDrains() throws Exception {
        var stream = new GeneratedStream(Integer.MAX_VALUE, 3);
        var request = streaming(stream, "application/json"); request.addHeader("Transfer-Encoding", "chunked");
        run(new RequestBodyLimitFilter(64), request, 413, false);
        assertEquals(65, stream.readCount);
    }

    @ParameterizedTest @ValueSource(strings = {"-1", "+1", "1,1", "abc", "9223372036854775808", ""})
    void malformedLengthIsRejectedWithoutReading(String value) throws Exception {
        var stream = new GeneratedStream(20, 1); var request = streaming(stream, "application/json");
        request.addHeader("Content-Length", value);
        run(new RequestBodyLimitFilter(64), request, 400, false); assertEquals(0, stream.readCount);
    }

    @Test void declaredOversizeDoesNotOpenOrReadBody() throws Exception {
        var request = new MockHttpServletRequest("POST", "/scrape") {
            @Override public ServletInputStream getInputStream() { fail("Body darf nicht geöffnet werden"); return null; }
        };
        request.addHeader("Content-Length", "65"); run(new RequestBodyLimitFilter(64), request, 413, false);
    }

    @ParameterizedTest @CsvSource({"1,2,400", "3,2,400", "2,2,200"})
    void falseLengthCannotHideBytesOrTruncation(int declared, int actual, int status) throws Exception {
        var request = request(" ".repeat(actual), "text/plain"); request.addHeader("Content-Length", declared);
        run(new RequestBodyLimitFilter(64), request, status, status == 200);
    }

    @ParameterizedTest @ValueSource(strings = {"duplicate", "conflicting", "chunkedAndLength", "unsupportedTransfer"})
    void contradictoryFramingIsRejectedBeforeRead(String mode) throws Exception {
        var stream = new GeneratedStream(2, 1); var request = streaming(stream, "application/json");
        if (mode.equals("unsupportedTransfer")) request.addHeader("Transfer-Encoding", "gzip, chunked");
        else {
            request.addHeader("Content-Length", "2");
            if (mode.equals("chunkedAndLength")) request.addHeader("Transfer-Encoding", "chunked");
            else request.addHeader("Content-Length", mode.equals("duplicate") ? "2" : "3");
        }
        run(new RequestBodyLimitFilter(64), request, 400, false); assertEquals(0, stream.readCount);
    }

    @ParameterizedTest @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE", "CUSTOM"})
    void limitCoversEveryPotentialWriteMethod(String method) throws Exception {
        var request = request(" ".repeat(65), "text/plain"); request.setMethod(method);
        run(new RequestBodyLimitFilter(64), request, 413, false);
    }

    @ParameterizedTest @NullSource @ValueSource(strings = {"text/plain", "application/octet-stream", "application/vnd.mangalib+json"})
    void alternativeOrMissingMediaTypeCannotBypassByteLimit(String type) throws Exception {
        run(new RequestBodyLimitFilter(64), request(" ".repeat(65), type), 413, false);
    }

    @ParameterizedTest @ValueSource(strings = {"application/x-www-form-urlencoded", "multipart/form-data; boundary=x", "multipart/mixed; boundary=x"})
    void unsupportedFormsAndUploadsAreRejectedBeforeContainerParameterOrPartParsing(String type) throws Exception {
        var request = new MockHttpServletRequest("POST", "/buyItemUpdate") {
            @Override public ServletInputStream getInputStream() { fail("Kein Bodylesen"); return null; }
            @Override public String getParameter(String name) { fail("Keine Formularparameter"); return null; }
            @Override public Collection<jakarta.servlet.http.Part> getParts() { fail("Keine Uploaddateien"); return null; }
        };
        request.setContentType(type); run(new RequestBodyLimitFilter(), request, 415, false);
    }

    @ParameterizedTest @ValueSource(strings = {"gzip", "br", "identity,gzip"})
    void compressedBodiesCannotOpenADecompressionPath(String encoding) throws Exception {
        var request = request("{}", "application/json"); request.addHeader("Content-Encoding", encoding);
        run(new RequestBodyLimitFilter(), request, 415, false);
    }

    @Test void contradictoryContentTypesAreRejectedBeforeBodyWork() throws Exception {
        var stream = new GeneratedStream(2, 1);
        // MockHttpServletRequest ersetzt Content-Type statt zwei Rohheader zu behalten.
        var request = new MockHttpServletRequest("POST", "/editBand") {
            @Override public Enumeration<String> getHeaders(String name) {
                if (name.equalsIgnoreCase("Content-Type")) return Collections.enumeration(
                        List.of("application/json", "application/x-www-form-urlencoded"));
                return super.getHeaders(name);
            }
            @Override public ServletInputStream getInputStream() { return stream; }
        };
        request.setContentType("application/json");
        run(new RequestBodyLimitFilter(), request, 400, false); assertEquals(0, stream.readCount);
    }

    @Test void invalidMediaTypeAndBrokenStreamProduceOnlyNeutralErrors() throws Exception {
        run(new RequestBodyLimitFilter(), request("synthetic-secret", "invalid media type"), 400, false);
        var request = new MockHttpServletRequest("POST", "/editBand") {
            @Override public ServletInputStream getInputStream() {
                return new ServletInputStream() {
                    @Override public int read() throws IOException { throw new IOException("synthetic-secret"); }
                    @Override public boolean isFinished() { return false; }
                    @Override public boolean isReady() { return true; }
                    @Override public void setReadListener(ReadListener listener) { fail("Kein Async"); }
                };
            }
        };
        run(new RequestBodyLimitFilter(), request, 400, false);
    }

    @Test void bodyBytesUtf8AndReaderAreReplayedUnchanged() throws Exception {
        String value = "{\"titel\":\"ÄÖÜ 漫画 😀\"}"; var request = request(value, "application/json;charset=UTF-8");
        request.addHeader("Content-Length", value.getBytes(StandardCharsets.UTF_8).length);
        var response = new MockHttpServletResponse();
        new RequestBodyLimitFilter().doFilter(request, response, (r, s) -> assertEquals(value, r.getReader().readLine()));
        assertEquals(200, response.getStatus());
    }

    @Test void nonUnicodeCharsetCannotBypassTheDecodedStringLimit() throws Exception {
        // UTF-8-é wird unter dem expliziten Latin-1-Header zu zwei Zeichen, auch beim echten MVC-Binding.
        run(new RequestBodyLimitFilter(), request("{\"x\":\"" + "é".repeat(3000) + "\"}",
                "application/json;charset=ISO-8859-1"), 413, false);
    }

    @ParameterizedTest @ValueSource(strings = {"UTF-16LE", "ISO-8859-1"})
    void validAlternativeJsonEncodingsRemainAccepted(String charset) throws Exception {
        String body = "{\"titel\":\"ÄÖÜ\"}";
        var request = request(body, "application/json;charset=" + charset);
        request.setContent(body.getBytes(java.nio.charset.Charset.forName(charset)));
        run(new RequestBodyLimitFilter(), request, 200, true);
    }

    @ParameterizedTest @CsvSource({"4096,200", "4097,413"})
    void lazyJsonStringsAreMaterializedAndLimitedBeforeBinding(int size, int status) throws Exception {
        run(new RequestBodyLimitFilter(), request("{\"x\":\"" + "x".repeat(size) + "\"}", "application/json"), status, status == 200);
    }

    @ParameterizedTest @CsvSource({"512,200", "513,413"})
    void jsonPropertyNamesAreBounded(int size, int status) throws Exception {
        run(new RequestBodyLimitFilter(), request("{\"" + "k".repeat(size) + "\":0}", "application/json"), status, status == 200);
    }

    @ParameterizedTest @CsvSource({"16,200", "17,413"})
    void jsonDepthBoundaryIsExplicit(int depth, int status) throws Exception {
        run(new RequestBodyLimitFilter(), request("[".repeat(depth) + "0" + "]".repeat(depth), "application/json"), status, status == 200);
    }

    @ParameterizedTest @CsvSource({"30064,200", "30065,413"})
    void totalObjectFieldCountIsBounded(int count, int status) throws Exception {
        var json = new StringJoiner(",", "{", "}");
        for (int i = 0; i < count; i++) json.add("\"k" + i + "\":0");
        run(new RequestBodyLimitFilter(), request(json.toString(), "application/json"), status, status == 200);
    }

    @ParameterizedTest @CsvSource({"30064,200", "30065,413"})
    void totalArrayValuesAreBounded(int count, int status) throws Exception {
        run(new RequestBodyLimitFilter(), request("[" + "0,".repeat(count - 1) + "0]", "application/json"), status, status == 200);
    }

    @ParameterizedTest @CsvSource({"64,200", "65,413"})
    void jsonNumberLengthIsBoundedBeforeNumericBinding(int digits, int status) throws Exception {
        run(new RequestBodyLimitFilter(), request("{\"x\":" + "9".repeat(digits) + "}", "application/json"), status, status == 200);
    }

    @ParameterizedTest @CsvSource({"2,200", "3,413"})
    void tokenCountAtAndOneAboveTheLimitIsChecked(int extraValues, int status) throws Exception {
        // 24999 * 4 Objekt-Tokens + 2 Array-Tokens + 2/3 NULL-Tokens = 100000/100001.
        String body = "[" + "{\"x\":0},".repeat(24_999) + "null,".repeat(extraValues - 1) + "null]";
        run(new RequestBodyLimitFilter(), request(body, "application/json"), status, status == 200);
    }

    @Test void tokenLimitAlsoCoversManySmallNestedObjects() throws Exception {
        run(new RequestBodyLimitFilter(), request("[" + "{\"x\":0},".repeat(29_999) + "{\"x\":0}]", "application/json"), 413, false);
    }

    @ParameterizedTest @ValueSource(strings = {"{\"reread\":1,\"reread\":2}",
            "{\"reread\":1,\"\\u0072eread\":2}", "{\"scrapedData\":{\"x\":1,\"x\":2}}", "{} {}", "{\"synthetic-secret\":}"})
    void ambiguousOrMalformedJsonIsRejectedWithoutExposingItsContent(String json) throws Exception {
        var response = run(new RequestBodyLimitFilter(), request(json, "application/json"), 400, false);
        assertEquals("Ungültige Anfrage.", response.getContentAsString());
    }

    @Test void registrationRunsBeforeFormAndSecurityFiltersWithoutStartingTomcat() {
        var config = new RequestLimitsConfiguration(); var registration = config.requestBodyLimits();
        assertEquals(Set.of("/*"), new HashSet<>(registration.getUrlPatterns()));
        assertTrue(registration.getOrder() < Ordered.HIGHEST_PRECEDENCE + 10);
        assertTrue(registration.getOrder() < -100); assertFalse(registration.isAsyncSupported());
        var factory = new TomcatServletWebServerFactory(); config.customize(factory);
        var connector = new Connector();
        factory.getConnectorCustomizers().forEach(c -> c.customize(connector));
        assertEquals(2 * 1024 * 1024, connector.getMaxPostSize());
        assertEquals(0, ((AbstractHttp11Protocol<?>) connector.getProtocolHandler()).getMaxSwallowSize());
        assertEquals(-1, connector.getLocalPort()); // Kein init/start/getWebServer.
    }

    private MockHttpServletRequest streaming(GeneratedStream stream, String type) {
        var request = new MockHttpServletRequest("POST", "/addMangaReihe") {
            @Override public ServletInputStream getInputStream() { return stream; }
            @Override public long getContentLengthLong() { return -1; }
        };
        request.setContentType(type); return request;
    }

    private static final class GeneratedStream extends ServletInputStream {
        final int size, chunk; int readCount;
        GeneratedStream(int size, int chunk) { this.size = size; this.chunk = chunk; }
        @Override public int read() { if (readCount == size) return -1; readCount++; return ' '; }
        @Override public int read(byte[] bytes, int offset, int length) {
            if (readCount == size) return -1;
            int count = Math.min(Math.min(length, chunk), size - readCount);
            Arrays.fill(bytes, offset, offset + count, (byte) ' '); readCount += count; return count;
        }
        @Override public boolean isFinished() { return readCount == size; }
        @Override public boolean isReady() { return true; }
        @Override public void setReadListener(ReadListener listener) { throw new AssertionError("Kein Async-Read"); }
    }
}
