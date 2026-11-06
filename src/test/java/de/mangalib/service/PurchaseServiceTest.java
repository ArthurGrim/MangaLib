package de.mangalib.service;

import de.mangalib.entity.*;
import de.mangalib.repository.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static de.mangalib.service.PurchaseService.Outcome.*;

/** Echte Koordination und Bibliotheksservices, nur Repository-/Transaktions-Fakes.
 * Kein Datenbank-Commit/-Rollback durch diese Tests behauptet. */
class PurchaseServiceTest {
    T03Fixtures.Library f;
    FakeLibraryTransactionManager tx;
    EinkaufslisteRepository cart;
    EinkaufslisteItemDetailsRepository details;
    EinkaufslisteItem item;
    PurchaseService service;

    @BeforeEach void setup() {
        f = new T03Fixtures.Library(); tx = new FakeLibraryTransactionManager();
        cart = mock(EinkaufslisteRepository.class); details = mock(EinkaufslisteItemDetailsRepository.class);
        item = T03Fixtures.item(false);
        when(cart.findByIdForPurchase(7L)).thenReturn(Optional.of(item));
        when(cart.save(any())).thenAnswer(i -> i.getArgument(0));
        when(f.rows.findByMangaIndex(123)).thenReturn(List.of(f.row));
        when(f.rows.findByIdForPurchase(42L)).thenReturn(Optional.of(f.row));
        service = new PurchaseService(new LibraryWriteCoordinator(tx), cart, details, f.rows, f.bands, f.series);
    }
    PurchaseService.Request request(Long status, boolean confirmed) {
        return new PurchaseService.Request(7, status, confirmed ? 42L : null, confirmed ? 4 : null, confirmed ? 4 : null);
    }
    void noWrites() {
        verify(cart, never()).save(any()); verify(cart, never()).flush();
        verify(f.rows, never()).save(any()); verify(f.bands, never()).save(any());
    }

    @ParameterizedTest @NullSource @ValueSource(longs = {1})
    void bothPurchaseModesUseSameConfirmedAppendAndDoNotRepeat(Long status) {
        var before = f.state();
        assertEquals(CONFIRM_VOLUMES, service.purchase(request(status, false)).outcome()); noWrites();
        assertEquals(UPDATED, service.purchase(request(status, true)).outcome());
        assertTrue(item.getGekauft()); assertEquals(4, f.stored.size());
        before.forEach((id, state) -> assertEquals(state, f.state().get(id)));
        assertFalse(f.stored.getLast().isIstGelesen()); assertNull(f.stored.getLast().getReread());
        assertFalse(f.row.getMangaDetails().isIstGelesen()); assertEquals(4, f.row.getMangaDetails().getReread());
        assertEquals(ALREADY_PURCHASED, service.purchase(request(status, true)).outcome());
        assertEquals(4, f.stored.size()); verify(cart, times(1)).save(item); verify(cart, times(1)).flush();
        assertEquals(3, tx.commits.get());
    }

    @Test void alreadyPurchasedHasNoLibraryReadOrWrite() {
        item.setGekauft(true);
        assertEquals(ALREADY_PURCHASED, service.purchase(request(null, false)).outcome());
        verifyNoInteractions(f.rows, f.bands, details); noWrites();
    }
    @Test void unknownBoughtStateIsNotFalse() {
        item.setGekauft(null);
        assertEquals(UNKNOWN_STATUS, service.purchase(request(null, false)).outcome());
        assertNull(item.getGekauft()); noWrites();
    }
    @Test void missingItemAndNonpositiveIdDoNotWrite() {
        when(cart.findByIdForPurchase(7L)).thenReturn(Optional.empty());
        assertEquals(NOT_FOUND, service.purchase(request(null, false)).outcome());
        assertEquals(NOT_FOUND, service.purchase(new PurchaseService.Request(0, null, null, null, null)).outcome()); noWrites();
    }

