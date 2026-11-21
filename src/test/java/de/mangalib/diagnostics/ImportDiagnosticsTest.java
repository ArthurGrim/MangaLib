package de.mangalib.diagnostics;

import de.mangalib.service.LiveScraperGateway;
import de.mangalib.windowsadapter.ProductionScraperClient;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Keine native Owner-Erzeugung: beide Produktionsgrenzen werden ausschließlich gemockt. */
class ImportDiagnosticsTest {
    @Test void requestCorrelationReachesImportBoundaryAndIsThenCleared() throws Exception {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(DiagnosticEvents.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>() {
            @Override protected void append(ch.qos.logback.classic.spi.ILoggingEvent event) {
                event.prepareForDeferredProcessing(); super.append(event);
            }
        };
        appender.start(); logger.addAppender(appender); org.slf4j.MDC.clear();
        try {
            var worker = mock(ProductionScraperClient.class);
            when(worker.series("1")).thenReturn(Map.of("Titel", "PRIVATE_TITLE"));
            var response = new org.springframework.mock.web.MockHttpServletResponse();
            new RequestDiagnosticsFilter().doFilter(new org.springframework.mock.web.MockHttpServletRequest("POST", "/synthetic"), response,
                    (req, res) -> new LiveScraperGateway(worker).scrapeSeries("1"));
            assertEquals(2, appender.list.size());
            for (var event : appender.list) {
                assertEquals(response.getHeader("X-MangaLib-Request-ID"), event.getMDCPropertyMap().get("requestId"));
                assertFalse(event.getFormattedMessage().contains("PRIVATE_TITLE"));
            }
            assertNull(org.slf4j.MDC.get("requestId")); verify(worker).series("1"); verifyNoMoreInteractions(worker);
        } finally { logger.detachAppender(appender); appender.stop(); org.slf4j.MDC.clear(); }
    }
    @Test void bothImportsDelegateExactlyOnceAndPreserveResults() {
        var worker = mock(ProductionScraperClient.class);
        var values = Map.of("Titel", "synthetic");
        when(worker.series("1")).thenReturn(values);
        when(worker.volume("2")).thenReturn(values);
        var gateway = new LiveScraperGateway(worker);
        assertSame(values, gateway.scrapeSeries("1"));
        assertSame(values, gateway.scrapeVolume("2"));
        verify(worker).series("1"); verify(worker).volume("2"); verifyNoMoreInteractions(worker);
    }
    @Test void failedImportIsNotRetriedOrReplacedByFallback() {
        var worker = mock(ProductionScraperClient.class);
        var failure = new IllegalStateException("SECRET");
        when(worker.series("1")).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> new LiveScraperGateway(worker).scrapeSeries("1")));
        verify(worker).series("1"); verifyNoMoreInteractions(worker);
    }
}
