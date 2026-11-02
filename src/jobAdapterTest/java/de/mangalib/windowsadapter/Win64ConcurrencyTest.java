package de.mangalib.windowsadapter;

import com.sun.jna.Pointer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static de.mangalib.windowsadapter.KernelCalls.*;
import static de.mangalib.windowsadapter.OwnedJobAdapter.ControlPhase.*;
import static org.junit.jupiter.api.Assertions.*;

/** Ausschließlich begrenzte Java-Threads und blockierbare Raw-Fakes, keine DLL-Aufrufe. */
class Win64ConcurrencyTest {
    record Call(String name, Object[] args) { }
    static final class Gate {
        final Predicate<Call> matches;
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        final AtomicBoolean consumed = new AtomicBoolean();
        Gate(Predicate<Call> matches) { this.matches = matches; }
        void pass(Call call) throws InterruptedException {
            if (matches.test(call) && consumed.compareAndSet(false, true)) {
                entered.countDown();
                if (!release.await(6, TimeUnit.SECONDS)) throw new AssertionError("Bounded fake gate expired: " + call.name());
            }
        }
        void awaitEntered() throws InterruptedException { assertTrue(entered.await(3, TimeUnit.SECONDS), "Fake call did not enter gate"); }
    }

    static final class Harness implements AutoCloseable {
        final AtomicLong now = new AtomicLong(100_000_000);
        final Win64ProcessBindingsTest.Raw raw = new Win64ProcessBindingsTest.Raw();
        final List<Gate> gates = new CopyOnWriteArrayList<>();
        final Map<String, AtomicInteger> entries = new ConcurrentHashMap<>();
        final ThreadLocal<Integer> savedError = ThreadLocal.withInitial(() -> 5);
        final ExecutorService threads = Executors.newFixedThreadPool(4, action -> {
            Thread thread = new Thread(action, "bounded-adapter-fake"); thread.setDaemon(true); return thread;
        });
        final Win64ProcessKernelCalls port;
        final Object ticket = new Object();
        final OwnedJobAdapter owner;
        Harness() {
            raw.clock = now;
            var api = (Win64ProcessKernelCalls.ProcessRawApi) Proxy.newProxyInstance(
                    KernelCalls.class.getClassLoader(), new Class<?>[]{Win64ProcessKernelCalls.ProcessRawApi.class},
                    (proxy, method, args) -> {
                        entries.computeIfAbsent(method.getName(), key -> new AtomicInteger()).incrementAndGet();
                        if (method.getName().equals("error")) return savedError.get();
                        var call = new Call(method.getName(), args);
                        for (Gate gate : gates) gate.pass(call); // Nie den Raw-Lock während des Wartens halten.
                        synchronized (raw) {
                            try {
                                Object result = raw.getClass().getMethod(method.getName(), method.getParameterTypes()).invoke(raw, args);
                                savedError.set(raw.error); return result;
                            }
                            catch (InvocationTargetException error) { throw error.getCause(); }
                        }
                    });
            port = new Win64ProcessKernelCalls(api, Win64ProcessBindingsTest.spec(List.of("SyntheticOnly")), now::get);
            owner = new OwnedJobAdapter(ticket, port, now::get, 8);
        }
        Gate gate(String method) { return gate(call -> call.name().equals(method)); }
        Gate gate(Predicate<Call> condition) { Gate gate = new Gate(condition); gates.add(gate); return gate; }
        <T> Future<T> async(Callable<T> action) { return threads.submit(action); }
        <T> T done(Future<T> future) throws Exception { return future.get(3, TimeUnit.SECONDS); }
        int calls(String name) { return entries.getOrDefault(name, new AtomicInteger()).get(); }
        void initialized() { assertTrue(owner.initialize(ticket, new Limits(true, false, 0, 0, 0))); }
        void started() { initialized(); assertNotNull(owner.start(ticket)); }
        void noCloses() { assertEquals(0, calls("close")); }
        @Override public void close() throws InterruptedException {
            // Auch bei fehlgeschlagener Assertion alle kontrollierten Blockaden lösen.
            gates.forEach(gate -> gate.release.countDown());
            threads.shutdown();
            if (!threads.awaitTermination(3, TimeUnit.SECONDS)) {
                threads.shutdownNow(); assertTrue(threads.awaitTermination(3, TimeUnit.SECONDS), "Fake threads did not finish");
            }
        }
    }

