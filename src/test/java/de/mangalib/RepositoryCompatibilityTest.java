package de.mangalib;

import de.mangalib.entity.*;
import de.mangalib.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Alle eigenen JPQL-/abgeleiteten Abfragen mit skalaren Assertions; nur eigene Rollback-Fixtures. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Rollback
class RepositoryCompatibilityTest {
    @Autowired private EntityManager em;
    @Autowired private MangaReiheRepository series;
    @Autowired private BandRepository bands;
    @Autowired private EinkaufslisteRepository shopping;
    @Autowired private VerlagRepository publishers;
    @Autowired private TypRepository types;
    @Autowired private FormatRepository formats;
    private TestDatabaseSnapshot.Snapshot before;

    @BeforeTransaction void capture() { before = TestDatabaseSnapshot.capture(em); }
    @AfterTransaction void verifyRollback() { assertEquals(before, TestDatabaseSnapshot.capture(em)); }

    @Test
    void executesAllDerivedSeriesSelectorsAndLookupNameQueries() throws Exception {
        var graph = PersistenceTestFixtures.persistGraph(em);
        em.flush(); em.clear();
        var m = series.findById(graph.series().getId()).orElseThrow();
        for (var result : List.of(series.findByStatus(m.getStatus()), series.findByVerlag(m.getVerlag()),
                series.findByTyp(m.getTyp()), series.findByFormat(m.getFormat()), series.findByMangaIndex(m.getMangaIndex()),
                series.findByTitel(m.getTitel()), series.findByTitelContainingIgnoreCase(m.getTitel().toUpperCase(Locale.ROOT)),
                series.findByIstGelesen(false))) {
            assertTrue(result.stream().anyMatch(row -> row.getId().equals(m.getId())));
        }
        assertTrue(series.findByIstGelesen(true).stream().noneMatch(row -> row.getId().equals(m.getId())));
        assertTrue(series.findMaxId() >= m.getId());
        assertEquals(m.getVerlag().getVerlagId(), publishers.findByName(m.getVerlag().getName()).orElseThrow().getVerlagId());
        assertEquals(m.getTyp().getTypId(), types.findByBezeichnung(m.getTyp().getBezeichnung()).orElseThrow().getTypId());
        assertEquals(m.getFormat().getFormatId(), formats.findByBezeichnung(m.getFormat().getBezeichnung()).orElseThrow().getFormatId());
        assertTrue(series.findByTitel("missing-" + UUID.randomUUID()).isEmpty());
    }

    @Test
    void executesSeriesAndBandYearMonthQueriesAndFirstBandSelection() throws Exception {
        var g = PersistenceTestFixtures.persistGraph(em);
        em.flush();
        // Zeitwerte ausschließlich eigener IDs, ohne Hibernate-Generation oder Schema zu ändern.
        em.createNativeQuery("update mangareihe set erstellt_am=:date where id=:id")
                .setParameter("date", LocalDate.of(2401, 12, 31)).setParameter("id", g.series().getId()).executeUpdate();
        for (var b : g.volumes()) em.createNativeQuery("update baende set erstellt_am=:date where id=:id")
                .setParameter("date", LocalDate.of(2401, 12, 31)).setParameter("id", b.getId()).executeUpdate();
        em.clear();
        assertTrue(series.findByErstelltAmYear(2401).stream().anyMatch(m -> m.getId().equals(g.series().getId())));
        assertTrue(series.findByErstelltAmYearAndMonth(2401, 12).stream().anyMatch(m -> m.getId().equals(g.series().getId())));
        assertTrue(series.findByErstelltAmYearAndMonth(2401, 1).stream().noneMatch(m -> m.getId().equals(g.series().getId())));
        var ids = g.volumes().stream().map(Band::getId).collect(java.util.stream.Collectors.toSet());
        assertEquals(ids, bands.findByMangaReiheId(g.series().getId()).stream().map(Band::getId).collect(java.util.stream.Collectors.toSet()));
        assertTrue(bands.findByErstelltAmYear(2401).stream().map(Band::getId).toList().containsAll(ids));
        assertTrue(bands.findByErstelltAmYearAndMonth(2401, 12).stream().map(Band::getId).toList().containsAll(ids));
        assertEquals(g.volumes().getFirst().getId(), bands.findFirstBandByMangaReiheId(g.series().getId()).orElseThrow().getId());
        assertTrue(bands.findFirstBandByMangaReiheId(-1L).isEmpty());
    }

