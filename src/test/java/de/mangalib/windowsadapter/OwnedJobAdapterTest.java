package de.mangalib.windowsadapter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static de.mangalib.windowsadapter.FakeKernelCalls.Fault.*;
import static de.mangalib.windowsadapter.KernelCalls.*;
import static org.junit.jupiter.api.Assertions.*;

/** Ausschließlich In-Memory-Fakes: keine Spring-, JNA-, JDBC- oder Prozessaufrufe. */
class OwnedJobAdapterTest {
    private final Object ticket = new Object();
    private final FakeKernelCalls kernel = new FakeKernelCalls();
    // 3 ist allein ein zurückgelesener Testparameter, keine Gesamtprozessgarantie.
    private final Limits limits = new Limits(true, false, 3, 1024, 5000);
    private final OwnedJobAdapter adapter = new OwnedJobAdapter(ticket, kernel, () -> kernel.now, 8);

    @Test
    void initializesPrivateScopedGroupAndReadsEffectiveLimits() {
        assertTrue(adapter.initialize(ticket, limits));
        assertEquals(limits, kernel.limits.get(kernel.lastJob));
        assertEquals(1, adapter.openResources());
        assertEquals(1, kernel.count("configure"));
        assertEquals(1, kernel.count("read-limits"));
        assertFalse(adapter.initialize(ticket, limits));
        var report = adapter.cleanup(ticket, 120);
        assertTrue(report.groupEndConfirmed());
        assertTrue(report.resourcesClosed());
        assertFalse(report.quarantineRequired());
    }

    @ParameterizedTest
    @EnumSource(value = FakeKernelCalls.Fault.class,
            names = {"CREATE", "CONFIGURE", "READ_LIMITS", "MISMATCH_LIMITS"})
    void initializationFailuresKeepOwnedPartialResourcesAndNeverStart(FakeKernelCalls.Fault fault) {
        kernel.faults.add(fault);
        assertFalse(adapter.initialize(ticket, limits));
        assertNull(adapter.start(ticket));
        var report = adapter.cleanup(ticket, 120);
        assertTrue(report.groupEndConfirmed());
        assertTrue(report.resourcesClosed());
        assertFalse(report.quarantineRequired());
        assertFalse(report.problems().isEmpty());
        assertEquals(0, kernel.count("start-suspended"));
        assertEquals(fault == CREATE ? 0 : 1, kernel.count("close"));
    }

    @ParameterizedTest
    @EnumSource(value = FakeKernelCalls.Fault.class,
            names = {"PREPARE_MEMORY", "PREPARE_ATTRIBUTES", "BEFORE_ASSIGNMENT", "AFTER_ASSIGNMENT"})
    void failuresBeforeAndAfterAssignmentAreCollectedWithoutRetry(FakeKernelCalls.Fault fault) {
        initialized();
        kernel.faults.add(fault);
        assertNotNull(adapter.start(ticket));
        assertFalse(adapter.resume(ticket));
        assertNull(adapter.start(ticket));
        var report = adapter.cleanup(ticket, 120);
        assertTrue(report.groupEndConfirmed());
        assertTrue(report.resourcesClosed());
        assertFalse(report.quarantineRequired());
        assertFalse(report.problems().isEmpty());
        assertEquals(0, kernel.count("resume"));
        assertTrue(kernel.count("start-suspended") <= 1);
        assertEquals(fault == AFTER_ASSIGNMENT ? 1 : 0, kernel.count("terminate"));
    }

    @Test
    void verifiesMembershipBeforeResumeAndNeverResumesTwice() {
        started();
        assertEquals(0, kernel.count("resume"));
        assertTrue(adapter.resume(ticket));
        assertFalse(adapter.resume(ticket));
        assertEquals(1, kernel.count("resume"));
        assertTrue(adapter.cleanup(ticket, 120).resourcesClosed());
        assertFalse(adapter.resume(ticket));
    }

