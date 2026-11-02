package de.mangalib.windowsadapter;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;

import static de.mangalib.windowsadapter.KernelCalls.*;
import static de.mangalib.windowsadapter.Win64Layouts.*;
import static org.junit.jupiter.api.Assertions.*;

/** JNA-Layout/Memory und Fake-RawApi; niemals Kernel32 laden oder Job erstellen. */
class Win64BindingsTest {
    static final class Raw implements Win64KernelCalls.RawApi {
        Pointer created = new Pointer(0x1234);
        int error = 5, creates, sets, closes, queries, flags = KILL_ON_CLOSE;
        int failureClass = -1, returnedAdjustment, population, growCount;
        boolean setOk = true, closeOk = true, foreverSmall;
        @Override public Pointer create() { creates++; return created; }
        @Override public boolean set(Pointer j, int type, Pointer p, int size) {
            sets++; assertEquals(EXTENDED_LIMITS, type); assertEquals(144, size);
            assertEquals(KILL_ON_CLOSE, p.getInt(16)); return setOk;
        }
        @Override public boolean query(Pointer j, int type, Pointer p, int size, IntByReference r) {
            queries++;
            if (type == failureClass) return false;
            if (type == EXTENDED_LIMITS) p.setInt(16, flags);
            if (type == BASIC_ACCOUNTING) { p.setInt(36, population); p.setInt(40, population); }
            if (type == PROCESS_IDS) {
                if ((growCount > 0 && size < 8 + 8 * growCount) || foreverSmall) {
                    p.setInt(0, growCount); error = MORE_DATA; return false;
                }
                p.setInt(0, population); p.setInt(4, population);
                for (int i = 0; i < population; i++) p.setLong(8L + 8L * i, 10 + i);
            }
            r.setValue(size + returnedAdjustment); return true;
        }
        @Override public boolean close(Pointer j) { closes++; return closeOk; }
        @Override public int error() { return error; }
    }
    private final Raw raw = new Raw();
    private final Win64KernelCalls port = new Win64KernelCalls(raw, "Windows 11", 8);
    private final Limits emptyLimits = new Limits(true, false, 0, 0, 0);

    @Test void verifiesWin64SizesOffsetsAndConstantsWithoutKernel32() {
        Win64Layouts.verify();
        assertEquals(0x2000, KILL_ON_CLOSE);
        assertEquals(0x0800, BREAKAWAY);
        assertEquals(0x1000, SILENT_BREAKAWAY);
        assertEquals(1, BASIC_ACCOUNTING); assertEquals(3, PROCESS_IDS); assertEquals(9, EXTENDED_LIMITS);
        assertEquals(0, raw.creates);
    }

    @Test void reusesExistingOwnedAdapterForAnEntireEmptyLifecycle() {
        Object ticket = new Object();
        var adapter = new OwnedJobAdapter(ticket, port, () -> 100, 8);
        assertTrue(adapter.initialize(ticket, emptyLimits));
        var report = adapter.cleanup(ticket, 120);
        assertTrue(report.groupEndConfirmed()); assertTrue(report.resourcesClosed());
        assertFalse(report.quarantineRequired());
        assertEquals(0, port.outstandingHandles()); assertEquals(0, port.outstandingBuffers());
        assertEquals(1, raw.creates); assertEquals(1, raw.closes);
    }

    @ParameterizedTest @ValueSource(longs = {0, -1})
    void rejectsInvalidNativeHandlesAndConsumesTheCreateAttempt(long value) {
        raw.created = value == 0 ? null : new Pointer(value);
        Failure failure = assertThrows(Failure.class, () -> port.createJob(new Scope()));
        assertEquals(5, failure.code);
        assertThrows(Failure.class, () -> port.createJob(new Scope()));
        assertEquals(1, raw.creates); assertEquals(0, raw.closes);
    }

    @Test void rejectsForgedClosedAndNumericallyReusedCapabilities() {
        Scope scope = new Scope(); Resource own = port.createJob(scope);
        assertThrows(Failure.class, () -> port.close(new Resource(scope, Kind.JOB, own.debugValue)));
        port.close(own);
        assertThrows(Failure.class, () -> port.readLimits(own));
        assertThrows(Failure.class, () -> port.close(own));
        assertEquals(1, raw.closes);
    }

    @Test void neverRetriesAHandleWithUnconfirmedClose() {
        Resource own = port.createJob(new Scope()); raw.closeOk = false;
        Failure error = assertThrows(Failure.class, () -> port.close(own));
        assertEquals("CloseHandle", error.operation); assertEquals(5, error.code);
        raw.closeOk = true;
        assertThrows(Failure.class, () -> port.close(own));
        assertEquals(1, raw.closes); assertEquals(1, port.outstandingHandles());
    }

    @Test void translatesSetFailureAndReleasesTheNativeBuffer() {
        Resource own = port.createJob(new Scope()); raw.setOk = false;
        assertEquals(5, assertThrows(Failure.class, () -> port.configure(own, emptyLimits)).code);
        assertEquals(0, port.outstandingBuffers()); port.close(own);
    }

