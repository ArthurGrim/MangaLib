package de.mangalib;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import de.mangalib.service.MangaScraper;

@SpringBootTest
public class MangaScraperTest {

    @Test
    public static void main(String[] args) {
        // Erstellen Sie eine Instanz Ihres Scrapers
        MangaScraper scraper = new MangaScraper();

        // Testen Sie den Scraper mit verschiedenen Indizes
        testScraper(scraper, "87");
    }

    private static void testScraper(MangaScraper scraper, String index) {
        System.out.println("Testing with index: " + index);
        try {
            // Rufen Sie die Methode des Scrapers auf, die die Daten extrahiert
            // Angenommen, die Methode heißt `scrapeData` und nimmt den Index als Parameter
            Map<String, String> extractedData = MangaScraper.scrapeMangaData(index);

            // Drucken Sie die extrahierten Daten
            if (extractedData != null) {
                extractedData.forEach((key, value) -> System.out.println(key + ": " + value));
            } else {
                System.out.println("Keine Daten für Index " + index + " gefunden.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("-------------------------------------------------");
    }
}

