package de.mangalib.watchdogmodel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static de.mangalib.watchdogmodel.FakeJobKernel.*;
import static de.mangalib.watchdogmodel.WatchdogModel.*;
import static org.junit.jupiter.api.Assertions.*;

/** Kein Spring, keine Threads/Timer, kein Native-/Datei-/Netzwerkzugriff. */
class WatchdogModelTest {
    private static final Identity HOST = new Identity(100, 1, "synthetic-host");
    private static final Identity WORKER = new Identity(101, 2, "synthetic-worker");

    private static final class Fixture {
        final Clock clock = new Clock();
        final FakeJobKernel kernel = new FakeJobKernel();
        final FakeRecoveryJournal journal = new FakeRecoveryJournal(FakeRecoveryJournal.State.CLEAN);
        final WatchdogModel model = new WatchdogModel(clock, kernel, journal);
        Fixture() { assertTrue(model.initialize(journal.baseline())); }
        Ticket admit() { return model.admit(Operation.VOLUME).orElseThrow(); }
        Ticket host() {
            Ticket t = admit(); model.prepareOuter(t); model.beginHost(t);
            assertTrue(model.hostReturned(t, HOST)); return t;
        }
        Ticket ready() {
            Ticket t = host(); send(Direction.S_H, new Signal(Type.HELLO)); send(Direction.H_S, new Signal(Type.READY));
            model.prepareInner(t); send(Direction.S_H, new Signal(Type.START)); return t;
        }
        Ticket worker() {
            Ticket t = ready(); model.beginWorker(t); assertTrue(model.workerReturned(t, WORKER)); return t;
        }
        Ticket running() {
            Ticket t = worker(); send(Direction.H_W, new Signal(Type.START)); send(Direction.W_H, new Signal(Type.STARTED)); return t;
        }
        Message message(Direction d, Body body) { return new Message(model.ticket(), 1, model.nextSequence(d), 32, 32, body); }
        boolean receive(Direction d, Message message) {
            Channel c = model.channel(d); return model.accept(c, c.principal, message);
        }
        void send(Direction d, Body body) { assertTrue(receive(d, message(d, body)), () -> "rejected " + body); }
        void result() {
            send(Direction.W_H, new ResultBegin(16, 2, 2)); send(Direction.W_H, new ResultChunk(16));
            send(Direction.W_H, new Signal(Type.RESULT_END)); send(Direction.H_S, new Signal(Type.RESULT_AVAILABLE));
            assertFalse(model.delivered());
        }
        void innerEnd() {
            send(Direction.H_S, new GroupEnd(kernel.observe(Role.H, model.inner())));
            send(Direction.H_S, new Signal(Type.RESOURCES_CLOSED));
        }
        IoEnd io() { return new IoEnd(model.ticket(), true, true, true, true, true, true); }
        boolean finish() { return model.finish(model.ticket(), kernel.observe(Role.S, model.outer()), io()); }
        void cleanSuccessful() {
            result(); model.workerEnded(model.ticket(), 0); innerEnd(); model.hostEnded(model.ticket(), false);
            assertTrue(finish()); assertTrue(model.delivered());
        }
    }

    @Test void completeLifecyclePreservesOwnershipAndRequiresBothGroupsAndJournal() {
        Fixture f = new Fixture(); Ticket t = f.running();
        assertEquals(State.RUNNING, f.model.state());
        assertEquals(Role.S, f.model.outer().owner); assertEquals(Role.H, f.model.inner().owner);
        assertSame(f.model.outer().job, f.model.inner().job.parent);
        assertTrue(f.kernel.member(f.model.host(), f.model.outer().job));
        assertFalse(f.kernel.member(f.model.host(), f.model.inner().job));
        assertTrue(f.kernel.member(f.model.worker(), f.model.inner().job));
        assertTrue(f.kernel.member(f.model.worker(), f.model.outer().job));
        assertEquals(FakeRecoveryJournal.State.INTENT, f.journal.state());
        f.cleanSuccessful();
        assertTrue(f.kernel.closed(t)); assertEquals(FakeRecoveryJournal.State.COMPLETED, f.journal.state());
    }

    @Test void intentFailurePreventsEvenOuterCreationAndAdmissionHasNoQueue() {
        Fixture f = new Fixture(); f.journal.failIntent = true;
        assertTrue(f.model.admit(Operation.SERIES).isEmpty()); assertNull(f.model.outer());
        assertEquals(State.QUARANTINED, f.model.state());
        assertTrue(f.model.admit(Operation.VOLUME).isEmpty());
    }

    @Test void simultaneousSeriesAndVolumeUseSameSingleSlotAndNewTicketsNeverAlias() {
        Fixture f = new Fixture(); Ticket old = f.running();
        assertTrue(f.model.admit(Operation.SERIES).isEmpty()); f.cleanSuccessful();
        Ticket next = f.model.admit(Operation.SERIES).orElseThrow();
        assertNotSame(old, next); assertTrue(next.generation > old.generation);
        assertTrue(f.model.admit(Operation.VOLUME).isEmpty());
    }

    @ParameterizedTest @EnumSource(Role.class)
    void wrongHandleOwnerCannotQueryTerminateCloseOrDuplicate(Role caller) {
        Fixture f = new Fixture(); f.running();
        Handle handle = caller == Role.S ? f.model.inner() : f.model.outer();
        assertThrows(IllegalStateException.class, () -> f.kernel.observe(caller, handle));
        assertThrows(IllegalStateException.class, () -> f.kernel.terminate(caller, handle));
        assertThrows(IllegalStateException.class, () -> f.kernel.close(caller, handle));
        assertThrows(IllegalStateException.class, () -> f.kernel.duplicate(caller, handle, caller));
        assertEquals(0, handle.job.terminates); assertEquals(HandleState.OPEN, handle.state);
    }

