package de.mangalib.diagnostics;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Diagnosehülle ohne Requestinhalte; die ID bezeichnet eine Anfrage, keinen Persistenznachweis. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestDiagnosticsFilter extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(RequestDiagnosticsFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        var previous = MDC.getCopyOfContextMap();
        long start = System.nanoTime();
        String id = UUID.randomUUID().toString();
        MDC.put("requestId", id);
        response.setHeader("X-MangaLib-Request-ID", id);
        boolean failed = false;
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException failure) {
            failed = true;
            // Exceptiontexte und Ursachen können SQL, URLs oder Secrets enthalten.
            // Nur den technischen Stack ohne ursprüngliche Meldung/Ursachenkette übernehmen.
            var diagnostic = new Exception("Request execution failed");
            diagnostic.setStackTrace(failure.getStackTrace());
            LOG.error("event=http_failure requestId={}", id, diagnostic);
            throw failure;
        } finally {
            try {
                long millis = (System.nanoTime() - start) / 1_000_000;
                int status = response.getStatus();
                // Nur das serverseitige MVC-Routenmuster, niemals URI/Query oder Formularwerte.
                Object mapping = request.getAttribute(org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
                String route = mapping instanceof String text && text.matches("[/a-zA-Z0-9{}_.-]{1,120}") ? text : "unmapped";
                String method = request.getMethod();
                if (method == null || !java.util.Set.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS").contains(method)) method = "OTHER";
                String format = "event=http_complete requestId={} method={} route={} responseStatusAtFilterEnd={} durationMs={}";
                if (failed || status >= 500) LOG.error(format, id, method, route, status, millis);
                else if (status >= 400) LOG.warn(format, id, method, route, status, millis);
                else if (java.util.Set.of("POST", "PUT", "PATCH", "DELETE").contains(method)) LOG.info(format, id, method, route, status, millis);
                else LOG.debug(format, id, method, route, status, millis);
            } finally {
                if (previous == null) MDC.clear();
                else MDC.setContextMap(previous);
            }
        }
    }
}
