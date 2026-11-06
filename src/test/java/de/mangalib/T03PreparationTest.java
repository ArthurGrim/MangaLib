package de.mangalib;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Ausschließlich Dateisystem und synthetische skalare Snapshots; keine JPA-Instanz. */
class T03PreparationTest {
    @TempDir Path directory;
    @Test void journalIsExclusiveDurableAndNeverRegistersMasterData() throws Exception {
        var run = UUID.randomUUID();
        try (var journal = new T03RunJournal(run, directory)) {
            journal.register("mangareihe", 51); journal.register("mangareihe", 51);
            assertThrows(IllegalArgumentException.class, () -> journal.register("status", 1));
            assertThrows(IllegalArgumentException.class, () -> journal.register("baende", -1));
            assertEquals(Set.of(51L), journal.ids().get("mangareihe"));
            assertTrue(Files.readString(directory.resolve("ids.txt")).contains("ID mangareihe 51"));
            assertThrows(java.io.IOException.class, () -> new T03RunJournal(run, directory));
            journal.phase(false);
        }
        assertTrue(Files.readString(directory.resolve("ids.txt")).endsWith("STOP\n"));
    }
    @Test void registeredNewRowsAreAllowedWithoutDemandingAnIdenticalTotalHash() {
        var before = snapshot(List.of(List.of(1L, "old")));
        var after = snapshot(List.of(List.of(1L, "old"), List.of(2L, "new")));
        T03Preservation.verify(before, after, Map.of("mangareihe", Set.of(2L)));
    }
    @Test void changedDeletedOrUnregisteredRowsAreRejected() {
        var before = snapshot(List.of(List.of(1L, "old")));
        for (var rows : List.of(List.of(List.<Object>of(1L, "changed")), List.<List<Object>>of(),
                List.of(List.<Object>of(1L, "old"), List.<Object>of(2L, "unknown")))) {
            assertThrows(AssertionError.class, () -> T03Preservation.verify(before, snapshot(rows), Map.of()));
        }
    }
    @Test void preexistingIdCannotBeClaimedAsOwned() {
        var before = snapshot(List.of(List.of(1L, "old")));
        assertThrows(AssertionError.class, () -> T03Preservation.verify(before, before, Map.of("mangareihe", Set.of(1L))));
    }
    @Test void schemaAndMasterDataMustStayExact() {
        var before = snapshot(List.of());
        for (String key : List.of("schema.columns", "status")) {
            var changed = new TreeMap<>(before.values()); changed.put(key, List.of(List.of("changed")));
            assertThrows(AssertionError.class, () -> T03Preservation.verify(before, new TestDatabaseSnapshot.Snapshot(changed), Map.of()));
        }
    }
    @Test void optInSourceSetAndTaskAreNotWiredToDefaultLifecycle() throws Exception {
        var build = Files.readString(Path.of("build.gradle.kts"));
        assertTrue(build.contains("testClassesDirs = t03Integration.output.classesDirs"));
        assertTrue(build.contains("COMMIT_NEW_SYNTHETIC_ROWS_ONLY"));
        assertTrue(build.contains("Files.createDirectory(directory)"));
        assertFalse(build.matches("(?s).*dependsOn\\([^)]*t03CommitTest[^)]*\\).*"));
        assertFalse(build.matches("(?s).*finalizedBy\\([^)]*t03CommitTest[^)]*\\).*"));
        assertTrue(Files.readString(Path.of("src/test/resources/META-INF/spring.factories"))
                .contains("TestDatabaseContextCustomizerFactory"));
    }
    @Test void onlyOwnSchemaDmlAndGlobalUsageAreAllowed() {
        T03ServerRights.verify(List.of("GRANT USAGE ON *.* TO 'test'@'localhost'",
                "GRANT SELECT, INSERT, UPDATE, DELETE ON `mangalib\\_test`.* TO 'test'@'localhost'"));
        for (String grant : List.of("GRANT ALL PRIVILEGES ON *.* TO 'test'@'localhost'",
                "GRANT SELECT, INSERT, UPDATE, DELETE, DROP ON `mangalib_test`.* TO 'test'@'localhost'",
                "GRANT SELECT ON `mangalib`.* TO 'test'@'localhost'", "GRANT 'role' TO 'test'@'localhost'",
                "GRANT SELECT, INSERT, UPDATE, DELETE ON `mangalib_test`.* TO 'test'@'localhost' WITH GRANT OPTION")) {
            assertThrows(IllegalStateException.class, () -> T03ServerRights.verify(List.of(grant)));
        }
        assertThrows(IllegalStateException.class, () -> T03ServerRights.verify(List.of("GRANT USAGE ON *.* TO 'test'@'localhost'")));
    }
    private static TestDatabaseSnapshot.Snapshot snapshot(List<List<Object>> rows) {
        var values = new TreeMap<String, List<List<Object>>>();
        for (String table : T03RunJournal.BUSINESS) values.put(table, table.equals("mangareihe") ? rows : List.of());
        for (String table : List.of("status", "verlage", "typen", "formate", "sammelbaende")) values.put(table, List.of());
        values.put("schema.columns", T03RunJournal.BUSINESS.stream().sorted().map(t -> List.<Object>of(t, "id", 1)).toList());
        return new TestDatabaseSnapshot.Snapshot(values);
    }
}
