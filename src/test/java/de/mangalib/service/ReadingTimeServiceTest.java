package de.mangalib.service;

import de.mangalib.repository.BandRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReadingTimeServiceTest {
    private BandRepository.ReadingEntry entry(Integer factor, Integer reread) {
        return new BandRepository.ReadingEntry() {
            public Integer getMultiplier() { return factor; }
            public Integer getReread() { return reread; }
        };
    }

    @Test void countsPhysicalVolumesByFactorAndOptionalKnownRepeats() {
        var result = ReadingTimeService.calculate(List.of(entry(1, 0), entry(3, 1), entry(2, 2)));
        assertEquals(270, result.minutes());
        assertEquals(585, result.minutesWithRereads());
        assertEquals("0 Tage 4 Std. 30 Min.", result.duration());
    }

    @Test void unknownHistoryAndFactorsRemainVisible() {
        var result = ReadingTimeService.calculate(List.of(entry(null, 2), entry(0, 1), entry(-1, 0),
                entry(1, null), entry(1, -1)));
        assertEquals(3, result.unknownVolumes());
        assertEquals(2, result.unknownRereads());
        assertEquals(90, result.minutes());
        assertEquals(90, result.minutesWithRereads());
    }

    @Test void formatsWholeDaysAndEmptyLibrary() {
        assertEquals("1 Tage 0 Std. 0 Min.",
                ReadingTimeService.calculate(java.util.Collections.nCopies(32, entry(1, 0))).duration());
        assertEquals("0 Tage 0 Std. 0 Min.", ReadingTimeService.calculate(List.of()).duration());
    }

    @Test void usesLongArithmeticForLargeHistoricalValues() {
        assertEquals(96_636_764_160L, ReadingTimeService.calculate(
                List.of(entry(1, Integer.MAX_VALUE))).minutesWithRereads());
    }

    @Test void readsProjectedBandStatesWithoutSaving() {
        var repository = mock(BandRepository.class);
        when(repository.readingEntries()).thenReturn(List.of(entry(3, 1)));
        assertEquals(270, new ReadingTimeService(repository).estimate().minutesWithRereads());
        verify(repository).readingEntries();
        verifyNoMoreInteractions(repository);
    }
}
