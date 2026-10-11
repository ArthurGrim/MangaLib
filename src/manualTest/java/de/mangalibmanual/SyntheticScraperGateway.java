package de.mangalibmanual;

import java.util.HashMap;
import java.util.Map;
import de.mangalib.service.ScraperGateway;
import de.mangalib.service.ScraperRequestRejectedException;

/** Deterministische Doubles ohne Browser, Netzwerk, JDBC oder Ausweichpfad zum Live-Scraper. */
public final class SyntheticScraperGateway implements ScraperGateway {
    public static final String COVER = "http://localhost:18080/manual-test/cover.svg";
    private void check(String index) {
        if (index == null || !java.util.Set.of("900001", "900002").contains(index))
            throw new ScraperRequestRejectedException();
    }
    public Map<String, String> scrapeVolume(String index) {
        check(index);
        return new HashMap<>(Map.of("BildUrl", COVER, "Preis", "6.99 €"));
    }
    public Map<String, String> scrapeSeries(String index) {
        check(index);
        var data = new HashMap<String, String>();
        data.put("Titel", "MANUELL " + index + " – ÄÖÜ & Sonderzeichen");
        data.put("Deutsche Ausgabe Verlag", "Testverlag");
        data.put("Deutsche Ausgabe Format", "Print");
        data.put("Deutsche Ausgabe Status", "Laufend");
        data.put("Deutsche Ausgabe Bände", "2");
        data.put("Erstveröffentlichung Herkunft", "Japan");
        data.put("Erstveröffentlichung Typ", "Manga");
        data.put("Erstveröffentlichung Status", "Laufend");
        data.put("Erstveröffentlichung Startjahr", "2024");
        data.put("Erstveröffentlichung Bände", "2");
        data.put("Cover URL", COVER);
        for (int band = 1; band <= 2; band++) {
            data.put("Band " + band + " Bild Url", COVER);
            data.put("Band " + band + " Preis", "6.99");
            data.put("Band " + band + " href", "http://localhost:18080/home?manual-band=" + band);
        }
        return data;
    }
}