    @Test void blockedQueryAllowsIndependentTerminationWithoutClosingInUseResources() throws Exception {
        try (var h = new Harness()) {
            h.started(); Gate query = h.gate(call -> call.name().equals("query") && (int) call.args()[1] == 1);
            Future<Boolean> inspect = h.async(() -> h.owner.inspect(h.ticket, 250_000_000)); query.awaitEntered();
            var report = h.done(h.async(() -> h.owner.cleanup(h.ticket, 300_000_000)));
            assertTrue(report.quarantineRequired()); assertFalse(report.groupEndConfirmed()); assertFalse(report.resourcesClosed());
            assertFalse(inspect.isDone()); assertEquals(1, h.calls("TerminateJobObject")); h.noCloses();
            var progress = h.done(h.async(() -> h.owner.cleanupProgress(h.ticket)));
            assertTrue(progress.requested()); assertTrue(progress.operationOutstanding()); assertEquals(SUCCEEDED, progress.control());
            assertThrows(Failure.class, () -> h.port.close(new Resource(new Scope(), Kind.JOB, 1)));
            query.release.countDown(); assertFalse(h.done(inspect));
            var end = h.owner.cleanup(h.ticket, 900_000_000);
            assertTrue(end.groupEndConfirmed()); assertTrue(end.resourcesClosed()); assertTrue(end.quarantineRequired());
            assertEquals(1, h.calls("TerminateJobObject")); assertEquals(0, h.port.retainedLaunchBuffers());
        }
    }

    @Test void lateSuspendedStartAfterControlTerminationNeverResumesOrProvesAnEmptyGroup() throws Exception {
        try (var h = new Harness()) {
            h.initialized(); Gate start = h.gate("CreateProcessW");
            var attempt = h.async(() -> h.owner.start(h.ticket)); start.awaitEntered();
            assertTrue(h.done(h.async(() -> h.owner.cleanup(h.ticket, 300_000_000))).quarantineRequired());
            assertFalse(attempt.isDone()); assertEquals(1, h.calls("TerminateJobObject")); h.noCloses();
            start.release.countDown(); assertNotNull(h.done(attempt));
            assertFalse(h.owner.resume(h.ticket)); assertEquals(0, h.calls("ResumeThread"));
            var report = h.owner.cleanup(h.ticket, 900_000_000);
            assertFalse(report.groupEndConfirmed()); assertFalse(report.resourcesClosed()); assertTrue(report.quarantineRequired());
            assertEquals(1, h.calls("TerminateJobObject")); // Kein zweiter Kill nach spätem Create.
            assertTrue(h.port.retainedLaunchBuffers() > 0); h.noCloses();
        }
    }

    @ParameterizedTest @ValueSource(strings = {"InitializeProcThreadAttributeList", "UpdateProcThreadAttribute"})
    void cancellationDuringPreparationPreventsAnyCreate(String operation) throws Exception {
        try (var h = new Harness()) {
            h.initialized(); Gate prepare = h.gate(operation);
            var start = h.async(() -> h.owner.start(h.ticket)); prepare.awaitEntered();
            assertFalse(h.done(h.async(() -> h.owner.cleanup(h.ticket, 300_000_000))).resourcesClosed());
            prepare.release.countDown(); assertNotNull(h.done(start));
            assertEquals(0, h.calls("CreateProcessW")); assertEquals(0, h.calls("ResumeThread"));
            var report = h.owner.cleanup(h.ticket, 900_000_000);
            assertTrue(report.groupEndConfirmed()); assertTrue(report.resourcesClosed()); assertTrue(report.quarantineRequired());
        }
    }

    @ParameterizedTest @ValueSource(strings = {"IsProcessInJob", "GetProcessTimes", "QueryFullProcessImageNameW"})
    void blockedIdentityBeforeResumeDoesNotBlockControlAndCannotResumeAfterStop(String operation) throws Exception {
        try (var h = new Harness()) {
            h.started(); Gate identity = h.gate(operation);
            var resume = h.async(() -> h.owner.resume(h.ticket)); identity.awaitEntered();
            var report = h.done(h.async(() -> h.owner.cleanup(h.ticket, 300_000_000)));
            assertFalse(report.resourcesClosed()); assertEquals(1, h.calls("TerminateJobObject")); h.noCloses();
            identity.release.countDown(); assertFalse(h.done(resume)); assertEquals(0, h.calls("ResumeThread"));
            var end = h.owner.cleanup(h.ticket, 900_000_000);
            assertTrue(end.groupEndConfirmed()); assertTrue(end.resourcesClosed()); assertTrue(end.quarantineRequired());
        }
    }