    @ParameterizedTest @ValueSource(strings = {"missingIndex", "zeroIndex", "multiple", "publisher", "type", "format", "changedIndex", "disappeared"})
    void ambiguousOrIncompatibleIdentityNeverWrites(String fault) {
        var expected = AMBIGUOUS_SERIES;
        switch (fault) {
            case "missingIndex" -> { item.setMangaIndex(null); when(f.rows.findByTitel(item.getTitel())).thenReturn(List.of(f.row)); }
            case "zeroIndex" -> item.setMangaIndex(0);
            case "multiple" -> when(f.rows.findByMangaIndex(123)).thenReturn(List.of(f.row, T03Fixtures.row(99)));
            case "publisher" -> { f.row.getVerlag().setVerlagId(2L); expected = INCOMPATIBLE_SERIES; }
            case "type" -> { f.row.getTyp().setTypId(2L); expected = INCOMPATIBLE_SERIES; }
            case "format" -> { f.row.getFormat().setFormatId(2L); expected = INCOMPATIBLE_SERIES; }
            case "changedIndex" -> { f.row.setMangaIndex(999); expected = INCOMPATIBLE_SERIES; }
            case "disappeared" -> when(f.rows.findByIdForPurchase(42L)).thenReturn(Optional.empty());
        }
        assertEquals(expected, service.purchase(request(null, true)).outcome()); noWrites();
        if (fault.equals("missingIndex")) verify(f.rows, never()).findByMangaIndex(any());
        if (fault.equals("zeroIndex")) verify(f.rows, never()).findByMangaIndex(0);
    }

    @ParameterizedTest @ValueSource(strings = {"gap", "duplicateNumber", "duplicateId", "unknownPrice", "missingDetails", "declaredMismatch", "foreignParent"})
    void inconsistentInventoryIsRejectedBeforeMutation(String fault) {
        switch (fault) {
            case "gap" -> f.stored.getFirst().setBandNr(4);
            case "duplicateNumber" -> f.stored.getFirst().setBandNr(2);
            case "duplicateId" -> f.stored.getFirst().setId(102L);
            case "unknownPrice" -> f.stored.getFirst().setPreis(null);
            case "missingDetails" -> f.row.setMangaDetails(null);
            case "declaredMismatch" -> f.row.setAnzahlBaende(2);
            case "foreignParent" -> f.stored.getFirst().setMangaReihe(T03Fixtures.row(99));
        }
        assertEquals(INVALID_INVENTORY, service.purchase(request(null, true)).outcome()); noWrites();
    }

    @Test void existingNumberAndStaleConfirmationAreRejected() {
        assertEquals(BAND_CONFLICT, service.purchase(new PurchaseService.Request(7, null, 42L, 3, 3)).outcome());
        assertEquals(BAND_CONFLICT, service.purchase(new PurchaseService.Request(7, null, 99L, 4, 4)).outcome()); noWrites();
    }

    @Test void locksAreAcquiredShoppingBeforeSeriesAndBeforeAnyMutation() {
        assertEquals(UPDATED, service.purchase(request(null, true)).outcome());
        var order = inOrder(cart, f.rows, f.bands);
        order.verify(cart).findByIdForPurchase(7L);
        order.verify(f.rows).findByMangaIndex(123);
        order.verify(f.rows).findByIdForPurchase(42L);
        order.verify(f.bands).findByMangaReiheId(42L);
        order.verify(f.rows).save(any());
    }

    @Test void combinedCountAboveLimitCannotMutateOrMarkPurchase() {
        f.stored.clear();
        for (int number = 1; number <= 10000; number++) f.stored.add(T03Fixtures.band(f.row, number));
        f.row.setAnzahlBaende(10000);
        assertThrows(IllegalArgumentException.class, () -> service.purchase(request(null, false)));
        noWrites(); assertEquals(10000, f.stored.size()); assertFalse(item.getGekauft());
    }

