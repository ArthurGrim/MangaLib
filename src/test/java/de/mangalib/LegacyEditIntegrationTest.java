package de.mangalib;

import de.mangalib.entity.*;
import de.mangalib.repository.*;
import de.mangalib.service.*;
import de.mangalib.testsupport.GuardedTestDataSource;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Altbestands-Roundtrips mit echten JPA- und MVC-Komponenten, aktiven Sicherheitsfiltern
 * und ausschließlich neuen Rollback-Fixtures. Kein echter HTTP-Server oder Scraper.
 */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Transactional
@Rollback
class LegacyEditIntegrationTest {
    @Autowired EntityManager em;
    @Autowired DataSource dataSource;
    @Autowired MangaReiheRepository rows;
    @Autowired BandRepository bands;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    // JSON.stringify im Browser sendet explizite NULL-Werte. Die serverseitige
    // NON_NULL-Responsekonfiguration darf diese nicht schon im Testrequest entfernen.
    private final ObjectMapper requestMapper = new tools.jackson.databind.json.JsonMapper();
    private PersistenceTestFixtures.Graph graph;
    private TestDatabaseSnapshot.Snapshot initial;
    private MockedStatic<MangaScraper> seriesScraper;
    private MockedStatic<BandScraper> volumeScraper;

    @BeforeTransaction void captureBefore() {
        assertFalse(TestTransaction.isActive());
        assertInstanceOf(GuardedTestDataSource.class, dataSource);
        initial = TestDatabaseSnapshot.capture(em);
    }

    @BeforeEach void fixtures() throws Exception {
        assertTrue(TestTransaction.isActive()); assertTrue(TestTransaction.isFlaggedForRollback());
        seriesScraper = mockStatic(MangaScraper.class, i -> { throw new AssertionError("Live-Scraper gesperrt"); });
        volumeScraper = mockStatic(BandScraper.class, i -> { throw new AssertionError("Live-Scraper gesperrt"); });
        graph = PersistenceTestFixtures.persistGraph(em);
        flushClear();
    }

    @AfterEach void noEscapesOrScrapers() {
        try {
            assertTrue(TestTransaction.isActive()); assertTrue(TestTransaction.isFlaggedForRollback());
            if (seriesScraper != null) seriesScraper.verifyNoInteractions();
            if (volumeScraper != null) volumeScraper.verifyNoInteractions();
        } finally {
            if (seriesScraper != null) seriesScraper.close();
            if (volumeScraper != null) volumeScraper.close();
        }
    }

    @AfterTransaction void proveRollback() {
        assertFalse(TestTransaction.isActive()); assertNotNull(initial);
        assertEquals(initial, TestDatabaseSnapshot.capture(em), "Altbestand und Schema nach echtem Rollback unverändert");
    }

    private void flushClear() { em.flush(); em.clear(); }
    private MangaReihe row() { return rows.findById(graph.series().getId()).orElseThrow(); }
    private List<Band> volumes() {
        return bands.findByMangaReiheId(row().getId()).stream().sorted(Comparator.comparing(Band::getBandNr)).toList();
    }
    private Band volume() { return volumes().getFirst(); }

    private JsonNode read(String path, int status) throws Exception {
        var response = mvc.perform(get("http://localhost:8080" + path).header("Host", "localhost:8080"))
                .andReturn().getResponse();
        assertEquals(status, response.getStatus(), "GET " + path);
        assertTrue(response.getContentType().startsWith("application/json"));
        return mapper.readTree(response.getContentAsString());
    }

    private MockHttpServletResponse write(String path, Map<String, Object> input, int status) throws Exception {
        assertTrue(TestTransaction.isActive()); assertTrue(TestTransaction.isFlaggedForRollback());
        var result = mvc.perform(post("http://localhost:8080" + path).header("Host", "localhost:8080")
                .header("Origin", "http://localhost:8080").with(csrf()).contentType("application/json")
                .content(requestMapper.writeValueAsString(input))).andReturn();
        assertFalse(result.getRequest().isAsyncStarted(), "Schreibaktion muss in der Testtransaktion bleiben");
        assertTrue(TestTransaction.isActive()); assertTrue(TestTransaction.isFlaggedForRollback());
        var response = result.getResponse();
        assertEquals(status, response.getStatus(), "POST " + path);
        assertFalse(response.getContentAsString().contains("Exception"));
        return response;
    }

