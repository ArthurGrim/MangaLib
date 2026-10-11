package de.mangalib.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.GenericFilterBean;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;

/** Prüft den tatsächlichen lokalen Authority-/Origin-Vertrag vor CSRF und Controllerarbeit. */
public final class LocalRequestFilter extends GenericFilterBean {
    private final int port;

    public LocalRequestFilter(int port) {
        this.port = port;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        var http = (HttpServletRequest) request;
        var reply = (HttpServletResponse) response;
        var hosts = Collections.list(http.getHeaders("Host"));
        // Kein Proxybetrieb: solche Header werden weder ausgewertet noch still akzeptiert.
        boolean forwarded = Collections.list(http.getHeaderNames()).stream().anyMatch(name ->
                name.equalsIgnoreCase("Forwarded") || name.toLowerCase(Locale.ROOT).startsWith("x-forwarded-"));
        if (forwarded || hosts.size() != 1 || !allowedAuthority(hosts.getFirst())
                || !"http".equals(http.getScheme()) || http.getServerPort() != port) {
            reject(reply);
            return;
        }
        var origins = Collections.list(http.getHeaders("Origin"));
        String expectedOrigin = "http://" + hosts.getFirst();
        // Browser lassen den HTTP-Standardport im Origin weg, auch bei explizitem Host :80.
        if (port == 80 && expectedOrigin.endsWith(":80")) {
            expectedOrigin = expectedOrigin.substring(0, expectedOrigin.length() - 3);
        }
        // Navigation ohne Origin ist erlaubt; Schreibzugriffe benötigen unabhängig davon CSRF.
        if (!origins.isEmpty() && (origins.size() != 1
                || !origins.getFirst().equalsIgnoreCase(expectedOrigin))) {
            reject(reply);
            return;
        }
        if (!Set.of("GET", "HEAD", "OPTIONS", "TRACE").contains(http.getMethod())) {
            var referers = Collections.list(http.getHeaders("Referer"));
            var sites = Collections.list(http.getHeaders("Sec-Fetch-Site"));
            // Ergänzende Browserindikatoren dürfen dem Session-/Origin-Vertrag nicht widersprechen.
            if ((!referers.isEmpty() && (referers.size() != 1 || !sameOriginReferer(referers.getFirst(), expectedOrigin)))
                    || (!sites.isEmpty() && (sites.size() != 1
                    || !Set.of("same-origin", "same-site", "none").contains(sites.getFirst())))) {
                reject(reply);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private boolean sameOriginReferer(String value, String expectedOrigin) {
        try {
            var uri = new URI(value);
            if (!"http".equalsIgnoreCase(uri.getScheme()) || uri.getRawAuthority() == null
                    || !allowedAuthority(uri.getRawAuthority())) return false;
            String origin = "http://" + uri.getRawAuthority();
            if (port == 80 && origin.endsWith(":80")) origin = origin.substring(0, origin.length() - 3);
            return expectedOrigin.equalsIgnoreCase(origin);
        } catch (URISyntaxException ignored) {
            return false;
        }
    }

    private boolean allowedAuthority(String authority) {
        String normalized = authority.toLowerCase(Locale.ROOT);
        return normalized.equals("localhost:" + port) || normalized.equals("127.0.0.1:" + port)
                || (port == 80 && (normalized.equals("localhost") || normalized.equals("127.0.0.1")));
    }

    private static void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write("Lokale Anfrage abgewiesen.");
    }
}