    @Test void blockedControlStillAllowsProgressAndDuplicateRequestsButNoClose() throws Exception {
        try (var h = new Harness()) {
            h.started(); Gate query = h.gate(call -> call.name().equals("query") && (int) call.args()[1] == 1);
            Gate terminate = h.gate("TerminateJobObject");
            var inspect = h.async(() -> h.owner.inspect(h.ticket, 250_000_000)); query.awaitEntered();
            var cleanup = h.async(() -> h.owner.cleanup(h.ticket, 300_000_000)); terminate.awaitEntered();
            var second = h.done(h.async(() -> h.owner.cleanup(h.ticket, 900_000_000)));
            assertFalse(second.groupEndConfirmed()); assertFalse(second.resourcesClosed()); assertTrue(second.quarantineRequired());
            var progress = h.done(h.async(() -> h.owner.cleanupProgress(h.ticket)));
            assertEquals(RUNNING, progress.control()); assertTrue(progress.requested());
            assertEquals(1, h.calls("TerminateJobObject")); h.noCloses();
            terminate.release.countDown(); assertFalse(h.done(cleanup).groupEndConfirmed());
            query.release.countDown(); assertFalse(h.done(inspect));
            assertTrue(h.owner.cleanup(h.ticket, 900_000_000).resourcesClosed());
            assertEquals(1, h.calls("TerminateJobObject"));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"WaitForSingleObject", "close"})
    void blockedCleanupCallDoesNotOwnTheProgressMonitorOrPermitDoubleFree(String operation) throws Exception {
        try (var h = new Harness()) {
            h.started(); Gate wait = h.gate(operation);
            var cleanup = h.async(() -> h.owner.cleanup(h.ticket, 300_000_000)); wait.awaitEntered();
            var second = h.done(h.async(() -> h.owner.cleanup(h.ticket, 900_000_000)));
            assertFalse(second.resourcesClosed()); assertEquals(1, h.calls("TerminateJobObject"));
            assertTrue(h.done(h.async(() -> h.owner.cleanupProgress(h.ticket))).operationOutstanding());
            int closes = h.calls("close");
            assertFalse(h.done(h.async(() -> h.owner.cleanup(h.ticket, 900_000_000))).resourcesClosed());
            assertEquals(closes, h.calls("close"));
            wait.release.countDown(); var end = h.done(cleanup);
            assertTrue(end.groupEndConfirmed()); assertTrue(end.resourcesClosed()); assertTrue(end.quarantineRequired());
            assertEquals(3, h.calls("close")); // Thread, Prozess, Job je genau einmal.
        }
    }

    @Test void deadlineExpiryWhileControlBlockedNeverRenewsTheCleanupBudget() throws Exception {
        try (var h = new Harness()) {
            h.started(); Gate identity = h.gate("GetProcessTimes"); Gate terminate = h.gate("TerminateJobObject");
            var resume = h.async(() -> h.owner.resume(h.ticket)); identity.awaitEntered();
            long deadline = h.now.get() + 15_000_000_000L;
            var cleanup = h.async(() -> h.owner.cleanup(h.ticket, deadline)); terminate.awaitEntered();
            h.now.set(deadline); terminate.release.countDown();
            var report = h.done(cleanup); assertTrue(report.quarantineRequired());
            assertTrue(report.problems().stream().anyMatch(p -> p.operation().equals("control-cleanup-deadline")));
            assertEquals(OVERDUE, h.owner.cleanupProgress(h.ticket).control());
            identity.release.countDown(); assertFalse(h.done(resume));
            var late = h.owner.cleanup(h.ticket, 99_000_000_000L);
            assertFalse(late.resourcesClosed()); assertTrue(late.quarantineRequired()); h.noCloses();
            assertEquals(1, h.calls("TerminateJobObject"));
        }
    }

