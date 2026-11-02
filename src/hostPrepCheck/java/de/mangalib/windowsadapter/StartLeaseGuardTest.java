package de.mangalib.windowsadapter;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static de.mangalib.windowsadapter.KernelCalls.*;
import static org.junit.jupiter.api.Assertions.*;

/** Externer Custodian nur als Fakevertrag, kein gestarteter Watchdogprozess. */
class StartLeaseGuardTest {
    @TempDir Path temporary;
    static final class Custody implements StartLeaseGuard.Custodian {
        final Object receipt = new Object(); int revokes, late; boolean alive = true;
        Resource job, process; CountDownLatch entered, release, retaining, retained;
        String failure = "";
        public Object retain(StartIntentStore.Binding b, StartIntentStore.Anchor a, Resource k) {
            assertNotNull(a); if (failure.equals("retain")) throw new IllegalStateException("synthetic"); job = k;
            if (retaining != null) {
                retaining.countDown();
                try { assertTrue(retained.await(3, TimeUnit.SECONDS)); } catch (InterruptedException error) { throw new AssertionError(error); }
            }
            return receipt;
        }
        public boolean live(Object token) { assertSame(receipt, token); if (failure.equals("live")) throw new IllegalStateException("synthetic"); return alive; }
        public void recordReturn(Object token, Resource held) { assertSame(receipt, token); if (failure.equals("record")) throw new IllegalStateException("synthetic"); process = held; }
        public void revoke(Object token) {
            assertSame(receipt, token); revokes++;
            if (failure.equals("revoke")) throw new IllegalStateException("synthetic");
            if (entered != null) {
                entered.countDown();
                try { assertTrue(release.await(3, TimeUnit.SECONDS)); } catch (InterruptedException error) { throw new AssertionError(error); }
            }
            alive = false;
        }
        public void retainLate(Object token, Resource held) { assertSame(receipt, token); process = held; late++; }
    }
    StartIntentStore store() throws Exception {
        return StartIntentStore.create(temporary.resolve("run"), new StartIntentStore.Binding(new UUID(1, 1), new UUID(2, 2), new UUID(3, 3)), point -> { });
    }
    @Test void intentPrecedesStartAndTheSameHeldJobAndProcessBindSingleResume() throws Exception {
        try (var store = store()) {
            var proof = store.prepare(); var custody = new Custody(); var q = new AtomicInteger();
            var guard = new StartLeaseGuard(store, proof, custody, q::incrementAndGet);
            Scope scope = new Scope(); Resource job = new Resource(scope, Kind.JOB, 1), root = new Resource(scope, Kind.PROCESS, 2);
            assertTrue(guard.beforeCreate(job)); assertFalse(guard.beforeCreate(job));
            assertTrue(guard.created(job, root)); assertTrue(guard.beforeResume(job, root));
            assertTrue(guard.afterResume(job, root)); assertFalse(guard.beforeResume(job, root)); assertEquals(0, q.get());
        }
    }
    @Test void lateCreateAfterAbortIsDeliveredToCustodianAndNeverAuthorizedForResume() throws Exception {
        try (var store = store()) {
            var proof = store.prepare(); var custody = new Custody(); var q = new AtomicInteger();
            var guard = new StartLeaseGuard(store, proof, custody, q::incrementAndGet);
            Scope scope = new Scope(); Resource job = new Resource(scope, Kind.JOB, 1), root = new Resource(scope, Kind.PROCESS, 2);
            assertTrue(guard.beforeCreate(job)); guard.ownerLost(); guard.ownerLost();
            assertFalse(guard.created(job, root)); assertFalse(guard.beforeResume(job, root));
            assertSame(root, custody.process); assertEquals(1, custody.late); assertEquals(1, custody.revokes);
            assertTrue(q.get() > 0); assertFalse(store.valid(proof));
        }
    }
    @Test void blockedRevokerDoesNotHoldThePermissionMonitor() throws Exception {
        try (var store = store()) {
            var custody = new Custody(); var guard = new StartLeaseGuard(store, store.prepare(), custody, () -> { });
            Scope scope = new Scope(); Resource job = new Resource(scope, Kind.JOB, 1), root = new Resource(scope, Kind.PROCESS, 2);
            assertTrue(guard.beforeCreate(job)); assertTrue(guard.created(job, root));
            custody.entered = new CountDownLatch(1); custody.release = new CountDownLatch(1); var error = new AtomicReference<Throwable>();
            Thread stop = new Thread(() -> { try { guard.ownerLost(); } catch (Throwable failure) { error.set(failure); } });
            stop.setDaemon(true); stop.start();
            try { assertTrue(custody.entered.await(2, TimeUnit.SECONDS)); assertFalse(guard.beforeResume(job, root)); }
            finally { custody.release.countDown(); stop.join(2500); if (stop.isAlive()) { stop.interrupt(); stop.join(500); } }
            assertFalse(stop.isAlive()); assertNull(error.get());
        }
    }
    @Test void lostCustodianOrForeignJobCannotHealQuarantine() throws Exception {
        try (var store = store()) {
            var custody = new Custody(); var q = new AtomicInteger(); var guard = new StartLeaseGuard(store, store.prepare(), custody, q::incrementAndGet);
            Scope scope = new Scope(); Resource job = new Resource(scope, Kind.JOB, 1), root = new Resource(scope, Kind.PROCESS, 2);
            assertTrue(guard.beforeCreate(job)); assertFalse(guard.created(new Resource(scope, Kind.JOB, 9), root));
            custody.alive = false; assertFalse(guard.created(job, root)); assertFalse(guard.beforeResume(job, root)); assertTrue(q.get() > 0);
        }
    }
    @ParameterizedTest @ValueSource(strings = {"retain", "live", "record", "revoke", "quarantine"})
    void failedCustodianOrQuarantineCallbackCannotReauthorizeStart(String failure) throws Exception {
        try (var store = store()) {
            var custody = new Custody(); var proof = store.prepare();
            var guard = new StartLeaseGuard(store, proof, custody, () -> { if (failure.equals("quarantine")) throw new IllegalStateException("synthetic"); });
            Scope scope = new Scope(); Resource job = new Resource(scope, Kind.JOB, 1), root = new Resource(scope, Kind.PROCESS, 2);
            if (failure.equals("retain") || failure.equals("live")) {
                custody.failure = failure; assertFalse(guard.beforeCreate(job));
            } else {
                assertTrue(guard.beforeCreate(job)); custody.failure = failure;
                if (failure.equals("record")) assertFalse(guard.created(job, root)); else guard.ownerLost();
            }
            guard.ownerLost(); assertFalse(guard.beforeResume(job, root)); assertFalse(guard.beforeCreate(job));
            assertFalse(store.valid(proof)); assertTrue(custody.revokes <= 1);
        }
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void foreignScopeOrWrongKindIsNeverPassedToLateCleanup(boolean wrongKind) throws Exception {
        try (var store = store()) {
            var custody = new Custody(); var guard = new StartLeaseGuard(store, store.prepare(), custody, () -> { });
            Scope scope = new Scope(); Resource job = new Resource(scope, Kind.JOB, 1);
            Resource foreign = new Resource(wrongKind ? scope : new Scope(), wrongKind ? Kind.THREAD : Kind.PROCESS, 2);
            assertTrue(guard.beforeCreate(job)); assertFalse(guard.created(job, foreign));
            assertNull(custody.process); assertEquals(0, custody.late); assertEquals(1, custody.revokes);
        }
    }
    @Test void cleanupCanOvertakeBlockedRetentionAndRevokesItsLateReceiptExactlyOnce() throws Exception {
        try (var store = store()) {
            var custody = new Custody(); var guard = new StartLeaseGuard(store, store.prepare(), custody, () -> { });
            Resource job = new Resource(new Scope(), Kind.JOB, 1);
            custody.retaining = new CountDownLatch(1); custody.retained = new CountDownLatch(1);
            var result = new AtomicReference<Boolean>(); var error = new AtomicReference<Throwable>();
            Thread start = new Thread(() -> { try { result.set(guard.beforeCreate(job)); } catch (Throwable failure) { error.set(failure); } });
            start.setDaemon(true); start.start();
            try { assertTrue(custody.retaining.await(2, TimeUnit.SECONDS)); guard.ownerLost(); }
            finally { custody.retained.countDown(); start.join(2500); if (start.isAlive()) { start.interrupt(); start.join(500); } }
            assertFalse(start.isAlive()); assertNull(error.get()); assertEquals(Boolean.FALSE, result.get());
            guard.ownerLost(); assertEquals(1, custody.revokes); assertFalse(guard.beforeCreate(job));
        }
    }
}
