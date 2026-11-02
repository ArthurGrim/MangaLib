package de.mangalib.watchdogmodel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static de.mangalib.watchdogmodel.BinaryIpcCodec.*;
import static de.mangalib.watchdogmodel.FakeJobKernel.*;
import static de.mangalib.watchdogmodel.WatchdogModel.*;
import static org.junit.jupiter.api.Assertions.*;

/** Integration bedeutet hier ausschließlich gekoppelte Speicher-/Kernel-/Journalfakes. */
class LifecycleCoordinatorTest {
    private static final Identity HOST = new Identity(100, 1, "synthetic-host");
    private static final Identity WORKER = new Identity(101, 2, "synthetic-worker");
    private static final class Fixture implements AutoCloseable {
        final Clock clock = new Clock();
        final FakeJobKernel kernel = new FakeJobKernel();
        final FakeRecoveryJournal journal = new FakeRecoveryJournal(FakeRecoveryJournal.State.CLEAN);
        final LifecycleCoordinator coordinator;
        final WatchdogModel model;
        Ticket ticket;
        Fixture() { this(new Id(7, 1)); }
        Fixture(Id session) {
            coordinator = new LifecycleCoordinator(session, clock, kernel, journal); model = coordinator.model;
            assertTrue(coordinator.initialize(journal.baseline()));
        }
        void admitted() { ticket = coordinator.request(Operation.VOLUME).orElseThrow(); }
        void outer() { admitted(); assertTrue(coordinator.prepareOuter(ticket)); }
        void hostPending() { outer(); assertTrue(coordinator.beginHost(ticket)); }
        void host() { hostPending(); assertTrue(coordinator.hostReturned(ticket, HOST)); }
        void ready() { host(); send(Direction.S_H, Type.HELLO); send(Direction.H_S, Type.READY); }
        void workerPending() { ready(); assertTrue(coordinator.prepareInner(ticket)); send(Direction.S_H, Type.START); assertTrue(coordinator.beginWorker(ticket)); }
        void running() {
            workerPending(); assertTrue(coordinator.workerReturned(ticket, WORKER));
            send(Direction.H_W, Type.START); send(Direction.W_H, Type.STARTED); assertEquals(State.RUNNING, model.state());
        }
        void send(Direction d, Type type) { assertTrue(coordinator.send(d, type), () -> "rejected " + type); }
        void send(Direction d, Body body, String text) { assertTrue(coordinator.send(d, body, text)); }
        void result() {
            long bytes = utf8Length(SyntheticProtocol.RESPONSE);
            send(Direction.W_H, new ResultBegin(bytes, 1, 1), "");
            send(Direction.W_H, new ResultChunk(bytes), SyntheticProtocol.RESPONSE);
            assertTrue(coordinator.syntheticResultAccepted()); send(Direction.W_H, Type.RESULT_END);
        }
        void end(Direction d) {
            send(d, Type.CHANNEL_END); coordinator.eof(d);
            assertTrue(coordinator.link(d).closed()); assertNull(coordinator.link(d).error());
            assertNotEquals(State.QUARANTINED, model.state());
        }
        void workerComplete() {
            end(Direction.W_H); end(Direction.H_W); send(Direction.H_S, Type.RESULT_AVAILABLE);
            assertTrue(coordinator.workerEnded(ticket, 0));
        }
        void innerComplete() {
            send(Direction.H_S, new GroupEnd(kernel.observe(Role.H, model.inner())), "");
            send(Direction.H_S, Type.RESOURCES_CLOSED);
        }
        void hostChannelsComplete() { end(Direction.S_H); end(Direction.H_S); }
        void readyToFinish() { running(); result(); workerComplete(); innerComplete(); hostChannelsComplete(); assertTrue(coordinator.hostEnded(ticket, false)); }
        IoEnd io() { return new IoEnd(ticket, true, true, true, true, true, true); }
        boolean finish() { return coordinator.finish(ticket, kernel.observe(Role.S, model.outer()), io()); }
        void quarantined() {
            assertEquals(State.QUARANTINED, model.state()); assertFalse(model.delivered());
            assertTrue(coordinator.request(Operation.SERIES).isEmpty());
        }
        @Override public void close() { coordinator.close(); assertEquals(0, coordinator.reservedBytes()); }
    }

