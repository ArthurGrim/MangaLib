package de.mangalib;

import de.mangalib.entity.Band;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Unveränderliche Werte aller persistierten Bandfelder; URL-Strings statt
 * URL.equals/hashCode, damit kein DNS-Aufruf Teil des Vergleichs sein kann. */
final class T03BandSnapshot {
    record State(Long id, Long row, Integer number, Integer index, BigDecimal price, BigDecimal adjustment,
                 boolean read, Integer reread, Boolean special, String cover, String passion,
                 LocalDate created, LocalDate updated) { }
    static Map<Long, State> capture(Collection<Band> bands) {
        var values = new TreeMap<Long, State>();
        for (var b : bands) {
            assertTrue(b.getId() != null && b.getId() > 0, "Bandidentität fehlt");
            assertNotNull(b.getMangaReihe()); assertNotNull(b.getMangaReihe().getId());
            var value = new State(b.getId(), b.getMangaReihe().getId(), b.getBandNr(), b.getBandIndex(),
                    b.getPreis(), b.getAenderungPreis(), b.isIstGelesen(), b.getReread(), b.getIstSpecial(),
                    b.getBildUrl() == null ? null : b.getBildUrl().toExternalForm(),
                    b.getMpUrl() == null ? null : b.getMpUrl().toExternalForm(), b.getErstelltAm(), b.getAktualisiertAm());
            assertTrue(values.put(b.getId(), value) == null, "Doppelte Bandidentität");
        }
        return Collections.unmodifiableMap(values);
    }
    static void verifyFourthOnly(Map<Long, State> before, Map<Long, State> after, long rowId) {
        assertEquals(3, before.size()); assertEquals(4, after.size());
        assertEquals(List.of(1, 2, 3), before.values().stream().map(State::number).sorted().toList());
        before.forEach((id, value) -> assertTrue(Objects.equals(value, after.get(id)), "Vorhandener Band verändert/ersetzt: STOP"));
        var added = new HashSet<>(after.keySet()); added.removeAll(before.keySet());
        assertEquals(1, added.size());
        var band = after.get(added.iterator().next());
        assertEquals(rowId, band.row()); assertEquals(4, band.number());
        assertTrue(after.values().stream().allMatch(value -> Objects.equals(rowId, value.row())));
    }
}
