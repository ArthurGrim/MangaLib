package de.mangalib.service;

import de.mangalib.entity.Band;
import de.mangalib.entity.MangaDetails;
import de.mangalib.entity.MangaReihe;
import de.mangalib.repository.BandRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Ist-Verhalten vor S03, ausschließlich synthetische Objekte und Repository-Mocks. */
class ReadingHistoryCharacterizationTest {
    private final BandRepository repository = mock(BandRepository.class);
    private final BandService service = new BandService();

    ReadingHistoryCharacterizationTest() {
        ReflectionTestUtils.setField(service, "bandRepository", repository);
    }

    @ParameterizedTest @NullSource @ValueSource(ints = {0, 2})
    void markingAndResettingPreservesHistoricalRereadAndDates(Integer reread) {
        Band band = new Band();
        band.setId(17L); band.setReread(reread);
        band.setErstelltAm(LocalDate.of(2020, 1, 2));
        band.setAktualisiertAm(LocalDate.of(2021, 3, 4));
        when(repository.findById(17L)).thenReturn(Optional.of(band));
        when(repository.save(band)).thenReturn(band);
        assertSame(band, service.updateBandIstGelesen(17L, true).orElseThrow());
        assertTrue(band.isIstGelesen());
        assertSame(band, service.updateBandIstGelesen(17L, false).orElseThrow());
        assertFalse(band.isIstGelesen());
        assertEquals(reread, band.getReread());
        assertEquals(LocalDate.of(2020, 1, 2), band.getErstelltAm());
        assertEquals(LocalDate.of(2021, 3, 4), band.getAktualisiertAm());
        // Nur Java-Objektverhalten: Hibernate-Zeitstempel werden hier nicht ausgeführt.
        verify(repository, times(2)).save(band);
    }

    @Test void repeatedReadMarkSavesAgainWithoutIncrementingReread() {
        Band band = new Band(); band.setIstGelesen(true); band.setReread(2);
        when(repository.findById(17L)).thenReturn(Optional.of(band));
        service.updateBandIstGelesen(17L, true);
        service.updateBandIstGelesen(17L, true);
        assertEquals(2, band.getReread());
        verify(repository, times(2)).save(band);
    }

    @Test void directMarkDoesNotRecalculateSeriesAggregate() {
        MangaDetails details = new MangaDetails(); details.setIstGelesen(false);
        MangaReihe row = new MangaReihe(); row.setMangaDetails(details);
        Band band = new Band(); band.setMangaReihe(row);
        when(repository.findById(17L)).thenReturn(Optional.of(band));
        service.updateBandIstGelesen(17L, true);
        assertTrue(band.isIstGelesen());
        assertFalse(details.isIstGelesen());
        verify(repository).findById(17L); verify(repository).save(band);
        verifyNoMoreInteractions(repository);
    }

    @Test void missingBandDoesNotSave() {
        when(repository.findById(17L)).thenReturn(Optional.empty());
        assertTrue(service.updateBandIstGelesen(17L, true).isEmpty());
        verify(repository).findById(17L); verifyNoMoreInteractions(repository);
    }

    @Test void missingArgumentsFailBeforeRepositoryAccess() {
        assertThrows(IllegalArgumentException.class, () -> service.updateBandIstGelesen(null, true));
        assertThrows(IllegalArgumentException.class, () -> service.updateBandIstGelesen(17L, null));
        verifyNoInteractions(repository);
    }

    @Test void aggregationPreservesEachVolumeAndNullRereads() {
        MangaReihe row = new MangaReihe(); row.setId(9L);
        MangaDetails details = new MangaDetails(); details.setMangaReihe(row);
        details.setIstGelesen(true); details.setReread(3);
        Band read = new Band(); read.setIstGelesen(true); read.setReread(null);
        Band unread = new Band(); unread.setIstGelesen(false); unread.setReread(2);
        when(repository.findByMangaReiheId(9L)).thenReturn(List.of(read, unread));
        service.updateMangaDetailsIstGelesenStatus(details);
        assertFalse(details.isIstGelesen()); assertEquals(3, details.getReread());
        assertTrue(read.isIstGelesen()); assertNull(read.getReread());
        assertFalse(unread.isIstGelesen()); assertEquals(2, unread.getReread());
        verify(repository).findByMangaReiheId(9L); verifyNoMoreInteractions(repository);
    }
}
