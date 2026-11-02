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
        return worker.series(index);
    }
    public Map<String, String> scrapeVolume(String index) {
        return worker.volume(index);
    }
    @PreDestroy public void close(){worker.close();}
}
