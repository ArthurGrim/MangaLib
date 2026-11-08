package de.mangalib;

import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Bestehende Zeilen exakt erhalten; ausschließlich registrierte neue IDs zulassen.
 * Schema/Stammdaten bleiben exakt gleich. Kein vollständiger Hashgleichheitsanspruch
 * bei absichtlich committed Fixtures. Keine Verbindung oder SQL-Ausführung. */
final class T03Preservation {
    private static final Map<String, String> KEYS = Map.of("status", "status_id", "verlage", "verlag_id",
            "typen", "typ_id", "formate", "format_id", "sammelbaende", "id", "mangareihe", "id",
            "mangadetails", "id", "baende", "id", "einkaufsliste", "id", "einkaufslistedetails", "id");
    static void verify(TestDatabaseSnapshot.Snapshot before, TestDatabaseSnapshot.Snapshot after,
            Map<String, Set<Long>> owned) {
        assertEquals(before.values().keySet(), after.values().keySet(), "Tabellen-/Metadatenschlüssel verändert");
        before.values().forEach((table, rows) -> {
            var actual = after.values().get(table);
            if (!T03RunJournal.BUSINESS.contains(table)) { assertTrue(Objects.equals(rows, actual), table); return; }
            int column = idColumn(before, table);
            var old = indexed(rows, column); var now = indexed(actual, column);
            old.forEach((id, value) -> {
                assertFalse(owned.getOrDefault(table, Set.of()).contains(id), "Fremde ID als Fixture registriert");
                assertTrue(Objects.equals(value, now.get(id)), "Bestehende Zeile geändert/gelöscht: " + table + "/" + id);
            });
            now.keySet().stream().filter(id -> !old.containsKey(id)).forEach(id ->
                    assertTrue(owned.getOrDefault(table, Set.of()).contains(id), "Unregistrierte neue Zeile: " + table + "/" + id));
        });
    }
    private static int idColumn(TestDatabaseSnapshot.Snapshot snapshot, String table) {
        return snapshot.values().get("schema.columns").stream()
                .filter(row -> table.equals(row.get(0)) && KEYS.get(table).equals(row.get(1)))
                .mapToInt(row -> ((Number) row.get(2)).intValue() - 1).findFirst().orElseThrow();
    }
    static Set<Long> ids(TestDatabaseSnapshot.Snapshot snapshot, String table) {
        return Set.copyOf(indexed(snapshot.values().get(table), idColumn(snapshot, table)).keySet());
    }
    /** Reine RAM-Projektion, Reihenfolge und Schema/Stammdaten unverändert. */
    static TestDatabaseSnapshot.Snapshot retain(TestDatabaseSnapshot.Snapshot snapshot, Map<String,Set<Long>> retained) {
        var values = new TreeMap<>(snapshot.values());
        for (String table : T03RunJournal.BUSINESS) {
            var wanted = retained.getOrDefault(table, Set.of());
            assertTrue(ids(snapshot, table).containsAll(wanted), "Historische ID fehlt: STOP");
            int column = idColumn(snapshot, table);
            values.put(table, snapshot.values().get(table).stream()
                    .filter(row -> wanted.contains(((Number) row.get(column)).longValue())).toList());
        }
        return new TestDatabaseSnapshot.Snapshot(values);
    }
    private static Map<Long, List<Object>> indexed(List<List<Object>> rows, int column) {
        var result = new HashMap<Long, List<Object>>();
        for (var row : rows) assertTrue(result.put(((Number) row.get(column)).longValue(), row) == null, "Doppelte ID");
        return result;
    }
}
