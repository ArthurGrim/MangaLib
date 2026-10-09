package de.mangalib;

import de.mangalib.service.BandScraper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("external-network")
@Disabled("Manueller Selenium-/Netzwerktest; nur nach ausdrücklicher Freigabe aktivieren.")
class BandScraperTest {
    @Test
    void scrapesBandMetadata() {
        var data = BandScraper.scrapeBandData("15295");
        assertNotNull(data.get("Titel"));
        assertFalse(data.get("Titel").isBlank());
        assertNotNull(data.get("BildUrl"));
        assertTrue(data.get("BildUrl").startsWith("https://"));
    }
}