    @Test void ownersCannotPassJobControlToOtherRoles() {
        Fixture f = new Fixture(); f.running();
        assertThrows(IllegalStateException.class, () -> f.kernel.duplicate(Role.S, f.model.outer(), Role.H));
        assertThrows(IllegalStateException.class, () -> f.kernel.duplicate(Role.S, f.model.outer(), Role.W));
        assertThrows(IllegalStateException.class, () -> f.kernel.duplicate(Role.H, f.model.inner(), Role.S));
        assertThrows(IllegalStateException.class, () -> f.kernel.duplicate(Role.H, f.model.inner(), Role.W));
    }

    @Test void localHandleCopiesPreventLastCloseAndFullResourceProof() {
        Fixture f = new Fixture(); f.running();
        Handle copy = f.kernel.duplicate(Role.H, f.model.inner(), Role.H);
        f.model.stop(f.model.ticket()); f.kernel.endGroup(f.model.inner());
        f.send(Direction.H_S, new GroupEnd(f.kernel.observe(Role.H, f.model.inner())));
        assertFalse(f.receive(Direction.H_S, f.message(Direction.H_S, new Signal(Type.RESOURCES_CLOSED))));
        assertFalse(copy.job.killOnCloseRequested); assertEquals(State.QUARANTINED, f.model.state());
        f.kernel.close(Role.H, copy); assertTrue(copy.job.killOnCloseRequested);
        assertFalse(f.finish());
    }

    @Test void noHandleCloseWhileStartIsOutstandingAndNoDoubleClose() {
        Fixture f = new Fixture(); Ticket t = f.ready(); f.model.beginWorker(t);
        assertThrows(IllegalStateException.class, () -> f.kernel.close(Role.H, f.model.inner()));
        f.kernel.failStart(f.model.pendingWorker());
        f.kernel.close(Role.H, f.model.inner());
        assertThrows(IllegalStateException.class, () -> f.kernel.close(Role.H, f.model.inner()));
    }

    @Test void hostMustBeRunningInKBeforeJAndWorkerMustFollowAuthenticatedStart() {
        Fixture f = new Fixture(); Ticket t = f.admit();
        assertThrows(IllegalStateException.class, () -> f.model.beginWorker(t));
        assertThrows(IllegalStateException.class, () -> f.model.prepareInner(t));
        assertThrows(IllegalStateException.class, () -> f.kernel.create(t, Role.H, Kind.J, null));
        f.model.prepareOuter(t); f.model.beginHost(t); f.model.hostReturned(t, HOST);
        assertThrows(IllegalStateException.class, () -> f.model.prepareInner(t));
        f.send(Direction.S_H, new Signal(Type.HELLO)); f.send(Direction.H_S, new Signal(Type.READY));
        f.model.prepareInner(t);
        assertThrows(IllegalStateException.class, () -> f.model.beginWorker(t));
        assertEquals(1, f.kernel.resumes);
    }

    @Test void workerResumeDoesNotMeanRunningUntilBoundWorkerHandshake() {
        Fixture f = new Fixture(); f.worker(); assertEquals(State.WORKER_STARTING, f.model.state());
        f.send(Direction.H_W, new Signal(Type.START));
        f.send(Direction.W_H, new Signal(Type.STARTED)); assertEquals(State.RUNNING, f.model.state());
    }

    @ParameterizedTest @ValueSource(ints = {0, 2})
    void wrongProtocolVersionPoisonsSession(int version) {
        Fixture f = new Fixture(); Ticket t = f.host();
        assertFalse(f.receive(Direction.S_H, new Message(t, version, 1, 32, 32, new Signal(Type.HELLO))));
        assertEquals(State.QUARANTINED, f.model.state());
        assertFalse(f.model.delivered());
    }

    @Test void correctTicketDoesNotAuthorizeForeignChannelOrWrongPrincipal() {
        Fixture first = new Fixture(); Ticket a = first.host();
        Fixture second = new Fixture(); second.host();
        assertEquals(a.generation, second.model.ticket().generation); // Nummerngleichheit reicht gerade nicht.
        Channel foreign = second.model.channel(Direction.S_H);
        assertFalse(first.model.accept(foreign, foreign.principal, new Message(a, 1, 1, 32, 32, new Signal(Type.HELLO))));
        assertEquals(State.QUARANTINED, first.model.state());
        assertEquals(State.HANDSHAKE, second.model.state());
        assertFalse(second.model.accept(second.model.channel(Direction.S_H), new Object(), second.message(Direction.S_H, new Signal(Type.HELLO))));
        assertEquals(State.QUARANTINED, second.model.state());
    }

    @Test void foreignTicketCannotStopOrEscalateCurrentGroup() {
        Fixture f = new Fixture(); f.running(); Fixture other = new Fixture(); Ticket foreign = other.running();
        assertFalse(f.model.stop(foreign)); assertFalse(f.model.escalate(foreign));
        assertFalse(f.receive(Direction.W_H, new Message(foreign, 1, 1, 32, 32, new Signal(Type.ERROR))));
        assertEquals(State.RUNNING, f.model.state());
        assertEquals(0, f.model.outer().job.terminates); assertEquals(0, f.model.inner().job.terminates);
    }

