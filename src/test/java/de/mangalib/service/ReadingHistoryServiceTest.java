package de.mangalib.service;

import de.mangalib.entity.*;
import de.mangalib.repository.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static de.mangalib.service.ReadingHistoryStatistics.Kind.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Echter Coordinator und Spring-Transaktionscallbacks, RAM statt JDBC. */
class ReadingHistoryServiceTest {
    private final ReadingHistoryStore store = mock(ReadingHistoryStore.class);
    private final BandRepository bands = mock(BandRepository.class);
    private final MangaReiheRepository rows = mock(MangaReiheRepository.class);
    private final FakeLibraryTransactionManager tx = new FakeLibraryTransactionManager();
    private final LibraryWriteCoordinator coordinator = new LibraryWriteCoordinator(tx);
    private final ReadingHistoryService service = new ReadingHistoryService(store, bands, rows, coordinator,
            Clock.fixed(Instant.parse("2026-11-15T12:00:00Z"), ZoneId.of("Europe/Berlin")));
    private final List<ReadingHistoryStore.Event> events = new ArrayList<>();
    private Band band;
    private MangaReihe row;
    private final LocalDate date = LocalDate.of(2026, 11, 15);

    @BeforeEach void setup() {
        row = new MangaReihe(); row.setId(3L);
        row.setStatus(new Status()); row.setVerlag(new Verlag()); row.setTyp(new Typ()); row.setFormat(new Format());
        row.getStatus().setStatusId(1L); row.getVerlag().setVerlagId(1L);
        row.getTyp().setTypId(1L); row.getFormat().setFormatId(1L);
        var detail = new MangaDetails(); detail.setMangaReihe(row); detail.setReread(0);
        detail.setId(3L);
        var factor = new Sammelband(); factor.setId(1L); factor.setMultiplikator(3); detail.setSammelbaende(factor); row.setMangaDetails(detail);
        band = new Band(); band.setId(7L); band.setMangaReihe(row); band.setBandNr(1); band.setReread(0);
        when(bands.findById(7L)).thenReturn(Optional.of(band));
        when(bands.existsById(7L)).thenReturn(true);
        when(bands.findByMangaReiheId(3L)).thenReturn(List.of(band));
        when(store.forBand(anyLong())).thenAnswer(call -> events.stream().filter(e -> e.bandId() == (long) call.getArgument(0)).toList());
        when(store.forOperation(any())).thenAnswer(call -> events.stream().filter(e -> e.operationId().equals(call.getArgument(0))).toList());
        when(store.all()).thenAnswer(call -> List.copyOf(events));
        doAnswer(call -> {
            events.add(new ReadingHistoryStore.Event(events.size()+1, call.getArgument(0), call.getArgument(1),
                    call.getArgument(2), call.getArgument(2), call.getArgument(3), 0, call.getArgument(4)));
            return null;
        }).when(store).insert(anyLong(), any(), any(), any(), any());
        when(store.correct(anyLong(), anyLong(), anyInt(), any())).thenAnswer(call -> {
            for (int i=0; i<events.size(); i++) {
                var event = events.get(i);
                if (event.id() == (long) call.getArgument(0) && event.revision() == (int) call.getArgument(2)) {
                    events.set(i, new ReadingHistoryStore.Event(event.id(), event.bandId(), event.operationId(),
                            call.getArgument(3), event.originalDate(), event.kind(), event.revision()+1, event.volumeFactor()));
                    return true;
                }
            }
            return false;
        });
    }

    @Test void firstReadAndRereadUpdateAggregateAndRetainFactor() {
        assertEquals(1, service.complete(List.of(7L), UUID.randomUUID(), FIRST_READ, date).size());
        assertTrue(band.isIstGelesen()); assertTrue(row.getMangaDetails().isIstGelesen());
        assertEquals(0, band.getReread());
        service.complete(List.of(7L), UUID.randomUUID(), REREAD, date);
        assertEquals(1, band.getReread()); assertEquals(1, row.getMangaDetails().getReread());
        assertEquals(2, events.size()); assertEquals(3, events.getFirst().volumeFactor());
        assertEquals(new ReadingHistoryService.Month("2026-11", 1, 1, 270, 0), service.months().getFirst());
        assertEquals(2, tx.commits.get());
    }

    @Test void replayDoesNotWriteAgainEvenAfterDateCorrection() {
        var operation = UUID.randomUUID();
        service.complete(List.of(7L), operation, FIRST_READ, date);
        service.correct(7, 1, 0, date.minusDays(1));
        assertEquals(date.minusDays(1), service.complete(List.of(7L), operation, FIRST_READ, date).getFirst().date());
        assertEquals(1, events.size()); verify(bands, times(1)).save(band);
        assertThrows(ReadingHistoryService.Conflict.class, () -> service.complete(List.of(7L), operation, FIRST_READ, date.minusDays(1)));
    }

