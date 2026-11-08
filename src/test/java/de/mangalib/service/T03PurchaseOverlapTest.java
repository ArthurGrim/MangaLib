package de.mangalib.service;

import de.mangalib.T03PurchaseOverlap;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Echte Coordinator-/Spring-TX-Steuerung, ausschließlich vorhandener RAM-Manager. */
class T03PurchaseOverlapTest {
    @Test void separatelyConfirmsExpectedSecondRollbackAndCleanup() throws Exception {
        var manager = spy(new FakeLibraryTransactionManager());
        var coordinator = new LibraryWriteCoordinator(manager);
        var observer = new T03PurchaseOverlap(manager, coordinator);
        assertEquals(List.of("UPDATED", "REJECTED"), observer.runWithRejectedSecondWrite(
                () -> coordinator.execute(() -> "UPDATED"),
                () -> { assertThrows(IllegalArgumentException.class, () -> coordinator.execute(() -> {
                    throw new IllegalArgumentException("synthetic duplicate");
                })); return "REJECTED"; }));
        assertTrue(observer.completionConfirmed());
        assertEquals(1, manager.commits.get()); assertEquals(1, manager.rollbacks.get());
        assertEquals("NEXT", coordinator.execute(() -> "NEXT"));
    }
    @Test void observesDifferentOperationsUnderTheSameCoordinator() throws Exception {
        var manager = spy(new FakeLibraryTransactionManager());
        var coordinator = new LibraryWriteCoordinator(manager);
        var observer = new T03PurchaseOverlap(manager, coordinator);
        var values = new ArrayList<String>();
        assertEquals(List.of("PURCHASE", "BAND"), observer.run(
                () -> coordinator.execute(() -> { values.add("purchase"); return "PURCHASE"; }),
                () -> coordinator.execute(() -> { values.add("band"); return "BAND"; })));
        assertEquals(List.of("purchase", "band"), values);
        assertTrue(observer.completionConfirmed());
    }
    @Test void provesQueuedSecondRequestAndTwoNonoverlappingTransactionsIncludingCleanup() throws Exception {
        var manager = spy(new FakeLibraryTransactionManager());
        var coordinator = new LibraryWriteCoordinator(manager);
        var observer = new T03PurchaseOverlap(manager, coordinator);
        var bought = new AtomicBoolean();
        assertEquals(List.of("UPDATED", "ALREADY"), observer.run(() -> coordinator.execute(() -> {
            // Auch die verschachtelte REQUIRED-Teilnahme darf nicht als neue TX zählen.
            var nested = new org.springframework.transaction.support.TransactionTemplate(manager);
            return nested.execute(status -> bought.getAndSet(true) ? "ALREADY" : "UPDATED");
        })));
        assertTrue(observer.completionConfirmed()); assertEquals(2, manager.commits.get());
    }

    @ParameterizedTest @ValueSource(strings = {"commit", "cleanup", "rollback"})
    void unconfirmedManagerCompletionFailsClosedWithoutRetry(String fault) {
        var manager = spy(new FakeLibraryTransactionManager()); manager.fault = fault;
        var coordinator = new LibraryWriteCoordinator(manager);
        var observer = new T03PurchaseOverlap(manager, coordinator);
        assertThrows(Exception.class, () -> observer.run(() -> coordinator.execute(() -> {
            if (fault.equals("rollback")) throw new IllegalStateException("synthetic work failure");
            return "synthetic";
        })));
        assertTrue(observer.threadsEnded());
        assertFalse(observer.completionConfirmed());
        assertThrows(IllegalStateException.class, () -> coordinator.execute(() -> fail("No retry")));
    }

    @Test void refusesObservationOfAnotherManagerBeforeAnyWork() {
        var first = spy(new FakeLibraryTransactionManager());
        var second = spy(new FakeLibraryTransactionManager());
        assertThrows(AssertionError.class, () -> new T03PurchaseOverlap(first, new LibraryWriteCoordinator(second)));
        assertEquals(0, first.begins.get()); assertEquals(0, second.begins.get());
    }

    @Test void detectsSecondRequestBypassingCoordinatorInsteadOfReportingSequentialSuccess() {
        var manager = spy(new FakeLibraryTransactionManager());
        var coordinator = new LibraryWriteCoordinator(manager);
        var observer = new T03PurchaseOverlap(manager, coordinator);
        var first = new AtomicBoolean(true);
        assertThrows(AssertionError.class, () -> observer.run(() -> first.getAndSet(false)
                ? coordinator.execute(() -> "UPDATED") : "ALREADY"));
        assertTrue(observer.threadsEnded());
    }

    @Test void earlyWorkFailureIsObservedAndBothThreadsAreClosed() {
        var manager = spy(new FakeLibraryTransactionManager());
        var coordinator = new LibraryWriteCoordinator(manager);
        var observer = new T03PurchaseOverlap(manager, coordinator);
        assertThrows(Exception.class, () -> observer.run(() -> coordinator.execute(() -> {
            throw new IllegalStateException("synthetic");
        })));
        assertTrue(observer.threadsEnded()); assertEquals(1, manager.rollbacks.get());
    }

    @Test void missingMutexArrivalExpiresWithoutReportingAConcurrencyPass() {
        var manager = spy(new FakeLibraryTransactionManager());
        var coordinator = new LibraryWriteCoordinator(manager);
        var observer = new T03PurchaseOverlap(manager, coordinator);
        var first = new AtomicBoolean(true);
        assertThrows(AssertionError.class, () -> observer.run(() -> {
            if (first.getAndSet(false)) return coordinator.execute(() -> "UPDATED");
            // Absichtlich kein Coordinator-Eintritt; die 2-s-Beobachtungsfrist muss STOP liefern.
            new java.util.concurrent.CountDownLatch(1).await(3, java.util.concurrent.TimeUnit.SECONDS);
            return "ALREADY";
        }));
        assertTrue(observer.threadsEnded());
    }
}
