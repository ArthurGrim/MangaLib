package de.mangalib;

import org.junit.jupiter.api.Test;

import de.mangalib.service.BandScraper;

import java.util.Map;

public class BandScraperTest {

    @Test
    public static void main(String[] args) {
        BandScraper scraper = new BandScraper();

        testScrapeBandData(scraper, "15295");
    }

    public static void testScrapeBandData(BandScraper scraper, String index) {
        System.out.println("Test with index: " + index);
        try {
            // Rufe die Methode auf, die getestet werden soll
            Map<String, String> extractedData = BandScraper.scrapeBandData(index);

            // Drucken Sie die extrahierten Daten
            if (extractedData != null) {
                extractedData.forEach((key, value) -> System.out.println(key + ": " + value));
            } else {
                System.out.println("Keine Daten für Index " + index + " gefunden.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            ;
        }
        System.out.println("------------------------------------------");
    }
}
