package de.mangalib.validation;

import de.mangalib.entity.*;
import de.mangalib.repository.*;
import de.mangalib.service.*;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Echter Anlage-Service bis zum Details-Speicheraufruf, ausschließlich Repository-/Service-Mocks. */
class SeriesCreationRereadTest {
    @ParameterizedTest @NullSource @ValueSource(ints = {0, 1, 10_000})
    void rereadIsPresentBeforeDetailsAreSaved(Integer reread) {
        var rows = mock(MangaReiheRepository.class);
        var statuses = mock(StatusRepository.class); var formats = mock(FormatRepository.class);
        var types = mock(TypRepository.class); var publishers = mock(VerlagRepository.class);
        var volumes = mock(BandRepository.class); var detailsRepository = mock(MangaDetailsRepository.class);
        var service = new MangaReiheService(rows, statuses, formats, types, publishers, volumes, detailsRepository);
        var statusService = mock(StatusService.class); var formatService = mock(FormatService.class);
        var typeService = mock(TypService.class); var publisherService = mock(VerlagService.class);
        var status = new Status(); var format = new Format(); var type = new Typ(); var publisher = new Verlag();
        when(statuses.findById(1L)).thenReturn(Optional.of(status));
        when(formats.findById(1L)).thenReturn(Optional.of(format));
        when(types.findById(1L)).thenReturn(Optional.of(type));
        when(publishers.findById(1L)).thenReturn(Optional.of(publisher));
        when(statusService.getStatusById(1L)).thenReturn(Optional.of(status));
        when(formatService.getFormatById(1L)).thenReturn(Optional.of(format));
        when(typeService.getTypById(1L)).thenReturn(Optional.of(type));
        when(publisherService.getVerlagById(1L)).thenReturn(Optional.of(publisher));
        for (var entry : Map.of("statusService", statusService, "formatService", formatService,
                "typService", typeService, "verlagService", publisherService).entrySet()) {
            ReflectionTestUtils.setField(service, entry.getKey(), entry.getValue());
        }
        when(rows.save(any(MangaReihe.class))).thenAnswer(invocation -> {
            MangaReihe row = invocation.getArgument(0); row.setId(42L); return row;
        });
        var detailService = mock(MangaDetailsService.class); var bands = mock(BandService.class);
        ReflectionTestUtils.setField(service, "mangaDetailsService", detailService);
        ReflectionTestUtils.setField(service, "bandService", bands);
        var details = new MangaDetails();
        when(detailService.createMangaDetails(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            details.setMangaReihe(invocation.getArgument(0)); return details;
        });
        when(bands.calculateAenderungGesamtpreis(any())).thenReturn(BigDecimal.ZERO);
        when(bands.calculateGesamtpreis(any())).thenReturn(new BigDecimal("13.98"));
        when(detailsRepository.save(any())).thenAnswer(invocation -> {
            MangaDetails saved = invocation.getArgument(0);
            assertEquals(reread, saved.getReread(), "Wert muss bereits vor dem Speicheraufruf gesetzt sein");
            return saved;
        });
        MangaReihe saved;
        if (reread == null) {
            // Bestehende Kauf-/Serviceaufrufer ohne Reread behalten die bisherige null-Semantik.
            saved = service.saveMangaReihe(null, 1L, 1L, 1L, 1L, "Test", 2, new BigDecimal("6.99"),
                    null, null, null, null, BigDecimal.ZERO, false, Map.of());
        } else {
            saved = service.saveMangaReihe(null, 1L, 1L, 1L, 1L, "Test", 2, new BigDecimal("6.99"),
                    null, null, null, null, BigDecimal.ZERO, false, reread, Map.of());
        }
        assertEquals(42L, saved.getId()); assertEquals(reread, details.getReread());
        verify(detailsRepository).save(details);
        verify(bands).createAndSaveBaende(saved, 2, new BigDecimal("6.99"), BigDecimal.ZERO, Map.of());
        assertEquals(new BigDecimal("6.99"), saved.getPreisProBand());
    }

    @ParameterizedTest @ValueSource(ints = {-1, 10_001})
    void invalidRereadIsRejectedBeforeRepositoryAccess(int reread) {
        var rows = mock(MangaReiheRepository.class); var statuses = mock(StatusRepository.class);
        var formats = mock(FormatRepository.class); var types = mock(TypRepository.class);
        var publishers = mock(VerlagRepository.class); var volumes = mock(BandRepository.class);
        var details = mock(MangaDetailsRepository.class);
        var service = new MangaReiheService(rows, statuses, formats, types, publishers, volumes, details);
        assertThrows(IllegalArgumentException.class, () -> service.saveMangaReihe(null, 1L, 1L, 1L, 1L,
                "Test", 2, BigDecimal.ONE, null, null, null, null, BigDecimal.ZERO, false, reread, Map.of()));
        verifyNoInteractions(rows, statuses, formats, types, publishers, volumes, details);
    }
}
