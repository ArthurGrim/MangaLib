package de.mangalib;

import de.mangalib.entity.*;
import de.mangalib.repository.*;
import de.mangalib.service.*;
import de.mangalib.testsupport.GuardedTestDataSource;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
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
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Echte JPA-/MVC-Prüfung. Alle Fixtures gehören zur zurückzurollenden Testtransaktion.
 * Physische Vorher-/Nachher-Snapshots prüfen zusätzlich Daten und Schema außerhalb dieser Transaktion.
 */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Transactional
@Rollback
class ReadingStatusIntegrationTest {
    @Autowired EntityManager em;
    @Autowired DataSource dataSource;
    @Autowired MangaReiheRepository rows;
    @Autowired BandRepository bands;
    @Autowired EinkaufslisteRepository shopping;
    @Autowired MangaDetailsRepository detailsRepository;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    private PersistenceTestFixtures.Graph graph;
    private TestDatabaseSnapshot.Snapshot initial;
    private MockedStatic<MangaScraper> seriesScraper;
    private MockedStatic<BandScraper> volumeScraper;

    @BeforeTransaction void beforeTransaction() {
        assertFalse(TestTransaction.isActive());
        assertInstanceOf(GuardedTestDataSource.class, dataSource);
        initial = TestDatabaseSnapshot.capture(em);
    }

    @BeforeEach void fixtures() throws Exception {
        assertTrue(TestTransaction.isActive()); assertTrue(TestTransaction.isFlaggedForRollback());
        seriesScraper = mockStatic(MangaScraper.class, invocation -> { throw new AssertionError("Live-Scraper gesperrt"); });
        volumeScraper = mockStatic(BandScraper.class, invocation -> { throw new AssertionError("Live-Scraper gesperrt"); });
        graph = PersistenceTestFixtures.persistGraph(em); // Prüft vor INSERT DB/Benutzer/InnoDB.
        graph.series().setMangaIndex(unusedIndex());
        flushClear();
    }

    @AfterEach void noScrapersOrTransactionEscape() {
        try {
            assertTrue(TestTransaction.isActive()); assertTrue(TestTransaction.isFlaggedForRollback());
            if (seriesScraper != null) seriesScraper.verifyNoInteractions();
            if (volumeScraper != null) volumeScraper.verifyNoInteractions();
        } finally {
            if (seriesScraper != null) seriesScraper.close();
            if (volumeScraper != null) volumeScraper.close();
        }
    }

    @AfterTransaction void proveActualRollback() {
        assertFalse(TestTransaction.isActive()); assertNotNull(initial);
        assertEquals(initial, TestDatabaseSnapshot.capture(em), "Fixtures/Altbestand/Schema nach Rollback unverändert");
    }

    private void flushClear() { em.flush(); em.clear(); }
    private MangaReihe row() { return rows.findById(graph.series().getId()).orElseThrow(); }
    private List<Band> volumes() { return bands.findByMangaReiheId(graph.series().getId()); }
    private int unusedIndex() {
        int index;
        do { index = 1 + (UUID.randomUUID().hashCode() & 0x3fffffff); } while (!rows.findByMangaIndex(index).isEmpty());
        return index;
    }

    /** Keine Entity-/URL-Gleichheit: skalare Werte bleiben auch nach clear() vergleichbar, ohne DNS. */
    private record BandState(Long id, Integer number, Integer index, Long parent, boolean read, Integer reread,
            BigDecimal price, BigDecimal adjustment, String image, String link, Boolean special,
            LocalDate created, LocalDate updated) {
        static BandState of(Band b) {
            return new BandState(b.getId(), b.getBandNr(), b.getBandIndex(), b.getMangaReihe().getId(), b.isIstGelesen(),
                    b.getReread(), b.getPreis(), b.getAenderungPreis(), b.getBildUrl() == null ? null : b.getBildUrl().toExternalForm(),
                    b.getMpUrl() == null ? null : b.getMpUrl().toExternalForm(), b.getIstSpecial(), b.getErstelltAm(), b.getAktualisiertAm());
        }
    }

    private Map<Long, BandState> state() {
        Map<Long, BandState> result = new HashMap<>();
        volumes().forEach(b -> result.put(b.getId(), BandState.of(b))); return result;
    }