    @Test void failedControlRemainsVisibleWithoutFallbackRetryOrFalseEndProof() throws Exception {
        try (var h = new Harness()) {
            h.started(); synchronized (h.raw) { h.raw.failure = "terminate"; }
            Gate query = h.gate(call -> call.name().equals("query") && (int) call.args()[1] == 1);
            var inspect = h.async(() -> h.owner.inspect(h.ticket, 250_000_000)); query.awaitEntered();
            var failed = h.done(h.async(() -> h.owner.cleanup(h.ticket, 300_000_000)));
            assertEquals(FAILED, h.owner.cleanupProgress(h.ticket).control());
            assertTrue(failed.problems().stream().anyMatch(p -> p.operation().equals("TerminateJobObject:control")));
            query.release.countDown(); assertFalse(h.done(inspect));
            synchronized (h.raw) { h.raw.noEnd = true; }
            var end = h.owner.cleanup(h.ticket, 900_000_000);
            assertFalse(end.groupEndConfirmed()); assertFalse(end.resourcesClosed()); assertTrue(end.quarantineRequired());
            assertEquals(1, h.calls("TerminateJobObject")); h.noCloses();
        }
    }

    @Test void oldTicketsAndRetiredControlsNeverAffectANewPrivateGroup() throws Exception {
        try (var old = new Harness(); var next = new Harness()) {
            old.started(); var ended = old.owner.cleanup(old.ticket, 300_000_000); assertTrue(ended.resourcesClosed());
            next.started(); int nextCalls = next.calls("TerminateJobObject");
            assertThrows(IllegalArgumentException.class, () -> next.owner.requestControlCleanup(old.ticket, 300_000_000));
            assertSame(ended, old.owner.cleanup(old.ticket, 900_000_000));
            assertEquals(nextCalls, next.calls("TerminateJobObject")); assertEquals(0, next.calls("close"));
            assertTrue(next.owner.cleanup(next.ticket, 300_000_000).resourcesClosed());
        }
    }

    @Test void controlPinsOwnJobUntilItsInvocationReturnsAndReservationIsReleased() throws Exception {
        try (var h = new Harness()) {
            var job = h.port.createJob(new Scope()); var control = h.port.prepareCleanupControl(job);
            assertThrows(Failure.class, () -> h.port.prepareCleanupControl(job));
            Gate terminate = h.gate("TerminateJobObject");
            var pending = h.async(() -> { control.terminate(15_000_000_000L); return true; }); terminate.awaitEntered();
            assertThrows(Failure.class, control::release);
            assertThrows(Failure.class, () -> h.port.close(job));
            assertThrows(Failure.class, () -> control.terminate(15_000_000_000L)); h.noCloses();
            terminate.release.countDown(); assertTrue(h.done(pending));
            control.release(); h.port.close(job); assertEquals(1, h.calls("close"));
            assertThrows(Failure.class, control::release);
        }
    }

    @Test void activeBaseQueryPinsHandleEvenAfterIndependentReservationRelease() throws Exception {
        try (var h = new Harness()) {
            var job = h.port.createJob(new Scope()); var control = h.port.prepareCleanupControl(job);
            Gate query = h.gate(call -> call.name().equals("query") && (int) call.args()[1] == 9);
            var read = h.async(() -> h.port.readLimits(job)); query.awaitEntered();
            control.release();
            assertThrows(Failure.class, () -> h.port.close(job)); h.noCloses();
            query.release.countDown(); assertEquals(new Limits(true, false, 0, 0, 0), h.done(read));
            h.port.close(job); assertEquals(1, h.calls("close"));
        }
    }

    @Test void alreadyEnteredResumeMayReturnLateButNeverReportsRestartOrRetries() throws Exception {
        try (var h = new Harness()) {
            h.started(); Gate resumeCall = h.gate("ResumeThread");
            var resume = h.async(() -> h.owner.resume(h.ticket)); resumeCall.awaitEntered();
            assertTrue(h.done(h.async(() -> h.owner.cleanup(h.ticket, 300_000_000))).quarantineRequired());
            resumeCall.release.countDown(); assertFalse(h.done(resume));
            assertFalse(h.owner.resume(h.ticket)); assertEquals(1, h.calls("ResumeThread"));
            var end = h.owner.cleanup(h.ticket, 900_000_000);
            assertTrue(end.groupEndConfirmed()); assertTrue(end.resourcesClosed()); assertTrue(end.quarantineRequired());
        }
    }

