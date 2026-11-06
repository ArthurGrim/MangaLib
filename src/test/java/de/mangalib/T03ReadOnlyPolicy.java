package de.mangalib;

import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Reiner Vergleich mit dem letzten dokumentierten DB-Zustand; keine Verbindung.
 * Abweichungen verlangen eine neue lesende Klärung, niemals automatische Anpassung. */
final class T03ReadOnlyPolicy {
    static final String EXPECTED_FINGERPRINT = "ca1433e49f4ce51e388480ff2e4118326011332264489dab82ab4d2040895570";
    static final Set<String> REFERENCES = Set.of("status", "verlage", "typen", "formate", "sammelbaende");

    static void verify(TestDatabaseSnapshot.Snapshot snapshot, String expectedFingerprint) throws Exception {
        var values = snapshot.values();
        var tables = values.get("schema.tables");
        assertNotNull(tables); assertEquals(10, tables.size(), "Tabellenanzahl abweichend: STOP");
        var names = new HashSet<String>();
        for (var table : tables) {
            assertTrue(names.add(table.get(0).toString()), "Doppelte Tabellenmetadaten: STOP");
            assertEquals("InnoDB", table.get(1), "Abweichende Tabellenengine: STOP");
        }
        var expectedNames = new HashSet<>(REFERENCES); expectedNames.addAll(T03RunJournal.BUSINESS);
        assertEquals(expectedNames, names, "Unerwartete Tabellen: STOP");
        assertFalse(values.get("schema.foreignKeys").isEmpty(), "Fremdschlüssel fehlen: STOP");
        assertFalse(values.get("schema.keyColumns").isEmpty(), "Schlüsselmetadaten fehlen: STOP");
        int references = 0;
        for (String table : REFERENCES) {
            assertFalse(values.get(table).isEmpty(), "Stammdatentabelle leer: STOP");
            references += values.get(table).size();
        }
        assertEquals(78, references, "Stammdatenanzahl abweichend: STOP");
        for (String table : T03RunJournal.BUSINESS)
            assertTrue(values.get(table).isEmpty(), "Vorhandene Geschäftsdatensätze/fremder Testlauf: STOP; " + table);
        assertEquals(expectedFingerprint, snapshot.fingerprint(), "Ausgangsdaten-/Schemafingerabdruck abweichend: STOP");
    }
}