    private void assertExtension(Map<Long, BandState> before, int target, Integer reread) {
        flushClear();
        var after = state(); assertEquals(target, after.size());
        before.forEach((id, value) -> assertEquals(value, after.get(id), "Persistierter Altband " + id));
        var newBands = after.values().stream().filter(b -> !before.containsKey(b.id())).toList();
        assertEquals(target - before.size(), newBands.size());
        assertTrue(newBands.stream().noneMatch(BandState::read));
        assertTrue(newBands.stream().allMatch(b -> Objects.equals(graph.series().getId(), b.parent())));
        assertEquals(java.util.stream.IntStream.rangeClosed(1, target).boxed().toList(),
                after.values().stream().map(BandState::number).sorted().toList());
        var reloaded = row(); assertEquals(target, reloaded.getAnzahlBaende());
        assertFalse(reloaded.getMangaDetails().isIstGelesen());
        assertEquals(reread, reloaded.getMangaDetails().getReread());
    }

    private Band addVolume(MangaReihe row, int number, boolean read, Integer reread) throws Exception {
        var b = new Band(); b.setMangaReihe(row); b.setBandNr(number); b.setBandIndex(400000 + number);
        b.setPreis(new BigDecimal("7.49")); b.setAenderungPreis(new BigDecimal("-0.02"));
        b.setBildUrl(PersistenceTestFixtures.url("https://example.invalid/m06/" + number));
        b.setMpUrl(PersistenceTestFixtures.url("https://example.invalid/volumes/" + number));
        b.setIstSpecial(true); b.setIstGelesen(read); b.setReread(reread); return bands.save(b);
    }

    private Map<String, Object> edit(int target) {
        var r = row(); var d = r.getMangaDetails(); var input = new HashMap<String, Object>();
        input.put("mangaReiheId", r.getId().toString()); input.put("mangaIndex", r.getMangaIndex());
        input.put("statusId", r.getStatus().getStatusId().toString()); input.put("verlagId", r.getVerlag().getVerlagId().toString());
        input.put("typId", r.getTyp().getTypId().toString()); input.put("formatId", r.getFormat().getFormatId().toString());
        input.put("sammelbandTypId", d == null ? null : d.getSammelbaende().getId().toString());
        input.put("titel", "M06 geändert " + r.getId()); input.put("anzahlBaende", target);
        input.put("preisProBand", "6.99"); input.put("gesamtpreisAenderung", "-0.02");
        input.put("istGelesen", true); input.put("reread", 9);
        input.put("anilistUrl", "https://example.invalid/new-metadata");
        // Auch gültige Importdaten dürfen bei Erweiterung keine Altbände überschreiben.
        input.put("scrapedData", Map.of("istEdit", "true", "Band 1 Bild Url", "https://example.invalid/replacement",
                "Band 1 href", "https://example.invalid/volumes/9999", "Band 1 Preis", "99.99"));
        return input;
    }

    private org.springframework.mock.web.MockHttpServletResponse postJson(String path, Object body, int status) throws Exception {
        var response = mvc.perform(post("http://localhost:8080" + path).header("Host", "localhost:8080")
                .header("Origin", "http://localhost:8080").with(csrf()).contentType("application/json")
                .content(mapper.writeValueAsString(body))).andReturn().getResponse();
        assertEquals(status, response.getStatus(), "HTTP-Vertrag: " + path); return response;
    }

    @ParameterizedTest @ValueSource(strings = {"full", "partial", "unread", "inconsistentFalse", "inconsistentTrue"})
    void directExtensionPreservesIndividualStatus(String mode) throws Exception {
        var r = row(); var existing = volumes(); existing.sort(Comparator.comparing(Band::getBandNr));
        existing.get(0).setIstGelesen(!mode.equals("unread") && !mode.equals("inconsistentTrue"));
        existing.get(1).setIstGelesen(mode.equals("full") || mode.equals("inconsistentFalse"));
        if (mode.equals("partial")) { addVolume(r, 3, true, 5); r.setAnzahlBaende(3); }
        r.getMangaDetails().setIstGelesen(mode.equals("full") || mode.equals("inconsistentTrue"));
        r.getMangaDetails().setReread(3); flushClear(); var before = state();
        int target = mode.equals("partial") ? 5 : 3;
        postJson("/addMangaReihe", edit(target), 200);
        assertExtension(before, target, 3);
        assertEquals("M06 geändert " + r.getId(), row().getTitel());
        assertEquals("https://example.invalid/new-metadata", row().getMangaDetails().getAnilistUrl());
    }