    @Test void lateHandleCallbackDuringBlockedControlStaysOwnedUntilCorrectPermitCanSettle() throws Exception {
        try (var h = new Harness()) {
            h.initialized(); synchronized (h.raw) { h.raw.throwStart = true; }
            var permit = h.owner.start(h.ticket); assertTrue(h.owner.pendingStart());
            Gate terminate = h.gate("TerminateJobObject");
            var stop = h.async(() -> h.owner.requestControlCleanup(h.ticket, 300_000_000)); terminate.awaitEntered();
            Resource memory = Win64NativeBindingsTest.memory(h.port);
            var result = h.port.completeReturn(memory, Win64ProcessKernelCalls.NativeReturn.CREATED, 0);
            assertEquals(StartStatus.FAILED, result.status());
            assertFalse(h.owner.startReturned(new Object(), permit, result));
            assertFalse(h.owner.startReturned(h.ticket, permit, result)); // Kontrollaufruf noch aktiv.
            assertTrue(h.owner.pendingStart()); assertEquals(5, h.owner.openResources()); h.noCloses();
            terminate.release.countDown(); assertTrue(h.done(stop).quarantineRequired());
            assertTrue(h.owner.startReturned(h.ticket, permit, result)); assertFalse(h.owner.resume(h.ticket));
            var end = h.owner.cleanup(h.ticket, 900_000_000);
            assertTrue(end.groupEndConfirmed()); assertTrue(end.resourcesClosed()); assertTrue(end.quarantineRequired());
            assertEquals(0, h.calls("ResumeThread"));
        }
    }

    @Test void stopDuringPostCreateIdentityCannotReturnASuccessfulSuspendedStart() throws Exception {
        try (var h = new Harness()) {
            h.initialized(); Gate identity = h.gate("GetProcessTimes");
            var start = h.async(() -> h.owner.start(h.ticket)); identity.awaitEntered();
            assertFalse(h.done(h.async(() -> h.owner.cleanup(h.ticket, 300_000_000))).resourcesClosed());
            identity.release.countDown(); assertNotNull(h.done(start));
            assertEquals("start-returned-after-stop", h.port.startFailure().operation);
            assertFalse(h.owner.resume(h.ticket)); assertEquals(0, h.calls("ResumeThread"));
            var end = h.owner.cleanup(h.ticket, 900_000_000);
            assertTrue(end.groupEndConfirmed()); assertTrue(end.resourcesClosed()); assertTrue(end.quarantineRequired());
        }
    }

    @Test void expiredCleanupBudgetStillCancelsNewStartsWithoutIssuingAnOverdueNativeCall() throws Exception {
        try (var h = new Harness()) {
            h.initialized(); Gate prepare = h.gate("UpdateProcThreadAttribute");
            var start = h.async(() -> h.owner.start(h.ticket)); prepare.awaitEntered();
            assertTrue(h.done(h.async(() -> h.owner.cleanup(h.ticket, h.now.get()))).quarantineRequired());
            assertEquals(0, h.calls("TerminateJobObject"));
            prepare.release.countDown(); assertNotNull(h.done(start));
            assertEquals(0, h.calls("CreateProcessW")); assertEquals(0, h.calls("ResumeThread"));
            var report = h.owner.cleanup(h.ticket, 900_000_000);
            assertFalse(report.resourcesClosed()); assertTrue(report.quarantineRequired()); h.noCloses();
        }
    }

    @ParameterizedTest @ValueSource(strings = {"set", "query"})
    void controlIsPreparedBeforePotentiallyBlockingInitializationQueries(String method) throws Exception {
        try (var h = new Harness()) {
            Gate initialize = h.gate(method);
            var pending = h.async(() -> h.owner.initialize(h.ticket, new Limits(true, false, 0, 0, 0)));
            initialize.awaitEntered();
            var report = h.done(h.async(() -> h.owner.cleanup(h.ticket, 300_000_000)));
            assertTrue(report.quarantineRequired()); assertEquals(1, h.calls("TerminateJobObject")); h.noCloses();
            initialize.release.countDown(); assertTrue(h.done(pending));
            assertNull(h.owner.start(h.ticket)); assertEquals(0, h.calls("CreateProcessW"));
            var end = h.owner.cleanup(h.ticket, 900_000_000);
            assertTrue(end.groupEndConfirmed()); assertTrue(end.resourcesClosed()); assertTrue(end.quarantineRequired());
        }
    }

