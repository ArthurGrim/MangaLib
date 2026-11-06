package de.mangalib;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Rein synthetische Vorprüfungsfälle, kein Spring-/JPA-Kontext. */
class T03ReadOnlyPreparationTest {
    @Test void acceptsOnlyExpectedEmptyBusinessBaseline() throws Exception {
        var snapshot = synthetic();
        T03ReadOnlyPolicy.verify(snapshot, snapshot.fingerprint());
    }
    @Test void refusesExistingBusinessRowsAndChangedFingerprint() throws Exception {
        var before = synthetic(); var changed = new TreeMap<>(before.values());
        changed.put("mangareihe", List.of(List.of(51L, "synthetic foreign run")));
        assertThrows(AssertionError.class, () -> T03ReadOnlyPolicy.verify(new TestDatabaseSnapshot.Snapshot(changed), before.fingerprint()));
        assertThrows(AssertionError.class, () -> T03ReadOnlyPolicy.verify(before, "unconfirmed"));
    }
    @Test void refusesDifferentEngineAndMissingForeignKeys() {
        for (String key : List.of("schema.tables", "schema.foreignKeys")) {
            var changed = new TreeMap<>(synthetic().values());
            changed.put(key, key.equals("schema.tables") ? List.of(List.of("baende", "MyISAM", "synthetic")) : List.of());
            assertThrows(AssertionError.class, () -> T03ReadOnlyPolicy.verify(new TestDatabaseSnapshot.Snapshot(changed), "unused"));
        }
    }
    @Test void readTaskPinsItsClassAndContainsNoFixtureCalls() throws Exception {
        var build = Files.readString(Path.of("build.gradle.kts"));
        assertTrue(build.contains("include(\"**/T03ReadOnlyPreflightTest.class\")"));
        assertTrue(build.contains("include(\"**/T03CommitAcceptanceTest.class\")"));
        var source = Files.readString(Path.of("src/t03Integration/java/de/mangalib/T03ReadOnlyPreflightTest.java"));
        for (String forbidden : List.of("em.persist(", "em.merge(", "em.remove(", "em.flush(", "persistGraph(", "coordinator.execute(", "purchases.purchase("))
            assertFalse(source.contains(forbidden), forbidden);
        assertTrue(source.contains("transaction.setReadOnly(true)"));
    }
    private static TestDatabaseSnapshot.Snapshot synthetic() {
        var data = new TreeMap<String, List<List<Object>>>();
        T03RunJournal.BUSINESS.forEach(table -> data.put(table, List.of()));
        T03ReadOnlyPolicy.REFERENCES.forEach(table -> data.put(table, List.of(List.of(1L))));
        var status = new ArrayList<List<Object>>();
        for (long id = 1; id <= 74; id++) status.add(List.of(id));
        data.put("status", status);
        var tables = data.keySet().stream().sorted().map(table -> List.<Object>of(table, "InnoDB", "synthetic")).toList();
        data.put("schema.tables", tables); data.put("schema.foreignKeys", List.of(List.of("synthetic-fk")));
        data.put("schema.keyColumns", List.of(List.of("synthetic-key")));
        return new TestDatabaseSnapshot.Snapshot(data);
    }
}