    @Test void replayAfterNewAdmissionCannotAffectNewTicket() {
        Fixture f = new Fixture(); Ticket old = f.running(); Channel oldChannel = f.model.channel(Direction.H_S);
        f.cleanSuccessful(); f.model.admit(Operation.SERIES).orElseThrow();
        assertFalse(f.model.accept(oldChannel, oldChannel.principal, new Message(old, 1, 99, 32, 32, new Signal(Type.ERROR))));
        assertEquals(State.PREPARING, f.model.state()); assertNull(f.model.outer());
    }

    @Test void duplicateAndSkippedSequenceAreRejected() {
        Fixture f = new Fixture(); Ticket t = f.host(); Message hello = f.message(Direction.S_H, new Signal(Type.HELLO));
        assertTrue(f.receive(Direction.S_H, hello)); assertFalse(f.receive(Direction.S_H, hello));
        assertEquals(State.QUARANTINED, f.model.state());
        Fixture g = new Fixture(); Ticket u = g.host();
        assertFalse(g.receive(Direction.S_H, new Message(u, 1, 2, 32, 32, new Signal(Type.HELLO))));
        assertEquals(State.QUARANTINED, g.model.state());
        assertSame(t, hello.ticket());
    }

    @ParameterizedTest @EnumSource(value = Type.class, names = {"READY", "START", "STARTED", "RESULT_END", "RESOURCES_CLOSED"})
    void reorderedOrWrongDirectionMessageCannotAdvanceHandshake(Type type) {
        Fixture f = new Fixture(); f.host();
        assertFalse(f.receive(Direction.S_H, f.message(Direction.S_H, new Signal(type))));
        assertEquals(State.QUARANTINED, f.model.state()); assertNull(f.model.inner());
    }

    @Test void unknownTypedPayloadOrTypeIsNotAccepted() {
        Fixture f = new Fixture(); Ticket t = f.host();
        assertFalse(f.receive(Direction.S_H, new Message(t, 1, 1, 32, 32, null)));
        Fixture g = new Fixture(); g.host();
        assertFalse(g.receive(Direction.S_H, g.message(Direction.S_H, new Signal(null))));
    }

    @ParameterizedTest @ValueSource(longs = {0, -1, 4097, Long.MAX_VALUE})
    void controlLengthCheckedBeforeAnyPayloadAction(long bytes) {
        Fixture f = new Fixture(); Ticket t = f.host();
        assertFalse(f.receive(Direction.S_H, new Message(t, 1, 1, bytes, bytes, new Signal(Type.HELLO))));
        assertEquals(State.QUARANTINED, f.model.state()); assertNull(f.model.inner());
    }

    @Test void partialFrameAndUnexpectedEofAreTerminalProtocolFailures() {
        Fixture f = new Fixture(); Ticket t = f.host();
        assertFalse(f.receive(Direction.S_H, new Message(t, 1, 1, 32, 31, new Signal(Type.HELLO))));
        Fixture g = new Fixture(); g.running(); assertFalse(g.model.eof(g.model.channel(Direction.W_H)));
        assertEquals(State.QUARANTINED, g.model.state()); assertEquals(1, g.model.inner().job.terminates);
    }

    @Test void resultRemainsInternalUntilGroupsResourcesAndJournalAreComplete() {
        Fixture f = new Fixture(); Ticket t = f.running(); f.result();
        assertEquals(16, f.model.resultBytes()); assertFalse(f.model.delivered());
        assertFalse(f.finish()); f.model.workerEnded(t, 0); f.innerEnd();
        assertFalse(f.finish()); f.model.hostEnded(t, false);
        assertTrue(f.finish()); assertTrue(f.model.delivered());
    }

    @ParameterizedTest @ValueSource(longs = {-1, 0, RESULT_CANDIDATE + 1, Long.MAX_VALUE})
    void resultCandidateRejectsWholeFrameWithoutTruncation(long size) {
        Fixture f = new Fixture(); f.running();
        assertFalse(f.receive(Direction.W_H, f.message(Direction.W_H, new ResultBegin(size, 2, 2))));
        assertEquals(0, f.model.resultBytes()); assertFalse(f.model.delivered());
    }

    @Test void candidateMaximumIsCountedWithoutAllocatingAndDoesNotProveMapCompatibility() {
        Fixture f = new Fixture(); f.running();
        f.send(Direction.W_H, new ResultBegin(RESULT_CANDIDATE, FIELDS, DEPTH));
        assertEquals(RESULT_CANDIDATE, f.model.resultBytes());
        for (int i = 0; i < RESULT_CANDIDATE / CHUNK_BYTES; i++) {
            assertTrue(f.receive(Direction.W_H, new Message(f.model.ticket(), 1, f.model.nextSequence(Direction.W_H),
                    CHUNK_BYTES, CHUNK_BYTES, new ResultChunk(CHUNK_BYTES))));
        }
        f.send(Direction.W_H, new Signal(Type.RESULT_END)); assertFalse(f.model.delivered());
    }