    @Test void sameDayExplicitRereadsAreSeparateButSameActionIsNot() {
        band.setIstGelesen(true);
        var operation = UUID.randomUUID();
        service.complete(List.of(7L), operation, REREAD, date);
        service.complete(List.of(7L), operation, REREAD, date);
        service.complete(List.of(7L), UUID.randomUUID(), REREAD, date);
        assertEquals(2, band.getReread()); assertEquals(2, events.size());
    }

    @Test void changedBatchMembershipIsConflict() {
        var operation = UUID.randomUUID(); service.complete(List.of(7L), operation, FIRST_READ, date);
        assertThrows(ReadingHistoryService.Conflict.class, () -> service.complete(List.of(7L, 8L), operation, FIRST_READ, date));
        assertEquals(1, events.size());
    }

    @Test void bulkMissingBandFailsBeforeMutation() {
        assertThrows(ReadingHistoryService.Missing.class, () -> service.complete(List.of(7L, 8L), UUID.randomUUID(), FIRST_READ, date));
        assertFalse(band.isIstGelesen()); assertTrue(events.isEmpty()); verify(bands, never()).save(any());
    }

    @Test void invalidRequestsNeverEnterTransaction() {
        for (var ids : List.of(List.<Long>of(), List.of(0L), List.of(7L, 7L)))
            assertThrows(IllegalArgumentException.class, () -> service.complete(ids, UUID.randomUUID(), FIRST_READ, date));
        assertThrows(IllegalArgumentException.class, () -> service.complete(List.of(7L), UUID.randomUUID(), FIRST_READ, date.plusDays(1)));
        assertThrows(IllegalArgumentException.class, () -> service.complete(List.of(7L), UUID.randomUUID(), FIRST_READ, null));
        assertEquals(0, tx.begins.get()); verifyNoInteractions(store, bands, rows);
    }

    @Test void unknownOrExhaustedCounterIsNotInventedOrOverflowed() {
        band.setIstGelesen(true);
        for (Integer value : Arrays.asList(null, -1, 10_000)) {
            band.setReread(value);
            assertThrows(ReadingHistoryService.Conflict.class, () -> service.complete(List.of(7L), UUID.randomUUID(), REREAD, date));
            assertEquals(value, band.getReread());
        }
        assertTrue(events.isEmpty());
    }

    @Test void rereadRequiresExistingReadKnowledgeAndResetDoesNotDeleteHistory() {
        assertThrows(ReadingHistoryService.Conflict.class, () -> service.complete(List.of(7L), UUID.randomUUID(), REREAD, date));
        service.complete(List.of(7L), UUID.randomUUID(), FIRST_READ, date);
        band.setIstGelesen(false);
        service.complete(List.of(7L), UUID.randomUUID(), REREAD, date);
        assertEquals(2, events.size()); assertEquals(1, band.getReread());
    }

    @Test void correctionMovesMonthWithoutTouchingCountersAndRejectsStaleVersion() {
        service.complete(List.of(7L), UUID.randomUUID(), FIRST_READ, date);
        service.correct(7, 1, 0, LocalDate.of(2026, 10, 31));
        assertEquals(0, band.getReread()); assertEquals(0, row.getMangaDetails().getReread());
        assertEquals("2026-10", service.months().getFirst().month());
        assertEquals(1, service.correct(7, 1, 0, LocalDate.of(2026, 10, 31)).revision());
        assertThrows(ReadingHistoryService.Conflict.class, () -> service.correct(7, 1, 0, date));
        assertThrows(ReadingHistoryService.Missing.class, () -> service.correct(8, 1, 0, date));
    }

    @Test void automaticTransitionNeedsTransactionAndCreatesOnlyFirstRead() {
        band.setIstGelesen(true);
        assertThrows(IllegalStateException.class, () -> service.recordTransition(band, false));
        coordinator.execute(() -> { service.recordTransition(band, false); service.recordTransition(band, false);
            service.recordTransition(band, true); band.setIstGelesen(false); service.recordTransition(band, true); return null; });
        assertEquals(1, events.size()); assertEquals(0, band.getReread());
    }