    @Test void differentShoppingPositionsMustReconfirmAfterAnotherPurchaseChangedTheRange() {
        var second = T03Fixtures.item(false); second.setId(8L);
        when(cart.findByIdForPurchase(8L)).thenReturn(Optional.of(second));
        assertEquals(CONFIRM_VOLUMES, service.purchase(request(null, false)).outcome());
        assertEquals(CONFIRM_VOLUMES, service.purchase(new PurchaseService.Request(8, null, null, null, null)).outcome());
        assertEquals(UPDATED, service.purchase(request(null, true)).outcome());
        assertEquals(BAND_CONFLICT, service.purchase(new PurchaseService.Request(8, null, 42L, 4, 4)).outcome());
        assertFalse(second.getGekauft()); assertEquals(4, f.stored.size());
        assertEquals(UPDATED, service.purchase(new PurchaseService.Request(8, null, 42L, 5, 5)).outcome());
        assertEquals(5, f.stored.size()); assertTrue(second.getGekauft());
    }

    @ParameterizedTest @ValueSource(strings = {"0.01", "-0.01", "0.02", "-0.02", "0.00"})
    void exactPurchaseRemainderAffectsOnlyNewBandsEvenWithReversedResults(String adjustment) {
        Collections.reverse(f.stored); var before = f.state();
        item.setAnzahlBaende(3); item.setAenderungGesamtpreis(new BigDecimal(adjustment));
        assertEquals(UPDATED, service.purchase(new PurchaseService.Request(7, null, 42L, 4, 6)).outcome());
        before.forEach((id, values) -> assertEquals(values, f.state().get(id)));
        var added = f.stored.stream().filter(b -> b.getBandNr() >= 4).sorted(Comparator.comparing(Band::getBandNr)).toList();
        var sum = added.stream().map(Band::getAenderungPreis).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal(adjustment).compareTo(sum));
        assertEquals(0, f.row.getGesamtpreis().compareTo(new BigDecimal("36.00").add(new BigDecimal(adjustment))));
        if (adjustment.equals("0.02")) assertEquals(List.of(new BigDecimal("0.01"), new BigDecimal("0.01"), new BigDecimal("0.00")),
                added.stream().map(Band::getAenderungPreis).toList());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void newPurchaseRequiresStatusAndCanCreateWithOrWithoutIndex(boolean hasIndex) {
        when(f.rows.findByMangaIndex(123)).thenReturn(List.of());
        if (!hasIndex) item.setMangaIndex(null);
        assertEquals(STATUS_REQUIRED, service.purchase(request(null, false)).outcome()); noWrites();
        prepareNewDetails();
        item.setPreis(BigDecimal.ZERO);
        assertEquals(CREATED, service.purchase(request(1L, false)).outcome());
        assertTrue(item.getGekauft()); assertEquals(1, f.stored.size()); assertEquals(BigDecimal.ZERO, f.stored.getFirst().getPreis());
        assertFalse(f.stored.getFirst().isIstGelesen());
    }

    private void prepareNewDetails() {
        var d = new EinkaufslisteItemDetails(); d.setId(7L); d.setEinkaufslisteItem(item);
        when(details.findById(7L)).thenReturn(Optional.of(d));
        var omnibus = mock(SammelbandService.class);
        when(omnibus.findById(1L)).thenReturn(Optional.of(f.row.getMangaDetails().getSammelbaende()));
        ReflectionTestUtils.setField(f.detailService, "sammelbaendeService", omnibus);
        f.stored.clear();
        when(f.statuses.findById(1L)).thenReturn(Optional.of(f.row.getStatus()));
        when(f.publishers.findById(1L)).thenReturn(Optional.of(f.row.getVerlag()));
        when(f.types.findById(1L)).thenReturn(Optional.of(f.row.getTyp()));
        when(f.formats.findById(1L)).thenReturn(Optional.of(f.row.getFormat()));
        when(f.rows.save(any())).thenAnswer(i -> { MangaReihe row = i.getArgument(0); if (row.getId() == null) row.setId(42L); return row; });
        // Nur die synthetische Identitätsmap: MapsId/Inverse so bereitstellen, wie
        // der nächste eigenständige Lesevorgang nach einem echten Commit sie lädt.
        doAnswer(i -> {
            MangaDetails saved = i.getArgument(0); saved.setId(saved.getMangaReihe().getId());
            saved.getMangaReihe().setMangaDetails(saved); return saved;
        }).when(f.details).save(any());
    }