    @Test
    void errorBeforeResumeRetainsPrimaryErrorAndAllowsOwnedGroupCleanup() {
        started();
        kernel.faults.add(RESUME);
        assertFalse(adapter.resume(ticket));
        assertFalse(adapter.resume(ticket));
        var report = adapter.cleanup(ticket, 120);
        assertEquals("RESUME", report.problems().getFirst().operation());
        assertTrue(report.groupEndConfirmed());
        assertTrue(report.resourcesClosed());
        assertEquals(1, kernel.count("resume"));
    }

    @Test
    void acceptsSixConfirmedDynamicMembersWithoutAProcessNameException() {
        started();
        Resource job = kernel.lastJob;
        for (int i = 0; i < 5; i++) kernel.addProcess(job,
                i < 3 ? "C:/synthetic/conhost.exe" : "C:/synthetic/helper.exe");
        assertTrue(adapter.inspect(ticket, 120));
        var report = adapter.cleanup(ticket, 120);
        assertTrue(report.groupEndConfirmed());
        assertTrue(report.resourcesClosed());
        assertFalse(report.quarantineRequired());
        assertEquals(6, kernel.count("wait"));
        assertEquals(1, kernel.count("terminate"));
        assertEquals(3, limits.activeProcessCandidate());
    }

    static Stream<Observation> inconsistentObservations() {
        return Stream.of(
                new Observation(false, true, 1, List.of(100L), 1, 1),
                new Observation(true, false, 1, List.of(100L), 1, 1),
                new Observation(true, true, 2, List.of(100L), 1, 2),
                new Observation(true, true, 1, List.of(100L), 0, 1),
                new Observation(true, true, 2, List.of(100L, 100L), 2, 2),
                new Observation(true, true, 1, List.of(-1L), 1, 1),
                new Observation(true, true, 1, List.of(100L), 1, 0),
                new Observation(true, true, 9, List.of(100L, 101L, 102L, 103L, 104L,
                        105L, 106L, 107L, 108L), 9, 9));
    }

    @ParameterizedTest
    @MethodSource("inconsistentObservations")
    void incompleteInconsistentOrOversizedSnapshotsCannotConfirmEnd(Observation observation) {
        started();
        kernel.observations.add(observation);
        var report = adapter.cleanup(ticket, 120);
        assertFalse(report.groupEndConfirmed());
        assertFalse(report.resourcesClosed());
        assertTrue(report.quarantineRequired());
        assertEquals(1, kernel.count("terminate"));
        assertEquals(0, kernel.count("close"));
    }

    @Test
    void stableButNonemptyFinalSnapshotStillBlocksRelease() {
        started();
        kernel.observations.add(new Observation(true, true, 1, List.of(100L), 1, 1));
        kernel.observations.add(new Observation(true, true, 1, List.of(100L), 1, 1));
        var report = adapter.cleanup(ticket, 120);
        assertFalse(report.groupEndConfirmed());
        assertTrue(report.quarantineRequired());
        assertEquals(1, kernel.count("terminate"));
        assertEquals(0, kernel.count("close"));
    }

    @Test
    void terminateFailureIsNotHiddenAndDoesNotCloseTheGroupHandle() {
        started();
        kernel.faults.add(TERMINATE);
        var report = adapter.cleanup(ticket, 120);
        assertFalse(report.groupEndConfirmed());
        assertTrue(report.quarantineRequired());
        assertEquals("TERMINATE", report.problems().getFirst().operation());
        assertFalse(kernel.closed.contains(kernel.lastJob));
    }

    @Test
    void successfulTerminateRequestIsNotEvidenceOfGroupOrProcessEnd() {
        started();
        kernel.terminateHasNoEffect = true;
        var report = adapter.cleanup(ticket, 120);
        assertEquals(1, kernel.count("terminate"));
        assertFalse(report.groupEndConfirmed());
        assertFalse(report.resourcesClosed());
        assertTrue(report.quarantineRequired());
    }