    @ParameterizedTest @NullSource @ValueSource(ints = {0, 3, 10_000})
    void allRereadsIncludingNullSurviveRealPersistence(Integer reread) throws Exception {
        var r = row(); var existing = volumes(); existing.sort(Comparator.comparing(Band::getBandNr));
        existing.get(0).setReread(null); existing.get(1).setReread(0);
        addVolume(r, 3, true, 2); addVolume(r, 4, false, 5); r.setAnzahlBaende(4);
        r.getMangaDetails().setReread(reread); flushClear(); var before = state();
        postJson("/addMangaReihe", edit(6), 200); assertExtension(before, 6, reread);
    }

    private EinkaufslisteItem purchaseFixture(boolean indexMatch, int count) {
        var r = row(); var item = shopping.findById(graph.shoppingItem().getId()).orElseThrow();
        item.setMangaIndex(indexMatch ? r.getMangaIndex() : unusedIndex()); item.setTitel(r.getTitel());
        item.setAnzahlBaende(count); return item;
    }

    @ParameterizedTest @CsvSource({"true,full", "false,full", "true,partial", "false,partial", "true,unread", "false,unread"})
    void bothPurchasePathsPreservePersistedOldVolumes(boolean indexMatch, String mode) throws Exception {
        for (Band b : volumes()) b.setIstGelesen(mode.equals("full") || mode.equals("partial") && b.getBandNr() == 1);
        row().getMangaDetails().setIstGelesen(mode.equals("full")); row().getMangaDetails().setReread(null);
        var item = purchaseFixture(indexMatch, 2); flushClear(); var before = state();
        var response = postJson("/buyItemUpdate", Map.of("itemId", item.getId().toString()), 200);
        assertEquals("Reihe aktualisiert", mapper.readTree(response.getContentAsString()).get("message").stringValue());
        assertExtension(before, 4, null);
        assertTrue(shopping.findById(item.getId()).orElseThrow().getGekauft());
    }

    @ParameterizedTest @ValueSource(ints = {1, 3, 5})
    void declaredCountMismatchAppendsOnlyBeyondActualPrefix(int declared) throws Exception {
        row().setAnzahlBaende(declared); flushClear(); var before = state();
        postJson("/addMangaReihe", edit(4), 200); assertExtension(before, 4, 2);
    }

    @ParameterizedTest @CsvSource({"gap,false", "gap,true", "duplicate,false", "duplicate,true",
            "noAddition,false", "noAddition,true", "missingDetails,false", "missingDetails,true"})
    void rejectsRepresentableAmbiguitiesWithoutPersistedSideEffects(String mode, boolean purchase) throws Exception {
        int target = 3;
        if (mode.equals("gap")) volumes().stream().filter(b -> b.getBandNr() == 2).findFirst().orElseThrow().setBandNr(4);
        if (mode.equals("duplicate")) volumes().stream().filter(b -> b.getBandNr() == 2).findFirst().orElseThrow().setBandNr(1);
        if (mode.equals("noAddition")) { row().setAnzahlBaende(1); target = 2; }
        if (mode.equals("missingDetails")) {
            // Fehlende inverse 1:1-Beziehung ohne DELETE darstellen: eigene neue Reihe ohne Details.
            var source = row(); var bare = new MangaReihe(); bare.setTitel("M06 ohne Details " + UUID.randomUUID());
            bare.setStatus(source.getStatus()); bare.setVerlag(source.getVerlag()); bare.setTyp(source.getTyp()); bare.setFormat(source.getFormat());
            bare.setMangaIndex(unusedIndex()); bare.setAnzahlBaende(2); bare.setPreisProBand(BigDecimal.ONE);
            bare.setGesamtpreis(new BigDecimal("2.00")); bare.setAenderungGesamtpreis(BigDecimal.ZERO); rows.save(bare);
            addVolume(bare, 1, true, null); addVolume(bare, 2, false, 2);
            graph = new PersistenceTestFixtures.Graph(bare, null, List.of(), graph.shoppingItem(), graph.shoppingDetails());
        }
        var item = purchaseFixture(true, 1); flushClear();
        var before = TestDatabaseSnapshot.capture(em);
        if (purchase) postJson("/buyItemUpdate", Map.of("itemId", item.getId().toString()), 400);
        else postJson("/addMangaReihe", edit(target), 400);
        flushClear();
        assertEquals(before, TestDatabaseSnapshot.capture(em), "Abgewiesene Aktion darf keinerlei Zeile verändern");
        assertFalse(shopping.findById(item.getId()).orElseThrow().getGekauft());
    }