    @ParameterizedTest @ValueSource(ints = {BASIC_ACCOUNTING, PROCESS_IDS, EXTENDED_LIMITS})
    void translatesQueryFailuresAndDoesNotRetainMemory(int kind) {
        Resource own = port.createJob(new Scope()); raw.failureClass = kind;
        Failure error = assertThrows(Failure.class, () -> {
            if (kind == EXTENDED_LIMITS) port.readLimits(own); else port.observe(own, 8, 10);
        });
        assertEquals(5, error.code); assertEquals(0, port.outstandingBuffers()); port.close(own);
    }

    @ParameterizedTest @ValueSource(ints = {-1, 1})
    void rejectsIncorrectStructureReturnLengths(int adjustment) {
        Resource own = port.createJob(new Scope()); raw.returnedAdjustment = adjustment;
        assertThrows(Failure.class, () -> port.readLimits(own));
        assertEquals(0, port.outstandingBuffers()); port.close(own);
    }

    @ParameterizedTest @ValueSource(ints = {0, 0x2800, 0x3000, 0x2008})
    void rejectsAbsentKillFlagBreakawayAndUnexpectedQuotaFlags(int flags) {
        Resource own = port.createJob(new Scope()); raw.flags = flags;
        assertThrows(Failure.class, () -> port.readLimits(own));
        port.close(own);
    }

    @Test void refusesQuotaExperimentsBeforeSetCall() {
        Resource own = port.createJob(new Scope());
        assertThrows(Failure.class, () -> port.configure(own, new Limits(true, false, 3, 0, 0)));
        assertEquals(0, raw.sets); port.close(own);
    }

    @Test void doesNotClaimNonemptyGroupSupportOrStableEnd() {
        Resource own = port.createJob(new Scope()); raw.population = 1;
        assertThrows(Failure.class, () -> port.observe(own, 8, 10)); port.close(own);
    }

    @Test void growsProcessListAtMostOnceWithinTheCapacity() {
        Resource own = port.createJob(new Scope()); raw.growCount = 6;
        assertTrue(port.observe(own, 8, 10).pids().isEmpty());
        assertEquals(4, raw.queries); // accounting, zwei PID-Abfragen, accounting
        assertEquals(0, port.outstandingBuffers()); port.close(own);
    }

    @Test void stopsAfterASecondIncompleteListInsteadOfRetrying() {
        Resource own = port.createJob(new Scope()); raw.growCount = 6; raw.foreverSmall = true;
        assertThrows(Failure.class, () -> port.observe(own, 8, 10));
        assertEquals(3, raw.queries); assertEquals(0, port.outstandingBuffers()); port.close(own);
    }

    @ParameterizedTest @ValueSource(ints = {0, 257})
    void rejectsUnboundedCapacitiesBeforeQuery(int capacity) {
        Resource own = port.createJob(new Scope());
        assertThrows(Failure.class, () -> port.observe(own, capacity, 10));
        assertEquals(0, raw.queries); port.close(own);
    }

    @Test void allProcessOperationsAreNonExecutingRejections() {
        Resource own = port.createJob(new Scope()); int before = raw.queries;
        assertThrows(Failure.class, () -> port.prepareLaunch(own, r -> fail()));
        assertThrows(Failure.class, () -> port.startSuspended(own, List.of(), r -> fail()));
        assertThrows(Failure.class, () -> port.resume(own));
        assertThrows(Failure.class, () -> port.terminate(own));
        assertThrows(Failure.class, () -> port.waitEnded(own, 10));
        assertThrows(Failure.class, () -> port.openProcess(own, 1));
        assertThrows(Failure.class, () -> port.identity(own));
        assertThrows(Failure.class, () -> port.member(own, own));
        assertEquals(before, raw.queries); port.close(own);
    }

    @ParameterizedTest @ValueSource(strings = {"Linux", "Mac OS X", ""})
    void rejectsUnsupportedSystemsBeforeCallingTheRawApi(String os) {
        assertThrows(UnsupportedOperationException.class, () -> new Win64KernelCalls(raw, os, 8));
        assertEquals(0, raw.creates);
    }

    @Test void rejectsUnsupportedPointerWidth() {
        assertThrows(UnsupportedOperationException.class, () -> new Win64KernelCalls(raw, "Windows", 4));
    }

    @Test void boundedPidDecoderRejectsTruncationDuplicatesAndInvalidCounts() {
        try (Memory buffer = new Memory(24)) {
            buffer.clear(); buffer.setInt(0, 2); buffer.setInt(4, 2);
            buffer.setLong(8, 10); buffer.setLong(16, 10);
            assertThrows(Failure.class, () -> decodePids(buffer, 24, 2));
            buffer.setLong(16, 11);
            assertThrows(Failure.class, () -> decodePids(buffer, 16, 2));
            assertEquals(List.of(10L, 11L), decodePids(buffer, 24, 2));
            buffer.setInt(4, -1);
            assertThrows(Failure.class, () -> decodePids(buffer, 24, 2));
        }
    }
}
