package de.mangalib.scrapermodel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumMap;
import java.util.concurrent.atomic.AtomicLong;

import static de.mangalib.scrapermodel.SupervisorModel.*;
import static org.junit.jupiter.api.Assertions.*;

/** Deterministische Ereignisfolgen; keine Threads, Prozesse, Spring- oder OS-Aufrufe. */
class SupervisorModelTest {
    enum FailedStage { BEFORE_NATIVE_CALL, NATIVE_CALL, RESUME }
    private final AtomicLong clock = new AtomicLong(100);
    private final SupervisorModel model = new SupervisorModel(clock::get, new Budgets(10, 100, 30, 5));

    @org.junit.jupiter.api.BeforeEach
    void confirmedSyntheticStartup() {
        assertTrue(model.initialize(Proof.CONFIRMED));
    }

    @Test
    void newInstanceAloneIsNotRecoveryEvidence() {
        var fresh = new SupervisorModel(clock::get, new Budgets(10, 100, 30, 5));
        assertEquals(State.UNAVAILABLE, fresh.state());
        assertTrue(fresh.admit(Operation.SERIES).isEmpty());
        assertFalse(fresh.initialize(Proof.UNKNOWN));
        assertEquals(State.QUARANTINED, fresh.state());
        assertFalse(fresh.initialize(Proof.CONFIRMED));
        fresh.shutdown();
        assertEquals(State.QUARANTINED, fresh.state());
        assertEquals(0, fresh.startRequests());
    }

