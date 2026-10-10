package de.mangalib;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Prüft physische Spaltenwerte vor, während und nach einer zurückgerollten Testtransaktion. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Rollback
class TestDataPreservationTest {
    // Feste Allowlist, keine vom Benutzer/Environment übernommenen SQL-Bezeichner.
    private static final Map<String, String> TABLE_KEYS = Map.of(
            "status", "status_id", "verlage", "verlag_id", "typen", "typ_id", "formate", "format_id",
            "sammelbaende", "id", "mangareihe", "id", "mangadetails", "id", "baende", "id",
            "einkaufsliste", "id", "einkaufslistedetails", "id");
    private static final Map<String, Integer> NEW_ROWS = Map.of(
            "mangareihe", 1, "mangadetails", 1, "baende", 2, "einkaufsliste", 1, "einkaufslistedetails", 1);

    @Autowired
    private EntityManager entityManager;
    private Map<String, List<List<Object>>> before;
    private Long seriesId;
    private Long shoppingId;
    private List<Long> volumeIds;

    @BeforeTransaction
    void captureExistingRowsOutsideTheWriteTransaction() {
        assertFalse(TestTransaction.isActive());
        before = snapshot();
    }

    @Test
    void preservesExistingRowsAndRollsBackOnlyNewTestAggregates() throws Exception {
        assertTrue(TestTransaction.isActive());
        assertTrue(TestTransaction.isFlaggedForRollback());
        var graph = PersistenceTestFixtures.persistGraph(entityManager);
        seriesId = graph.series().getId();
        shoppingId = graph.shoppingItem().getId();
        volumeIds = graph.volumes().stream().map(volume -> volume.getId()).toList();
        entityManager.flush();
        entityManager.clear();

        var during = snapshot();
        for (String table : TABLE_KEYS.keySet()) {
            assertEquals(before.get(table).size() + NEW_ROWS.getOrDefault(table, 0), during.get(table).size(),
                    "Unerwartete Zeilenanzahl in " + table);
            // Keine Entity-Gleichheit/verwalteten Objekte vergleichen: skalare SQL-Werte
            // erfassen auch FKs und verhindern, dass Änderungen im Persistence Context verborgen bleiben.
            assertTrue(during.get(table).containsAll(before.get(table)),
                    "Vorhandene Spaltenwerte wurden verändert: " + table);
        }
        // Kein Cleanup und kein manuelles Commit: Spring beendet die Testtransaktion per Rollback.
    }

    @AfterTransaction
    void verifyRollbackAndUnchangedDatabaseAfterTransactionCompletion() {
        assertFalse(TestTransaction.isActive());
        assertNotNull(before, "Der Vorher-Zustand muss vorhanden sein.");
        var after = snapshot();
        assertAll(TABLE_KEYS.keySet().stream().map(table -> () ->
                assertTrue(before.get(table).equals(after.get(table)),
                        "Rollback/Datenerhalt fehlgeschlagen für " + table)));
        if (seriesId != null) {
            assertAbsent("MangaReihe", seriesId);
            assertAbsent("MangaDetails", seriesId);
        }
        if (shoppingId != null) {
            assertAbsent("EinkaufslisteItem", shoppingId);
            assertAbsent("EinkaufslisteItemDetails", shoppingId);
        }
        if (volumeIds != null) {
            volumeIds.forEach(id -> assertAbsent("Band", id));
        }
    }

    private void assertAbsent(String entity, Long id) {
        assertEquals(0L, entityManager.createQuery(
                "select count(e) from " + entity + " e where e.id = :id", Long.class)
                .setParameter("id", id).getSingleResult(), "Testdatensatz nach Rollback noch vorhanden: " + entity);
    }

    private Map<String, List<List<Object>>> snapshot() {
        var result = new LinkedHashMap<String, List<List<Object>>>();
        TABLE_KEYS.forEach((table, key) -> {
            // Native SELECTs laufen ausschließlich über die vorhandene GuardedTestDataSource.
            // SELECT * schützt auch Spalten, die eventuell noch nicht im Entity gemappt sind.
            @SuppressWarnings("unchecked")
            List<Object[]> rows = entityManager.createNativeQuery("select * from " + table + " order by " + key)
                    .getResultList();
            result.put(table, rows.stream()
                    .map(row -> Collections.unmodifiableList(Arrays.asList(row.clone())))
                    .toList());
        });
        return Collections.unmodifiableMap(result);
    }
}