    @Test void foreignSeriesVolumesAreNeitherAggregatedNorModified() throws Exception {
        var other = PersistenceTestFixtures.persistGraph(em);
        other.seriesDetails().setIstGelesen(true); other.volumes().forEach(b -> b.setIstGelesen(true));
        flushClear();
        var foreign = bands.findByMangaReiheId(other.series().getId()).stream().map(BandState::of).sorted(Comparator.comparing(BandState::id)).toList();
        var before = state(); postJson("/addMangaReihe", edit(3), 200); assertExtension(before, 3, 2);
        assertEquals(foreign, bands.findByMangaReiheId(other.series().getId()).stream().map(BandState::of).sorted(Comparator.comparing(BandState::id)).toList());
        assertTrue(rows.findById(other.series().getId()).orElseThrow().getMangaDetails().isIstGelesen());
    }

    @Test void changingOneVolumeStillAggregatesWithoutTouchingItsSiblings() throws Exception {
        var existing = volumes(); existing.sort(Comparator.comparing(Band::getBandNr));
        var sibling = BandState.of(existing.get(0)); var b = existing.get(1); flushClear();
        postJson("/editBand", Map.of("id", b.getId().toString(), "bandNr", "2", "istGelesen", true,
                "reread", b.getReread().toString()), 204);
        flushClear(); assertEquals(sibling, BandState.of(bands.findById(sibling.id()).orElseThrow()));
        assertTrue(bands.findById(b.getId()).orElseThrow().isIstGelesen()); assertTrue(row().getMangaDetails().isIstGelesen());
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void ordinarySeriesEditRetainsExistingReadSelection(boolean read) throws Exception {
        var input = edit(2); input.put("istGelesen", read); input.put("reread", 3);
        var ids = state().keySet(); postJson("/addMangaReihe", input, 200); flushClear();
        assertEquals(ids, state().keySet()); assertEquals(read, row().getMangaDetails().isIstGelesen());
        assertTrue(volumes().stream().allMatch(b -> b.isIstGelesen() == read)); assertEquals(3, row().getMangaDetails().getReread());
    }

    @ParameterizedTest @ValueSource(ints = {0, 3, 10_000})
    void creationStillPersistsValidatedReread(int reread) throws Exception {
        var input = edit(2); input.put("scrapedData", Map.of("istEdit", "false"));
        input.put("mangaReiheId", null); input.put("mangaIndex", unusedIndex());
        input.put("titel", "M06 Neuanlage " + UUID.randomUUID()); input.put("istGelesen", false); input.put("reread", reread);
        var response = postJson("/addMangaReihe", input, 200);
        Long id = mapper.readTree(response.getContentAsString()).get("id").longValue(); flushClear();
        var saved = rows.findById(id).orElseThrow(); assertEquals(reread, saved.getMangaDetails().getReread());
        assertFalse(saved.getMangaDetails().isIstGelesen()); var created = bands.findByMangaReiheId(id);
        assertEquals(2, created.size()); assertTrue(created.stream().noneMatch(Band::isIstGelesen));
    }

    @Test void purchaseAsNewSeriesRetainsStatusSelectionAndUnreadDefault() throws Exception {
        var item = shopping.findById(graph.shoppingItem().getId()).orElseThrow();
        item.setMangaIndex(unusedIndex()); item.setTitel("M06 neuer Kauf " + UUID.randomUUID()); flushClear();
        postJson("/buyItemUpdate", Map.of("itemId", item.getId().toString()), 417);
        assertFalse(shopping.findById(item.getId()).orElseThrow().getGekauft());
        postJson("/buyItemCreate", Map.of("itemId", item.getId().toString(),
                "statusId", row().getStatus().getStatusId().toString()), 200);
        flushClear(); assertTrue(shopping.findById(item.getId()).orElseThrow().getGekauft());
        var created = rows.findByTitel(item.getTitel()); assertEquals(1, created.size());
        assertFalse(created.get(0).getMangaDetails().isIstGelesen());
        var createdBands = bands.findByMangaReiheId(created.get(0).getId());
        assertEquals(2, createdBands.size()); assertTrue(createdBands.stream().noneMatch(Band::isIstGelesen));
    }
}