    @ParameterizedTest @ValueSource(strings = {"0.01", "-0.01", "0.02"})
    void newSeriesPurchaseAlsoPreservesEveryRemainderCent(String amount) {
        when(f.rows.findByMangaIndex(123)).thenReturn(List.of()); prepareNewDetails();
        item.setAnzahlBaende(3); item.setAenderungGesamtpreis(new BigDecimal(amount));
        assertEquals(CREATED, service.purchase(request(1L, false)).outcome());
        assertEquals(3, f.stored.size());
        assertEquals(0, new BigDecimal(amount).compareTo(f.stored.stream().map(Band::getAenderungPreis)
                .reduce(BigDecimal.ZERO, BigDecimal::add)));
        assertTrue(f.stored.stream().noneMatch(Band::isIstGelesen));
    }

    @Test void missingUpdateResultThrowsAndDoesNotMarkBought() {
        var library = mock(MangaReiheService.class);
        service = new PurchaseService(new LibraryWriteCoordinator(tx), cart, details, f.rows, f.bands, library);
        assertThrows(IllegalStateException.class, () -> service.purchase(request(null, true)));
        verify(cart, never()).save(any()); verify(cart, never()).flush();
        assertFalse(item.getGekauft()); assertEquals(1, tx.rollbacks.get());
    }

    @Test void missingNewSeriesResultThrowsAndDoesNotMarkBought() {
        when(f.rows.findByMangaIndex(123)).thenReturn(List.of()); prepareNewDetails();
        service = new PurchaseService(new LibraryWriteCoordinator(tx), cart, details, f.rows, f.bands, mock(MangaReiheService.class));
        assertThrows(IllegalStateException.class, () -> service.purchase(request(1L, false)));
        verify(cart, never()).save(any()); verify(cart, never()).flush();
        assertFalse(item.getGekauft()); assertEquals(1, tx.rollbacks.get());
    }

    @Test void missingNewPurchaseDetailsReturn404OutcomeWithoutWrite() {
        when(f.rows.findByMangaIndex(123)).thenReturn(List.of());
        assertEquals(DETAILS_NOT_FOUND, service.purchase(request(1L, false)).outcome()); noWrites();
    }

    @ParameterizedTest @ValueSource(strings = {"price", "adjustment", "fraction"})
    void unknownOrFractionalNewPriceIsRejectedBeforeWrites(String field) {
        if (field.equals("price")) item.setPreis(null);
        else if (field.equals("adjustment")) item.setAenderungGesamtpreis(null);
        else item.setPreis(new BigDecimal("0.001"));
        assertThrows(IllegalArgumentException.class, () -> service.purchase(request(null, true))); noWrites();
        assertEquals(1, tx.rollbacks.get());
    }

    @ParameterizedTest @ValueSource(strings = {"library", "marker", "flush", "commit"})
    void failuresNeverReturnSuccessAndReachRollbackOrUnconfirmedCompletion(String phase) {
        if (phase.equals("library")) doThrow(new IllegalStateException("synthetic library")).when(f.rows).save(any());
        if (phase.equals("marker")) doThrow(new IllegalStateException("synthetic marker")).when(cart).save(any());
        if (phase.equals("flush")) doThrow(new IllegalStateException("synthetic flush")).when(cart).flush();
        if (phase.equals("commit")) tx.fault = "commit";
        assertThrows(RuntimeException.class, () -> service.purchase(request(null, true)));
        assertEquals(0, tx.commits.get());
        if (!phase.equals("commit")) assertEquals(1, tx.rollbacks.get());
        if (phase.equals("library")) verify(cart, never()).save(any());
    }