    @Test
    void preservesCentSumsStatusExclusionAndMultiplierCoalesce() throws Exception {
        int volumesBefore = zero(series.findeGesamtAnzahlBaende());
        int multipliedBefore = zero(series.findeGesamtAnzahlMitMultiplikator());
        var moneyBefore = zero(series.findeGesamtSummeGesamtpreis());
        var g = PersistenceTestFixtures.persistGraph(em);
        assertNotEquals(4L, g.series().getStatus().getStatusId());
        em.flush();
        assertEquals(volumesBefore + 2, series.findeGesamtAnzahlBaende());
        assertEquals(multipliedBefore + 2 * g.seriesDetails().getSammelbaende().getMultiplikator(), series.findeGesamtAnzahlMitMultiplikator());
        assertMoney(moneyBefore.add(new BigDecimal("12.34")), series.findeGesamtSummeGesamtpreis());
        // Fehlende Details: COALESCE muss weiterhin den Faktor 1 liefern.
        g.series().setMangaDetails(null);
        em.flush();
        assertEquals(multipliedBefore + 2, series.findeGesamtAnzahlMitMultiplikator());
        g.series().setStatus(em.find(Status.class, 4L));
        assertNotNull(g.series().getStatus(), "Bestehender Ausschlussstatus 4 ist erforderlich.");
        em.flush();
        assertEquals(volumesBefore, zero(series.findeGesamtAnzahlBaende()));
        assertEquals(multipliedBefore, zero(series.findeGesamtAnzahlMitMultiplikator()));
        assertMoney(moneyBefore, zero(series.findeGesamtSummeGesamtpreis()));
    }

    @Test
    void executesEveryBandCategoryAtItsBoundaries() throws Exception {
        var beforeCounts = categoryCounts();
        var g = PersistenceTestFixtures.persistGraph(em);
        for (int count : List.of(1, 2, 5, 6, 10, 11, 20, 21, 50, 51, 100, 101)) {
            g.series().setAnzahlBaende(count);
            em.flush();
            var actual = categoryCounts();
            int expectedCategory = count == 1 ? 0 : count <= 5 ? 1 : count <= 10 ? 2 : count <= 20 ? 3 : count <= 50 ? 4 : count <= 100 ? 5 : 6;
            for (int i = 0; i < 7; i++) assertEquals(beforeCounts[i] + (i == expectedCategory ? 1 : 0), actual[i]);
        }
    }

    @Test
    void executesMonthlyAggregatesIntervalsSortingAndPurchasedFiltersAcrossYearBoundary() throws Exception {
        assertTrue(shopping.findMonthlyBandDataByYear(2401).isEmpty());
        assertTrue(shopping.findMonthlySpendingDataByYear(2402).isEmpty());
        var first = PersistenceTestFixtures.persistGraph(em).shoppingItem();
        var second = PersistenceTestFixtures.persistGraph(em).shoppingItem();
        first.setErscheinungsdatum(LocalDate.of(2401, 12, 31)); first.setGekauft(true);
        second.setErscheinungsdatum(LocalDate.of(2402, 1, 1)); second.setGekauft(false);
        em.flush(); em.clear();
        var interval = shopping.findByErscheinungsdatumBetween(LocalDate.of(2401, 12, 31), LocalDate.of(2402, 1, 1), Sort.by("erscheinungsdatum").descending());
        assertEquals(List.of(second.getId(), first.getId()), interval.stream().map(EinkaufslisteItem::getId).toList());
        aggregate(shopping.findMonthlyBandDataByYear(2401), 12, new BigDecimal("2"));
        aggregate(shopping.findMonthlySpendingDataByYear(2401), 12, new BigDecimal("14.98"));
        aggregate(shopping.findBandeProMonat(2401), 12, new BigDecimal("2"));
        aggregate(shopping.findGeldProMonat(2401), 12, new BigDecimal("14.98"));
        aggregate(shopping.findMonthlyBandDataByYear(2402), 1, new BigDecimal("2"));
        aggregate(shopping.findMonthlySpendingDataByYear(2402), 1, new BigDecimal("14.98"));
        assertTrue(shopping.findBandeProMonat(2402).isEmpty());
        assertTrue(shopping.findGeldProMonat(2402).isEmpty());
        assertTrue(shopping.findDistinctYears().containsAll(List.of(2401, 2402)));
        em.find(EinkaufslisteItem.class, second.getId()).setErscheinungsdatum(null);
        em.flush();
        assertTrue(shopping.findMonthlyBandDataByYear(2402).isEmpty());
        assertTrue(shopping.findDistinctYears().contains(null));
    }

    private int[] categoryCounts() {
        return new int[] {series.countReihenMitEinemBand(), series.countReihenMitZweiBisFuenfBaenden(),
                series.countReihenMitSechsBisZehnBaenden(), series.countReihenMitElfBisZwanzigBaenden(),
                series.countReihenMitEinundzwanzigBisFuenfzigBaenden(), series.countReihenMitEinundfuenfzigBisHundertBaenden(), series.countReihenMitMehrAlsHundertBaenden()};
    }
    private static int zero(Integer value) { return value == null ? 0 : value; }
    private static BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private static void assertMoney(BigDecimal expected, BigDecimal actual) { assertNotNull(actual); assertEquals(0, expected.compareTo(actual)); }
    private static void aggregate(List<Object[]> rows, int month, BigDecimal value) {
        assertEquals(1, rows.size());
        assertInstanceOf(Integer.class, rows.getFirst()[0]);
        assertEquals(month, rows.getFirst()[0]);
        assertInstanceOf(Number.class, rows.getFirst()[1]);
        assertMoney(value, new BigDecimal(rows.getFirst()[1].toString()));
    }
}