    @Test
    void emptyGroupDoesNotReplaceSignalOfEveryRetainedProcessInstance() {
        started();
        kernel.neverSignaled.add(kernel.lastStart.process());
        var report = adapter.cleanup(ticket, 120);
        assertFalse(report.groupEndConfirmed());
        assertTrue(report.quarantineRequired());
        assertEquals(0, kernel.count("close"));
    }

    @ParameterizedTest
    @EnumSource(value = FakeKernelCalls.Fault.class,
            names = {"CLOSE_PROCESS", "CLOSE_ATTRIBUTES", "CLOSE_MEMORY", "CLOSE_JOB"})
    void partialCloseNeverHidesAnErrorOrRetriesAnUncertainHandle(FakeKernelCalls.Fault fault) {
        started();
        kernel.faults.add(fault);
        var first = adapter.cleanup(ticket, 120);
        assertTrue(first.groupEndConfirmed());
        assertFalse(first.resourcesClosed());
        assertTrue(first.quarantineRequired());
        long closeCount = kernel.count("close");
        assertSame(first, adapter.cleanup(ticket, 9999));
        assertEquals(closeCount, kernel.count("close"));
        assertTrue(adapter.openResources() > 0);
        if (fault == CLOSE_ATTRIBUTES) {
            assertTrue(kernel.events.stream().filter(e -> e.operation().equals("close"))
                    .noneMatch(e -> e.resource().kind == Kind.MEMORY));
        }
    }

    @Test
    void primaryInitializationErrorAndSecondaryCloseErrorBothRemainVisible() {
        kernel.faults.add(CONFIGURE);
        kernel.faults.add(CLOSE_JOB);
        assertFalse(adapter.initialize(ticket, limits));
        var report = adapter.cleanup(ticket, 120);
        assertEquals(List.of("CONFIGURE", "CLOSE_JOB"),
                report.problems().stream().map(OwnedJobAdapter.Problem::operation).toList());
        assertNotNull(report.problems().get(0).cause());
        assertNotNull(report.problems().get(1).cause());
        assertTrue(report.quarantineRequired());
    }

    @Test
    void successfulCleanupClosesEachCapabilityOnceAndOldGroupCannotTouchANewGroup() {
        started();
        Resource oldJob = kernel.lastJob;
        var first = adapter.cleanup(ticket, 120);
        assertTrue(first.groupEndConfirmed());
        assertTrue(first.resourcesClosed());
        assertFalse(first.quarantineRequired());
        assertEquals(0, adapter.openResources());
        assertEquals(kernel.count("close"), kernel.closed.size());
        int eventCount = kernel.events.size();
        assertSame(first, adapter.cleanup(ticket, 500));
        assertEquals(eventCount, kernel.events.size());
        kernel.nextValue = oldJob.debugValue; // numerischer Wert wiederverwendet
        Object nextTicket = new Object();
        var next = new OwnedJobAdapter(nextTicket, kernel, () -> kernel.now, 8);
        assertTrue(next.initialize(nextTicket, limits));
        Resource newJob = kernel.lastJob;
        assertEquals(oldJob.debugValue, newJob.debugValue);
        assertNotSame(oldJob, newJob);
        assertSame(first, adapter.cleanup(ticket, 600));
        assertFalse(kernel.closed.contains(newJob));
        assertFalse(adapter.inspect(ticket, 600));
        assertNull(adapter.start(ticket));
    }

    @Test
    void foreignTicketsCannotCloseResumeInitializeOrInspectOwnResources() {
        started();
        var foreign = new Object();
        int before = kernel.events.size();
        assertThrows(IllegalArgumentException.class, () -> adapter.cleanup(foreign, 120));
        assertThrows(IllegalArgumentException.class, () -> adapter.resume(foreign));
        assertThrows(IllegalArgumentException.class, () -> adapter.initialize(foreign, limits));
        assertThrows(IllegalArgumentException.class, () -> adapter.inspect(foreign, 120));
        assertEquals(before, kernel.events.size());
    }

