package de.mangalib.validation;

import de.mangalib.entity.Band;
import de.mangalib.entity.MangaReihe;
import de.mangalib.repository.*;
import de.mangalib.service.*;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Service-Einstiegspunkte werden direkt mit Mocks auf Nebenwirkungsfreiheit geprüft. */
class WriteServiceSafetyTest {
    private MangaReiheRepository rows;
    private BandRepository volumes;
    private MangaReiheService series;
    private BandService bands;
    private EinkaufslisteService shopping;
    private ScraperGateway scraper;

    @BeforeEach void setup() {
        rows = mock(MangaReiheRepository.class); volumes = mock(BandRepository.class);
        series = new MangaReiheService(rows, mock(StatusRepository.class), mock(FormatRepository.class),
                mock(TypRepository.class), mock(VerlagRepository.class), volumes, mock(MangaDetailsRepository.class));
        bands = new BandService(); shopping = new EinkaufslisteService(mock(EinkaufslisteRepository.class));
        scraper = mock(ScraperGateway.class);
        ReflectionTestUtils.setField(bands, "bandRepository", volumes);
        ReflectionTestUtils.setField(bands, "scraperGateway", scraper);
    }

    @ParameterizedTest @ValueSource(ints = {0, -1, 10_001, Integer.MAX_VALUE})
    void everyVolumeLoopRejectsInvalidCountBeforeLookupOrWrites(int count) {
        assertThrows(IllegalArgumentException.class, () -> series.saveMangaReihe(null, 1L, 1L, 1L, 1L,
                "Test", count, BigDecimal.ONE, null, null, null, null, BigDecimal.ZERO, false, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> series.updateMangaReihe(1L, null, 1L, 1L, 1L, 1L,
                "Test", count, BigDecimal.ONE, null, null, null, null, null, BigDecimal.ZERO, false, 0, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> bands.createAndSaveBaende(new MangaReihe(), count,
                BigDecimal.ONE, BigDecimal.ZERO, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> bands.updateOrCreateBaende(new MangaReihe(), count,
                BigDecimal.ONE, BigDecimal.ZERO, Map.of(), true));
        assertThrows(IllegalArgumentException.class, () -> shopping.saveEinkaufslisteItem(null, 1L, 1L, 1L,
                "Test", count, 1d, null, null, null, null, 0d, null, Map.of()));
        verifyNoInteractions(rows, volumes, scraper);
    }

    @ParameterizedTest @ValueSource(ints = {-1, 10_001})
    void rereadGuardIsAlsoEnforcedByServices(int reread) {
        var band = new Band(); band.setReread(reread);
        assertThrows(IllegalArgumentException.class, () -> bands.updateBand(band));
        assertThrows(IllegalArgumentException.class, () -> series.updateMangaReihe(1L, null, 1L, 1L, 1L, 1L,
                "Test", 1, BigDecimal.ONE, null, null, null, null, null, BigDecimal.ZERO, false, reread, Map.of()));
        verifyNoInteractions(rows, volumes);
    }

    @ParameterizedTest @ValueSource(strings = {"", "0", "-1", "1.2", "abc", "2147483648"})
    void invalidAutofillIndexCannotReachScraper(String index) {
        assertThrows(IllegalArgumentException.class, () -> bands.autofillBandData(index));
        verifyNoInteractions(scraper);
    }

    @Test void missingOptionalVolumeDataDoesNotThrow() {
        var output = new java.util.HashMap<String, String>(); output.put("Preis", null); output.put("BildUrl", null);
        when(scraper.scrapeVolume("123")).thenReturn(output);
        assertTrue(bands.autofillBandData("123").isEmpty());
        verify(scraper).scrapeVolume("123");
    }

    @Test void validAutofillMaintainsPriceImageAndLinkContract() {
        when(scraper.scrapeVolume("123")).thenReturn(Map.of("BildUrl", "https://example.invalid/a.jpg", "Preis", "6.99 €"));
        assertEquals(Map.of("BildUrl", "https://example.invalid/a.jpg", "Preis", "6.99",
                "mpUrl", "https://www.manga-passion.de/volumes/123"), bands.autofillBandData("123"));
    }

    static java.util.stream.Stream<String> allowedAttributes() { return MangaReiheService.SORT_ATTRIBUTES.stream(); }

    @ParameterizedTest @MethodSource("allowedAttributes")
    void bothSortOverloadsPassOnlyAllowlistedProperties(String attribute) {
        series.findAllSorted(attribute);
        series.findAllSorted(attribute, Sort.Direction.DESC);
        verify(rows).findAll(Sort.by(attribute));
        verify(rows).findAll(Sort.by(Sort.Direction.DESC, attribute));
    }

    @ParameterizedTest @ValueSource(strings = {"", " titel", "titel ", "title", "mangaDetails.coverUrl", "status", "LOWER(titel)", "titel desc;drop table mangareihe"})
    void unknownSortAttributeNeverReachesRepository(String attribute) {
        assertThrows(IllegalArgumentException.class, () -> series.findAllSorted(attribute));
        assertThrows(IllegalArgumentException.class, () -> series.findAllSorted(attribute, Sort.Direction.ASC));
        verifyNoInteractions(rows);
    }

    @Test void nullSortAndDirectionAreControlled() {
        assertThrows(IllegalArgumentException.class, () -> series.findAllSorted(null));
        assertThrows(IllegalArgumentException.class, () -> series.findAllSorted("titel", null));
        verifyNoInteractions(rows);
    }

    @Test void malformedImportIsRejectedBeforeFirstRepositoryAccess() {
        assertThrows(IllegalArgumentException.class, () -> series.saveMangaReihe(null, 1L, 1L, 1L, 1L,
                "Test", 1, BigDecimal.ONE, null, null, null, null, BigDecimal.ZERO, false,
                Map.of("Band 1 Bild Url", "file:///private")));
        verifyNoInteractions(rows, volumes);
    }

    @Test void actualBandUpdateNeverUsesUrlEqualityOrDns() throws Exception {
        var handler = new java.net.URLStreamHandler() {
            protected java.net.URLConnection openConnection(java.net.URL url) { throw new AssertionError("Netzwerk"); }
            protected boolean equals(java.net.URL a, java.net.URL b) { throw new AssertionError("URL.equals"); }
            protected int hashCode(java.net.URL url) { throw new AssertionError("URL.hashCode"); }
            protected java.net.InetAddress getHostAddress(java.net.URL url) { throw new AssertionError("DNS"); }
        };
        var oldUrl = java.net.URL.of(new java.net.URI("https://example.invalid/a"), handler);
        var sameUrl = java.net.URL.of(new java.net.URI("https://example.invalid/a"), handler);
        var newUrl = java.net.URL.of(new java.net.URI("https://example.invalid/b"), handler);
        var row = new MangaReihe(); row.setId(1L); row.setAnzahlBaende(1); row.setAenderungGesamtpreis(BigDecimal.ZERO);
        var detail = new de.mangalib.entity.MangaDetails(); detail.setMangaReihe(row); row.setMangaDetails(detail);
        var stored = new Band(); stored.setId(1L); stored.setMangaReihe(row); stored.setPreis(BigDecimal.ONE);
        stored.setAenderungPreis(BigDecimal.ZERO); stored.setBildUrl(oldUrl); stored.setMpUrl(oldUrl);
        var input = new Band(); input.setId(1L); input.setBildUrl(sameUrl); input.setMpUrl(newUrl);
        when(volumes.findById(1L)).thenReturn(Optional.of(stored));
        when(volumes.findByMangaReiheId(1L)).thenReturn(java.util.List.of(stored));
        ReflectionTestUtils.setField(bands, "mangaReiheRepository", rows);
        bands.updateBand(input);
        assertSame(oldUrl, stored.getBildUrl()); assertSame(newUrl, stored.getMpUrl());
        verify(volumes).save(stored); verify(rows).save(row);
    }

    @Test void unknownShoppingReferenceCannotBeStoredAsNull() {
        var publishers = mock(VerlagService.class);
        ReflectionTestUtils.setField(shopping, "verlagService", publishers);
        assertThrows(IllegalArgumentException.class, () -> shopping.saveEinkaufslisteItem(null, 123L, 1L, 1L,
                "Test", 1, 5.99d, null, null, null, null, -0.02d, null, Map.of()));
        verify(publishers).getVerlagById(123L);
        verifyNoInteractions(ReflectionTestUtils.getField(shopping, "einkaufslisteRepository"));
    }

    @Test void unknownOmnibusReferenceIsCheckedBeforeShoppingInsert() {
        var publishers = mock(VerlagService.class); var types = mock(TypService.class); var formats = mock(FormatService.class);
        var omnibuses = mock(SammelbandService.class);
        when(publishers.getVerlagById(1L)).thenReturn(Optional.of(new de.mangalib.entity.Verlag()));
        when(types.getTypById(1L)).thenReturn(Optional.of(new de.mangalib.entity.Typ()));
        when(formats.getFormatById(1L)).thenReturn(Optional.of(new de.mangalib.entity.Format()));
        ReflectionTestUtils.setField(shopping, "verlagService", publishers);
        ReflectionTestUtils.setField(shopping, "typService", types); ReflectionTestUtils.setField(shopping, "formatService", formats);
        ReflectionTestUtils.setField(shopping, "sammelbaendeService", omnibuses);
        assertThrows(IllegalArgumentException.class, () -> shopping.saveEinkaufslisteItem(null, 1L, 1L, 1L,
                "Test", 1, 5.99d, null, null, null, 123L, -0.02d, null, Map.of()));
        verify(omnibuses).findById(123L);
        verifyNoInteractions(ReflectionTestUtils.getField(shopping, "einkaufslisteRepository"));
    }
}
