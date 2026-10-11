package de.mangalib.service;

import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Unveränderte Live-Aufrufe für den regulären Betrieb; nicht im manuellen Artefakt enthalten. */
@Component
@Profile("!manual-test")
public final class LiveScraperGateway implements ScraperGateway {
    public Map<String, String> scrapeSeries(String index) {
        return MangaScraper.scrapeMangaData(index);
    }
    public Map<String, String> scrapeVolume(String index) {
        return BandScraper.scrapeBandData(index);
    }
}
