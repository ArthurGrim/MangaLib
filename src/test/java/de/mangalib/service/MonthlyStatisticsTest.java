package de.mangalib.service;

import de.mangalib.repository.EinkaufslisteRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MonthlyStatisticsTest {
    @Test void preservesCentsAndPassesSelectedYearToRepository() {
        var repository = mock(EinkaufslisteRepository.class);
        when(repository.findGeldProMonat(2025)).thenReturn(List.<Object[]>of(
                new Object[]{12, new BigDecimal("14.98")}));
        when(repository.findGeldProMonat(2026)).thenReturn(List.<Object[]>of(
                new Object[]{1, new BigDecimal("-0.02")}));
        var service = new EinkaufslisteService(repository);
        assertEquals(new BigDecimal("14.98"), service.getGeldProMonat(2025).get(12));
        assertEquals(new BigDecimal("-0.02"), service.getGeldProMonat(2026).get(1));
        assertTrue(service.getGeldProMonat(2027).isEmpty());
        verify(repository).findGeldProMonat(2025);
        verify(repository).findGeldProMonat(2026);
        verify(repository).findGeldProMonat(2027);
        verifyNoMoreInteractions(repository);
    }
}