    @Test
    void foreignGroupAndForeignNamedProcessAreNeverTerminated() {
        Resource foreignJob = kernel.createJob(new Scope());
        Resource foreignProcess = kernel.addProcess(foreignJob, "C:/synthetic/conhost.exe");
        started();
        var report = adapter.cleanup(ticket, 120);
        assertTrue(report.resourcesClosed());
        assertTrue(kernel.handles.get(foreignProcess).alive);
        assertFalse(kernel.closed.contains(foreignJob));
        assertTrue(kernel.events.stream().filter(e -> e.operation().equals("terminate"))
                .allMatch(e -> e.resource() != foreignJob));
    }

    @Test
    void matchingNameWithoutNativeMembershipIsNotTrusted() {
        Resource foreignJob = kernel.createJob(new Scope());
        Resource foreignProcess = kernel.addProcess(foreignJob, "C:/synthetic/conhost.exe");
        started();
        long pid = kernel.handles.get(foreignProcess).pid;
        kernel.observations.add(new Observation(true, true, 1, List.of(pid), 1, 1));
        var report = adapter.cleanup(ticket, 120);
        assertTrue(report.quarantineRequired());
        assertFalse(report.groupEndConfirmed());
        assertEquals(1, kernel.count("terminate"));
        assertTrue(kernel.handles.get(foreignProcess).alive);
    }

    @Test
    void changedHeldInstanceIdentityCannotBeExplainedAwayAsPidReuse() {
        started();
        kernel.handles.get(kernel.lastStart.process()).birth++;
        var report = adapter.cleanup(ticket, 120);
        assertFalse(report.groupEndConfirmed());
        assertTrue(report.quarantineRequired());
        assertEquals(0, kernel.count("close"));
    }

    @ParameterizedTest
    @EnumSource(value = FakeKernelCalls.Fault.class, names = {"OBSERVE", "OPEN", "IDENTITY", "MEMBERSHIP"})
    void queryAndIdentityFailuresAreUnknownNotSuccessfulEnd(FakeKernelCalls.Fault fault) {
        started();
        kernel.addProcess(kernel.lastJob, "C:/synthetic/helper.exe");
        kernel.faults.add(fault);
        var report = adapter.cleanup(ticket, 120);
        assertFalse(report.groupEndConfirmed());
        assertTrue(report.quarantineRequired());
        assertFalse(report.resourcesClosed());
    }

    @Test
    void globalDeadlineIsSharedByAllRetainedWaitsAndCannotBeRenewed() {
        started();
        kernel.addProcess(kernel.lastJob, "C:/synthetic/helper.exe");
        kernel.waitCost = 5;
        var report = adapter.cleanup(ticket, 110);
        assertFalse(report.groupEndConfirmed());
        assertTrue(report.quarantineRequired());
        assertEquals(List.of(10L, 5L), kernel.events.stream()
                .filter(e -> e.operation().equals("wait")).map(FakeKernelCalls.Event::remaining).toList());
        int count = kernel.events.size();
        assertSame(report, adapter.cleanup(ticket, 10000));
        assertEquals(count, kernel.events.size());
    }

    @Test
    void deadlineAlreadyExpiredDoesNotIssueNewWaitOrCloseCalls() {
        started();
        var report = adapter.cleanup(ticket, kernel.now);
        assertTrue(report.quarantineRequired());
        assertFalse(report.groupEndConfirmed());
        assertEquals(0, kernel.count("wait"));
        assertEquals(0, kernel.count("close"));
        assertEquals(0, kernel.count("terminate"));
    }

