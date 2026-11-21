package de.mangalib.service;

import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import de.mangalib.windowsadapter.ProductionScraperClient;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;

/** Beide Live-Importe ausschließlich über den gemeinsamen privaten Windows-Job. */
@Component
@Profile("!manual-test")
public final class LiveScraperGateway implements ScraperGateway {
    private final ProductionScraperClient worker;
    public LiveScraperGateway(){this(new ProductionScraperClient());}
    @Autowired public LiveScraperGateway(ProductionScraperClient worker){this.worker=java.util.Objects.requireNonNull(worker);}
    @Bean public static ProductionScraperClient sharedScraperWorker(org.springframework.core.env.Environment environment){
        return environment.matchesProfiles("test")?ProductionScraperClient.disabled():new ProductionScraperClient();
    }
    public Map<String, String> scrapeSeries(String index) {
        return observed(index, true);
    }
    public Map<String, String> scrapeVolume(String index) {
        return observed(index, false);
    }
    private Map<String, String> observed(String index, boolean series) {
        long start = System.nanoTime();
        var action = series ? de.mangalib.diagnostics.DiagnosticEvents.Action.IMPORT_SERIES
                : de.mangalib.diagnostics.DiagnosticEvents.Action.IMPORT_VOLUME;
        de.mangalib.diagnostics.DiagnosticEvents.started(action);
        try {
            var result = series ? worker.series(index) : worker.volume(index);
            // Erst der bestehende Owner prüft Gruppenende/EOF/Dispose und gibt den Slot frei.
            de.mangalib.diagnostics.DiagnosticEvents.completed(action, start);
            return result;
        } catch (RuntimeException failure) {
            // Der Controller protokolliert die bereinigte Exception einmal; kein zweiter Stack hier.
            org.slf4j.LoggerFactory.getLogger(de.mangalib.diagnostics.DiagnosticEvents.class)
                    .warn("event=import_unavailable action={} durationMs={}", action, (System.nanoTime()-start)/1_000_000);
            throw failure;
        }
    }
    @PreDestroy public void close(){worker.close();}
}
