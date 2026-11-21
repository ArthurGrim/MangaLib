package de.mangalib.diagnostics;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.classic.spi.ILoggingEvent;
import static org.junit.jupiter.api.Assertions.*;

class RequestDiagnosticsFilterTest {
    @Test void recordsOnlyServerTemplateAndNeverActualPathOrInvalidTemplate() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(RequestDiagnosticsFilter.class);
        var appender = new ListAppender<ILoggingEvent>(); appender.start(); logger.addAppender(appender);
        try {
            for (String route : List.of("/items/{id}", "SECRET\r\nforged")) {
                var request = new MockHttpServletRequest("POST", "/items/SECRET?token=SECRET");
                var response = new MockHttpServletResponse();
                new RequestDiagnosticsFilter().doFilter(request, response, (req, res) ->
                    request.setAttribute(org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, route));
            }
            assertEquals(2, appender.list.size());
            assertTrue(appender.list.get(0).getFormattedMessage().contains("route=/items/{id}"));
            assertTrue(appender.list.get(1).getFormattedMessage().contains("route=unmapped"));
            assertTrue(appender.list.stream().noneMatch(e -> e.getFormattedMessage().contains("SECRET")));
        } finally { MDC.clear(); logger.detachAppender(appender); appender.stop(); }
    }
    @Test void clearsEmptyContextAndDoesNotReuseClientId() throws Exception {
        MDC.clear();
        var filter = new RequestDiagnosticsFilter();
        var first = new MockHttpServletResponse();
        var second = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(), first, (req, res) -> {});
        filter.doFilter(new MockHttpServletRequest(), second, (req, res) -> {});
        assertNull(MDC.get("requestId"));
        assertNotEquals(first.getHeader("X-MangaLib-Request-ID"), second.getHeader("X-MangaLib-Request-ID"));
    }
    @Test void generatesOwnIdAndRestoresContextOnSuccessAndFailure() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(RequestDiagnosticsFilter.class);
        var appender = new ListAppender<ILoggingEvent>(); appender.start(); logger.addAppender(appender);
        try {
            for (boolean fail : List.of(false, true)) {
                MDC.put("existing", "preserve");
                var request = new MockHttpServletRequest("POST", "/private-title?token=SECRET");
                request.addHeader("X-MangaLib-Request-ID", "SECRET\r\nforged");
                var response = new MockHttpServletResponse();
                var filter = new RequestDiagnosticsFilter();
                var failure = new IllegalArgumentException("SECRET database credentials");
                jakarta.servlet.FilterChain chain = (req, res) -> {
                    assertNotNull(MDC.get("requestId"));
                    assertEquals(MDC.get("requestId"), response.getHeader("X-MangaLib-Request-ID"));
                    if (fail) throw failure;
                    response.setStatus(400);
                };
                if (fail) assertSame(failure, assertThrows(IllegalArgumentException.class, () -> filter.doFilter(request, response, chain)));
                else filter.doFilter(request, response, chain);
                assertEquals("preserve", MDC.get("existing")); assertNull(MDC.get("requestId"));
                assertTrue(response.getHeader("X-MangaLib-Request-ID").matches("[a-f0-9-]{36}"));
            }
            assertEquals(3, appender.list.size());
            for (var event : appender.list) {
                assertFalse(event.getFormattedMessage().contains("SECRET"));
                if (event.getThrowableProxy() != null) {
                    assertEquals("Request execution failed", event.getThrowableProxy().getMessage());
                    assertNull(event.getThrowableProxy().getCause());
                }
            }
        } finally { MDC.clear(); logger.detachAppender(appender); appender.stop(); }
    }
}