    @Test
    void closedButOverdueCleanupDoesNotBecomeTimelySuccess() {
        initialized();
        kernel.closeCost = 5;
        var report = adapter.cleanup(ticket, 105);
        assertTrue(report.groupEndConfirmed());
        assertTrue(report.resourcesClosed());
        assertTrue(report.quarantineRequired());
        assertEquals(1, kernel.count("close"));
    }

    @Test
    void boundedJobEndWaitSharesTheProcessWaitDeadline() {
        started();
        kernel.waitCost = 4;
        kernel.jobWaitCost = 6;
        var report = adapter.cleanup(ticket, 110);
        assertFalse(report.groupEndConfirmed());
        assertTrue(report.quarantineRequired());
        assertEquals(List.of(6L), kernel.events.stream().filter(e -> e.operation().equals("await-empty"))
                .map(FakeKernelCalls.Event::remaining).toList());
        assertEquals(0, kernel.count("close"));
    }

    @Test
    void invalidOrForeignCapabilitiesCannotBecomeAnOwnedJob() {
        for (Resource invalid : List.of(new Resource(new Scope(), Kind.JOB, 1),
                new Resource(new Scope(), Kind.JOB, 0))) {
            var fake = new FakeKernelCalls();
            fake.substituteJob = invalid;
            var scoped = new OwnedJobAdapter(ticket, fake, () -> fake.now, 8);
            assertFalse(scoped.initialize(ticket, limits));
            var report = scoped.cleanup(ticket, 120);
            assertTrue(report.quarantineRequired());
            assertFalse(report.groupEndConfirmed());
            assertEquals(0, fake.count("terminate"));
            assertEquals(0, fake.count("close"));
            assertEquals(0, fake.count("configure"));
        }
    }

    @Test
    void pendingNativeReturnKeepsAttributesMemoryAndOwnJobUntilActualCallback() {
        initialized();
        kernel.pendingStart = true;
        var permit = adapter.start(ticket);
        assertTrue(adapter.pendingStart());
        assertTrue(adapter.launchPrepared());
        assertEquals(3, adapter.openResources());
        var pendingReport = adapter.cleanup(ticket, 120);
        assertTrue(pendingReport.quarantineRequired());
        assertEquals(0, kernel.count("close"));
        StartResult late = kernel.completePending(kernel.lastJob);
        int before = kernel.events.size();
        assertFalse(adapter.startReturned(new Object(), permit, late));
        assertEquals(before, kernel.events.size());
        assertTrue(adapter.startReturned(ticket, permit, late));
        assertFalse(adapter.startReturned(ticket, permit, late));
        assertFalse(adapter.resume(ticket));
        var finalReport = adapter.cleanup(ticket, 9999);
        assertTrue(finalReport.groupEndConfirmed());
        assertTrue(finalReport.resourcesClosed());
        assertTrue(finalReport.quarantineRequired()); // Incident nicht automatisch geheilt
        assertEquals(0, kernel.count("resume"));
    }

    @Test
    void oldStartPermitCannotCompleteANewOwnersAttempt() {
        initialized();
        kernel.pendingStart = true;
        var oldPermit = adapter.start(ticket);
        var otherTicket = new Object();
        var other = new OwnedJobAdapter(otherTicket, kernel, () -> kernel.now, 8);
        assertTrue(other.initialize(otherTicket, limits));
        var newPermit = other.start(otherTicket);
        assertNotSame(oldPermit, newPermit);
        StartResult result = kernel.completePending(kernel.lastJob);
        int count = kernel.events.size();
        assertFalse(other.startReturned(otherTicket, oldPermit, result));
        assertFalse(other.startReturned(ticket, newPermit, result));
        assertEquals(count, kernel.events.size());
        assertTrue(other.pendingStart());
        assertTrue(adapter.pendingStart());
    }

    private void initialized() { assertTrue(adapter.initialize(ticket, limits)); }
    private void started() {
        initialized();
        assertNotNull(adapter.start(ticket));
        assertFalse(adapter.pendingStart());
    }
}
