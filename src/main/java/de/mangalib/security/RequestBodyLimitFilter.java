package de.mangalib.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.core.*;
import tools.jackson.core.json.JsonFactory;

/** Technischer Ressourcenrahmen vor Parameterbindung, JSON-Deserialisierung und Facharbeit.
 * Der Body wird höchstens bis Limit+1 gelesen, in kleinen Blöcken gehalten und danach
 * unverändert weitergereicht. Keine unbeschränkte ContentCachingRequestWrapper-Kopie.
 */
public final class RequestBodyLimitFilter extends OncePerRequestFilter {
    public static final int MAX_BODY_BYTES = 32 * 1024 * 1024;
    public static final int MAX_JSON_DEPTH = 16;
    public static final int MAX_JSON_STRING = 4096;
    public static final int MAX_JSON_NAME = 512;
    public static final int MAX_JSON_FIELDS = 30_064;
    public static final int MAX_JSON_ARRAY_VALUES = 30_064;
    public static final long MAX_JSON_TOKENS = 100_000;
    private final int byteLimit;
    private final JsonFactory json = JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder()
                    .maxNestingDepth(MAX_JSON_DEPTH).maxStringLength(MAX_JSON_STRING)
                    .maxNameLength(MAX_JSON_NAME).maxNumberLength(64)
                    .maxTokenCount(MAX_JSON_TOKENS).build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();