    @Test void resultChunkOverflowSecondHeaderAndWrongSenderAreRejected() {
        Fixture f = new Fixture(); f.running(); f.send(Direction.W_H, new ResultBegin(8, 2, 2));
        assertFalse(f.receive(Direction.W_H, f.message(Direction.W_H, new ResultChunk(9))));
        Fixture g = new Fixture(); g.running(); g.send(Direction.W_H, new ResultBegin(8, 2, 2));
        assertFalse(g.receive(Direction.W_H, g.message(Direction.W_H, new ResultBegin(8, 2, 2))));
        Fixture h = new Fixture(); h.running();
        assertFalse(h.receive(Direction.H_S, h.message(Direction.H_S, new ResultBegin(8, 2, 2))));
    }

    @Test void resultCannotFinishEarlyOrArriveAfterCleanup() {
        Fixture f = new Fixture(); f.running(); f.send(Direction.W_H, new ResultBegin(8, 2, 2));
        assertFalse(f.receive(Direction.W_H, f.message(Direction.W_H, new Signal(Type.RESULT_END))));
        Fixture g = new Fixture(); Ticket t = g.running(); g.model.stop(t);
        assertFalse(g.receive(Direction.W_H, g.message(Direction.W_H, new ResultBegin(8, 2, 2))));
        assertFalse(g.model.delivered());
    }

    @Test void workerEndBeforeResultIsFailureButCanReturnIdleAfterFullCleanup() {
        Fixture f = new Fixture(); Ticket t = f.running(); f.model.workerEnded(t, 0);
        assertEquals(State.CLEANING, f.model.state()); f.innerEnd(); f.model.hostEnded(t, false);
        assertTrue(f.finish()); assertFalse(f.model.delivered());
    }

    @Test void workerCrashNeverDeliversAnEarlierResult() {
        Fixture f = new Fixture(); Ticket t = f.running(); f.result(); f.model.workerEnded(t, 1);
        f.innerEnd(); f.model.hostEnded(t, false); assertTrue(f.finish()); assertFalse(f.model.delivered());
    }

    @Test void dynamicDescendantsPreventCompletionAfterRootExit() {
        Fixture f = new Fixture(); Ticket t = f.running();
        Instance child = f.kernel.descendant(f.model.worker(), new Identity(102, 3, "synthetic-child"));
        assertTrue(f.kernel.member(child, f.model.outer().job));
        f.result(); f.model.workerEnded(t, 0);
        Observation stillActive = f.kernel.observe(Role.H, f.model.inner());
        assertEquals(1, stillActive.active()); assertEquals(1, stillActive.accounting());
        assertEquals(child.identity, stillActive.members().getFirst());
        assertFalse(f.kernel.provesEmpty(Role.H, f.model.inner(), stillActive));
        f.kernel.end(child); f.innerEnd(); f.model.hostEnded(t, false); assertTrue(f.finish());
    }

    @Test void identityRequiresHeldInstanceBirthAndImageNotJustPid() {
        Fixture f = new Fixture(); f.running();
        assertFalse(f.kernel.matches(f.model.worker(), new Identity(WORKER.pid(), 88, WORKER.image())));
        assertFalse(f.kernel.matches(f.model.worker(), new Identity(WORKER.pid(), WORKER.birth(), "other")));
        f.model.stop(f.model.ticket()); f.kernel.endGroup(f.model.inner()); f.model.worker().identified = false;
        assertFalse(f.kernel.provesEmpty(Role.H, f.model.inner(), f.kernel.observe(Role.H, f.model.inner())));
    }

    @Test void terminateReturnIsNotEndAndEscalationTouchesOnlyOwnedK() {
        Fixture f = new Fixture(); Ticket t = f.running(); Fixture foreign = new Fixture(); foreign.running();
        f.model.stop(t); assertTrue(f.model.worker().alive); assertEquals(1, f.model.inner().job.terminates);
        assertTrue(f.model.escalate(t)); assertTrue(f.model.host().alive); assertTrue(f.model.worker().alive);
        assertFalse(f.finish()); assertEquals(1, f.model.outer().job.terminates);
        assertEquals(0, foreign.model.outer().job.terminates); assertTrue(foreign.model.worker().alive);
    }

    @Test void hostCrashTriggersQuarantineAndKillOnCloseRequestNotAssumedWorkerEnd() {
        Fixture f = new Fixture(); Ticket t = f.running(); f.model.hostEnded(t, true);
        assertFalse(f.model.host().alive); assertTrue(f.model.worker().alive);
        assertTrue(f.model.inner().job.killOnCloseRequested); assertEquals(State.QUARANTINED, f.model.state());
        assertTrue(f.model.escalate(t)); f.kernel.endGroup(f.model.outer());
        assertFalse(f.finish()); assertTrue(f.model.admit(Operation.VOLUME).isEmpty());
    }

    @Test void blockedHostAllowsOuterRequestWithoutInnerResponseOrNewBudget() {
        Fixture f = new Fixture(); Ticket t = f.running(); f.model.blockHost(t); f.model.stop(t); long deadline = f.model.cleanupDeadline();
        f.clock.advance(7); assertTrue(f.model.escalate(t)); assertEquals(deadline, f.model.cleanupDeadline());
        assertEquals(0, f.model.inner().job.terminates);
        assertEquals(State.CLEANING, f.model.state()); assertFalse(f.finish());
        f.clock.advance(8); f.model.tick(); assertEquals(State.QUARANTINED, f.model.state());
        assertTrue(f.model.host().alive); assertTrue(f.model.worker().alive);
        assertFalse(f.model.escalate(t)); assertEquals(1, f.model.outer().job.terminates);
    }