    @Test void fullCommunicationAndLifecycleRequireIntentAtomicMembershipEveryEndAndJournal() {
        try (Fixture f = new Fixture()) {
            f.admitted(); assertEquals(FakeRecoveryJournal.State.INTENT, f.journal.state());
            assertNull(f.model.outer()); assertEquals(0, f.kernel.resumes);
            assertTrue(f.coordinator.prepareOuter(f.ticket)); assertEquals(Role.S, f.model.outer().owner);
            assertFalse(f.model.supervisor() instanceof Instance);
            assertThrows(IllegalStateException.class, () -> f.kernel.begin(Role.S, f.model.outer(), Role.S, null));
            assertTrue(f.coordinator.beginHost(f.ticket)); assertNull(f.model.host());
            assertTrue(f.coordinator.hostReturned(f.ticket, HOST));
            assertTrue(f.kernel.member(f.model.host(), f.model.outer().job)); assertFalse(f.model.host().suspended);
            f.send(Direction.S_H, Type.HELLO); f.send(Direction.H_S, Type.READY);
            assertTrue(f.coordinator.prepareInner(f.ticket)); assertEquals(Role.H, f.model.inner().owner);
            assertSame(f.model.outer().job, f.model.inner().job.parent);
            assertFalse(f.kernel.member(f.model.host(), f.model.inner().job));
            f.send(Direction.S_H, Type.START); assertTrue(f.coordinator.beginWorker(f.ticket));
            assertTrue(f.coordinator.workerReturned(f.ticket, WORKER));
            assertTrue(f.kernel.member(f.model.worker(), f.model.inner().job));
            assertTrue(f.kernel.member(f.model.worker(), f.model.outer().job));
            f.send(Direction.H_W, Type.START); f.send(Direction.W_H, Type.STARTED);
            f.result(); assertFalse(f.model.delivered()); f.workerComplete();
            assertTrue(f.model.host().alive); assertFalse(f.finish());
            f.innerComplete(); f.hostChannelsComplete(); assertFalse(f.finish()); // H noch in K.
            assertTrue(f.coordinator.hostEnded(f.ticket, false)); assertTrue(f.finish());
            assertTrue(f.model.delivered()); assertTrue(f.kernel.closed(f.ticket));
            assertEquals(FakeRecoveryJournal.State.COMPLETED, f.journal.state());
            assertEquals(0, f.coordinator.reservedBytes());
        }
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3})
    void hostCannotCreateInnerBeforeHandshakeOrWithoutConfirmedIdentity(int missing) {
        try (Fixture f = new Fixture()) {
            if (missing == 0) f.hostPending(); else if (missing == 1) f.host(); else f.ready();
            if (missing == 2) f.model.host().identified = false;
            if (missing == 3) f.model.host().suspended = true;
            assertFalse(f.coordinator.prepareInner(f.ticket)); assertNull(f.model.inner()); f.quarantined();
        }
    }

    @Test void workerStartRequiresOwnedConfiguredInnerJobAndHostProof() {
        try (Fixture f = new Fixture()) {
            f.ready(); f.send(Direction.S_H, Type.START);
            assertFalse(f.coordinator.beginWorker(f.ticket)); assertNull(f.model.worker());
            assertNull(f.model.inner()); f.quarantined();
        }
        try (Fixture f = new Fixture()) {
            f.ready(); assertTrue(f.coordinator.prepareInner(f.ticket)); f.send(Direction.S_H, Type.START);
            f.model.host().identified = false;
            assertFalse(f.coordinator.beginWorker(f.ticket)); assertNull(f.model.worker()); f.quarantined();
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void lateHostOrWorkerReturnCannotResumeOrReopenStoppedGroups(boolean worker) {
        try (Fixture f = new Fixture()) {
            if (worker) f.workerPending(); else f.hostPending();
            int resumes = f.kernel.resumes;
            assertTrue(f.coordinator.stop(f.ticket)); assertTrue(f.coordinator.escalate(f.ticket));
            Long deadline = f.model.cleanupDeadline();
            if (worker) assertFalse(f.coordinator.workerReturned(f.ticket, WORKER));
            else assertFalse(f.coordinator.hostReturned(f.ticket, HOST));
            Instance late = worker ? f.model.worker() : f.model.host();
            assertTrue(late.suspended); assertTrue(late.alive); assertEquals(resumes, f.kernel.resumes);
            assertTrue(f.kernel.member(late, f.model.outer().job)); assertTrue(f.model.outer().job.stopped);
            assertEquals(deadline, f.model.cleanupDeadline()); assertFalse(f.finish());
            assertTrue(f.coordinator.request(Operation.VOLUME).isEmpty());
        }
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3, 4, 5})
    void missingIntentBeforeCreationStartOrResumePreventsFurtherExecution(int phase) {
        try (Fixture f = new Fixture()) {
            switch (phase) {
                case 0 -> f.admitted(); case 1 -> f.outer(); case 2 -> f.ready();
                case 3 -> { f.ready(); assertTrue(f.coordinator.prepareInner(f.ticket)); f.send(Direction.S_H, Type.START); }
                case 4 -> f.hostPending(); default -> f.workerPending();
            }
            int resumes = f.kernel.resumes;
            f.journal.loseWriter(f.model.supervisor());
            boolean accepted = switch (phase) {
                case 0 -> f.coordinator.prepareOuter(f.ticket); case 1 -> f.coordinator.beginHost(f.ticket);
                case 2 -> f.coordinator.prepareInner(f.ticket); case 3 -> f.coordinator.beginWorker(f.ticket);
                case 4 -> f.coordinator.hostReturned(f.ticket, HOST); default -> f.coordinator.workerReturned(f.ticket, WORKER);
            };
            assertFalse(accepted); f.quarantined(); assertEquals(resumes, f.kernel.resumes);
            if (phase == 0) assertNull(f.model.outer());
            if (phase == 2) assertNull(f.model.inner());
        }
    }

    @ParameterizedTest @EnumSource(value = FakeRecoveryJournal.State.class, names = {"UNKNOWN", "CORRUPT", "STALE", "INTENT", "UNCERTAIN", "COMPLETED"})
    void invalidRecoveryBaselineNeverCreatesAGroup(FakeRecoveryJournal.State state) {
        var journal = new FakeRecoveryJournal(state);
        try (var coordinator = new LifecycleCoordinator(new Id(7, 1), new Clock(), new FakeJobKernel(), journal)) {
            assertFalse(coordinator.initialize(journal.baseline()));
            assertTrue(coordinator.request(Operation.VOLUME).isEmpty());
            assertNull(coordinator.model.outer()); assertEquals(State.QUARANTINED, coordinator.model.state());
        }
    }

    @Test void failedIntentDoesNotAllocateKernelOrChannels() {
        try (Fixture f = new Fixture()) {
            f.journal.failIntent = true; assertTrue(f.coordinator.request(Operation.VOLUME).isEmpty());
            assertNull(f.model.outer()); assertEquals(0, f.coordinator.reservedBytes()); f.quarantined();
        }
    }

    @Test void wireMessagesBeforeAdmissionCannotAllocateJobsOrChannels() {
        try (Fixture f = new Fixture()) {
            assertFalse(f.coordinator.send(Direction.S_H, Type.HELLO));
            assertEquals(State.IDLE, f.model.state()); assertNull(f.model.ticket());
            assertNull(f.model.outer()); assertEquals(0, f.coordinator.reservedBytes());
        }
    }

    @Test void workerWireMessageBeforeWorkerAcquisitionIsControlledFailure() {
        try (Fixture f = new Fixture()) {
            f.ready(); assertFalse(f.coordinator.send(Direction.H_W, Type.START));
            assertNull(f.model.worker()); assertNull(f.model.inner()); f.quarantined();
        }
    }

    @Test void dynamicDescendantRemainsInBothGroupsAndBlocksFalseInnerEnd() {
        try (Fixture f = new Fixture()) {
            f.running(); Instance child = f.kernel.descendant(f.model.worker(), new Identity(102, 3, "dynamic-child"));
            assertTrue(f.kernel.member(child, f.model.inner().job)); assertTrue(f.kernel.member(child, f.model.outer().job));
            f.result(); f.workerComplete(); assertTrue(child.alive);
            Frame envelope = f.coordinator.frame(Direction.H_S, new Signal(Type.RESOURCES_CLOSED), "");
            Frame lie = new Frame(envelope.envelope(), new GroupProof(new Id(20, f.ticket.generation),
                    f.model.inner().job.revision, true, true, 0, 0));
            assertFalse(f.coordinator.sendFrame(Direction.H_S, lie)); f.quarantined();
            assertTrue(child.alive); assertEquals(HandleState.OPEN, f.model.inner().state);
        }
    }

    @Test void confirmedDescendantEndAllowsNormalCompletionButIsNeverInferredFromTerminate() {
        try (Fixture f = new Fixture()) {
            f.running(); Instance child = f.kernel.descendant(f.model.worker(), new Identity(102, 3, "dynamic-child"));
            f.result(); f.workerComplete(); assertTrue(child.alive); assertFalse(f.finish());
            f.kernel.end(child); f.innerComplete(); f.hostChannelsComplete(); f.coordinator.hostEnded(f.ticket, false);
            assertTrue(f.finish()); assertTrue(f.model.delivered());
        }
    }

    @Test void hostCrashDuringPartialResultDoesNotEndWorkerOrDescendants() {
        try (Fixture f = new Fixture()) {
            f.running(); Instance child = f.kernel.descendant(f.model.worker(), new Identity(102, 3, "dynamic-child"));
            f.send(Direction.W_H, new ResultBegin(utf8Length(SyntheticProtocol.RESPONSE), 1, 1), "");
            assertTrue(f.coordinator.hostEnded(f.ticket, true)); f.quarantined();
            assertTrue(child.alive); assertTrue(f.model.worker().alive); assertFalse(f.coordinator.syntheticResultAccepted());
            assertTrue(f.model.inner().job.killOnCloseRequested); assertFalse(f.finish());
        }
    }

    @Test void hostEndWithoutConfirmedDescendantEndRemainsQuarantinedAfterMemoryCleanup() {
        try (Fixture f = new Fixture()) {
            f.running(); Instance child = f.kernel.descendant(f.model.worker(), new Identity(102, 3, "dynamic-child"));
            f.coordinator.hostEnded(f.ticket, true); f.coordinator.close();
            assertEquals(0, f.coordinator.reservedBytes()); assertTrue(child.alive); f.quarantined();
            assertFalse(f.finish());
        }
    }

    @Test void confirmedIncidentResourceRetirementNeverBecomesRecoveryOrSlotRelease() {
        try (Fixture f = new Fixture()) {
            f.running(); f.coordinator.hostEnded(f.ticket, true); f.coordinator.close();
            f.kernel.endGroup(f.model.outer()); // Eigenes bestätigtes Fake-Endereignis, nicht Folge von close.
            assertTrue(f.coordinator.retire(f.ticket, f.kernel.observe(Role.S, f.model.outer()), f.io()));
            assertTrue(f.kernel.closed(f.ticket)); assertEquals(FakeRecoveryJournal.State.UNCERTAIN, f.journal.state());
            f.quarantined();
        }
    }

    @Test void channelEndCannotStandInForGroupEnd() {
        try (Fixture f = new Fixture()) {
            f.running(); f.result(); f.end(Direction.W_H); f.end(Direction.H_W);
            assertTrue(f.model.worker().alive); assertTrue(f.model.host().alive); assertFalse(f.finish());
            assertFalse(f.coordinator.send(Direction.S_H, Type.CHANNEL_END)); f.quarantined();
        }
    }

    @Test void endedGroupsCannotStandInForOpenIpcChannels() {
        try (Fixture f = new Fixture()) {
            f.running(); f.result(); f.send(Direction.H_S, Type.RESULT_AVAILABLE);
            f.coordinator.workerEnded(f.ticket, 0); f.innerComplete(); f.coordinator.hostEnded(f.ticket, false);
            assertFalse(f.finish()); assertEquals(State.CLEANING, f.model.state());
            assertFalse(f.model.delivered()); assertTrue(f.coordinator.reservedBytes() > 0);
        }
    }

    @Test void failedInnerTerminationPermitsOnlyModelEscalationWithoutRenewingDeadline() {
        try (Fixture f = new Fixture()) {
            f.running(); f.kernel.failTerminateJ = true; f.coordinator.stop(f.ticket); f.quarantined();
            Long deadline = f.model.cleanupDeadline(); assertEquals(1, f.model.inner().job.terminationAttempts);
            assertEquals(0, f.model.inner().job.terminates); assertTrue(f.model.worker().alive);
            assertTrue(f.coordinator.escalate(f.ticket)); assertEquals(1, f.model.outer().job.terminates);
            f.coordinator.stop(f.ticket); assertEquals(1, f.model.inner().job.terminationAttempts);
            assertEquals(deadline, f.model.cleanupDeadline()); assertTrue(f.model.host().alive); assertFalse(f.finish());
            f.clock.advance(15); f.coordinator.tick(); assertFalse(f.coordinator.escalate(f.ticket)); f.quarantined();
        }
    }

    @Test void wireStopRequestsCleanupButNeitherKillsGroupsNorAutomaticallyEscalates() {
        try (Fixture f = new Fixture()) {
            f.running(); f.send(Direction.S_H, Type.STOP); Long deadline = f.model.cleanupDeadline();
            f.send(Direction.H_S, Type.STOP_ACCEPTED);
            assertEquals(State.CLEANING, f.model.state()); assertEquals(1, f.model.inner().job.terminates);
            assertEquals(0, f.model.outer().job.terminates); assertTrue(f.model.worker().alive);
            assertTrue(f.coordinator.escalate(f.ticket)); assertEquals(deadline, f.model.cleanupDeadline());
            assertTrue(f.model.host().alive); assertFalse(f.finish());
        }
    }

    @Test void failedOuterTerminationCannotBeRetriedOrTurnedIntoSuccessfulEnd() {
        try (Fixture f = new Fixture()) {
            f.running(); f.kernel.failTerminateK = true;
            assertFalse(f.coordinator.escalate(f.ticket)); f.quarantined();
            assertEquals(1, f.model.outer().job.terminationAttempts); assertEquals(0, f.model.outer().job.terminates);
            f.kernel.failTerminateK = false; assertFalse(f.coordinator.escalate(f.ticket));
            assertEquals(1, f.model.outer().job.terminationAttempts); assertTrue(f.model.host().alive); assertFalse(f.finish());
        }
    }

    @Test void aSingleCleanupDeadlineSurvivesStopEscalationAndLateEvidence() {
        try (Fixture f = new Fixture()) {
            f.running(); f.coordinator.stop(f.ticket); Long deadline = f.model.cleanupDeadline();
            f.clock.advance(14); f.coordinator.stop(f.ticket); f.coordinator.escalate(f.ticket);
            assertEquals(deadline, f.model.cleanupDeadline()); f.clock.advance(1); f.coordinator.tick(); f.quarantined();
            f.kernel.endGroup(f.model.outer()); assertFalse(f.finish()); assertEquals(deadline, f.model.cleanupDeadline());
        }
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3, 4, 5})
    void everyResourceEndComponentIsStillRequired(int missing) {
        try (Fixture f = new Fixture()) {
            f.readyToFinish(); IoEnd incomplete = new IoEnd(f.ticket, missing != 0, missing != 1, missing != 2,
                    missing != 3, missing != 4, missing != 5);
            assertFalse(f.coordinator.finish(f.ticket, f.kernel.observe(Role.S, f.model.outer()), incomplete));
            assertFalse(f.model.delivered()); f.clock.advance(15); f.coordinator.tick(); f.quarantined();
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void handleOrJournalCompletionFailureNeverFreesTheSlot(boolean journal) {
        try (Fixture f = new Fixture()) {
            f.readyToFinish(); f.kernel.failClose = !journal; f.journal.failCompletion = journal;
            assertFalse(f.finish()); f.quarantined();
            if (!journal) {
                assertEquals(HandleState.UNCERTAIN, f.model.outer().state);
                assertThrows(IllegalStateException.class, () -> f.kernel.configure(Role.S, f.model.outer()));
            }
        }
    }

    @ParameterizedTest @EnumSource(Role.class)
    void handleCopiesCannotCrossTrustRoles(Role caller) {
        try (Fixture f = new Fixture()) {
            f.running(); Handle source = caller == Role.S ? f.model.outer() : f.model.inner();
            Role recipient = caller == Role.S ? Role.H : Role.S;
            assertThrows(IllegalStateException.class, () -> f.kernel.duplicate(caller, source, recipient));
            assertThrows(IllegalStateException.class, () -> f.kernel.terminate(caller == Role.S ? Role.H : Role.S, source));
            assertTrue(f.model.host().alive); assertTrue(f.model.worker().alive);
        }
    }

    @Test void outerMembershipDoesNotAuthorizeResumingInnerWorkerAndForeignInstanceIsNotAdopted() {
        try (Fixture f = new Fixture(); Fixture foreign = new Fixture()) {
            f.running(); foreign.running();
            f.model.worker().suspended = true;
            assertThrows(IllegalStateException.class, () -> f.kernel.resume(Role.S, f.model.outer(), f.model.worker()));
            assertFalse(f.kernel.member(foreign.model.worker(), f.model.outer().job));
            assertThrows(IllegalStateException.class, () -> f.kernel.resume(Role.H, f.model.inner(), foreign.model.worker()));
            assertThrows(IllegalStateException.class, () -> f.kernel.create(f.ticket, Role.H, Kind.J, foreign.model.host()));
        }
    }

    @Test void stoppedOuterGroupAlsoPreventsResumeThroughStillOpenInnerHandle() {
        try (Fixture f = new Fixture()) {
            f.running(); f.model.worker().suspended = true;
            f.kernel.terminate(Role.S, f.model.outer());
            assertFalse(f.model.inner().job.stopped);
            assertThrows(IllegalStateException.class, () -> f.kernel.resume(Role.H, f.model.inner(), f.model.worker()));
        }
    }

    @Test void uncertainOuterHandleCannotBeUsedToContinueWithInnerCreation() {
        try (Fixture f = new Fixture()) {
            f.ready(); f.kernel.failClose = true;
            assertThrows(IllegalStateException.class, () -> f.kernel.close(Role.S, f.model.outer()));
            assertEquals(HandleState.UNCERTAIN, f.model.outer().state);
            assertFalse(f.coordinator.prepareInner(f.ticket)); assertNull(f.model.inner()); f.quarantined();
            assertFalse(f.coordinator.escalate(f.ticket));
        }
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3, 4, 5})
    void badHandshakeEnvelopeAndMismatchedProfileFailClosedAfterOuterCreation(int wrong) {
        try (Fixture f = new Fixture()) {
            f.host(); Frame good = f.coordinator.frame(Direction.S_H, new Signal(Type.HELLO), LifecycleCoordinator.PROFILE);
            Envelope e = good.envelope(); Id bad = new Id(99, 99);
            Frame corrupt = new Frame(new Envelope(wrong == 0 ? bad : e.session(), wrong == 1 ? bad : e.ticket(),
                    wrong == 2 ? bad : e.channel(), wrong == 3 ? bad : e.sender(), e.direction(),
                    wrong == 4 ? e.sequence() + 1 : e.sequence()), new Control(Type.HELLO, wrong == 5 ? SyntheticProtocol.PROFILE : LifecycleCoordinator.PROFILE));
            assertFalse(f.coordinator.sendFrame(Direction.S_H, corrupt)); f.quarantined();
            assertNull(f.model.inner()); assertTrue(f.model.host().alive);
        }
    }

    @Test void binaryVersionErrorTravelsThroughCodecAndPoisonsOnlyTheActiveAttempt() {
        try (Fixture f = new Fixture()) {
            f.host(); Frame frame = f.coordinator.frame(Direction.S_H, new Signal(Type.HELLO), LifecycleCoordinator.PROFILE);
            BufferBudget budget = new BufferBudget(1024); byte[] bytes = new byte[160]; int count;
            try (Encoder encoder = Encoder.open(frame, SyntheticProtocol.LIMITS, budget.account(1024))) {
                assertNotNull(encoder); count = encoder.read(bytes, 0, bytes.length); assertTrue(encoder.done());
            }
            bytes[5] = 9; MemoryIpcLink link = f.coordinator.link(Direction.S_H);
            for (int at = 0; at < count && !link.closed();) {
                int copied = link.offerRaw(bytes, at, count - at); assertTrue(copied > 0); at += copied; link.readStep(copied);
            }
            assertEquals(BinaryIpcCodec.Error.VERSION, link.error()); assertEquals(0, budget.used()); f.quarantined();
        }
    }

    @Test void anotherSupervisorSessionCannotAuthorizeSameGenerationTicketAndSequence() {
        try (Fixture f = new Fixture(); Fixture foreign = new Fixture(new Id(7, 2))) {
            f.host(); foreign.host();
            Frame foreignFrame = foreign.coordinator.frame(Direction.S_H, new Signal(Type.HELLO), LifecycleCoordinator.PROFILE);
            assertFalse(f.coordinator.sendFrame(Direction.S_H, foreignFrame)); f.quarantined();
            assertEquals(State.HANDSHAKE, foreign.model.state()); assertTrue(f.model.host().alive);
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void duplicateOrLateSuccessCannotRestoreAnActiveLifecycle(boolean late) {
        try (Fixture f = new Fixture()) {
            if (late) {
                f.running(); f.result(); f.coordinator.stop(f.ticket);
                assertFalse(f.coordinator.send(Direction.H_S, Type.RESULT_AVAILABLE));
            } else {
                f.host(); f.send(Direction.S_H, Type.HELLO);
                Frame ready = f.coordinator.frame(Direction.H_S, new Signal(Type.READY), LifecycleCoordinator.PROFILE);
                assertTrue(f.coordinator.sendFrame(Direction.H_S, ready));
                assertFalse(f.coordinator.sendFrame(Direction.H_S, ready));
            }
            f.quarantined();
        }
    }

    @Test void unexpectedSyntheticResultBytesNeverBecomeAnAcceptedResult() {
        try (Fixture f = new Fixture()) {
            f.running(); String bad = "synthetic-other"; long bytes = utf8Length(bad);
            f.send(Direction.W_H, new ResultBegin(bytes, 1, 1), "");
            assertFalse(f.coordinator.send(Direction.W_H, new ResultChunk(bytes), bad));
            assertFalse(f.coordinator.syntheticResultAccepted()); f.quarantined();
        }
    }

    @Test void newAdmissionRequiresFullPriorEndAndOldChannelCannotAffectNewTicket() {
        try (Fixture f = new Fixture()) {
            f.readyToFinish(); assertTrue(f.coordinator.request(Operation.SERIES).isEmpty());
            MemoryIpcLink oldLink = f.coordinator.link(Direction.W_H); Ticket old = f.ticket;
            assertTrue(f.finish()); f.ticket = f.coordinator.request(Operation.SERIES).orElseThrow();
            assertNotSame(old, f.ticket); assertEquals(State.PREPARING, f.model.state());
            assertFalse(f.coordinator.workerReturned(old, WORKER));
            assertEquals(0, oldLink.offerRaw(new byte[]{1}, 0, 1));
            assertFalse(f.model.delivered()); assertEquals(0, f.coordinator.reservedBytes());
        }
    }
}