    @Test void twoDifferentEndpointModesForSameItemCannotAppendTwice() throws Exception {
        var inCommit = new CountDownLatch(1); var release = new CountDownLatch(1);
        var secondAtLock = new CountDownLatch(1);
        var mutex = new java.util.concurrent.locks.ReentrantLock(true) {
            @Override public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
                if (Thread.currentThread().getName().equals("purchase-2")) secondAtLock.countDown();
                return super.tryLock(timeout, unit);
            }
        };
        service = new PurchaseService(new LibraryWriteCoordinator(tx, mutex, java.time.Duration.ofSeconds(5)),
                cart, details, f.rows, f.bands, f.series);
        tx.beforeCommit = () -> { inCommit.countDown(); LibraryWriteCoordinatorTest.await(release); };
        var number = new java.util.concurrent.atomic.AtomicInteger();
        var pool = Executors.newFixedThreadPool(2, work -> new Thread(work, "purchase-" + number.incrementAndGet()));
        try {
            var first = pool.submit(() -> service.purchase(request(null, true)));
            assertTrue(inCommit.await(5, TimeUnit.SECONDS));
            var second = pool.submit(() -> service.purchase(request(1L, true)));
            assertTrue(secondAtLock.await(5, TimeUnit.SECONDS)); assertFalse(second.isDone());
            assertEquals(1, tx.begins.get()); verify(cart, times(1)).save(item);
            release.countDown();
            assertEquals(UPDATED, first.get(5, TimeUnit.SECONDS).outcome());
            assertEquals(ALREADY_PURCHASED, second.get(5, TimeUnit.SECONDS).outcome());
            assertEquals(4, f.stored.size()); verify(cart, times(1)).save(any());
        } finally { release.countDown(); pool.shutdownNow(); assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS)); }
    }

    @Test void twoDifferentPurchasesForSameNewSeriesCreateOnlyOneAndRequireFreshAppendConfirmation() throws Exception {
        prepareNewDetails();
        var created = new java.util.concurrent.atomic.AtomicReference<MangaReihe>();
        var secondItem = T03Fixtures.item(false); secondItem.setId(8L);
        when(cart.findByIdForPurchase(8L)).thenReturn(Optional.of(secondItem));
        when(f.rows.findByMangaIndex(123)).thenAnswer(i -> created.get() == null ? List.of() : List.of(created.get()));
        when(f.rows.findByIdForPurchase(42L)).thenAnswer(i -> Optional.ofNullable(created.get()));
        doAnswer(i -> {
            MangaReihe saved = i.getArgument(0);
            if (saved.getId() == null) { saved.setId(42L); assertTrue(created.compareAndSet(null, saved)); }
            return saved;
        }).when(f.rows).save(any());
        var inCommit = new CountDownLatch(1); var release = new CountDownLatch(1); var waiting = new CountDownLatch(1);
        var mutex = new java.util.concurrent.locks.ReentrantLock(true) {
            @Override public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
                if (Thread.currentThread().getName().equals("new-purchase-2")) waiting.countDown();
                return super.tryLock(timeout, unit);
            }
        };
        service = new PurchaseService(new LibraryWriteCoordinator(tx, mutex, java.time.Duration.ofSeconds(5)),
                cart, details, f.rows, f.bands, f.series);
        tx.beforeCommit = () -> { inCommit.countDown(); LibraryWriteCoordinatorTest.await(release); };
        var number = new java.util.concurrent.atomic.AtomicInteger();
        var pool = Executors.newFixedThreadPool(2, work -> new Thread(work, "new-purchase-" + number.incrementAndGet()));
        try {
            var first = pool.submit(() -> service.purchase(request(1L, false)));
            assertTrue(inCommit.await(5, TimeUnit.SECONDS));
            var second = pool.submit(() -> service.purchase(new PurchaseService.Request(8, 1L, null, null, null)));
            assertTrue(waiting.await(5, TimeUnit.SECONDS)); assertFalse(second.isDone()); assertEquals(1, tx.begins.get());
            release.countDown(); assertEquals(CREATED, first.get(5, TimeUnit.SECONDS).outcome());
            var preview = second.get(5, TimeUnit.SECONDS);
            assertEquals(CONFIRM_VOLUMES, preview.outcome()); assertEquals(42L, preview.seriesId());
            assertEquals(2, preview.firstBand()); assertEquals(2, preview.lastBand());
            assertTrue(item.getGekauft()); assertFalse(secondItem.getGekauft()); assertEquals(1, f.stored.size());
            verify(cart, times(1)).save(item); verify(cart, never()).save(secondItem);
        } finally { release.countDown(); pool.shutdownNow(); assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS)); }
    }
}
