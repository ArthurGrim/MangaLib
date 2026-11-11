package de.mangalib.service;

import de.mangalib.repository.BandRepository;
import de.mangalib.repository.MangaReiheRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StatisticsDistributionServiceTest {
    @Test void preservesUnknownGroupsAndCountsBandsSeparately() {
        var series = mock(MangaReiheRepository.class);
        var volumes = mock(BandRepository.class);
        when(series.statisticsByOrigin()).thenReturn(List.<Object[]>of(
                new Object[]{null, 2L}, new Object[]{"", 1L}, new Object[]{" Japan ", 3L}));
        when(series.statisticsByOutOfPrint()).thenReturn(List.<Object[]>of(
                new Object[]{null, 2L}, new Object[]{false, 4L}));
        when(volumes.statisticsByReadState()).thenReturn(List.<Object[]>of(
                new Object[]{true, 8L}, new Object[]{false, 7L}));
        var result = new StatisticsDistributionService(series, volumes).overview();
        assertEquals(List.of("Typ", "Format", "Status", "Herkunft", "Lesestatus", "Vergriffenheit"),
                result.stream().map(StatisticsDistributionService.Distribution::title).toList());
        assertEquals(Map.of("Unbekannt", 3L, "Japan", 3L), result.get(3).counts());
        assertEquals("Bände", result.get(4).unit());
        assertEquals(Map.of("Gelesen", 8L, "Ungelesen", 7L), result.get(4).counts());
        assertEquals(Map.of("Unbekannt", 2L, "Nicht vergriffen", 4L), result.get(5).counts());
        assertTrue(result.getFirst().counts().isEmpty());
        verify(series).statisticsByType(); verify(series).statisticsByFormat();
        verify(series).statisticsByStatus(); verify(series).statisticsByOrigin();
        verify(series).statisticsByOutOfPrint(); verify(volumes).statisticsByReadState();
        verifyNoMoreInteractions(series, volumes);
    }
}