    @Test void unknownJobCreationOutcomeNeverGuessesAHandleForControl() throws Exception {
        try (var h = new Harness()) {
            Gate createJob = h.gate("create");
            var initialize = h.async(() -> h.owner.initialize(h.ticket, new Limits(true, false, 0, 0, 0)));
            createJob.awaitEntered();
            var report = h.done(h.async(() -> h.owner.cleanup(h.ticket, 300_000_000)));
            assertTrue(report.quarantineRequired()); assertEquals(UNAVAILABLE, h.owner.cleanupProgress(h.ticket).control());
            assertEquals(0, h.calls("TerminateJobObject")); h.noCloses();
            createJob.release.countDown(); assertTrue(h.done(initialize)); assertNull(h.owner.start(h.ticket));
            var end = h.owner.cleanup(h.ticket, 900_000_000);
            assertTrue(end.groupEndConfirmed()); assertTrue(end.resourcesClosed()); assertTrue(end.quarantineRequired());
            assertEquals(0, h.calls("CreateProcessW"));
        }
    }

    @Test void provenGroupEndDoesNotReleaseHandlesWhileControlIsStillInFlight() throws Exception {
        try (var h = new Harness()) {
            h.started(); synchronized (h.raw) { h.raw.pids.clear(); }
            Gate query = h.gate(call -> call.name().equals("query") && (int) call.args()[1] == 1);
            Gate terminate = h.gate("TerminateJobObject");
            var cleanup = h.async(() -> h.owner.cleanup(h.ticket, 300_000_000)); query.awaitEntered();
            var control = h.async(() -> h.owner.requestControlCleanup(h.ticket, 300_000_000)); terminate.awaitEntered();
            query.release.countDown(); var groupEnd = h.done(cleanup);
            assertTrue(groupEnd.groupEndConfirmed()); assertFalse(groupEnd.resourcesClosed()); h.noCloses();
            assertEquals(RUNNING, h.owner.cleanupProgress(h.ticket).control());
            terminate.release.countDown(); assertTrue(h.done(control).quarantineRequired());
            var end = h.owner.cleanup(h.ticket, 900_000_000);
            assertTrue(end.groupEndConfirmed()); assertTrue(end.resourcesClosed()); assertTrue(end.quarantineRequired());
            assertEquals(3, h.calls("close"));
        }
    }

    @Test void newTerminationCannotStartAfterResourceClosureHasBeenReserved() throws Exception {
        try (var h = new Harness()) {
            h.started(); synchronized (h.raw) { h.raw.pids.clear(); }
            Gate close = h.gate("close");
            var cleanup = h.async(() -> h.owner.cleanup(h.ticket, 300_000_000)); close.awaitEntered();
            var late = h.done(h.async(() -> h.owner.requestControlCleanup(h.ticket, 300_000_000)));
            assertFalse(late.resourcesClosed()); assertEquals(0, h.calls("TerminateJobObject"));
            close.release.countDown(); var end = h.done(cleanup);
            assertTrue(end.groupEndConfirmed()); assertTrue(end.resourcesClosed()); assertTrue(end.quarantineRequired());
            assertEquals(3, h.calls("close"));
        }
    }

    @Test void nativeStartWithoutPreparedControlIsRejectedBeforeCreate() throws Exception {
        try (var h = new Harness()) {
            var job = h.port.createJob(new Scope()); var preparation = new java.util.ArrayList<Resource>();
            h.port.prepareLaunch(job, preparation::add);
            var result = h.port.startSuspended(job, preparation, resource -> fail("No process acquisition expected"));
            assertEquals(StartStatus.FAILED, result.status());
            assertEquals("cleanup-control-not-prepared", h.port.startFailure().operation);
            assertEquals(0, h.calls("CreateProcessW"));
            h.port.close(preparation.get(1)); h.port.close(preparation.getFirst()); h.port.close(job);
        }
    }

    @Test void releasedControlCannotAuthorizeAnotherStartOrBePreparedAgain() throws Exception {
        try (var h = new Harness()) {
            var job = h.port.createJob(new Scope()); var control = h.port.prepareCleanupControl(job);
            control.release();
            assertThrows(Failure.class, () -> h.port.prepareLaunch(job, resource -> fail()));
            assertThrows(Failure.class, () -> h.port.prepareCleanupControl(job));
            assertEquals(0, h.calls("CreateProcessW")); h.port.close(job);
        }
    }
}
