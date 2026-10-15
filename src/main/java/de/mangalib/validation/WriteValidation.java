package de.mangalib.validation;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Technische Eingabegrenzen; keine Änderung der Preis-, Lese- oder Kaufregeln. */
public final class WriteValidation {
    // Großzügig auch für sehr lange Reihen, begrenzt aber die synchronen Speicher-Schleifen.
    public static final int MIN_VOLUMES = 1;
    public static final int MAX_VOLUMES = 10_000;
    public static final int MAX_REREAD = 10_000;
    // Bestehende String-Mappings haben die JPA-Standardlänge 255.
    public static final int MAX_TEXT_LENGTH = 255;
    private static final BigDecimal MONEY_LIMIT = new BigDecimal("100000000");

    private WriteValidation() { }

    public static void require(boolean condition) {
        if (!condition) throw new IllegalArgumentException("Ungültige Eingabe.");
    }

    public static void volumes(Integer value) {
        require(value != null && value >= MIN_VOLUMES && value <= MAX_VOLUMES);
    }

    public static void reread(Integer value) {
        require(value == null || value >= 0 && value <= MAX_REREAD);
    }

    public static void index(Integer value) {
        require(value == null || value > 0);
    }

    public static void title(String value) {
        require(value != null && !value.isBlank()
                && value.codePointCount(0, value.length()) <= MAX_TEXT_LENGTH);
    }

    /** Vor BigDecimal-Verarbeitung Länge/Exponent begrenzen, ohne Vorzeichenregeln zu erfinden. */
    public static BigDecimal money(String value) {
        require(value != null && !value.isBlank() && value.length() <= 64);
        BigDecimal number = new BigDecimal(value);
        money(number);
        return number;
    }

    public static void money(BigDecimal value) {
        require(value == null || value.scale() >= -8 && value.scale() <= 10
                && value.abs().compareTo(MONEY_LIMIT) < 0);
    }

    /** URI-Parsing löst keine Hosts auf. Leere optionale Werte bleiben unverändert. */
    public static String optionalUrl(String value) {
        if (value == null || value.isEmpty()) return value;
        require(value.codePointCount(0, value.length()) <= MAX_TEXT_LENGTH);
        try {
            URI uri = new URI(value);
            require(uri.isAbsolute() && !uri.isOpaque()
                    && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getRawUserInfo() == null
                    && (uri.getPort() == -1 || uri.getPort() >= 1 && uri.getPort() <= 65535));
            // URI.getHost() erkennt internationale Domainnamen nicht. IDN-Konvertierung
            // prüft deren Syntax, ohne DNS und ohne den gespeicherten String zu verändern.
            if (uri.getHost() == null) {
                URL parsed = uri.toURL();
                require(uri.getRawAuthority() != null && !uri.getRawAuthority().contains("@")
                        && !uri.getRawAuthority().contains(":-") && !parsed.getHost().isEmpty()
                        && (parsed.getPort() == -1 || parsed.getPort() >= 1 && parsed.getPort() <= 65535));
                java.net.IDN.toASCII(parsed.getHost(), java.net.IDN.USE_STD3_ASCII_RULES);
            }
            return value;
        } catch (java.net.URISyntaxException | java.net.MalformedURLException e) {
            throw new IllegalArgumentException("Ungültige Eingabe.");
        }
    }

    public static URL toUrl(String value) {
        optionalUrl(value);
        if (value == null || value.isEmpty()) return null;
        try {
            return new URI(value).toURL();
        } catch (java.net.URISyntaxException | java.net.MalformedURLException e) {
            throw new IllegalArgumentException("Ungültige Eingabe.");
        }
    }

    /** Absichtlich syntaktisch: URL.equals/hashCode können DNS-Auflösung auslösen. */
    public static boolean sameUrl(URL first, URL second) {
        return Objects.equals(first == null ? null : first.toExternalForm(),
                second == null ? null : second.toExternalForm());
    }

    /** Nur Stringwerte; fehlende/null optionale Importfelder bedeuten keine Übernahme. */
    public static Map<String, String> scrapedData(Object input) {
        if (input == null) return new LinkedHashMap<>();
        require(input instanceof Map<?, ?>);
        Map<?, ?> source = (Map<?, ?>) input;
        require(source.size() <= MAX_VOLUMES * 3 + 32);
        Map<String, String> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            require(key instanceof String && ((String) key).length() <= MAX_TEXT_LENGTH);
            require(value == null || value instanceof String);
            if (value == null) return;
            String name = (String) key;
            String text = (String) value;
            require(text.codePointCount(0, text.length()) <= MAX_TEXT_LENGTH);
            if (name.matches("Band \\d+ (Bild Url|href)")
                    || java.util.Set.of("Cover", "Cover URL", "BildUrl", "mpUrl", "anilistUrl", "coverUrl").contains(name)) {
                optionalUrl(text);
                if (name.endsWith("href") && !text.isEmpty()) {
                    var matcher = java.util.regex.Pattern.compile("volumes/(\\d+)").matcher(text);
                    if (matcher.find()) index(Integer.valueOf(matcher.group(1)));
                }
            }
            if ((name.matches("Band \\d+ Preis") || name.equals("Preis")) && !text.isBlank()) {
                money(text.replace("€", "").trim().replace(",", "."));
            }
            result.put(name, text);
        });
        return result;
    }
}