    @Test void supervisorCrashAndRestartDoNotPretendCleanupWasGuaranteed() {
        Fixture f = new Fixture(); Ticket t = f.running(); f.model.supervisorFailed(t);
        assertEquals(0, f.model.inner().job.terminates); // Kein nachträglich erfundener geordneter Stop.
        assertTrue(f.model.outer().job.killOnCloseRequested); f.clock.advance(1000); f.model.tick();
        assertTrue(f.model.worker().alive); assertTrue(f.model.host().alive);
        assertFalse(f.model.escalate(t)); assertEquals(State.QUARANTINED, f.model.state());
        WatchdogModel restart = new WatchdogModel(f.clock, f.kernel, f.journal);
        assertFalse(restart.initialize(f.journal.baseline())); assertTrue(restart.admit(Operation.VOLUME).isEmpty());
    }

    @Test void lateHostStartAfterCleanupCannotResumeOrOpenChannels() {
        Fixture f = new Fixture(); Ticket t = f.admit(); f.model.prepareOuter(t); f.model.beginHost(t);
        f.model.stop(t); assertTrue(f.model.escalate(t));
        assertFalse(f.model.hostReturned(t, HOST)); assertEquals(0, f.kernel.resumes);
        assertTrue(f.model.host().suspended); assertNull(f.model.channel(Direction.S_H));
        assertFalse(f.finish());
    }

    @Test void lateWorkerAfterEarlierTerminationRemainsOwnedAndInvalidatesEmptyObservation() {
        Fixture f = new Fixture(); Ticket t = f.ready(); f.model.beginWorker(t); f.model.stop(t);
        f.kernel.endGroup(f.model.inner()); Observation oldEmpty = f.kernel.observe(Role.H, f.model.inner());
        assertFalse(f.kernel.provesEmpty(Role.H, f.model.inner(), oldEmpty)); // ausstehendes Create
        assertFalse(f.model.workerReturned(t, WORKER)); assertTrue(f.model.worker().suspended);
        assertEquals(1, f.kernel.resumes); assertFalse(f.kernel.provesEmpty(Role.H, f.model.inner(), oldEmpty));
        assertEquals(1, f.model.inner().job.terminates); assertTrue(f.model.escalate(t));
        assertTrue(f.model.worker().alive); assertFalse(f.model.delivered());
    }

    @ParameterizedTest @EnumSource(value = FakeRecoveryJournal.State.class, names = {"UNKNOWN", "CORRUPT", "STALE", "INTENT", "UNCERTAIN", "COMPLETED"})
    void unknownOrDirtyJournalCannotInitializeEvenNewModel(FakeRecoveryJournal.State state) {
        FakeRecoveryJournal journal = new FakeRecoveryJournal(state);
        WatchdogModel model = new WatchdogModel(new Clock(), new FakeJobKernel(), journal);
        assertFalse(model.initialize(journal.baseline())); assertEquals(State.QUARANTINED, model.state());
        assertTrue(model.admit(Operation.VOLUME).isEmpty()); assertFalse(model.initialize(journal.baseline()));
    }

    @Test void baselineFromForeignStoreAndConcurrentWriterAreRejected() {
        FakeRecoveryJournal first = new FakeRecoveryJournal(FakeRecoveryJournal.State.CLEAN);
        FakeRecoveryJournal second = new FakeRecoveryJournal(FakeRecoveryJournal.State.CLEAN);
        WatchdogModel model = new WatchdogModel(new Clock(), new FakeJobKernel(), first);
        assertFalse(model.initialize(second.baseline()));
        Fixture f = new Fixture(); WatchdogModel competing = new WatchdogModel(f.clock, f.kernel, f.journal);
        assertFalse(competing.initialize(f.journal.baseline())); assertEquals(State.IDLE, f.model.state());
    }

    @Test void journalCannotCompleteForeignTicketGroupOrOldRevision() {
        FakeRecoveryJournal journal = new FakeRecoveryJournal(FakeRecoveryJournal.State.CLEAN); Object owner = new Object();
        assertTrue(journal.claim(owner, journal.baseline()));
        Fixture a = new Fixture(); Ticket ta = a.admit(); Fixture b = new Fixture(); Ticket tb = b.admit();
        assertTrue(journal.intent(owner, ta)); Object k = new Object(), j = new Object(); journal.bind(owner, ta, k, j);
        FakeRecoveryJournal.End valid = journal.endCandidate(ta, k, j);
        assertFalse(journal.complete(owner, journal.endCandidate(tb, k, j)));
        assertFalse(journal.complete(owner, journal.endCandidate(ta, new Object(), j)));
        assertFalse(journal.complete(new Object(), valid)); assertTrue(journal.complete(owner, valid));
        assertFalse(journal.complete(owner, valid));
        assertTrue(journal.intent(owner, tb)); assertFalse(journal.complete(owner, valid));
    }

