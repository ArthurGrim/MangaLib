package de.mangalib.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static de.mangalib.service.ReadingHistoryStatistics.*;
import static org.junit.jupiter.api.Assertions.*;

class ReadingHistoryStatisticsTest {
    private Completion completion(long band, String date, Kind kind) {
        return new Completion(UUID.randomUUID(), band, LocalDate.parse(date), kind);
    }

    @Test void monthsSeparateFirstReadFromRereadsAcrossYearBoundary() {
        var result = byMonth(List.of(completion(1, "2025-12-31", Kind.FIRST_READ),
                completion(1, "2026-01-01", Kind.REREAD), completion(2, "2026-01-31", Kind.FIRST_READ)));
        assertEquals(List.of(YearMonth.of(2025, 12), YearMonth.of(2026, 1)), List.copyOf(result.keySet()));
        assertEquals(new Counts(1, 0), result.get(YearMonth.of(2025, 12)));
        assertEquals(new Counts(1, 1), result.get(YearMonth.of(2026, 1)));
        assertEquals(2, result.get(YearMonth.of(2026, 1)).total());
    }

    @Test void leapDayAndBackdatedCompletionsUseChosenCalendarMonth() {
        assertEquals(new Counts(1, 0), byMonth(List.of(completion(1, "2024-02-29", Kind.FIRST_READ)))
                .get(YearMonth.of(2024, 2)));
    }

    @Test void identicalActionCountsOnlyOnce() {
        var event = completion(1, "2026-11-15", Kind.REREAD);
        assertEquals(new Counts(0, 1), byMonth(List.of(event, event)).get(YearMonth.of(2026, 11)));
    }

    @Test void consciouslyRepeatedCompletionsOnSameDayCountSeparately() {
        var first = completion(1, "2026-11-15", Kind.FIRST_READ);
        var reread = completion(1, "2026-11-15", Kind.REREAD);
        var another = completion(1, "2026-11-15", Kind.REREAD);
        assertEquals(new Counts(1, 2), byMonth(List.of(first, reread, another))
                .get(YearMonth.of(2026, 11)));
    }

    @Test void bulkActionCountsEachBandWithoutMergingThem() {
        var id = UUID.randomUUID(); var date = LocalDate.of(2026, 11, 15);
        assertEquals(new Counts(2, 0), byMonth(List.of(new Completion(id, 1, date, Kind.FIRST_READ),
                new Completion(id, 2, date, Kind.FIRST_READ))).get(YearMonth.of(2026, 11)));
    }

    @Test void conflictingDateOrKindForSameActionIsRejected() {
        var event = completion(1, "2026-11-15", Kind.FIRST_READ);
        assertThrows(IllegalArgumentException.class, () -> byMonth(List.of(event,
                new Completion(event.operationId(), 1, event.date().plusDays(1), event.kind()))));
        assertThrows(IllegalArgumentException.class, () -> byMonth(List.of(event,
                new Completion(event.operationId(), 1, event.date(), Kind.REREAD))));
    }

    @Test void missingHistoricalDatesAreNeverReplacedWithToday() {
        assertThrows(IllegalArgumentException.class, () -> new Completion(UUID.randomUUID(), 1, null, Kind.FIRST_READ));
        assertTrue(byMonth(List.of()).isEmpty());
    }

    @Test void invalidIdentitiesAndMissingKindsAreRejected() {
        var date = LocalDate.of(2026, 11, 15);
        assertThrows(IllegalArgumentException.class, () -> new Completion(null, 1, date, Kind.FIRST_READ));
        assertThrows(IllegalArgumentException.class, () -> new Completion(UUID.randomUUID(), 0, date, Kind.FIRST_READ));
        assertThrows(IllegalArgumentException.class, () -> new Completion(UUID.randomUUID(), -1, date, Kind.FIRST_READ));
        assertThrows(IllegalArgumentException.class, () -> new Completion(UUID.randomUUID(), 1, date, null));
    }

    @Test void resultCannotBeChangedAndInputIsPreserved() {
        var event = completion(1, "2026-11-15", Kind.FIRST_READ);
        var input = new java.util.ArrayList<>(List.of(event));
        var result = byMonth(input);
        assertThrows(UnsupportedOperationException.class, result::clear);
        input.clear();
        assertEquals(new Counts(1, 0), result.get(YearMonth.of(2026, 11)));
        assertEquals(LocalDate.of(2026, 11, 15), event.date());
    }
}