    @Test void storeFailureTriggersRollbackAndUncertainCommitQuarantines() {
        band.setIstGelesen(true);
        doThrow(new IllegalStateException("synthetic")).when(store).insert(anyLong(), any(), any(), any(), any());
        // RAM-Fake stellt den Objektzustand beim Rollback ausdrücklich wieder her, kein MySQL-Nachweis.
        tx.afterRollback = () -> band.setReread(0);
        assertThrows(IllegalStateException.class, () -> service.complete(List.of(7L), UUID.randomUUID(), REREAD, date));
        assertEquals(1, tx.rollbacks.get()); assertEquals(0, band.getReread());
        reset(store); when(store.forOperation(any())).thenReturn(List.of()); when(store.forBand(anyLong())).thenReturn(List.of());
        tx.fault = "commit";
        assertThrows(RuntimeException.class, () -> service.complete(List.of(7L), UUID.randomUUID(), REREAD, date));
        int begins = tx.begins.get();
        assertThrows(IllegalStateException.class, () -> service.complete(List.of(7L), UUID.randomUUID(), REREAD, date));
        assertEquals(begins, tx.begins.get());
    }

    @Test void concurrentReplayWaitsUntilCommitAndCountsOnce() throws Exception {
        band.setIstGelesen(true); var operation = UUID.randomUUID();
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var first = new AtomicBoolean(true);
        tx.beforeCommit = () -> { if (first.getAndSet(false)) { entered.countDown();
            try { if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("deadline"); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); } } };
        var executor = Executors.newFixedThreadPool(2);
        try {
            var a = executor.submit(() -> service.complete(List.of(7L), operation, REREAD, date));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var b = executor.submit(() -> service.complete(List.of(7L), operation, REREAD, date));
            assertFalse(b.isDone()); release.countDown();
            assertEquals(1, a.get(5, TimeUnit.SECONDS).size()); assertEquals(1, b.get(5, TimeUnit.SECONDS).size());
            assertEquals(1, band.getReread()); assertEquals(1, events.size());
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    @Test void manualCounterEditsAreRejectedAndUnknownFactorsRemainVisible() {
        assertThrows(ReadingHistoryService.Conflict.class, () -> service.requireUnchangedCounter(1, 2));
        service.requireUnchangedCounter(1, 1); service.requireUnchangedCounter(null, null);
        row.getMangaDetails().getSammelbaende().setMultiplikator(0);
        service.complete(List.of(7L), UUID.randomUUID(), FIRST_READ, date);
        assertEquals(1, service.months().getFirst().unknownFactors());
        assertEquals(0, service.months().getFirst().minutes());
    }

    @Test void firstReadCannotBeMovedAfterKnownReread() {
        service.complete(List.of(7L), UUID.randomUUID(), FIRST_READ, date.minusDays(2));
        service.complete(List.of(7L), UUID.randomUUID(), REREAD, date.minusDays(1));
        assertThrows(ReadingHistoryService.Conflict.class, () -> service.correct(7, 1, 0, date));
        assertThrows(ReadingHistoryService.Conflict.class, () -> service.correct(7, 2, 0, date.minusDays(3)));
        assertEquals(date.minusDays(2), events.getFirst().date()); assertEquals(1, band.getReread());
    }

    private BandRepository.ReadingState projected(Integer factor, Integer reread, boolean read) {
        return new BandRepository.ReadingState() {
            public Long getId() { return 7L; }
            public Boolean getReadState() { return read; }
            public Integer getMultiplier() { return factor; }
            public Integer getReread() { return reread; }
        };
    }

    @Test void totalReadingTimeRetainsHistoryAfterResetAndDoesNotDoubleCount() {
        service.complete(List.of(7L), UUID.randomUUID(), FIRST_READ, date);
        service.complete(List.of(7L), UUID.randomUUID(), REREAD, date);
        when(bands.readingStates()).thenReturn(List.of(projected(1, 1, false)));
        // Beide Lektüren waren 3in1, spätere Metadatenänderung und Statusreset ändern sie nicht.
        assertEquals(new ReadingTimeService.Estimate(135, 270, 0, 0), service.readingTime());
    }

    @Test void legacyUndatedCountsAreNotAddedToDatedCountsTwice() {
        band.setIstGelesen(true); band.setReread(2);
        service.complete(List.of(7L), UUID.randomUUID(), REREAD, date);
        when(bands.readingStates()).thenReturn(List.of(projected(1, 3, true)));
        assertEquals(new ReadingTimeService.Estimate(45, 270, 0, 0), service.readingTime());
        when(bands.readingStates()).thenReturn(List.of(projected(null, null, true)));
        assertEquals(new ReadingTimeService.Estimate(0, 135, 1, 1), service.readingTime());
        when(bands.readingStates()).thenReturn(List.of(projected(1, 0, true)));
        assertThrows(IllegalStateException.class, service::readingTime);
    }
}
