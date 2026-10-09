package de.mangalib;

import de.mangalib.service.MangaScraper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("external-network")
@Disabled("Manueller Selenium-/Netzwerktest; nur nach ausdrücklicher Freigabe aktivieren.")
class MangaScraperTest {
    @Test
    void scrapesSeriesMetadata() {
        var data = MangaScraper.scrapeMangaData("87");
        assertNotNull(data.get("Titel"));
        assertFalse(data.get("Titel").isBlank());
        assertNotNull(data.get("Deutsche Ausgabe Verlag"));
        assertFalse(data.get("Deutsche Ausgabe Verlag").isBlank());
    }
}