    @Test
    void failedStartupEvidenceAlsoQuarantines() {
        var fresh = new SupervisorModel(clock::get, new Budgets(10, 100, 30, 5));
        assertFalse(fresh.initialize(Proof.FAILED));
        assertTrue(fresh.admit(Operation.VOLUME).isEmpty());
        assertEquals(State.QUARANTINED, fresh.state());
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void admitsExactlyOneSharedSlotAndNeverQueues(Operation operation) {
        var ticket = model.admit(operation).orElseThrow();
        assertEquals(operation, ticket.operation());
        assertEquals(State.STARTING, model.state());
        for (Operation second : Operation.values()) assertTrue(model.admit(second).isEmpty());
        assertEquals(1, model.startRequests());
        assertTrue(model.startReturned(ticket, true));
        assertEquals(State.RUNNING, model.state());
        assertTrue(model.admit(Operation.VOLUME).isEmpty());
    }

    @Test
    void failureBeforeProcessCreationStillNeedsFullResourceEvidence() {
        var ticket = model.admit(Operation.SERIES).orElseThrow();
        assertTrue(model.startReturned(ticket, false));
        assertEquals(State.VERIFYING, model.state());
        assertFalse(model.verify(ticket, new Evidence(1, java.util.Map.of())));
        assertTrue(model.admit(Operation.VOLUME).isEmpty());
        assertTrue(model.verify(ticket, completeEvidence(2)));
        assertEquals(State.IDLE, model.state());
        assertEquals(1, model.startRequests());
        assertEquals(1, model.releases());
    }

    @ParameterizedTest
    @EnumSource(FailedStage.class)
    void startFailuresNeverRetryAndResourcesRemainOwnedUntilEndProof(FailedStage stage) {
        var ticket = model.admit(Operation.SERIES).orElseThrow();
        // Deterministischer Adapter-Fake: unterschiedliche Fehlerstellen liefern
        // denselben abgeschlossenen Start mit successful=false; Ressourcen bleiben offen.
        assertTrue(model.startReturned(ticket, false), stage.name());
        var pending = new EnumMap<>(completeEvidence(1).checks());
        pending.put(Check.NATIVE_RESOURCES_CLOSED, Proof.UNKNOWN);
        if (stage != FailedStage.BEFORE_NATIVE_CALL) {
            pending.put(Check.RETAINED_INSTANCES_ENDED, Proof.UNKNOWN);
        }
        assertFalse(model.verify(ticket, new Evidence(1, pending)));
        assertTrue(model.admit(Operation.VOLUME).isEmpty());
        assertTrue(model.verify(ticket, completeEvidence(2)));
        assertEquals(1, model.startRequests());
        assertEquals(1, model.stopRequests());
        assertEquals(1, model.releases());
    }

    @Test
    void unconfirmedMembershipDuringStartIsAStickySecurityIncident() {
        var ticket = model.admit(Operation.VOLUME).orElseThrow();
        model.identityUncertain(ticket);
        assertTrue(model.startOutstanding());
        assertFalse(model.verify(ticket, completeEvidence(1)));
        model.startReturned(ticket, false);
        assertFalse(model.verify(ticket, completeEvidence(2)));
        assertEquals(State.QUARANTINED, model.state());
        assertEquals(0, model.releases());
    }

    @Test
    void startTimeoutCannotCancelPendingNativeReturnOrReleaseItsResources() {
        var ticket = model.admit(Operation.VOLUME).orElseThrow();
        clock.set(110);
        model.tick();
        assertEquals(State.STOPPING, model.state());
        assertTrue(model.startOutstanding());
        model.verificationStarted(ticket);
        assertFalse(model.verify(ticket, completeEvidence(1)));
        clock.set(115);
        model.tick();
        assertEquals(State.QUARANTINED, model.state());
        assertTrue(model.startReturned(ticket, true));
        assertFalse(model.startOutstanding());
        assertFalse(model.verify(ticket, completeEvidence(2)));
        assertTrue(model.barrierConfirmed());
        assertTrue(model.admit(Operation.SERIES).isEmpty());
        assertEquals(1, model.startRequests());
        assertEquals(1, model.stopRequests());
        assertEquals(0, model.releases());
    }

    @Test
    void lateStartBeforeCleanupDeadlineIsStoppedRatherThanRun() {
        var ticket = model.admit(Operation.VOLUME).orElseThrow();
        clock.set(110);
        assertTrue(model.startReturned(ticket, true));
        assertEquals(State.VERIFYING, model.state());
        assertEquals(1, model.stopRequests());
        assertTrue(model.verify(ticket, completeEvidence(1)));
        assertEquals(State.IDLE, model.state());
    }

    @Test
    void runtimeTimeoutRequestsStopButDoesNotAssertTerminationOrRetry() {
        var ticket = running();
        clock.set(130);
        model.tick();
        assertEquals(State.STOPPING, model.state());
        assertFalse(model.barrierConfirmed());
        assertEquals(0, model.releases());
        model.tick();
        assertEquals(1, model.stopRequests());
        model.verificationStarted(ticket);
        assertTrue(model.verify(ticket, completeEvidence(1)));
        assertEquals(1, model.startRequests());
        assertEquals(State.IDLE, model.state());
    }

    @Test
    void seriesAndVolumeHaveDifferentFixedDeadlinesNotProgressRenewals() {
        var ticket = model.admit(Operation.SERIES).orElseThrow();
        clock.set(109);
        model.startReturned(ticket, true);
        clock.set(199);
        model.tick();
        assertEquals(State.RUNNING, model.state());
        clock.set(200);
        model.tick();
        assertEquals(State.STOPPING, model.state());
    }

    @ParameterizedTest
    @EnumSource(Check.class)
    void everyMissingUnknownOrFailedBarrierComponentBlocksRelease(Check missing) {
        var ticket = verifying();
        long revision = 0;
        for (Proof proof : new Proof[] {Proof.UNKNOWN, Proof.FAILED}) {
            var checks = new EnumMap<>(completeEvidence(1).checks());
            checks.put(missing, proof);
            assertFalse(model.verify(ticket, new Evidence(++revision, checks)));
            assertFalse(model.barrierConfirmed());
            assertEquals(0, model.releases());
        }
        var checks = new EnumMap<>(completeEvidence(1).checks());
        checks.remove(missing);
        assertFalse(model.verify(ticket, new Evidence(++revision, checks)));
        clock.set(105);
        model.tick();
        assertEquals(State.QUARANTINED, model.state());
        assertFalse(model.verify(ticket, completeEvidence(++revision)));
        assertTrue(model.admit(Operation.SERIES).isEmpty());
    }

    @Test
    void cleanupHasOneDeadlineAcrossStopVerificationAndShutdown() {
        var ticket = running();
        model.finished(ticket);
        long deadline = model.cleanupDeadline();
        clock.set(103);
        model.verificationStarted(ticket);
        model.shutdown();
        assertEquals(deadline, model.cleanupDeadline());
        assertEquals(1, model.stopRequests());
        clock.set(105);
        // Kein explizites tick nötig: verspäteter Complete-Callback darf Frist nicht umgehen.
        assertFalse(model.verify(ticket, completeEvidence(1)));
        assertEquals(State.QUARANTINED, model.state());
    }

    @Test
    void unknownIdentityLatchesQuarantineEvenAfterAllResourcesAreProvenEnded() {
        var ticket = running();
        assertTrue(model.identityUncertain(ticket));
        assertEquals(State.QUARANTINED, model.state());
        assertFalse(model.verify(ticket, completeEvidence(1)));
        assertTrue(model.barrierConfirmed());
        model.shutdown();
        assertEquals(State.QUARANTINED, model.state());
        assertEquals(0, model.releases());
        assertEquals(1, model.stopRequests());
    }

    @Test
    void completeEvidenceReleasesExactlyOnceAndOldCallbacksCannotAffectNewOwner() {
        var old = verifying();
        assertTrue(model.verify(old, completeEvidence(1)));
        assertFalse(model.verify(old, completeEvidence(2)));
        var current = model.admit(Operation.SERIES).orElseThrow();
        assertTrue(current.generation() > old.generation());
        assertFalse(model.startReturned(old, true));
        assertFalse(model.finished(old));
        assertFalse(model.verificationStarted(old));
        assertFalse(model.identityUncertain(old));
        assertFalse(model.verify(old, completeEvidence(3)));
        assertEquals(State.STARTING, model.state());
        assertTrue(model.startOutstanding());
        assertEquals(1, model.releases());
        assertEquals(1, model.stopRequests());
        assertEquals(2, model.startRequests());
    }

    @Test
    void foreignTicketWithSameGenerationHasNoAuthority() {
        var ticket = model.admit(Operation.VOLUME).orElseThrow();
        var other = new SupervisorModel(clock::get, new Budgets(10, 100, 30, 5));
        assertTrue(other.initialize(Proof.CONFIRMED));
        var foreign = other.admit(Operation.VOLUME).orElseThrow();
        assertEquals(ticket.generation(), foreign.generation());
        assertFalse(model.startReturned(foreign, true));
        assertFalse(model.identityUncertain(foreign));
        assertFalse(model.verify(foreign, completeEvidence(1)));
        assertEquals(State.STARTING, model.state());
    }

    @Test
    void oldEvidenceRevisionCannotReplaceANewerIncompleteSnapshot() {
        var ticket = verifying();
        assertFalse(model.verify(ticket, new Evidence(2, java.util.Map.of())));
        assertFalse(model.verify(ticket, completeEvidence(1)));
        assertFalse(model.verify(ticket, completeEvidence(2)));
        assertTrue(model.verify(ticket, completeEvidence(3)));
    }

    @Test
    void cannotMixSeparatePartialEvidenceIntoACompleteBarrier() {
        var ticket = verifying();
        var first = new EnumMap<>(completeEvidence(1).checks());
        first.remove(Check.IPC_READERS_ENDED);
        assertFalse(model.verify(ticket, new Evidence(1, first)));
        assertFalse(model.verify(ticket,
                new Evidence(2, java.util.Map.of(Check.IPC_READERS_ENDED, Proof.CONFIRMED))));
        assertEquals(0, model.releases());
    }

    @ParameterizedTest
    @EnumSource(value = State.class, names = {"STARTING", "RUNNING", "VERIFYING"})
    void shutdownClosesAdmissionBeforeCleanupAndNeverReopensIt(State initial) {
        var ticket = model.admit(Operation.VOLUME).orElseThrow();
        if (initial != State.STARTING) model.startReturned(ticket, true);
        if (initial == State.VERIFYING) {
            model.finished(ticket);
            model.verificationStarted(ticket);
        }
        assertEquals(initial, model.state());
        model.shutdown();
        model.shutdown();
        assertTrue(model.admit(Operation.SERIES).isEmpty());
        if (initial == State.STARTING) {
            assertTrue(model.startOutstanding());
            assertFalse(model.verify(ticket, completeEvidence(1)));
            model.startReturned(ticket, true);
        }
        model.verificationStarted(ticket);
        assertTrue(model.verify(ticket, completeEvidence(2)));
        assertEquals(State.CLOSED, model.state());
        assertTrue(model.admit(Operation.VOLUME).isEmpty());
        assertEquals(1, model.stopRequests());
        assertEquals(1, model.releases());
    }

    @Test
    void idleShutdownNeedsNoResourceActions() {
        model.shutdown();
        assertEquals(State.CLOSED, model.state());
        assertTrue(model.admit(Operation.SERIES).isEmpty());
        assertEquals(0, model.startRequests());
        assertEquals(0, model.stopRequests());
    }

    @Test
    void preReturnEvidenceAndDoubleStartReturnCannotForgeCompletion() {
        var ticket = model.admit(Operation.VOLUME).orElseThrow();
        assertFalse(model.verify(ticket, completeEvidence(1)));
        assertTrue(model.startReturned(ticket, true));
        assertFalse(model.startReturned(ticket, false));
        assertFalse(model.verify(ticket, completeEvidence(2)));
        assertEquals(State.RUNNING, model.state());
    }

    private Ticket running() {
        var ticket = model.admit(Operation.VOLUME).orElseThrow();
        model.startReturned(ticket, true);
        return ticket;
    }

    private Ticket verifying() {
        var ticket = running();
        model.finished(ticket);
        model.verificationStarted(ticket);
        return ticket;
    }
}
