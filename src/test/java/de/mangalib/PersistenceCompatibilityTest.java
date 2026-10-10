package de.mangalib;

import de.mangalib.entity.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/** ORM-Verträge auf dem bestehenden Schema, unabhängig von Preisberechnung und Kauf-Workflows. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Rollback
class PersistenceCompatibilityTest {
    @Autowired
    private EntityManager entityManager;
    private PersistenceTestFixtures.Graph insertedGraph;

    @Test
    void roundTripsSeriesDetailsVolumesAndUpdatesWithoutChangingIdentities() throws Exception {
        var started = Instant.now().minusSeconds(2);
        var firstDate = LocalDate.now();
        var graph = persistGraph();
        var seriesId = graph.series().getId();
        var bandId = graph.volumes().getFirst().getId();
        flushAndClear();

        var series = entityManager.find(MangaReihe.class, seriesId);
        var details = entityManager.find(MangaDetails.class, seriesId);
        var volume = entityManager.find(Band.class, bandId);
        assertNotSame(graph.series(), series);
        assertEquals(graph.series().getTitel(), series.getTitel());
        assertEquals(2, series.getAnzahlBaende());
        assertEquals(graph.series().getStatus().getStatusId(), series.getStatus().getStatusId());
        assertEquals(graph.series().getVerlag().getVerlagId(), series.getVerlag().getVerlagId());
        assertEquals(graph.series().getTyp().getTypId(), series.getTyp().getTypId());
        assertEquals(graph.series().getFormat().getFormatId(), series.getFormat().getFormatId());
        assertEquals(seriesId, details.getId());
        assertEquals(seriesId, details.getMangaReihe().getId());
        assertEquals(seriesId, series.getMangaDetails().getId());
        assertEquals(graph.seriesDetails().getSammelbaende().getId(), details.getSammelbaende().getId());
        assertEquals("Japan", details.getHerkunft());
        assertEquals(2020, details.getStartJahr());
        assertEquals(2, details.getReread());
        assertEquals(graph.seriesDetails().getCoverUrl(), details.getCoverUrl());
        assertEquals(graph.seriesDetails().getAnilistUrl(), details.getAnilistUrl());
        assertMoney("5.99", series.getPreisProBand());
        assertMoney("12.34", series.getGesamtpreis());
        assertMoney("0.36", series.getAenderungGesamtpreis());
        assertMoney("5.99", volume.getPreis());
        assertEquals(seriesId, volume.getMangaReihe().getId());
        assertEquals(1, volume.getBandNr());
        assertTrue(volume.isIstGelesen());
        assertEquals(2, volume.getReread());
        assertEquals(graph.volumes().getFirst().getBildUrl().toExternalForm(), volume.getBildUrl().toExternalForm());
        assertEquals(graph.volumes().getFirst().getMpUrl().toExternalForm(), volume.getMpUrl().toExternalForm());
        assertDateInWindow(series.getErstelltAm(), firstDate);
        assertDateInWindow(volume.getErstelltAm(), firstDate);
        assertDateInWindow(volume.getAktualisiertAm(), firstDate);
        assertNotNull(series.getAktualisiertAm());
        assertFalse(series.getAktualisiertAm().toInstant().isBefore(started));
        assertFalse(series.getAktualisiertAm().toInstant().isAfter(Instant.now().plusSeconds(2)));
        var created = series.getErstelltAm();
        var timestamp = series.getAktualisiertAm().toLocalDateTime();

        // Zweiter Reload muss den persistierten Timestamp unverändert wiedergeben.
        entityManager.clear();
        series = entityManager.find(MangaReihe.class, seriesId);
        assertEquals(timestamp, series.getAktualisiertAm().toLocalDateTime());
        series.getMangaDetails().setHerkunft("Südkorea");
        series.setGesamtpreis(new BigDecimal("11.98"));
        series.setAenderungGesamtpreis(BigDecimal.ZERO);
        volume = entityManager.find(Band.class, bandId);
        volume.setPreis(new BigDecimal("5.63"));
        volume.setAenderungPreis(new BigDecimal("-0.36"));
        volume.setBildUrl(PersistenceTestFixtures.url("https://example.invalid/changed.jpg"));
        flushAndClear();

        series = entityManager.find(MangaReihe.class, seriesId);
        volume = entityManager.find(Band.class, bandId);
        assertEquals(seriesId, series.getMangaDetails().getId());
        assertEquals("Südkorea", series.getMangaDetails().getHerkunft());
        assertEquals(created, series.getErstelltAm());
        assertFalse(series.getAktualisiertAm().toLocalDateTime().isBefore(timestamp));
        assertMoney("11.98", series.getGesamtpreis());
        assertMoney("0.00", series.getAenderungGesamtpreis());
        assertMoney("5.63", volume.getPreis());
        assertMoney("-0.36", volume.getAenderungPreis());
        assertEquals("https://example.invalid/changed.jpg", volume.getBildUrl().toExternalForm());
        var volumes = entityManager.createQuery(
                "select b from Band b where b.mangaReihe.id = :id order by b.bandNr", Band.class)
                .setParameter("id", seriesId).getResultList();
        assertEquals(graph.volumes().stream().map(Band::getId).toList(), volumes.stream().map(Band::getId).toList());
        assertMoney("6.35", volumes.get(1).getPreis());
        assertFalse(volumes.get(1).isIstGelesen());
    }

    @Test
    void roundTripsShoppingDetailsAndLeapDayThenUpdatesTheSameRows() throws Exception {
        var graph = persistGraph();
        var itemId = graph.shoppingItem().getId();
        flushAndClear();
        var item = entityManager.find(EinkaufslisteItem.class, itemId);
        var details = entityManager.find(EinkaufslisteItemDetails.class, itemId);
        assertNotSame(graph.shoppingItem(), item);
        assertEquals(graph.shoppingItem().getTitel(), item.getTitel());
        assertEquals(itemId, details.getId());
        assertEquals(itemId, details.getEinkaufslisteItem().getId());
        assertEquals(itemId, item.getEinkaufslisteItemDetails().getId());
        assertEquals(graph.shoppingDetails().getSammelbaendeId().getId(), details.getSammelbaendeId().getId());
        assertEquals(graph.shoppingItem().getVerlagId().getVerlagId(), item.getVerlagId().getVerlagId());
        assertEquals(graph.shoppingItem().getTypId().getTypId(), item.getTypId().getTypId());
        assertEquals(graph.shoppingItem().getFormatId().getFormatId(), item.getFormatId().getFormatId());
        assertEquals(LocalDate.of(2024, 2, 29), item.getErscheinungsdatum());
        assertMoney("7.49", item.getPreis());
        assertMoney("14.98", item.getGesamtpreis());
        assertMoney("-0.02", item.getAenderungGesamtpreis());
        assertEquals(2, details.getAnzahlBaendeDe());
        assertEquals(4, details.getAnzahlBaendeErstv());
        assertEquals("Japan", details.getHerkunft());
        assertEquals(graph.shoppingDetails().getCoverUrl(), details.getCoverUrl());
        assertEquals(graph.shoppingDetails().getAnilistUrl(), details.getAnilistUrl());
        assertFalse(item.getGekauft());

        item.setPreis(new BigDecimal("8.01"));
        item.setGesamtpreis(new BigDecimal("16.02"));
        item.setErscheinungsdatum(LocalDate.of(2025, 12, 31));
        details.setCoverUrl("https://example.invalid/new-cover.jpg");
        details.setAnzahlBaendeDe(3);
        flushAndClear();
        item = entityManager.find(EinkaufslisteItem.class, itemId);
        assertEquals(itemId, item.getEinkaufslisteItemDetails().getId());
        assertMoney("8.01", item.getPreis());
        assertMoney("16.02", item.getGesamtpreis());
        assertMoney("-0.02", item.getAenderungGesamtpreis());
        assertEquals(LocalDate.of(2025, 12, 31), item.getErscheinungsdatum());
        assertEquals("https://example.invalid/new-cover.jpg", item.getEinkaufslisteItemDetails().getCoverUrl());
        assertEquals(3, item.getEinkaufslisteItemDetails().getAnzahlBaendeDe());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = { false, true })
    void preservesNullableBooleansWithoutCoercingNullToFalse(Boolean value) throws Exception {
        var graph = persistGraph();
        graph.series().setIstEbayPreis(value);
        graph.series().setIstVergriffen(value);
        graph.volumes().getFirst().setIstSpecial(value);
        graph.shoppingItem().setIstEbayPreis(value);
        graph.shoppingItem().setIstVergriffen(value);
        graph.shoppingItem().setGekauft(value);
        flushAndClear();
        var series = entityManager.find(MangaReihe.class, graph.series().getId());
        var volume = entityManager.find(Band.class, graph.volumes().getFirst().getId());
        var item = entityManager.find(EinkaufslisteItem.class, graph.shoppingItem().getId());
        assertEquals(value, series.getIstEbayPreis());
        assertEquals(value, series.getIstVergriffen());
        assertEquals(value, volume.getIstSpecial());
        assertEquals(value, item.getIstEbayPreis());
        assertEquals(value, item.getIstVergriffen());
        assertEquals(value, item.getGekauft());
    }

    private PersistenceTestFixtures.Graph persistGraph() throws Exception {
        insertedGraph = PersistenceTestFixtures.persistGraph(entityManager);
        return insertedGraph;
    }

    @AfterTransaction
    void verifiesEveryInsertedAggregateIsAbsentAfterRollback() {
        assertFalse(TestTransaction.isActive());
        if (insertedGraph == null) {
            return; // Bei fehlgeschlagenem Setup übernimmt Spring weiterhin den Rollback.
        }
        assertAbsent("MangaReihe", insertedGraph.series().getId());
        assertAbsent("MangaDetails", insertedGraph.series().getId());
        insertedGraph.volumes().forEach(volume -> assertAbsent("Band", volume.getId()));
        assertAbsent("EinkaufslisteItem", insertedGraph.shoppingItem().getId());
        assertAbsent("EinkaufslisteItemDetails", insertedGraph.shoppingItem().getId());
    }

    private void assertAbsent(String entity, Long id) {
        // Nach dem echten Transaktionsende lesend über die bestehende geschützte DataSource prüfen.
        assertEquals(0L, entityManager.createQuery(
                "select count(e) from " + entity + " e where e.id = :id", Long.class)
                .setParameter("id", id).getSingleResult(), "Testdatensatz nach Rollback noch vorhanden: " + entity);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "Geldbetrag muss centgenau erhalten bleiben.");
    }

    private static void assertDateInWindow(LocalDate actual, LocalDate earliest) {
        assertNotNull(actual);
        assertFalse(actual.isBefore(earliest));
        assertFalse(actual.isAfter(LocalDate.now()));
    }
}
