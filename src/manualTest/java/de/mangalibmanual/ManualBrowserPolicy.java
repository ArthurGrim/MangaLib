package de.mangalibmanual;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

/** Nur manuell: auch versehentlich übernommene Cover dürfen keine externen Bildrequests erzeugen. */
public final class ManualBrowserPolicy extends OncePerRequestFilter {
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Kein script-src: bestehende Inline-Interaktionen und die S01-Eventprobe bleiben prüfbar.
        response.setHeader("Content-Security-Policy", "img-src 'self' http://localhost:18080 http://127.0.0.1:18080; connect-src 'self'; object-src 'none'; frame-src 'none'");
        chain.doFilter(request, response);
    }
}
