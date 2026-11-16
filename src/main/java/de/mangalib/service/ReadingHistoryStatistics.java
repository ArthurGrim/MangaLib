package de.mangalib.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;

/** Monatsauswertung ausdrücklich datierter Lektüren, ohne historische Zähler zu deuten.
 * Der optionale ReadingHistoryService liefert die persistierten Ereignisse.
 */
public final class ReadingHistoryStatistics {
    private ReadingHistoryStatistics() {}

    public enum Kind { FIRST_READ, REREAD }

    /** Der Kalendertag ist fachlich gewählt und wird nicht über Zeitzonen verschoben. */
    public record Completion(UUID operationId, long bandId, LocalDate date, Kind kind) {
        public Completion {
            if (operationId == null || bandId <= 0 || date == null || kind == null)
                throw new IllegalArgumentException("Unvollständige Lektüre.");
        }
    }

    public record Counts(long firstReads, long rereads) {
        public long total() { return Math.addExact(firstReads, rereads); }
    }

    private record Key(UUID operationId, long bandId) {}

    /** Zählt Abschlüsse, nicht unterschiedliche Bände. Gleiche Aktionen zählen nur einmal.
     * Widersprüchliche Inhalte desselben Schlüssels werden nicht still zusammengeführt.
     * Dies ersetzt keinen transaktionalen Wiederholungsschutz beim Schreiben.
     */
    public static SortedMap<YearMonth, Counts> byMonth(Collection<Completion> completions) {
        Objects.requireNonNull(completions);
        Map<Key, Completion> seen = new HashMap<>();
        SortedMap<YearMonth, Counts> months = new TreeMap<>();
        for (Completion completion : completions) {
            Objects.requireNonNull(completion);
            var previous = seen.putIfAbsent(new Key(completion.operationId(), completion.bandId()), completion);
            if (previous != null) {
                if (!previous.equals(completion)) throw new IllegalArgumentException("Widersprüchliche Lektüre.");
                continue;
            }
            var month = YearMonth.from(completion.date());
            var count = months.getOrDefault(month, new Counts(0, 0));
            months.put(month, completion.kind() == Kind.FIRST_READ
                    ? new Counts(Math.incrementExact(count.firstReads()), count.rereads())
                    : new Counts(count.firstReads(), Math.incrementExact(count.rereads())));
        }
        return Collections.unmodifiableSortedMap(months);
    }
}