    /** Bildet die tatsächlichen Formularfelder aus dem echten JSON-Leseweg ab.
     * Unbekannter Reihenpreis bleibt der bestehende 0-Sentinel für unverändert.
     */
    private Map<String, Object> seriesForm() throws Exception {
        var data = read("/getMangaReiheData/" + row().getId(), 200);
        var input = new HashMap<String, Object>();
        input.put("mangaReiheId", Long.toString(data.get("id").longValue()));
        input.put("mangaIndex", data.hasNonNull("mangaIndex") ? data.get("mangaIndex").intValue() : null);
        for (String key : List.of("statusId", "verlagId", "typId", "formatId")) input.put(key, Long.toString(data.get(key).longValue()));
        input.put("sammelbandTypId", row().getMangaDetails().getSammelbaende().getId().toString());
        input.put("titel", "T02 Altbestand geändert " + row().getId());
        input.put("anzahlBaende", data.get("anzahlBaende").intValue());
        input.put("reread", data.get("reread").intValue());
        for (String key : List.of("istGelesen", "istEbayPreis", "istVergriffen"))
            input.put(key, data.hasNonNull(key) ? data.get(key).booleanValue() : null);
        for (String key : List.of("anilistUrl", "coverUrl"))
            input.put(key, data.hasNonNull(key) ? data.get(key).stringValue() : null);
        input.put("preisProBand", row().getPreisProBand() == null ? "0" : row().getPreisProBand().toPlainString());
        input.put("gesamtpreisAenderung", row().getAenderungGesamtpreis() == null ? "0" : row().getAenderungGesamtpreis().toPlainString());
        input.put("scrapedData", Map.of("istEdit", "true"));
        return input;
    }

    private Map<String, Object> bandForm() throws Exception {
        var data = read("/getBand/" + volume().getId(), 200);
        var input = new HashMap<String, Object>();
        for (String key : List.of("id", "bandNr", "bandIndex")) input.put(key, Long.toString(data.get(key).longValue()));
        for (String key : List.of("preis", "aenderungPreis"))
            input.put(key, data.hasNonNull(key) ? data.get(key).decimalValue().setScale(2).toPlainString() : null);
        input.put("istGelesen", data.get("istGelesen").booleanValue());
        input.put("reread", data.get("rereadNull").booleanValue() ? null : Integer.toString(data.get("reread").intValue()));
        input.put("istSpecial", data.get("specialNull").booleanValue() ? null : data.get("istSpecial").booleanValue());
        for (String key : List.of("bildUrl", "mpUrl")) input.put(key, data.get(key).stringValue());
        return input;
    }

    private TestDatabaseSnapshot.Snapshot snapshot() { flushClear(); return TestDatabaseSnapshot.capture(em); }
    private void unchanged(TestDatabaseSnapshot.Snapshot before) { assertEquals(before, snapshot(), "Keine Teilmutation bei Ablehnung"); }

    @ParameterizedTest @NullSource @ValueSource(ints = {0, 3})
    void bandRereadAndNullableSpecialSurviveJsonRoundTrip(Integer reread) throws Exception {
        volume().setReread(reread); volume().setIstSpecial(null); row().getMangaDetails().setReread(7);
        var before = snapshot(); var data = read("/getBand/" + volume().getId(), 200);
        assertTrue(data.get("id").isIntegralNumber()); assertTrue(data.get("preis").isNumber());
        assertTrue(data.get("istGelesen").isBoolean()); assertEquals(reread == null ? 0 : reread, data.get("reread").intValue());
        assertEquals(reread == null, data.get("rereadNull").booleanValue());
        assertFalse(data.get("istSpecial").booleanValue()); assertTrue(data.get("specialNull").booleanValue());
        write("/editBand", bandForm(), 204); unchanged(before);
        assertEquals(reread, volume().getReread()); assertNull(volume().getIstSpecial());
        assertEquals(7, row().getMangaDetails().getReread());
    }