    @Test void journalCompletionFailureRetainsQuarantineEvenWithEmptyClosedGroups() {
        Fixture f = new Fixture(); Ticket t = f.running(); f.result(); f.model.workerEnded(t, 0);
        f.innerEnd(); f.model.hostEnded(t, false); f.journal.failCompletion = true;
        assertFalse(f.finish()); assertTrue(f.kernel.closed(t)); assertEquals(State.QUARANTINED, f.model.state());
        assertFalse(f.model.delivered()); assertTrue(f.model.admit(Operation.SERIES).isEmpty());
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3, 4, 5})
    void everyIoOrResourceComponentIsRequired(int missing) {
        Fixture f = new Fixture(); Ticket t = f.running(); f.result(); f.model.workerEnded(t, 0); f.innerEnd(); f.model.hostEnded(t, false);
        IoEnd partial = new IoEnd(t, missing != 0, missing != 1, missing != 2, missing != 3, missing != 4, missing != 5);
        assertFalse(f.model.finish(t, f.kernel.observe(Role.S, f.model.outer()), partial));
        assertEquals(HandleState.OPEN, f.model.outer().state); assertFalse(f.model.delivered());
        f.clock.advance(15); f.model.tick(); assertEquals(State.QUARANTINED, f.model.state());
    }

    @Test void outerEvidenceMustMatchGroupRevisionCompletenessAccountingAndActualPopulation() {
        Fixture f = new Fixture(); Ticket t = f.running(); f.model.stop(t);
        Observation live = f.kernel.observe(Role.S, f.model.outer());
        Observation forged = new Observation(live.job(), live.revision(), true, true, 0, java.util.List.of(), 0);
        assertFalse(f.kernel.provesEmpty(Role.S, f.model.outer(), forged));
        f.kernel.endGroup(f.model.inner()); f.innerEnd(); f.model.hostEnded(t, false);
        Observation good = f.kernel.observe(Role.S, f.model.outer());
        assertFalse(f.model.finish(t, live, f.io()));
        assertFalse(f.model.finish(t, new Observation(good.job(), good.revision(), false, true, 0, java.util.List.of(), 0), f.io()));
        assertFalse(f.model.finish(t, new Observation(good.job(), good.revision(), true, false, 0, java.util.List.of(), 0), f.io()));
        assertFalse(f.model.finish(t, new Observation(good.job(), good.revision(), true, true, 0, java.util.List.of(), 1), f.io()));
        Fixture other = new Fixture(); other.running();
        assertFalse(f.model.finish(t, other.kernel.observe(Role.S, other.model.outer()), f.io()));
        assertFalse(f.model.finish(t, good, new IoEnd(other.model.ticket(), true, true, true, true, true, true)));
        assertTrue(f.finish());
    }

    @Test void unknownCloseResultCannotBeRetriedOrCountedAsClosed() {
        Fixture f = new Fixture(); Ticket t = f.running(); f.model.stop(t); f.kernel.endGroup(f.model.inner());
        f.send(Direction.H_S, new GroupEnd(f.kernel.observe(Role.H, f.model.inner()))); f.kernel.failClose = true;
        assertFalse(f.receive(Direction.H_S, f.message(Direction.H_S, new Signal(Type.RESOURCES_CLOSED))));
        assertEquals(HandleState.UNCERTAIN, f.model.inner().state); assertEquals(State.QUARANTINED, f.model.state());
        f.kernel.failClose = false;
        assertThrows(IllegalStateException.class, () -> f.kernel.close(Role.H, f.model.inner()));
        assertFalse(f.kernel.closed(t));
    }

    @Test void duplicateCompletionAfterSuccessCannotReopenOrDoubleCloseResources() {
        Fixture f = new Fixture(); Ticket t = f.running(); Channel channel = f.model.channel(Direction.H_S);
        f.cleanSuccessful();
        assertFalse(f.model.accept(channel, channel.principal, new Message(t, 1, 99, 32, 32, new Signal(Type.RESOURCES_CLOSED))));
        assertFalse(f.model.finish(t, null, f.io())); assertEquals(State.IDLE, f.model.state());
    }

    @Test void startDeadlineIncludesPreparationAndHandshakeAndUsesExactBoundary() {
        Fixture f = new Fixture(); Ticket t = f.admit(); assertEquals(30, f.model.startDeadline());
        f.clock.advance(29); f.model.tick(); assertEquals(State.PREPARING, f.model.state());
        f.clock.advance(1); f.model.tick(); assertEquals(State.CLEANING, f.model.state());
        assertEquals(45, f.model.cleanupDeadline()); assertThrows(IllegalStateException.class, () -> f.model.prepareOuter(t));
    }

    @Test void hostHandshakeTimeoutCreatesNoWorkerAndLeaseDoesNotRenewStartDeadline() {
        Fixture f = new Fixture(); f.host();
        for (int i = 0; i < 7; i++) { f.clock.advance(4); f.send(Direction.S_H, new Signal(Type.LEASE)); }
        f.clock.advance(2); f.model.tick(); assertEquals(State.CLEANING, f.model.state());
        assertNull(f.model.inner()); assertEquals(30, f.model.startDeadline());
    }

    @Test void hostLeaseLossAndExplicitHandshakeAbortNeverStartWorker() {
        Fixture f = new Fixture(); f.host(); f.clock.advance(5); f.model.tick();
        assertEquals(State.CLEANING, f.model.state()); assertNull(f.model.worker());
        Fixture g = new Fixture(); Ticket t = g.host(); g.send(Direction.S_H, new Signal(Type.STOP));
        assertEquals(State.CLEANING, g.model.state()); assertFalse(g.model.workerReturned(t, WORKER));
    }

    @ParameterizedTest @EnumSource(Operation.class)
    void operationDeadlineIncludesStartupAndStatusCannotExtendIt(Operation operation) {
        Fixture f = new Fixture(); Ticket t = f.model.admit(operation).orElseThrow();
        f.model.prepareOuter(t); f.model.beginHost(t); f.clock.advance(2); f.model.hostReturned(t, HOST);
        f.send(Direction.S_H, new Signal(Type.HELLO)); f.send(Direction.H_S, new Signal(Type.READY));
        f.model.prepareInner(t); f.send(Direction.S_H, new Signal(Type.START)); f.model.beginWorker(t);
        f.model.workerReturned(t, WORKER); f.send(Direction.H_W, new Signal(Type.START)); f.send(Direction.W_H, new Signal(Type.STARTED));
        long deadline = operation == Operation.VOLUME ? 120 : 3600;
        while (f.clock.now() < deadline - 2) { f.clock.advance(4); f.send(Direction.S_H, new Signal(Type.LEASE)); f.send(Direction.H_S, new Signal(Type.STATUS)); }
        assertEquals(State.RUNNING, f.model.state());
        f.clock.advance(2); f.model.tick(); assertEquals(State.CLEANING, f.model.state());
        assertEquals(deadline, f.model.operationDeadline()); assertEquals(deadline + 15, f.model.cleanupDeadline());
    }

    @Test void cleanupAtFifteenSecondsCannotBeRenewedByStopOrLateEvidence() {
        Fixture f = new Fixture(); Ticket t = f.running(); f.model.stop(t); long deadline = f.model.cleanupDeadline();
        f.clock.advance(14); f.model.stop(t); assertEquals(deadline, f.model.cleanupDeadline());
        assertEquals(1, f.model.inner().job.terminates); f.clock.advance(1); f.model.tick();
        assertEquals(State.QUARANTINED, f.model.state()); f.kernel.endGroup(f.model.outer());
        assertFalse(f.finish()); assertFalse(f.model.admit(Operation.SERIES).isPresent());
    }

    @Test void clockRejectsBackwardsAndOverflowWithoutRealWaiting() {
        Clock clock = new Clock(); assertThrows(IllegalArgumentException.class, () -> clock.advance(-1));
        clock.advance(Long.MAX_VALUE); assertThrows(ArithmeticException.class, () -> clock.advance(1));
        FakeRecoveryJournal journal = new FakeRecoveryJournal(FakeRecoveryJournal.State.CLEAN);
        WatchdogModel model = new WatchdogModel(clock, new FakeJobKernel(), journal); assertTrue(model.initialize(journal.baseline()));
        assertThrows(ArithmeticException.class, () -> model.admit(Operation.VOLUME));
        assertEquals(State.IDLE, model.state()); assertEquals(FakeRecoveryJournal.State.CLEAN, journal.state());
    }

    @Test void failedHostStartCanReleaseOnlyAfterExplicitNativeReturnAndFullEmptyProof() {
        Fixture f = new Fixture(); Ticket t = f.admit(); f.model.prepareOuter(t); f.model.beginHost(t);
        f.model.stop(t); assertFalse(f.finish());
        assertTrue(f.model.startFailed(t, Role.H)); assertFalse(f.model.startFailed(t, Role.H));
        assertTrue(f.finish()); assertFalse(f.model.delivered()); assertEquals(0, f.kernel.resumes);
    }

    @Test void failedWorkerStartCanReleaseWithoutPretendingAnInstanceExisted() {
        Fixture f = new Fixture(); Ticket t = f.ready(); f.model.beginWorker(t);
        assertTrue(f.model.startFailed(t, Role.W)); assertNull(f.model.worker());
        f.innerEnd(); f.model.hostEnded(t, false); assertTrue(f.finish()); assertFalse(f.model.delivered());
    }

    @Test void unknownHandleCopyCannotBecomeAClaimOfLastClose() {
        Fixture f = new Fixture(); f.running(); Handle copy = f.kernel.duplicate(Role.H, f.model.inner(), Role.H);
        f.kernel.failClose = true;
        assertThrows(IllegalStateException.class, () -> f.kernel.close(Role.H, copy));
        f.kernel.failClose = false; f.kernel.close(Role.H, f.model.inner());
        assertFalse(copy.job.killOnCloseRequested); assertFalse(f.kernel.closed(copy.job));
    }

    @Test void outerCloseFailureCannotCompleteTheJournalOrBeRetried() {
        Fixture f = new Fixture(); Ticket t = f.running(); f.result(); f.model.workerEnded(t, 0);
        f.innerEnd(); f.model.hostEnded(t, false); f.kernel.failClose = true;
        assertFalse(f.finish()); assertEquals(HandleState.UNCERTAIN, f.model.outer().state);
        assertEquals(FakeRecoveryJournal.State.UNCERTAIN, f.journal.state()); assertFalse(f.model.delivered());
        f.kernel.failClose = false;
        assertThrows(IllegalStateException.class, () -> f.kernel.observe(Role.S, f.model.outer()));
        assertFalse(f.model.finish(t, null, f.io()));
        assertThrows(IllegalStateException.class, () -> f.kernel.close(Role.S, f.model.outer()));
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void correctChannelDoesNotAuthorizeEndedOrUnidentifiedSender(boolean ended) {
        Fixture f = new Fixture(); f.running();
        if (ended) f.kernel.end(f.model.worker()); else f.model.worker().identified = false;
        assertFalse(f.receive(Direction.W_H, f.message(Direction.W_H, new Signal(Type.STATUS))));
        assertEquals(State.QUARANTINED, f.model.state());
    }

    @Test void errorAndStopAcceptedAreNotGroupEndAndStopIsIdempotent() {
        Fixture f = new Fixture(); Ticket t = f.running(); f.send(Direction.W_H, new Signal(Type.ERROR));
        long cleanup = f.model.cleanupDeadline(); f.send(Direction.H_S, new Signal(Type.STOP_ACCEPTED));
        f.send(Direction.S_H, new Signal(Type.STOP));
        assertEquals(cleanup, f.model.cleanupDeadline()); assertEquals(1, f.model.inner().job.terminates);
        assertTrue(f.model.worker().alive); assertFalse(f.finish());
        f.kernel.endGroup(f.model.inner()); f.innerEnd(); f.model.hostEnded(t, false);
        assertTrue(f.finish()); assertFalse(f.model.delivered());
    }

    @Test void groupEvidenceFromWorkerIsNeverAControlPrivilege() {
        Fixture f = new Fixture(); f.running(); f.model.stop(f.model.ticket());
        assertFalse(f.receive(Direction.W_H, f.message(Direction.W_H,
                new GroupEnd(f.kernel.observe(Role.H, f.model.inner())))));
        assertEquals(State.QUARANTINED, f.model.state()); assertEquals(HandleState.OPEN, f.model.inner().state);
    }

    @Test void workerStartReturnedAfterStartupDeadlineNeverResumes() {
        Fixture f = new Fixture(); Ticket t = f.ready(); f.model.beginWorker(t); f.clock.advance(30);
        assertFalse(f.model.workerReturned(t, WORKER));
        assertTrue(f.model.worker().suspended); assertEquals(1, f.kernel.resumes);
        assertEquals(45, f.model.cleanupDeadline()); assertEquals(State.CLEANING, f.model.state());
    }

    @Test void workerCannotBeCreatedUntilKAndJLimitsAreConfigured() {
        Fixture f = new Fixture(); Ticket t = f.admit();
        Handle unconfigured = f.kernel.create(t, Role.S, Kind.K, null);
        assertThrows(IllegalStateException.class, () -> f.kernel.begin(Role.S, unconfigured, Role.H, null));
        Fixture g = new Fixture(); Ticket u = g.ready(); g.model.inner().job.configured = false;
        assertThrows(IllegalStateException.class, () -> g.model.beginWorker(u)); assertNull(g.model.worker());
    }

    @Test void fieldAndDepthBudgetsRejectOversizeEnvelopeWithoutAnyResult() {
        Fixture f = new Fixture(); f.running();
        assertFalse(f.receive(Direction.W_H, f.message(Direction.W_H, new ResultBegin(8, FIELDS + 1, 2))));
        Fixture g = new Fixture(); g.running();
        assertFalse(g.receive(Direction.W_H, g.message(Direction.W_H, new ResultBegin(8, 2, DEPTH + 1))));
        assertEquals(0, f.model.resultBytes()); assertEquals(0, g.model.resultBytes());
    }

    @Test void transportChunkAndPhysicalLengthMustAgreeBeforeAccounting() {
        Fixture f = new Fixture(); f.running(); f.send(Direction.W_H, new ResultBegin(100, 2, 2));
        assertFalse(f.receive(Direction.W_H, f.message(Direction.W_H, new ResultChunk(33))));
        Fixture g = new Fixture(); g.running(); g.send(Direction.W_H, new ResultBegin(RESULT_CANDIDATE, 2, 2));
        assertFalse(g.receive(Direction.W_H, new Message(g.model.ticket(), 1, g.model.nextSequence(Direction.W_H),
                CHUNK_BYTES + 1, CHUNK_BYTES + 1, new ResultChunk(CHUNK_BYTES + 1))));
    }

    @Test void lateConfirmedResourceRetirementDoesNotHealCrashQuarantineOrJournal() {
        Fixture f = new Fixture(); Ticket t = f.running(); f.model.hostEnded(t, true);
        assertFalse(f.model.retireAfterIncident(t, f.kernel.observe(Role.S, f.model.outer()), f.io()));
        f.kernel.endGroup(f.model.outer());
        Observation ended = f.kernel.observe(Role.S, f.model.outer());
        assertTrue(f.model.retireAfterIncident(t, ended, f.io()));
        assertFalse(f.model.retireAfterIncident(t, ended, f.io()));
        assertTrue(f.kernel.closed(t)); assertEquals(State.QUARANTINED, f.model.state());
        assertEquals(FakeRecoveryJournal.State.UNCERTAIN, f.journal.state());
        assertTrue(f.model.admit(Operation.SERIES).isEmpty()); assertFalse(f.model.delivered());
        assertFalse(f.model.escalate(t));
    }

    @Test void escalationCannotAffectAnotherOwnedGroupInTheSameFakeKernel() {
        Fixture f = new Fixture(); Ticket t = f.running();
        FakeRecoveryJournal otherJournal = new FakeRecoveryJournal(FakeRecoveryJournal.State.CLEAN);
        WatchdogModel other = new WatchdogModel(f.clock, f.kernel, otherJournal);
        assertTrue(other.initialize(otherJournal.baseline())); Ticket foreign = other.admit(Operation.SERIES).orElseThrow();
        other.prepareOuter(foreign); other.beginHost(foreign);
        other.hostReturned(foreign, new Identity(500, 50, "synthetic-other-host"));
        f.model.stop(t); f.model.escalate(t); f.kernel.endGroup(f.model.outer());
        assertTrue(other.host().alive); assertEquals(0, other.outer().job.terminates);
        assertEquals(1, f.kernel.observe(Role.S, other.outer()).active());
        assertEquals(FakeRecoveryJournal.State.INTENT, otherJournal.state());
    }
}
