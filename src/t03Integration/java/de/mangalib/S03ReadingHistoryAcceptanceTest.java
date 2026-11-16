package de.mangalib;

import de.mangalib.entity.*;
import de.mangalib.service.*;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static de.mangalib.service.ReadingHistoryStatistics.Kind.*;
import static org.junit.jupiter.api.Assertions.*;

/** Getrennte Opt-in-Tasks nach geprüfter S03-Schemamigration: Rollbackfälle und ein
 * eigener Commit-/Konkurrenzgraph mit identitätsgebundener Entfernung. */
@SpringBootTest(properties = "mangalib.reading-history.enabled=true")
@ActiveProfiles("test")
class S03ReadingHistoryAcceptanceTest {
    static {
        if (!"true".equals(System.getProperty("s03.history.approved")))
            throw new IllegalStateException("Separate Freigabe für migriertes S03-Testschema fehlt.");
    }
    @Autowired EntityManager em;
    @Autowired LibraryWriteCoordinator coordinator;
    @Autowired ReadingHistoryService history;
    @Autowired BandService bands;
    @MockitoBean ScraperGateway scrapers;

    private List<?> events() {
        // Unabhängig vom historischen Zehn-Tabellen-Snapshot auch neue Ereigniszeilen erhalten.
        List<?> values = em.createNativeQuery("select id, band_id, operation_id, completion_date, initial_completion_date, kind, revision, volume_factor, created_at from band_reading_events order by id").getResultList();
        return values.stream().map(row -> java.util.Arrays.asList(((Object[]) row).clone())).toList();
    }

    @Test void completeReplayCorrectionAndCounterSurviveReloadAndRollback() {
        var before = TestDatabaseSnapshot.capture(em); var beforeEvents = events();
        try {
            assertThrows(FixtureRollback.class, () -> coordinator.execute(() -> {
                var baseline = history.readingTime();
                var graph = fixture();
                graph.volumes().forEach(band -> { band.setReread(0); band.setIstGelesen(false); });
                em.flush(); em.clear();
                long id = graph.volumes().getFirst().getId(); var first = UUID.randomUUID();
                history.complete(List.of(id), first, FIRST_READ, history.today().minusDays(2));
                var repeat = UUID.randomUUID();
                history.complete(List.of(id), repeat, REREAD, history.today());
                em.flush(); em.clear();
                assertEquals(1, em.find(Band.class, id).getReread());
                assertTrue(em.find(Band.class, id).isIstGelesen());
                long minutes = 45L * graph.seriesDetails().getSammelbaende().getMultiplikator();
                assertEquals(baseline.minutes() + minutes, history.readingTime().minutes());
                assertEquals(baseline.minutesWithRereads() + 2 * minutes, history.readingTime().minutesWithRereads());
                var recorded = history.history(id);
                assertEquals(2, recorded.size());
                history.complete(List.of(id), repeat, REREAD, history.today());
                var corrected = history.correct(id, recorded.getFirst().id(), 0, history.today().minusDays(3));
                em.flush(); em.clear();
                assertEquals(history.today().minusDays(3), history.history(id).getFirst().date());
                assertEquals(1, corrected.revision()); assertEquals(1, em.find(Band.class, id).getReread());
                assertEquals(2, history.history(id).size());
                assertThrows(ReadingHistoryService.Conflict.class,
                        () -> history.correct(id, corrected.id(), 0, history.today().minusDays(4)));
                throw new FixtureRollback();
            }));
        } finally {
            em.clear(); assertEquals(before, TestDatabaseSnapshot.capture(em)); assertEquals(beforeEvents, events());
        }
    }

    @Test void normalStatusChangeKeepsHistoryOnResetAndDoesNotInventReread() {
        var before = TestDatabaseSnapshot.capture(em); var beforeEvents = events();
        try {
            assertThrows(FixtureRollback.class, () -> coordinator.execute(() -> {
                var graph = fixture();
                var first = graph.volumes().getFirst(); first.setIstGelesen(false); first.setReread(0);
                em.flush(); em.clear(); long id = first.getId();
                bands.updateBandIstGelesen(id, true);
                bands.updateBandIstGelesen(id, false);
                bands.updateBandIstGelesen(id, true);
                em.flush(); em.clear();
                assertEquals(1, history.history(id).size()); assertEquals(0, em.find(Band.class, id).getReread());
                throw new FixtureRollback();
            }));
        } finally {
            em.clear(); assertEquals(before, TestDatabaseSnapshot.capture(em)); assertEquals(beforeEvents, events());
        }
    }

    private record Fixture(MangaReihe series, MangaDetails seriesDetails, List<Band> volumes) {}

