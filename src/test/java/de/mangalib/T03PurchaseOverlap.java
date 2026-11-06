package de.mangalib;

import de.mangalib.service.LibraryWriteCoordinator;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Nur Testbeobachtung des echten Managers/Mutex. Keine zusätzliche Transaktion,
 * kein produktiver Hook. Der erste Commit wartet höchstens 4 s, unterhalb der
 * produktiven 5-s-Mutexfrist; Queue-Mitgliedschaft ist der Überlappungsnachweis. */
public final class T03PurchaseOverlap {
    private final PlatformTransactionManager manager;
    private final ReentrantLock mutex;
    private final CountDownLatch atCommit = new CountDownLatch(1), release = new CountDownLatch(1);
    private final AtomicReference<Thread> firstThread = new AtomicReference<>(), secondThread = new AtomicReference<>();
    private final Map<TransactionStatus, String> transactions = Collections.synchronizedMap(new IdentityHashMap<>());
    private final List<String> events = new CopyOnWriteArrayList<>();
    private final AtomicInteger active = new AtomicInteger(), maximum = new AtomicInteger();
    private volatile boolean threadsEnded = true;
    private volatile boolean completionUncertain;

    public T03PurchaseOverlap(PlatformTransactionManager manager, LibraryWriteCoordinator coordinator) {
        this.manager = manager;
        assertTrue(mockingDetails(manager).isSpy(), "Testmanager muss echter Spy sein");
        var template = (TransactionTemplate) ReflectionTestUtils.getField(coordinator, "transaction");
        assertSame(manager, template.getTransactionManager(), "Coordinator verwendet anderen Manager: STOP");
        mutex = (ReentrantLock) ReflectionTestUtils.getField(coordinator, "mutex");
        assertNotNull(mutex);
        doAnswer(call -> {
            String who = who();
            TransactionStatus status;
            try { status = (TransactionStatus) call.callRealMethod(); }
            catch (Throwable failure) { if (who != null) completionUncertain = true; throw failure; }
            if (who != null && status.isNewTransaction()) {
                try {
                    assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                    assertTrue(mutex.isHeldByCurrentThread(), "Transaktionsbeginn ohne Mutex");
                } catch (Throwable failure) {
                    completionUncertain = true;
                    try { manager.rollback(status); }
                    catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
                    throw failure;
                }
                transactions.put(status, who);
                maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
                events.add(who + "_BEGIN");
            }
            return status;
        }).when(manager).getTransaction(any());
        doAnswer(call -> finish(call, true)).when(manager).commit(any());
        doAnswer(call -> finish(call, false)).when(manager).rollback(any());
    }

    private Object finish(org.mockito.invocation.InvocationOnMock call, boolean commit) throws Throwable {
        var status = (TransactionStatus) call.getArgument(0);
        String who = transactions.get(status);
        if (who == null) return call.callRealMethod(); // REQUIRED-Teilnahme und fremde Read-TX unverändert.
        if (commit && who.equals("FIRST")) {
            try {
                assertTrue(mutex.isHeldByCurrentThread());
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                atCommit.countDown();
                assertTrue(release.await(4, TimeUnit.SECONDS), "Commitbeobachtung abgelaufen: STOP");
            } catch (Throwable failure) {
                // Der Beobachter darf bei eigenem Fristfehler keine offene TX hinterlassen.
                // Rollback benutzt denselben Manager; jede unklare Bereinigung bleibt STOP.
                try { manager.rollback(status); }
                catch (Throwable cleanup) { completionUncertain = true; failure.addSuppressed(cleanup); }
                if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
                throw failure;
            }
        }
        Object result;
        try { result = call.callRealMethod(); }
        catch (Throwable failure) { completionUncertain = true; throw failure; }
        assertTrue(status.isCompleted());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertFalse(TransactionSynchronizationManager.isSynchronizationActive());
        assertTrue(mutex.isHeldByCurrentThread(), "Mutex vor Managercleanup freigegeben");
        events.add(who + (commit ? "_COMMIT_CLEANED" : "_ROLLBACK_CLEANED"));
        active.decrementAndGet();
        return result;
    }

    private String who() {
        return Thread.currentThread() == firstThread.get() ? "FIRST"
                : Thread.currentThread() == secondThread.get() ? "SECOND" : null;
    }

    public <T> List<T> run(Callable<T> purchase) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        threadsEnded = false;
        try {
            var first = executor.submit(() -> { firstThread.set(Thread.currentThread()); return purchase.call(); });
            long firstDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            while (!atCommit.await(20, TimeUnit.MILLISECONDS)) {
                if (first.isDone()) { first.get(); fail("Erster Kauf ohne beobachteten Commit: STOP"); }
                assertTrue(System.nanoTime() < firstDeadline, "Erster aktiver Kaufcommit nicht erreicht: STOP");
            }
            var second = executor.submit(() -> { secondThread.set(Thread.currentThread()); return purchase.call(); });
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while ((secondThread.get() == null || !mutex.hasQueuedThread(secondThread.get())) && System.nanoTime() < deadline) {
                if (second.isDone()) break;
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1)); // Polling, niemals Erfolgsbeweis.
            }
            assertNotNull(secondThread.get());
            assertNotSame(firstThread.get(), secondThread.get());
            assertTrue(mutex.hasQueuedThread(secondThread.get()), "Zweiter Kauf wartet nicht am echten Coordinator: STOP");
            assertTrue(mutex.isLocked()); assertFalse(first.isDone()); assertFalse(second.isDone());
            assertEquals(1, active.get()); assertEquals(List.of("FIRST_BEGIN"), events);
            release.countDown();
            var results = List.of(first.get(45, TimeUnit.SECONDS), second.get(45, TimeUnit.SECONDS));
            assertEquals(List.of("FIRST_BEGIN", "FIRST_COMMIT_CLEANED", "SECOND_BEGIN", "SECOND_COMMIT_CLEANED"), events);
            assertEquals(1, maximum.get()); assertEquals(0, active.get());
            return results;
        } finally {
            release.countDown();
            executor.shutdown();
            threadsEnded = executor.awaitTermination(50, TimeUnit.SECONDS);
            assertTrue(threadsEnded, "Threadabschluss unbestätigt: STOP; keine weiteren DB-Prüfungen");
        }
    }
    public boolean threadsEnded() { return threadsEnded; }
    public boolean completionConfirmed() { return threadsEnded && active.get() == 0 && !completionUncertain; }
}