    @ParameterizedTest @NullSource @ValueSource(booleans = {false, true})
    void knownAndUnknownFlagsSurviveIndependentEdits(Boolean flag) throws Exception {
        row().setIstEbayPreis(flag); row().setIstVergriffen(flag); volume().setIstSpecial(flag);
        flushClear(); var bandBefore = TestDatabaseSnapshot.capture(em).values().get("baende");
        write("/addMangaReihe", seriesForm(), 200); flushClear();
        assertEquals(flag, row().getIstEbayPreis()); assertEquals(flag, row().getIstVergriffen());
        assertEquals(bandBefore, TestDatabaseSnapshot.capture(em).values().get("baende"));
        write("/editBand", bandForm(), 204); flushClear(); assertEquals(flag, volume().getIstSpecial());
    }

    @ParameterizedTest @NullSource @ValueSource(ints = {0, 3})
    void metadataEditPreservesPartialReadAndEveryBandValue(Integer reread) throws Exception {
        row().getMangaDetails().setReread(reread); volume().setReread(null);
        flushClear(); var before = TestDatabaseSnapshot.capture(em).values().get("baende");
        var data = read("/getMangaReiheData/" + row().getId(), 200);
        assertEquals(reread == null, data.get("rereadNull").booleanValue());
        assertEquals(reread == null ? 0 : reread, data.get("reread").intValue());
        write("/addMangaReihe", seriesForm(), 200); flushClear();
        assertEquals(before, TestDatabaseSnapshot.capture(em).values().get("baende"));
        assertEquals(reread, row().getMangaDetails().getReread()); assertFalse(row().getMangaDetails().isIstGelesen());
        assertEquals("T02 Altbestand geändert " + row().getId(), row().getTitel());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void explicitReadBulkWorksEvenWhenSeriesValueAlreadyMatches(boolean read) throws Exception {
        row().getMangaDetails().setIstGelesen(read); volume().setReread(null); flushClear();
        var before = volumes().stream().map(Band::getReread).toList();
        var input = seriesForm(); input.put("applyReadToVolumes", true);
        write("/addMangaReihe", input, 200); flushClear();
        assertTrue(volumes().stream().allMatch(b -> b.isIstGelesen() == read));
        assertEquals(before, volumes().stream().map(Band::getReread).toList());
    }

    @ParameterizedTest @ValueSource(ints = {0, 3, 10_000})
    void explicitRereadBulkUsesMinimumAndDoesNotLowerHigherBand(int minimum) throws Exception {
        volume().setReread(null); volumes().get(1).setReread(10_000);
        row().getMangaDetails().setReread(minimum); flushClear();
        var input = seriesForm(); input.put("applyRereadToVolumes", true);
        write("/addMangaReihe", input, 200); flushClear();
        assertEquals(minimum, volume().getReread()); assertEquals(10_000, volumes().get(1).getReread());
        assertEquals(minimum, row().getMangaDetails().getReread());
        assertTrue(volume().isIstGelesen()); assertFalse(volumes().get(1).isIstGelesen());
    }

    @Test void explicitZeroAndFalseReplaceUnknownValuesWithoutImplicitBulk() throws Exception {
        row().getMangaDetails().setReread(null); volume().setReread(null); volume().setIstSpecial(null); flushClear();
        var input = seriesForm(); input.put("rereadEdited", true);
        write("/addMangaReihe", input, 200); flushClear();
        assertEquals(0, row().getMangaDetails().getReread()); assertNull(volume().getReread());
        var band = bandForm(); band.put("reread", "0"); band.put("istSpecial", false);
        write("/editBand", band, 204); flushClear();
        assertEquals(0, volume().getReread()); assertEquals(false, volume().getIstSpecial());
    }

    @Test void changedSeriesValuesAloneAndForgedImportOptionsNeverOverwriteBands() throws Exception {
        volume().setReread(null); flushClear();
        var before = TestDatabaseSnapshot.capture(em).values().get("baende");
        var input = seriesForm(); input.put("reread", 5); input.put("istGelesen", true);
        input.put("scrapedData", Map.of("istEdit", "true", "applyReadToVolumes", "true", "applyRereadToVolumes", "true"));
        write("/addMangaReihe", input, 200); flushClear();
        assertEquals(5, row().getMangaDetails().getReread()); assertTrue(row().getMangaDetails().isIstGelesen());
        assertEquals(before, TestDatabaseSnapshot.capture(em).values().get("baende"));
    }

    @ParameterizedTest @ValueSource(strings = {"omitted", "empty"})
    void legacyMissingAndEmptyBandRereadStillMeanZero(String mode) throws Exception {
        volume().setReread(3); flushClear(); var input = bandForm();
        if (mode.equals("omitted")) input.remove("reread"); else input.put("reread", "");
        write("/editBand", input, 204); flushClear(); assertEquals(0, volume().getReread());
    }

    @Test void missingCsrfAndForeignHostCannotMutateLegacyFixtures() throws Exception {
        var before = snapshot(); var input = bandForm(); input.put("istSpecial", true);
        assertEquals(403, mvc.perform(post("http://localhost:8080/editBand").header("Host", "localhost:8080")
                .header("Origin", "http://localhost:8080").contentType("application/json")
                .content(requestMapper.writeValueAsString(input))).andReturn().getResponse().getStatus());
        assertEquals(403, mvc.perform(post("http://localhost:8080/editBand").header("Host", "foreign.invalid:8080")
                .with(csrf()).contentType("application/json").content(requestMapper.writeValueAsString(input)))
                .andReturn().getResponse().getStatus());
        unchanged(before);
    }

    @ParameterizedTest @ValueSource(strings = {"anilistUrl", "coverUrl", "bildUrl", "mpUrl"})
    void historicalUrlSurvivesButDifferentUnsafeUrlIsRejected(String field) throws Exception {
        boolean series = field.equals("anilistUrl") || field.equals("coverUrl");
        if (field.equals("anilistUrl")) row().getMangaDetails().setAnilistUrl("https://user@example.invalid/old");
        if (field.equals("coverUrl")) row().getMangaDetails().setCoverUrl("/legacy/old.png");
        if (field.equals("bildUrl")) volume().setBildUrl(PersistenceTestFixtures.url("file:///synthetic/old.png"));
        if (field.equals("mpUrl")) volume().setMpUrl(PersistenceTestFixtures.url("https://user@example.invalid/old"));
        flushClear(); var input = series ? seriesForm() : bandForm(); Object old = input.get(field);
        if (!series) input.put("istSpecial", true);
        write(series ? "/addMangaReihe" : "/editBand", input, series ? 200 : 204); flushClear();
        var reloaded = series ? seriesForm() : bandForm(); assertEquals(old, reloaded.get(field));
        var before = snapshot(); reloaded.put(field, "https://different-user@example.invalid/new");
        write(series ? "/addMangaReihe" : "/editBand", reloaded, 400); unchanged(before);
    }

    @ParameterizedTest @ValueSource(strings = {"seriesClear", "bandClear", "bandReplace"})
    void intentionalUrlClearAndReplacementRetainExistingSemantics(String mode) throws Exception {
        flushClear(); var input = mode.equals("seriesClear") ? seriesForm() : bandForm();
        String old = (String) input.get(mode.equals("seriesClear") ? "anilistUrl" : "bildUrl");
        String replacement = mode.equals("bandReplace") ? "http://localhost:18080/synthetic/new.png" : "";
        input.put(mode.equals("seriesClear") ? "anilistUrl" : "bildUrl", replacement);
        write(mode.equals("seriesClear") ? "/addMangaReihe" : "/editBand", input, mode.equals("seriesClear") ? 200 : 204);
        flushClear();
        assertEquals(mode.equals("bandClear") ? old : replacement, mode.equals("seriesClear")
                ? row().getMangaDetails().getAnilistUrl() : volume().getBildUrl().toExternalForm());
    }

    @ParameterizedTest @ValueSource(strings = {"preis", "aenderungPreis", "seriesPrice"})
    void unknownMoneyRemainsUnknownWhileMetadataCanBeEdited(String field) throws Exception {
        if (field.equals("preis")) volume().setPreis(null);
        if (field.equals("aenderungPreis")) volume().setAenderungPreis(null);
        if (field.equals("seriesPrice")) row().setPreisProBand(null);
        row().setGesamtpreis(null); row().setAenderungGesamtpreis(null); flushClear();
        var original = TestDatabaseSnapshot.capture(em).values().get("baende");
        var data = read("/mangaReihe/" + row().getId() + "/bands", 200);
        if (!field.equals("seriesPrice")) assertEquals("Unbekannt", data.get(0).get("totalPreis").stringValue());
        write("/addMangaReihe", seriesForm(), 200); flushClear();
        assertNull(row().getGesamtpreis()); assertNull(row().getAenderungGesamtpreis());
        assertEquals(original, TestDatabaseSnapshot.capture(em).values().get("baende"));
        var input = bandForm(); input.put("istSpecial", true); write("/editBand", input, 204); flushClear();
        assertNull(row().getGesamtpreis()); assertNull(row().getAenderungGesamtpreis());
        if (field.equals("preis")) assertNull(volume().getPreis());
        if (field.equals("aenderungPreis")) assertNull(volume().getAenderungPreis());
        if (field.equals("seriesPrice")) assertNull(row().getPreisProBand());
        assertEquals(new BigDecimal("6.35"), volumes().get(1).getPreis());
    }

    @ParameterizedTest @ValueSource(strings = {"bandPrice", "seriesPrice", "priceImport", "extension"})
    void incompleteFinancialBasisRejectsBeforeAnyPartialMutation(String mode) throws Exception {
        volumes().get(1).setAenderungPreis(null); var before = snapshot();
        var input = mode.equals("bandPrice") ? bandForm() : seriesForm();
        if (mode.equals("bandPrice")) { input.put("preis", "8.99"); input.put("istSpecial", true); }
        if (mode.equals("seriesPrice")) input.put("preisProBand", "8.99");
        if (mode.equals("priceImport")) input.put("scrapedData", Map.of("istEdit", "true", "Band 1 Preis", "8.99"));
        if (mode.equals("extension")) input.put("anzahlBaende", 3);
        write(mode.equals("bandPrice") ? "/editBand" : "/addMangaReihe", input, 400); unchanged(before);
    }

    @Test void unchangedCentAmountsAndSumsAreNotRecomputed() throws Exception {
        volume().setPreis(new BigDecimal("8.49")); volume().setAenderungPreis(new BigDecimal("-0.02"));
        row().setGesamtpreis(new BigDecimal("43.21")); flushClear();
        var before = TestDatabaseSnapshot.capture(em);
        write("/editBand", bandForm(), 204); unchanged(before);
        write("/addMangaReihe", seriesForm(), 200); flushClear();
        assertEquals(new BigDecimal("43.21"), row().getGesamtpreis());
        assertEquals(new BigDecimal("8.49"), volume().getPreis());
        assertEquals(new BigDecimal("-0.02"), volume().getAenderungPreis());
    }

    @ParameterizedTest @ValueSource(strings = {"omnibus", "status", "publisher", "type", "format"})
    void missingNullableRelationIsRejectedWithoutReplacementOrPartialMutation(String missing) throws Exception {
        var input = seriesForm();
        switch (missing) {
            case "omnibus" -> row().getMangaDetails().setSammelbaende(null);
            case "status" -> row().setStatus(null);
            case "publisher" -> row().setVerlag(null);
            case "type" -> row().setTyp(null);
            case "format" -> row().setFormat(null);
            default -> fail("Unbekanntes Testfixture");
        }
        var before = snapshot();
        var error = read("/getMangaReiheData/" + row().getId(), 400);
        assertEquals("Bestand kann nicht bearbeitet werden: Beziehungen fehlen.", error.get("error").stringValue());
        write("/addMangaReihe", input, 400); unchanged(before);
        var band = bandForm(); band.put("istSpecial", true); write("/editBand", band, 400); unchanged(before);
    }

    @Test void missingDetailsOnNewBareSeriesNeverCreatesReplacement() throws Exception {
        var input = seriesForm(); var source = row();
        var bare = new MangaReihe(); bare.setTitel("T02 ohne Details " + UUID.randomUUID());
        bare.setStatus(source.getStatus()); bare.setVerlag(source.getVerlag()); bare.setTyp(source.getTyp()); bare.setFormat(source.getFormat());
        bare.setAnzahlBaende(2); bare.setPreisProBand(BigDecimal.ONE); bare.setGesamtpreis(new BigDecimal("2.00"));
        bare.setAenderungGesamtpreis(BigDecimal.ZERO); em.persist(bare);
        input.put("mangaReiheId", bare.getId().toString()); var before = snapshot();
        read("/getMangaReiheData/" + bare.getId(), 400); write("/addMangaReihe", input, 400); unchanged(before);
        assertNull(rows.findById(bare.getId()).orElseThrow().getMangaDetails());
    }
}
