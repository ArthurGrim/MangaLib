package de.mangalib.windowsadapter;

import de.mangalib.watchdogmodel.HostProbeLifecycle;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static de.mangalib.windowsadapter.KernelCalls.*;
import static de.mangalib.windowsadapter.OuterJobCustodian.*;
import static org.junit.jupiter.api.Assertions.*;

/** Ausschließlich JDK/Port-Fakes. Keine JNA-Klasse, kein OS-Handle/-Prozess. */
@Timeout(10)
class OuterJobCustodianTest {
    @TempDir Path temporary;
    static final StartIntentStore.Binding BINDING = new StartIntentStore.Binding(new UUID(1, 1), new UUID(2, 2), new UUID(3, 3));
    static final Identity OWNER = new Identity(41, 101, "C:/synthetic/owner.exe"), HOST = new Identity(42, 102, "C:/synthetic/host.exe");
    static final UUID CHANNEL = new UUID(4, 4);
    static final class Gate {
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        void pause() {
            entered.countDown();
            try { if (!release.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("synthetic-timeout"); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException("synthetic-interrupted"); }
        }
    }
    static final class Fake implements Port {
        final Object cHandle = new Object(), rHandle = new Object(), oHandle = new Object(), peer = new Object();
        final Owner owner = new Owner(oHandle, OWNER, PROCESS_RIGHTS, true);
        final Scope scope = new Scope();
        final Resource job = new Resource(scope, Kind.JOB, 7), host = new Resource(scope, Kind.PROCESS, 8);
        final List<String> closes = new ArrayList<>();
        long now;
        int controls, roots, kills, badControl = -1, badRoot = -1, badEnd = -1, failedClose = -1;
        boolean alive = true, rootAlive = true, sourceClosed, settled, ioClosed, communicationEnded,
                killFails, closeFails, ownerWrong, ownerUnknown;
        Gate controlGate, rootGate, ownerGate, endGate, killGate, closeGate;
        Control held;
        public Control duplicateControl(Resource source, StartIntentStore.Binding binding, int rights) {
            assertSame(job, source); assertEquals(BINDING, binding); assertEquals(JOB_RIGHTS, rights); controls++;
            if (controlGate != null) controlGate.pause();
            if (badControl == 0) return null;
            held = new Control(badControl == 1 ? oHandle : cHandle,
                    badControl == 2 ? new Resource(new Scope(), Kind.JOB, 7) : source,
                    badControl == 3 ? new StartIntentStore.Binding(new UUID(9, 9), binding.session(), binding.group()) : binding,
                    badControl == 4 ? rights | 1 : rights, badControl != 5, badControl != 6, badControl != 7, badControl != 8);
            return held;
        }
        public ProcessEnd ownerEnd(Owner owner, long remaining) {
            assertSame(this.owner, owner); assertTrue(remaining > 0);
            if (ownerGate != null) ownerGate.pause();
            return new ProcessEnd(oHandle, ownerWrong ? HOST : OWNER, ownerUnknown ? Signal.UNKNOWN : alive ? Signal.ALIVE : Signal.ENDED);
        }
        public Root duplicateRoot(Control control, Resource source, int rights) {
            assertSame(held, control); assertSame(host, source); assertEquals(PROCESS_RIGHTS, rights); roots++;
            if (rootGate != null) rootGate.pause();
            if (badRoot == 0) throw new IllegalStateException("synthetic-transfer-lost");
            return new Root(badRoot == 1 ? cHandle : rHandle,
                    badRoot == 2 ? new Resource(new Scope(), Kind.PROCESS, 8) : source,
                    badRoot == 3 ? new Identity(42, 0, "") : HOST,
                    badRoot == 4 ? rights | 1 : rights, badRoot != 5, badRoot != 6, badRoot != 7);
        }
        public boolean terminate(Control control, long remaining) {
            assertSame(held, control); assertTrue(remaining > 0); kills++;
            if (killGate != null) killGate.pause();
            if (!killFails) rootAlive = false;
            return !killFails;
        }
        public End inspectEnd(Control control, Root root, long remaining) {
            assertSame(held, control); assertTrue(remaining > 0);
            if (endGate != null) endGate.pause();
            return new End(badEnd == 0 ? null : held,
                    new Observation(badEnd != 1, badEnd != 2, badEnd == 3 ? 1 : 0, badEnd == 4 ? List.of(42L) : List.of(),
                            rootAlive || badEnd == 5 ? 1 : 0, badEnd == 6 ? -1 : 1),
                    new Fence(held, sourceClosed && badEnd != 7, settled && badEnd != 8, ioClosed && badEnd != 9, communicationEnded && badEnd != 10),
                    root == null ? null : new ProcessEnd(badEnd == 11 ? oHandle : rHandle,
                            badEnd == 12 ? new Identity(42, 999, HOST.image()) : HOST, rootAlive ? Signal.ALIVE : Signal.ENDED));
        }
        private boolean close(String which) {
            assertFalse(closes.contains(which)); closes.add(which);
            if (closeGate != null) closeGate.pause(); return !closeFails && closes.size() != failedClose;
        }
        public boolean closeRoot(Root root) { assertSame(rHandle, root.handle()); return close("root"); }
        public boolean closeControl(Control control) { assertSame(cHandle, control.handle()); return close("K"); }
        public boolean closeOwner(Owner owner) { assertSame(oHandle, owner.handle()); return close("owner"); }
        void fence() { sourceClosed = settled = ioClosed = communicationEnded = true; }
    }
    final class Fixture implements AutoCloseable {
        final Fake port = new Fake();
        final StartIntentStore store;
        final StartIntentStore.Proof proof;
        final OuterJobCustodian core;
        Object receipt;
        Fixture() throws IOException {
            store = StartIntentStore.create(temporary.resolve(UUID.randomUUID().toString()), BINDING, point -> { }); proof = store.prepare();
            core = new OuterJobCustodian(BINDING, proof.anchor(), () -> store.valid(proof), port.owner, port.peer, CHANNEL, port, () -> port.now, 1000, 100, 200);
        }
        void adopt() { core.pollOwner(); receipt = core.retain(BINDING, proof.anchor(), port.job); assertNotNull(receipt); }
        void returned() { adopt(); core.recordReturn(receipt, port.host); }
        void ended() { returned(); core.revoke(receipt); core.terminateStep(); port.fence(); core.inspectEnd(); assertTrue(core.report().groupEnded()); }
        @Override public void close() throws IOException { store.close(); }
    }
    @Test void custodianRuntimeContainsNoJnaAndCannotLoadANativeBinding() {
        assertThrows(ClassNotFoundException.class, () -> Class.forName("com.sun.jna.Native", false, getClass().getClassLoader()));
    }
    @Test void ownDuplicateAndBoundOwnerAreRequiredAndFinalCloseNeverClosesTheSourceHandle() throws Exception {
        try (var f = new Fixture()) {
            f.ended(); assertEquals(Signal.ALIVE, f.core.report().owner());
            assertEquals(1, f.port.controls); assertEquals(1, f.port.roots);
            f.core.closeResources(); f.core.closeResources(); f.core.terminateStep();
            assertEquals(List.of("root", "K", "owner"), f.port.closes); assertEquals(1, f.port.kills);
            assertTrue(f.core.report().resourcesClosed()); assertTrue(f.core.report().quarantine());
            assertFalse(f.core.live(f.receipt)); // Endbeleg ist niemals Admission/Slotfreigabe.
        }
    }
    @ParameterizedTest @ValueSource(ints = {0,1,2,3,4,5,6,7,8})
    void invalidControlTransfersCannotAuthorizeOrTerminateAnyForeignHandle(int invalid) throws Exception {
        try (var f = new Fixture()) {
            f.port.badControl = invalid; f.core.pollOwner();
            assertNull(f.core.retain(BINDING, f.proof.anchor(), f.port.job)); f.core.terminateStep(); f.core.closeResources();
            assertEquals(0, f.port.kills); assertTrue(f.port.closes.isEmpty()); assertTrue(f.core.report().quarantine());
        }
    }
    @ParameterizedTest @ValueSource(ints = {0,1,2,3,4,5,6,7})
    void unconfirmedRootTransfersRemainOwnedByPortAndNeverProduceAnEndProof(int invalid) throws Exception {
        try (var f = new Fixture()) {
            f.adopt(); f.port.badRoot = invalid; f.core.recordReturn(f.receipt, f.port.host);
            assertFalse(f.core.live(f.receipt)); f.core.terminateStep(); f.port.fence(); f.core.inspectEnd(); f.core.closeResources();
            assertFalse(f.core.report().groupEnded()); assertTrue(f.port.closes.isEmpty()); assertTrue(f.core.report().quarantine());
        }
    }
    @ParameterizedTest @ValueSource(ints = {0,1,2,3,4,5,6,7,8,9,10,11,12})
    void incompleteForeignOrInconsistentEndEvidenceNeverReleasesResources(int invalid) throws Exception {
        try (var f = new Fixture()) {
            f.returned(); f.core.revoke(f.receipt); f.core.terminateStep(); f.port.fence(); f.port.badEnd = invalid;
            f.core.inspectEnd(); f.core.closeResources();
            assertFalse(f.core.report().groupEnded()); assertFalse(f.core.report().resourcesClosed()); assertTrue(f.port.closes.isEmpty());
        }
    }
    @Test void ownerDeathWhileStartIsPendingTriggersControlButDoesNotInventALateCreateFence() throws Exception {
        try (var f = new Fixture()) {
            f.adopt(); f.port.alive = false; f.core.pollOwner(); f.core.terminateStep(); f.port.fence(); f.core.inspectEnd();
            assertEquals(Signal.ENDED, f.core.report().owner()); assertEquals(1, f.port.kills);
            assertFalse(f.core.report().groupEnded()); assertFalse(f.core.report().resourcesClosed());
            f.port.rootAlive = true; f.core.recordReturn(f.receipt, f.port.host);
            assertFalse(f.core.live(f.receipt)); f.core.inspectEnd(); assertFalse(f.core.report().groupEnded());
            f.core.terminateStep(); f.core.terminateStep(); f.core.inspectEnd(); f.core.closeResources();
            assertEquals(2, f.port.kills); assertTrue(f.core.report().resourcesClosed()); assertTrue(f.core.report().quarantine());
        }
    }
    @Test void lostCommunicationOrProgressNeverMeansOwnerDeathAndNeverExtendsCleanup() throws Exception {
        try (var f = new Fixture()) {
            f.returned(); f.core.communicationLost(); long deadline = f.core.report().cleanupDeadline();
            assertEquals(Signal.ALIVE, f.core.report().owner());
            f.port.now = 30; f.core.communicationLost(); assertEquals(deadline, f.core.report().cleanupDeadline());
            assertTrue(f.core.receive(new Message(f.port.peer, CHANNEL, BINDING, f.proof.anchor(), 1, Command.PROGRESS)));
            assertFalse(f.core.live(f.receipt)); assertEquals(deadline, f.core.report().cleanupDeadline());
        }
        try (var f = new Fixture()) {
            f.adopt(); f.port.now = 200; assertFalse(f.core.live(f.receipt));
            assertEquals(Signal.ALIVE, f.core.report().owner()); assertTrue(f.core.report().quarantine());
        }
    }
    @ParameterizedTest @ValueSource(booleans = {false,true})
    void foreignOrUnknownOwnerIsNeverAdmitted(boolean unknown) throws Exception {
        try (var f = new Fixture()) {
            f.port.ownerWrong = !unknown; f.port.ownerUnknown = unknown; f.core.pollOwner();
            assertNull(f.core.retain(BINDING, f.proof.anchor(), f.port.job)); assertEquals(0, f.port.controls);
            assertEquals(Signal.UNKNOWN, f.core.report().owner());
        }
    }
    @ParameterizedTest @ValueSource(strings = {"owner", "query", "transfer", "adopt", "terminate", "close"})
    void blockedPortCallNeverHoldsDecisionMonitorAndLateReturnCannotReauthorize(String action) throws Exception {
        try (var f = new Fixture()) {
            Gate gate = new Gate(); Runnable call;
            switch (action) {
                case "adopt" -> { f.core.pollOwner(); f.port.controlGate = gate; call = () -> f.core.retain(BINDING, f.proof.anchor(), f.port.job); }
                case "transfer" -> { f.adopt(); f.port.rootGate = gate; call = () -> f.core.recordReturn(f.receipt, f.port.host); }
                case "owner" -> { f.returned(); f.port.ownerGate = gate; call = f.core::pollOwner; }
                case "query" -> { f.returned(); f.core.revoke(f.receipt); f.port.fence(); f.port.rootAlive = false; f.port.endGate = gate; call = f.core::inspectEnd; }
                case "terminate" -> { f.returned(); f.core.revoke(f.receipt); f.port.killGate = gate; call = f.core::terminateStep; }
                default -> { f.ended(); f.port.closeGate = gate; call = f.core::closeResources; }
            }
            var error = new AtomicReference<Throwable>(); Thread thread = new Thread(() -> { try { call.run(); } catch (Throwable failure) { error.set(failure); } });
            thread.setDaemon(true); thread.start();
            try {
                assertTrue(gate.entered.await(2, TimeUnit.SECONDS)); f.core.communicationLost();
                assertFalse(f.core.live(f.receipt)); assertTrue(f.core.report().callsOutstanding() > 0);
                if (action.equals("owner") || action.equals("query") || action.equals("transfer")) {
                    f.core.terminateStep(); assertEquals(1, f.port.kills);
                }
                if (action.equals("terminate")) { f.port.now = 100; assertEquals(Termination.OVERDUE, f.core.report().termination()); f.core.terminateStep(); assertEquals(1, f.port.kills); }
                f.core.closeResources();
            } finally { gate.release.countDown(); thread.join(2500); if (thread.isAlive()) { thread.interrupt(); thread.join(500); } }
            assertFalse(thread.isAlive()); assertNull(error.get()); assertFalse(f.core.live(f.receipt));
        }
    }
    @Test void noCloseRetryAndNoNewDeadlineAfterFailedTerminationOrUnknownClose() throws Exception {
        try (var f = new Fixture()) {
            f.returned(); f.port.killFails = true; f.core.revoke(f.receipt); f.core.terminateStep(); f.core.terminateStep();
            assertEquals(1, f.port.kills); assertEquals(Termination.FAILED, f.core.report().termination());
            f.port.rootAlive = false; f.port.fence(); f.core.inspectEnd(); f.port.closeFails = true;
            f.core.closeResources(); f.core.closeResources(); assertEquals(List.of("root"), f.port.closes);
            assertFalse(f.core.report().resourcesClosed()); assertTrue(f.core.report().quarantine());
        }
    }
    @ParameterizedTest @ValueSource(booleans = {false,true})
    void custodianCrashBeforeOrAfterCleanupDoesNotRestartOrCloseByAssumption(boolean after) throws Exception {
        try (var f = new Fixture()) {
            if (after) f.ended(); else f.adopt();
            f.core.custodianLost(); f.core.terminateStep(); f.core.closeResources();
            assertFalse(f.core.report().controlReachable()); assertFalse(f.core.live(f.receipt)); assertTrue(f.core.report().quarantine());
            assertFalse(f.core.report().resourcesClosed()); assertTrue(f.port.closes.isEmpty());
            assertNull(f.core.retain(BINDING, f.proof.anchor(), f.port.job));
        }
    }
    @ParameterizedTest @ValueSource(ints = {0,1,2,3,4,5})
    void foreignOldReorderedOrDuplicateMessagesCannotAuthorizeAnything(int invalid) throws Exception {
        try (var f = new Fixture()) {
            f.adopt();
            var message = new Message(invalid == 0 ? new Object() : f.port.peer, invalid == 1 ? new UUID(8,8) : CHANNEL,
                    invalid == 2 ? new StartIntentStore.Binding(new UUID(9,9), BINDING.session(), BINDING.group()) : BINDING,
                    invalid == 3 ? new StartIntentStore.Anchor(2, f.proof.anchor().hash()) : f.proof.anchor(), invalid == 4 ? 2 : 1, Command.PROGRESS);
            if (invalid == 5) assertTrue(f.core.receive(message));
            assertFalse(f.core.receive(message)); assertFalse(f.core.live(f.receipt)); assertTrue(f.core.report().quarantine());
        }
    }
    @Test void liveLeaseAndIntentBridgeRefuseResumeAfterIndependentOwnerSignal() throws Exception {
        try (var f = new Fixture()) {
            try (var lifecycle = new HostProbeLifecycle(() -> f.port.now)) {
                Object ticket = lifecycle.admit(); assertNotNull(ticket);
                f.core.pollOwner(); var guard = new StartLeaseGuard(f.store, f.proof, f.core, () -> lifecycle.failed(ticket));
                assertTrue(guard.beforeCreate(f.port.job)); assertTrue(guard.created(f.port.job, f.port.host));
                f.port.alive = false; f.core.pollOwner();
                assertFalse(guard.beforeResume(f.port.job, f.port.host)); f.core.terminateStep();
                assertEquals(1, f.port.kills); assertFalse(f.store.valid(f.proof));
                f.port.fence(); f.core.inspectEnd(); f.core.closeResources(); assertTrue(f.core.report().resourcesClosed());
                assertTrue(lifecycle.quarantine()); assertFalse(lifecycle.slotFree()); assertNull(lifecycle.admit());
            }
        }
    }
    @Test void damagedOrHistoricalIntentNeverAcquiresControl() throws Exception {
        try (var f = new Fixture()) {
            Files.write(f.store.directory().resolve("r1.bin"), new byte[]{1}); // Nur eigene neue TempDir-Datei.
            f.core.pollOwner(); assertNull(f.core.retain(BINDING, f.proof.anchor(), f.port.job));
            assertEquals(0, f.port.controls); assertTrue(f.core.report().quarantine());
            assertThrows(IOException.class, () -> StartIntentStore.create(f.store.directory(), BINDING, point -> { }));
        }
    }
    @ParameterizedTest @ValueSource(ints = {1,2,3})
    void eachUnknownCloseStopsTheResourceChainAndIsNeverRetried(int failedClose) throws Exception {
        try (var f = new Fixture()) {
            f.ended(); f.port.failedClose = failedClose; f.core.closeResources(); f.core.closeResources();
            assertEquals(failedClose, f.port.closes.size()); assertFalse(f.core.report().resourcesClosed());
            assertTrue(f.core.report().quarantine()); assertFalse(f.core.live(f.receipt));
        }
    }
    @Test void cleanupDeadlineIsNotRenewedForLateCreateAndOwnerEndIsNotAStartFence() throws Exception {
        try (var f = new Fixture()) {
            f.adopt(); f.core.communicationLost(); f.core.terminateStep(); f.port.now = 100;
            f.core.recordReturn(f.receipt, f.port.host); f.port.fence(); f.core.terminateStep(); f.core.inspectEnd(); f.core.closeResources();
            assertEquals(1, f.port.kills); assertEquals(100, f.core.report().cleanupDeadline());
            assertFalse(f.core.report().groupEnded()); assertFalse(f.core.report().resourcesClosed());
        }
    }
    @Test void foreignRootAndInconsistentSecondReturnNeverReachTransferOrHealTheBarrier() throws Exception {
        try (var f = new Fixture()) {
            f.adopt(); f.core.recordReturn(f.receipt, new Resource(new Scope(), Kind.PROCESS, 8));
            assertEquals(0, f.port.roots); f.core.recordReturn(f.receipt, f.port.host);
            f.core.terminateStep(); f.port.fence(); f.core.inspectEnd();
            assertFalse(f.core.report().groupEnded()); assertTrue(f.core.report().quarantine());
        }
        try (var f = new Fixture()) {
            f.ended(); f.core.recordReturn(f.receipt, new Resource(f.port.scope, Kind.PROCESS, 99)); f.core.inspectEnd();
            assertFalse(f.core.report().groupEnded()); assertTrue(f.core.report().quarantine());
        }
    }
    @ParameterizedTest @ValueSource(strings = {"deadline", "crash", "foreign-return"})
    void lateCloseReturnCannotStartAnotherCloseAfterDeadlineOrLostAuthority(String failure) throws Exception {
        try (var f = new Fixture()) {
            f.ended(); Gate gate = new Gate(); f.port.closeGate = gate;
            var error = new AtomicReference<Throwable>();
            Thread closer = new Thread(() -> { try { f.core.closeResources(); } catch (Throwable problem) { error.set(problem); } });
            closer.setDaemon(true); closer.start();
            try {
                assertTrue(gate.entered.await(2, TimeUnit.SECONDS));
                if (failure.equals("deadline")) f.port.now = 100;
                else if (failure.equals("crash")) f.core.custodianLost();
                else f.core.recordReturn(new Object(), f.port.host);
            } finally { gate.release.countDown(); closer.join(2500); if (closer.isAlive()) { closer.interrupt(); closer.join(500); } }
            assertFalse(closer.isAlive()); assertNull(error.get()); assertEquals(List.of("root"), f.port.closes);
            assertFalse(f.core.report().resourcesClosed()); assertEquals(0, f.core.report().callsOutstanding());
            f.core.closeResources(); assertEquals(List.of("root"), f.port.closes);
        }
    }
    @ParameterizedTest @ValueSource(booleans = {false,true})
    void custodianFailureDuringTransferLeavesNoClaimOfOwnershipCleanupOrRestart(boolean duringAdoption) throws Exception {
        try (var f = new Fixture()) {
            Gate gate = new Gate(); Runnable transfer;
            if (duringAdoption) {
                f.core.pollOwner(); f.port.controlGate = gate;
                transfer = () -> assertNull(f.core.retain(BINDING, f.proof.anchor(), f.port.job));
            } else {
                f.adopt(); f.port.rootGate = gate; transfer = () -> f.core.recordReturn(f.receipt, f.port.host);
            }
            var error = new AtomicReference<Throwable>(); Thread thread = new Thread(() -> { try { transfer.run(); } catch (Throwable problem) { error.set(problem); } });
            thread.setDaemon(true); thread.start();
            try { assertTrue(gate.entered.await(2, TimeUnit.SECONDS)); f.core.custodianLost(); }
            finally { gate.release.countDown(); thread.join(2500); if (thread.isAlive()) { thread.interrupt(); thread.join(500); } }
            assertFalse(thread.isAlive()); assertNull(error.get()); f.core.terminateStep(); f.core.closeResources();
            assertFalse(f.core.report().controlReachable()); assertFalse(f.core.report().resourcesClosed());
            assertEquals(0, f.port.kills); assertTrue(f.port.closes.isEmpty()); assertTrue(f.core.report().quarantine());
        }
    }
}
