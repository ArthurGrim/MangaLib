package de.mangalib.windowsadapter;

import de.mangalib.watchdogmodel.HostProbeLifecycle;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static de.mangalib.windowsadapter.ControlHostStartAdapter.*;
import static de.mangalib.windowsadapter.KernelCalls.*;
import static org.junit.jupiter.api.Assertions.*;

/** Keine JNA-Klasse/OS-Operation: lediglich endliche, im finally aufhebbare Java-Testblockaden. */
class ControlHostStartAdapterTest {
    private static final class Gate {
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        void pause() {
            entered.countDown();
            try { if (!release.await(3, TimeUnit.SECONDS)) throw new Failure("bounded-fake-timeout", 0); }
            catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new Failure("fake-interrupted", 0); }
        }
        void await() throws Exception { assertTrue(entered.await(2, TimeUnit.SECONDS)); }
    }
    private static final class Port implements HostCalls {
        final FakeKernelCalls fake = new FakeKernelCalls();
        LaunchContract contract = new LaunchContract(true, true, false, true,
                List.of(new StdioEnd(), new StdioEnd(), new StdioEnd()), "C:/synthetic/java.exe");
        volatile boolean inStart, inControl, cancelled;
        boolean noControl, failControl, denyMembership;
        int controls, exit;
        Gate createGate, startGate, controlGate;
        public LaunchContract contract() { return contract; }
        public Resource createJob(Scope scope) { if (createGate != null) createGate.pause(); return fake.createJob(scope); }
        public void configure(Resource job, Limits limits) { fake.configure(job, limits); }
        public Limits readLimits(Resource job) { return fake.readLimits(job); }
        public CleanupControl prepareCleanupControl(Resource job) {
            if (noControl) return null;
            return new CleanupControl() {
                boolean released, attempted;
                public void cancelStarts() { cancelled = true; }
                public void terminate(long remaining) {
                    assertFalse(released); assertFalse(attempted); assertTrue(remaining > 0);
                    attempted = true; controls++; inControl = true;
                    try {
                        if (controlGate != null) controlGate.pause();
                        if (failControl) throw new Failure("control-terminate", 5);
                        fake.terminate(job);
                    } finally { inControl = false; }
                }
                public void release() { assertFalse(inStart); assertFalse(inControl); assertFalse(released); released = true; }
            };
        }
        public void prepareLaunch(Resource job, Consumer<Resource> acquired) { fake.prepareLaunch(job, acquired); }
        public StartResult startSuspended(Resource job, List<Resource> preparation, Consumer<Resource> acquired) {
            inStart = true;
            try { if (startGate != null) startGate.pause(); return fake.startSuspended(job, preparation, acquired); }
            finally { inStart = false; }
        }
        public void resume(Resource thread) { fake.resume(thread); }
        public Identity identity(Resource process) { return fake.identity(process); }
        public boolean member(Resource process, Resource job) { return !denyMembership && fake.member(process, job); }
        public Observation observe(Resource job, int capacity, long remaining) { return fake.observe(job, capacity, remaining); }
        public Observation awaitEmptyJob(Resource job, int capacity, long remaining) { return fake.awaitEmptyJob(job, capacity, remaining); }
        public Resource openProcess(Resource job, long pid) { return fake.openProcess(job, pid); }
        public void terminate(Resource job) { fake.terminate(job); }
        public boolean waitEnded(Resource process, long remaining) { return fake.waitEnded(process, remaining); }
        public int exitCode(Resource root) { assertFalse(fake.handles.get(root).alive); return exit; }
        public void close(Resource resource) { assertFalse(inStart); assertFalse(inControl); fake.close(resource); }
    }
    private static final class Fixture implements AutoCloseable {
        final Port port = new Port();
        final HostProbeLifecycle lifecycle = new HostProbeLifecycle(() -> port.fake.now);
        final Object ticket = lifecycle.admit();
        final ControlHostStartAdapter adapter = new ControlHostStartAdapter(ticket, lifecycle, port, () -> port.fake.now);
        void running() { assertTrue(adapter.start()); assertEquals(0, port.fake.count("resume")); assertTrue(adapter.resume()); }
        void communication() { assertTrue(lifecycle.handshake()); assertTrue(lifecycle.resultAndEnd()); }
        void ended() { port.fake.processes.forEach(process -> process.alive = false); }
        @Override public void close() { lifecycle.close(); }
    }
    private static Thread thread(Runnable action, AtomicReference<Throwable> failure) {
        Thread thread = new Thread(() -> { try { action.run(); } catch (Throwable error) { failure.set(error); } }, "bounded-host-fake");
        thread.setDaemon(true); thread.start(); return thread;
    }
    private static void join(Thread thread, AtomicReference<Throwable> failure) throws Exception {
        thread.join(2500); if (thread.isAlive()) thread.interrupt();
        assertFalse(thread.isAlive()); if (failure.get() != null) throw new AssertionError(failure.get());
    }

    @Test void successfulHostOnlyProbeUsesExistingModelOwnedLedgerAndFullEndBarrier() {
        try (Fixture f = new Fixture()) {
            f.running(); assertEquals(new Limits(true, false, 0, 0, 0), f.port.fake.limits.get(f.port.fake.lastJob));
            assertEquals(1, f.port.fake.count("create")); assertEquals(1, f.port.fake.count("start-suspended"));
            f.communication(); assertFalse(f.lifecycle.slotFree()); f.ended();
            Completion end = f.adapter.cleanup(true);
            assertTrue(end.groupEnded()); assertTrue(end.resourcesClosed()); assertTrue(end.slotReleased());
            assertFalse(end.quarantine()); assertTrue(f.lifecycle.slotFree()); assertEquals(0, f.adapter.openResources());
            assertEquals(0, f.port.controls); // Normaler Exit statt angenommener Terminierung.
            long closes = f.port.fake.count("close"); assertSame(end, f.adapter.cleanup(true));
            assertTrue(f.lifecycle.slotFree()); assertEquals(closes, f.port.fake.count("close"));
        }
    }

    @Test void missingIntentOrIndependentControlPreventsCreateOrStart() {
        try (Fixture f = new Fixture()) {
            f.lifecycle.loseIntent(); assertFalse(f.adapter.start()); assertEquals(0, f.port.fake.count("create"));
            assertTrue(f.lifecycle.quarantine());
        }
        try (Fixture f = new Fixture()) {
            f.port.noControl = true; assertFalse(f.adapter.start()); assertEquals(0, f.port.fake.count("start-suspended"));
            assertTrue(f.lifecycle.quarantine());
        }
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3, 4})
    void unsafeAtomicitySuspendOrInheritanceContractNeverReachesCreateProcess(int missing) {
        try (Fixture f = new Fixture()) {
            List<StdioEnd> ends = f.port.contract.childEnds();
            f.port.contract = new LaunchContract(missing != 0, missing != 1, missing == 2,
                    missing != 3, missing == 4 ? List.of(ends.getFirst(), ends.getFirst(), ends.getLast()) : ends,
                    f.port.contract.executable());
            assertFalse(f.adapter.start()); assertEquals(0, f.port.fake.count("start-suspended"));
            assertTrue(f.lifecycle.quarantine()); assertEquals(0, f.port.fake.count("resume"));
        }
    }

    @ParameterizedTest @EnumSource(value = FakeKernelCalls.Fault.class, names = {"CREATE", "CONFIGURE", "READ_LIMITS", "MISMATCH_LIMITS",
            "PREPARE_MEMORY", "PREPARE_ATTRIBUTES", "BEFORE_ASSIGNMENT", "AFTER_ASSIGNMENT", "MEMBERSHIP", "IDENTITY"})
    void errorsBeforeOrAfterProcessCreationRetainResourcesAndNeverResume(FakeKernelCalls.Fault fault) {
        try (Fixture f = new Fixture()) {
            f.port.fake.faults.add(fault); assertFalse(f.adapter.start());
            assertEquals(0, f.port.fake.count("resume")); assertTrue(f.lifecycle.quarantine());
            if (fault == FakeKernelCalls.Fault.AFTER_ASSIGNMENT) assertTrue(f.adapter.openResources() >= 5);
        }
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3})
    void identityChangeResumeErrorAndRevokedAuthorityPreventSuccessfulActivation(int failure) {
        try (Fixture f = new Fixture()) {
            assertTrue(f.adapter.start());
            if (failure == 0) f.port.fake.handles.get(f.port.fake.lastStart.process()).birth++;
            if (failure == 1) f.port.fake.faults.add(FakeKernelCalls.Fault.RESUME);
            if (failure == 2) f.lifecycle.failed(f.ticket);
            if (failure == 3) f.port.fake.now = 30_000_000_000L;
            assertFalse(f.adapter.resume()); assertTrue(f.lifecycle.quarantine());
            assertTrue(f.port.fake.count("resume") <= 1);
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void absentMembershipOrUnexpectedExecutableCannotBecomeTrustedHost(boolean image) {
        try (Fixture f = new Fixture()) {
            if (image) {
                var previous = f.port.contract;
                f.port.contract = new LaunchContract(true, true, false, true, previous.childEnds(), "C:/other/java.exe");
            } else f.port.denyMembership = true;
            assertFalse(f.adapter.start()); assertEquals(0, f.port.fake.count("resume"));
            assertTrue(f.lifecycle.quarantine()); assertTrue(f.adapter.openResources() > 0);
        }
    }

    @Test void pendingLateReturnAfterCleanupRemainsOwnedAndCannotResume() {
        try (Fixture f = new Fixture()) {
            f.port.fake.pendingStart = true; assertFalse(f.adapter.start()); assertTrue(f.adapter.pendingStart());
            assertTrue(f.adapter.requestCleanup().quarantineRequired());
            StartResult late = f.port.fake.completePending(f.port.fake.lastJob);
            assertFalse(f.adapter.startReturned(late)); assertFalse(f.adapter.resume());
            assertTrue(f.adapter.openResources() >= 5); assertEquals(0, f.port.fake.count("resume"));
            assertTrue(f.lifecycle.quarantine());
        }
    }

    @Test void blockedStartDoesNotHoldCleanupMonitorAndLateCreationCannotBeResumed() throws Exception {
        try (Fixture f = new Fixture()) {
            f.port.startGate = new Gate(); var failure = new AtomicReference<Throwable>();
            Thread start = thread(() -> assertFalse(f.adapter.start()), failure);
            try {
                f.port.startGate.await(); assertTrue(f.adapter.pendingStart());
                assertTrue(f.adapter.requestCleanup().quarantineRequired()); assertEquals(1, f.port.controls);
                assertEquals(0, f.port.fake.count("close")); assertTrue(f.lifecycle.quarantine());
            } finally { f.port.startGate.release.countDown(); join(start, failure); }
            assertEquals(0, f.port.fake.count("resume")); assertTrue(f.port.fake.processes.getFirst().alive);
            Completion end = f.adapter.cleanup(false); assertFalse(end.slotReleased()); assertFalse(end.groupEnded());
            assertTrue(f.adapter.openResources() > 0); // Terminate vor late-create ist kein Endbeweis.
        }
    }

    @Test void secondAdapterCannotCreateAnotherJobForTheSameReservedIntent() throws Exception {
        try (Fixture f = new Fixture()) {
            f.port.createGate = new Gate(); Port other = new Port();
            var second = new ControlHostStartAdapter(f.ticket, f.lifecycle, other, () -> f.port.fake.now);
            var failure = new AtomicReference<Throwable>(); Thread first = thread(() -> assertFalse(f.adapter.start()), failure);
            try {
                f.port.createGate.await(); assertFalse(second.start()); assertEquals(0, other.fake.count("create"));
            } finally { f.port.createGate.release.countDown(); join(first, failure); }
            assertEquals(1, f.port.fake.count("create")); assertEquals(0, f.port.fake.count("start-suspended"));
            assertTrue(f.lifecycle.quarantine());
        }
    }

    @Test void rejectedAdmissionPreservesExistingCleanupAuthority() {
        try (Fixture f = new Fixture()) {
            f.running(); assertNull(f.lifecycle.admit());
            assertTrue(f.adapter.requestCleanup().quarantineRequired()); assertEquals(1, f.port.controls);
            assertTrue(f.lifecycle.quarantine());
        }
    }

    @Test void blockedControlDoesNotAllowResumeOrCloseAndTestThreadAlwaysReturns() throws Exception {
        try (Fixture f = new Fixture()) {
            assertTrue(f.adapter.start()); f.port.controlGate = new Gate(); var failure = new AtomicReference<Throwable>();
            Thread control = thread(f.adapter::requestCleanup, failure);
            try {
                f.port.controlGate.await(); assertFalse(f.adapter.resume());
                assertFalse(f.adapter.cleanup(false).resourcesClosed()); assertEquals(0, f.port.fake.count("close"));
                assertEquals(1, f.port.controls); assertTrue(f.lifecycle.quarantine());
            } finally { f.port.controlGate.release.countDown(); join(control, failure); }
        }
    }

    @Test void failedControlAndRepeatedCleanupNeverIssueAnotherTerminationOrNewDeadline() {
        try (Fixture f = new Fixture()) {
            f.running(); f.port.failControl = true; f.adapter.requestCleanup();
            long deadline = f.lifecycle.cleanupDeadline(f.ticket);
            f.port.fake.now += 1_000_000_000L; f.adapter.requestCleanup(); f.adapter.cleanup(false);
            assertEquals(deadline, f.lifecycle.cleanupDeadline(f.ticket)); assertEquals(1, f.port.controls);
            assertTrue(f.lifecycle.quarantine()); assertFalse(f.lifecycle.slotFree());
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void handshakeFailureOrEarlyEofQuarantinesEvenAfterActualKernelCleanup(boolean early) {
        try (Fixture f = new Fixture()) {
            f.running(); if (early) f.lifecycle.earlyEof(); else assertFalse(f.lifecycle.badHandshake());
            f.ended(); Completion end = f.adapter.cleanup(false);
            assertTrue(end.resourcesClosed()); assertFalse(end.slotReleased()); assertTrue(f.lifecycle.quarantine());
        }
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2})
    void correctChannelEndDoesNotReplaceRootSignalOrCompleteJobEnd(int missing) {
        try (Fixture f = new Fixture()) {
            f.running(); f.communication();
            if (missing == 0) f.port.fake.terminateHasNoEffect = true;
            if (missing == 1) { f.ended(); f.port.fake.neverSignaled.add(f.port.fake.lastStart.process()); }
            if (missing == 2) {
                f.ended(); f.port.fake.addProcess(f.port.fake.lastJob, "synthetic-dynamic"); f.port.fake.terminateHasNoEffect = true;
            }
            Completion end = f.adapter.cleanup(true);
            assertFalse(end.groupEnded()); assertFalse(end.resourcesClosed()); assertFalse(end.slotReleased());
            assertTrue(f.lifecycle.quarantine());
        }
    }

    @ParameterizedTest @EnumSource(value = FakeKernelCalls.Fault.class, names = {"CLOSE_PROCESS", "CLOSE_ATTRIBUTES", "CLOSE_MEMORY", "CLOSE_JOB"})
    void incompleteHandleEndIsNotRetriedOrReportedAsComplete(FakeKernelCalls.Fault fault) {
        try (Fixture f = new Fixture()) {
            f.running(); f.communication(); f.ended(); f.port.fake.faults.add(fault);
            Completion end = f.adapter.cleanup(true); long closes = f.port.fake.count("close");
            assertFalse(end.resourcesClosed()); assertFalse(end.slotReleased()); assertTrue(f.lifecycle.quarantine());
            f.port.fake.faults.clear(); f.adapter.cleanup(true); assertEquals(closes, f.port.fake.count("close"));
        }
    }

    @Test void noInnerJobOrWorkerIsAuthorizedAndNonzeroExitDoesNotFreeSlot() {
        try (Fixture f = new Fixture()) {
            f.running(); assertTrue(f.lifecycle.ready()); assertTrue(f.lifecycle.refusesInnerJob());
            assertEquals(1, f.port.fake.processes.size()); assertTrue(f.lifecycle.quarantine());
        }
        try (Fixture f = new Fixture()) {
            f.running(); f.communication(); f.ended(); f.port.exit = 2;
            assertFalse(f.adapter.cleanup(true).slotReleased()); assertTrue(f.lifecycle.quarantine());
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void lateHandshakeAndExpiredCleanupCannotReleaseProbeSlot(boolean cleanup) {
        try (Fixture f = new Fixture()) {
            f.running();
            if (cleanup) {
                f.communication(); f.ended(); f.port.fake.now = 15_000_000_000L;
                assertFalse(f.adapter.cleanup(true).slotReleased()); assertTrue(f.adapter.openResources() > 0);
            } else {
                f.port.fake.now = 30_000_000_000L; assertFalse(f.lifecycle.handshake());
            }
            assertTrue(f.lifecycle.quarantine()); assertFalse(f.lifecycle.slotFree());
        }
    }
}