    /** Zwei echte Threads/Transaktionen: gleicher Schlüssel dedupliziert, getrennte Aktionen zählen. */
    @Test void concurrentActionsCommitExactlyOnceAndKeepCountersConsistent() throws Exception {
        var before = TestDatabaseSnapshot.capture(em); var beforeEvents = events();
        var graph = coordinator.execute(() -> { var value = fixture(); em.flush(); return value; });
        long rowId = graph.series().getId(), bandId = graph.volumes().getFirst().getId();
        String title = graph.series().getTitel();
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var same = UUID.randomUUID();
            parallel(pool, () -> history.complete(List.of(bandId), same, FIRST_READ, history.today()),
                    () -> history.complete(List.of(bandId), same, FIRST_READ, history.today()));
            parallel(pool, () -> history.complete(List.of(bandId), UUID.randomUUID(), REREAD, history.today()),
                    () -> history.complete(List.of(bandId), UUID.randomUUID(), REREAD, history.today()));
            coordinator.execute(() -> {
                em.clear();
                assertEquals(3, history.history(bandId).size());
                assertEquals(2, em.find(Band.class, bandId).getReread());
                assertTrue(em.find(Band.class, bandId).isIstGelesen());
                return null;
            });
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(40, java.util.concurrent.TimeUnit.SECONDS),
                    "Keine Bereinigung bei noch laufenden Schreibthreads");
            // Nur der gerade selbst erzeugte und erneut identifizierte Graph, niemals Altbestände.
            coordinator.execute(() -> {
                em.clear();
                assertEquals(title, em.find(MangaReihe.class, rowId).getTitel());
                assertTrue(title.startsWith("S03_ROLLBACK_"));
                assertEquals(graph.volumes().stream().map(Band::getId).sorted().toList(),
                        em.createQuery("select b.id from Band b where b.mangaReihe.id=:id order by b.id", Long.class)
                                .setParameter("id", rowId).getResultList());
                em.createNativeQuery("delete from band_reading_events where band_id in (select id from baende where manga_reihe_id=:id)")
                        .setParameter("id", rowId).executeUpdate();
                assertEquals(2, em.createNativeQuery("delete from baende where manga_reihe_id=:id").setParameter("id", rowId).executeUpdate());
                assertEquals(1, em.createNativeQuery("delete from mangadetails where id=:id").setParameter("id", rowId).executeUpdate());
                assertEquals(1, em.createNativeQuery("delete from mangareihe where id=:id").setParameter("id", rowId).executeUpdate());
                return null;
            });
            em.clear(); assertEquals(before, TestDatabaseSnapshot.capture(em)); assertEquals(beforeEvents, events());
        }
    }

    private void parallel(java.util.concurrent.ExecutorService pool, Runnable first, Runnable second) throws Exception {
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        var futures = java.util.stream.Stream.of(first, second).map(action -> pool.submit(() -> {
            ready.countDown();
            try { if (!start.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("Startbarriere"); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
            action.run();
        })).toList();
        try { assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)); }
        finally { start.countDown(); }
        for (var future : futures) future.get(35, java.util.concurrent.TimeUnit.SECONDS);
    }

    /** Eigene Rollbackfixture im Coordinator statt eines Spring-Testtransaktionshelfers.
     * Kein gemeinsamer Guard wird abgeschwächt; Stammdaten werden nur referenziert.
     */
    private Fixture fixture() {
        assertTrue(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals("mangalib_test", em.createNativeQuery("select database()").getSingleResult());
        assertEquals("mangalib_test_user@localhost", em.createNativeQuery("select current_user()").getSingleResult());
        for (String table : List.of("mangareihe", "mangadetails", "baende", "band_reading_events"))
            assertEquals("InnoDB", em.createNativeQuery("select ENGINE from information_schema.TABLES where TABLE_SCHEMA=database() and TABLE_NAME=:table")
                    .setParameter("table", table).getSingleResult());
        var row = new MangaReihe(); row.setTitel("S03_ROLLBACK_" + UUID.randomUUID());
        row.setStatus(first(Status.class, "statusId")); row.setVerlag(first(Verlag.class, "verlagId"));
        row.setTyp(first(Typ.class, "typId")); row.setFormat(first(Format.class, "formatId"));
        row.setAnzahlBaende(2); row.setPreisProBand(new java.math.BigDecimal("5.00"));
        row.setGesamtpreis(new java.math.BigDecimal("10.00")); row.setAenderungGesamtpreis(java.math.BigDecimal.ZERO);
        em.persist(row);
        var details = new MangaDetails(); details.setMangaReihe(row); details.setReread(0);
        details.setSammelbaende(first(Sammelband.class, "id")); em.persist(details); row.setMangaDetails(details);
        var volumes = new java.util.ArrayList<Band>();
        for (int number = 1; number <= 2; number++) {
            var band = new Band(); band.setMangaReihe(row); band.setBandNr(number); band.setReread(0);
            band.setPreis(new java.math.BigDecimal("5.00")); band.setAenderungPreis(java.math.BigDecimal.ZERO);
            em.persist(band); volumes.add(band);
        }
        return new Fixture(row, details, volumes);
    }

    private <T> T first(Class<T> type, String id) {
        return em.createQuery("from " + type.getSimpleName() + " order by " + id, type).setMaxResults(1).getSingleResult();
    }

    private static class FixtureRollback extends RuntimeException {}
}
