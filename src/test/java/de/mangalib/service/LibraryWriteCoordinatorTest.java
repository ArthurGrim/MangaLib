package de.mangalib.service;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

class LibraryWriteCoordinatorTest {
    @Test void mutexEnclosesBeginCommitAndCleanupAndNestedWorkUsesOneTransaction() {
        var tx = new FakeLibraryTransactionManager(); var mutex = new ReentrantLock();
        var coordinator = new LibraryWriteCoordinator(tx, mutex, Duration.ofSeconds(1));
        tx.beforeCommit = () -> assertTrue(mutex.isHeldByCurrentThread());
        assertEquals("ok", coordinator.execute(() -> {
            assertTrue(mutex.isHeldByCurrentThread());
            return coordinator.execute(() -> "ok");
        }));
        assertEquals(List.of("begin", "commit-start", "commit-end", "cleanup"), tx.events);
        assertEquals(1, tx.begins.get()); assertFalse(mutex.isLocked());
    }

    @ParameterizedTest @ValueSource(strings = {"runtime", "checked", "error"})
    void anyWorkFailureRollsBackAndOnlyThenAllowsAnotherWrite(String kind) {
        var tx = new FakeLibraryTransactionManager(); var mutex = new ReentrantLock();
        var coordinator = new LibraryWriteCoordinator(tx, mutex, Duration.ofSeconds(1));
        tx.beforeRollback = () -> assertTrue(mutex.isHeldByCurrentThread());
        Class<? extends Throwable> expectedType = kind.equals("error") ? AssertionError.class : IllegalStateException.class;
        assertThrows(expectedType, () -> coordinator.execute(() -> {
            if (kind.equals("checked")) throw new IOException("synthetic checked");
            if (kind.equals("error")) throw new AssertionError("synthetic error");
            throw new IllegalStateException("synthetic runtime");
        }));
        assertEquals(1, tx.rollbacks.get()); assertEquals(0, tx.commits.get()); assertFalse(mutex.isLocked());
        assertEquals("next", coordinator.execute(() -> "next")); assertEquals(1, tx.commits.get());
    }

    @ParameterizedTest @ValueSource(strings = {"begin", "commit", "rollback", "cleanup"})
    void unconfirmedTransactionCompletionBlocksLaterWorkWithoutRetry(String fault) {
        var tx = new FakeLibraryTransactionManager(); tx.fault = fault;
        var coordinator = new LibraryWriteCoordinator(tx);
        assertThrows(RuntimeException.class, () -> coordinator.execute(() -> {
            if (fault.equals("rollback")) throw new IllegalStateException("synthetic failure");
            return "not-confirmed";
        }));
        int began = tx.begins.get(); tx.fault = "";
        assertThrows(IllegalStateException.class, () -> coordinator.execute(() -> fail("No further write permitted")));
        assertEquals(began, tx.begins.get());
    }

    @Test void ambientTransactionIsRejectedRatherThanReleasingMutexBeforeItsEnd() {
        var tx = new FakeLibraryTransactionManager(); var coordinator = new LibraryWriteCoordinator(tx);
        new TransactionTemplate(tx).execute(status -> {
            assertThrows(IllegalStateException.class, () -> coordinator.execute(() -> fail("No work allowed")));
            assertEquals(0, tx.commits.get()); return null;
        });
        assertEquals(1, tx.commits.get());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void anotherWriterCannotBeginWhileCommitOrRollbackIsBlocked(boolean rollback) throws Exception {
        var tx = new FakeLibraryTransactionManager();
        var enteredCompletion = new CountDownLatch(1); var releaseCompletion = new CountDownLatch(1);
        var secondAtLock = new CountDownLatch(1);
        var mutex = new ReentrantLock(true) {
            @Override public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
                if (Thread.currentThread().getName().equals("second-writer")) secondAtLock.countDown();
                return super.tryLock(timeout, unit);
            }
        };
        Runnable block = () -> { enteredCompletion.countDown(); await(releaseCompletion); };
        if (rollback) tx.beforeRollback = block; else tx.beforeCommit = block;
        var coordinator = new LibraryWriteCoordinator(tx, mutex, Duration.ofSeconds(5));
        var first = new FutureTask<>(() -> {
            try { return coordinator.execute(() -> { if (rollback) throw new IllegalStateException("synthetic"); return 1; }); }
            catch (IllegalStateException expected) { if (!rollback) throw expected; return 0; }
        });
        var second = new FutureTask<>(() -> coordinator.execute(() -> 2));
        var a = new Thread(first, "first-writer"); var b = new Thread(second, "second-writer");
        try {
            a.start(); assertTrue(enteredCompletion.await(5, TimeUnit.SECONDS)); b.start();
            assertTrue(secondAtLock.await(5, TimeUnit.SECONDS));
            assertEquals(1, tx.begins.get()); assertFalse(second.isDone());
            releaseCompletion.countDown(); assertEquals(rollback ? 0 : 1, first.get(5, TimeUnit.SECONDS));
            assertEquals(2, second.get(5, TimeUnit.SECONDS)); assertEquals(2, tx.begins.get());
        } finally { releaseCompletion.countDown(); a.join(5000); b.join(5000); assertFalse(a.isAlive()); assertFalse(b.isAlive()); }
    }

    @Test void lockWaitIsBoundedAndDoesNotStartATransaction() throws Exception {
        var tx = new FakeLibraryTransactionManager(); var mutex = new ReentrantLock();
        var coordinator = new LibraryWriteCoordinator(tx, mutex, Duration.ofMillis(20));
        mutex.lock();
        var task = new FutureTask<>(() -> assertThrows(LibraryWriteCoordinator.WriteBusyException.class,
                () -> coordinator.execute(() -> fail("No write allowed"))));
        var thread = new Thread(task);
        try { thread.start(); task.get(2, TimeUnit.SECONDS); assertEquals(0, tx.begins.get()); }
        finally { mutex.unlock(); thread.join(2000); assertFalse(thread.isAlive()); }
    }

    static void await(CountDownLatch latch) {
        try { if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("Test latch timed out"); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
    }
}