    public RequestBodyLimitFilter() { this(MAX_BODY_BYTES); }
    // Kleine Budgets für exakte Streaming-/Grenzwerttests; produktiv immer die feste Obergrenze.
    RequestBodyLimitFilter(int byteLimit) {
        if (byteLimit < 1) throw new IllegalArgumentException("Positives Bodylimit erforderlich.");
        this.byteLimit = byteLimit;
    }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return Set.of("GET", "HEAD", "OPTIONS", "TRACE").contains(request.getMethod());
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws IOException, ServletException {
        Body body;
        try {
            long declared = declaredLength(request);
            if (declared > byteLimit) throw new Rejected(413);
            if (Collections.list(request.getHeaders("Content-Type")).size() > 1) throw new Rejected(400);
            MediaType type = request.getContentType() == null ? null : MediaType.parseMediaType(request.getContentType());
            // Keine Upload-/Formular-Schreibschnittstelle vorhanden. Vor CSRF-getParameter
            // und MultipartResolver ablehnen, damit der Container keinen zweiten Bodyweg öffnet.
            if (type != null && (type.getType().equals("multipart")
                    || type.isCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))) throw new Rejected(415);
            var encodings = Collections.list(request.getHeaders("Content-Encoding"));
            if (!encodings.isEmpty() && (encodings.size() != 1
                    || !encodings.getFirst().equalsIgnoreCase("identity"))) throw new Rejected(415);
            body = readBounded(request.getInputStream());
            if (declared >= 0 && declared != body.length()) throw new Rejected(400);
            if (body.length() > 0 && type != null && (type.getSubtype().equals("json")
                    || type.getSubtype().endsWith("+json"))) validateJson(body, type);
        } catch (Rejected rejected) {
            reject(response, rejected.status); return;
        } catch (JacksonException | IllegalArgumentException | IOException invalid) {
            // Nie Parsertexte, Bodyauszüge, URL-/Dateiangaben oder Stacktraces zurückgeben.
            reject(response, 400); return;
        }
        // Controller-/Servicefehler nicht als Filterfehler verschlucken.
        chain.doFilter(new BodyRequest(request, body), response);
    }

    private long declaredLength(HttpServletRequest request) throws Rejected {
        var lengths = Collections.list(request.getHeaders("Content-Length"));
        var transfers = Collections.list(request.getHeaders("Transfer-Encoding"));
        if (lengths.size() > 1 || !lengths.isEmpty() && !transfers.isEmpty()
                || !transfers.isEmpty() && (transfers.size() != 1
                    || !transfers.getFirst().equalsIgnoreCase("chunked"))) throw new Rejected(400);
        if (lengths.isEmpty()) return -1;
        String value = lengths.getFirst();
        if (!value.matches("[0-9]{1,19}")) throw new Rejected(400);
        try { return Long.parseLong(value); }
        catch (NumberFormatException invalid) { throw new Rejected(400); }
    }

    private Body readBounded(InputStream input) throws IOException, Rejected {
        var blocks = new ArrayList<byte[]>();
        int total = 0;
        while (true) {
            byte[] block = new byte[Math.min(8192, byteLimit - total + 1)];
            int filled = 0;
            // Kleine Netzwerk-Reads dürfen nicht je ein neues gespeichertes Objekt erzeugen.
            while (filled < block.length) {
                int count = input.read(block, filled, block.length - filled);
                if (count < 0) break;
                if (count == 0) {
                    int single = input.read();
                    if (single < 0) break;
                    block[filled] = (byte) single; count = 1;
                }
                filled += count; total += count;
                if (total > byteLimit) throw new Rejected(413);
            }
            if (filled > 0) blocks.add(filled == block.length ? block : Arrays.copyOf(block, filled));
            if (filled < block.length) return new Body(blocks, total);
        }
    }

    private void validateJson(Body body, MediaType type) throws Rejected {
        var charset = type.getCharset();
        // Wie Springs Jackson-Converter: Unicode per Byte-Erkennung, andere Zeichensätze
        // per Reader. Andernfalls könnten Vorprüfung und Binding unterschiedliche Strings sehen.
        boolean unicode = charset == null || Set.of("UTF-8", "UTF-16", "UTF-16BE", "UTF-16LE",
                "UTF-32", "UTF-32BE", "UTF-32LE").contains(charset.name());
        try (var parser = unicode ? json.createParser(ObjectReadContext.empty(), body.open())
                : json.createParser(ObjectReadContext.empty(), new InputStreamReader(body.open(), charset))) {
            var arrays = new ArrayDeque<Boolean>();
            int fields = 0, elements = 0, roots = 0;
            JsonToken token;
            while ((token = parser.nextToken()) != null) {
                if (token == JsonToken.PROPERTY_NAME) {
                    if (++fields > MAX_JSON_FIELDS) throw new Rejected(413);
                } else if (!token.isStructEnd()) {
                    if (arrays.isEmpty() && ++roots > 1) throw new Rejected(400);
                    if (!arrays.isEmpty() && arrays.peek() && ++elements > MAX_JSON_ARRAY_VALUES)
                        throw new Rejected(413);
                }
                if (token == JsonToken.VALUE_STRING) parser.getString(); // Lazy Strings tatsächlich prüfen.
                if (token.isStructStart()) arrays.push(token == JsonToken.START_ARRAY);
                if (token.isStructEnd()) arrays.pop();
            }
        } catch (tools.jackson.core.exc.StreamConstraintsException excessive) {
            throw new Rejected(413);
        }
    }

    private static void reject(HttpServletResponse response, int status) throws IOException {
        response.setStatus(status);
        response.setContentType("text/plain;charset=UTF-8");
        response.setHeader("Connection", "close");
        response.getWriter().write(switch (status) {
            case 413 -> "Anfrage überschreitet die technischen Größenbegrenzungen.";
            case 415 -> "Nicht unterstütztes Anfrageformat.";
            default -> "Ungültige Anfrage.";
        });
    }

    private static final class Rejected extends Exception {
        final int status;
        Rejected(int status) { this.status = status; }
    }

    private record Body(List<byte[]> blocks, int length) {
        InputStream open() {
            return new SequenceInputStream(Collections.enumeration(blocks.stream()
                    .map(block -> (InputStream) new ByteArrayInputStream(block)).toList()));
        }
    }

    private static final class BodyRequest extends HttpServletRequestWrapper {
        private final ServletInputStream input;
        BodyRequest(HttpServletRequest original, Body body) {
            super(original);
            input = new ServletInputStream() {
                private final InputStream source = body.open();
                private int remaining = body.length();
                @Override public int read() throws IOException {
                    int value = source.read(); if (value >= 0) remaining--; return value;
                }
                @Override public int read(byte[] bytes, int off, int len) throws IOException {
                    int count = source.read(bytes, off, len); if (count > 0) remaining -= count; return count;
                }
                @Override public boolean isFinished() { return remaining == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) {
                    throw new IllegalStateException("MangaLib verarbeitet Request-Bodies synchron.");
                }
                @Override public void close() throws IOException { source.close(); }
            };
        }
        @Override public ServletInputStream getInputStream() { return input; }
        @Override public BufferedReader getReader() {
            String encoding = getCharacterEncoding();
            return new BufferedReader(new InputStreamReader(input,
                    encoding == null ? StandardCharsets.UTF_8 : java.nio.charset.Charset.forName(encoding)));
        }
    }
}
