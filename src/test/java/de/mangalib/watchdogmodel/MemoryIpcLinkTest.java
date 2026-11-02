package de.mangalib.watchdogmodel;

import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static de.mangalib.watchdogmodel.BinaryIpcCodec.*;
import static de.mangalib.watchdogmodel.WatchdogModel.*;
import static org.junit.jupiter.api.Assertions.*;

/** Nur explizite Speicher-/Fake-Ereignisse, keine JVMs, echten Kanäle oder Threads. */
class MemoryIpcLinkTest {
    private static final Limits LIMITS = new Limits(160, 512, 1024, 100, 4);
    private static final class Fixture implements AutoCloseable {
        final Clock clock = new Clock();
        final FakeJobKernel kernel = new FakeJobKernel();
        final FakeRecoveryJournal journal = new FakeRecoveryJournal(FakeRecoveryJournal.State.CLEAN);
        final WatchdogModel model = new WatchdogModel(clock, kernel, journal);
        final BufferBudget budget = new BufferBudget(40_000);
        final WireIdentityRegistry registry = new WireIdentityRegistry(64);
        final Map<Direction, WatchdogWireBridge> bridges = new EnumMap<>(Direction.class);
        final Map<Direction, MemoryIpcLink> links = new EnumMap<>(Direction.class);
        final Ticket ticket;
        Fixture() {
            assertTrue(model.initialize(journal.baseline())); ticket = model.admit(Operation.VOLUME).orElseThrow();
            model.prepareOuter(ticket); model.beginHost(ticket);
            assertTrue(model.hostReturned(ticket, new FakeJobKernel.Identity(100, 1, "synthetic-host")));
        }
        WatchdogWireBridge bridge(Direction d) {
            return bridges.computeIfAbsent(d, key -> {
                long sender = key == Direction.S_H ? 20 : key == Direction.W_H ? 22 : 21;
                return new WatchdogWireBridge(model, key, model.channel(key).principal,
                        new Id(1, 1), new Id(1, 2), new Id(1, 100 + key.ordinal()), new Id(1, sender), new Id(1, 30), registry, drainSink((f, n) -> true));
            });
        }
        MemoryIpcLink link(Direction d) {
            return links.computeIfAbsent(d, key -> new MemoryIpcLink(LIMITS, budget.account(10_000), 17, bridge(key), bridge(key)::transportEnded));
        }
        Frame frame(Direction d, Body body, String text) { return bridge(d).outbound(model.nextSequence(d), body, text); }
        void send(Direction d, Body body, String text) {
            MemoryIpcLink link = link(d); assertTrue(link.send(frame(d, body, text))); drain(link);
            assertFalse(link.closed(), () -> "link rejected: " + link.error());
        }
        void send(Direction d, Type type) { send(d, new Signal(type), ""); }
        void ready() {
            send(Direction.S_H, Type.HELLO); send(Direction.H_S, Type.READY); model.prepareInner(ticket); send(Direction.S_H, Type.START);
        }
        void running() {
            ready(); model.beginWorker(ticket); model.workerReturned(ticket, new FakeJobKernel.Identity(101, 2, "synthetic-worker"));
            send(Direction.H_W, Type.START); send(Direction.W_H, Type.STARTED);
            assertEquals(State.RUNNING, model.state());
        }
        void reject(Direction d, Frame frame) {
            MemoryIpcLink link = link(d); assertTrue(link.send(frame)); drain(link); assertTrue(link.closed());
            assertNotNull(link.error()); assertNotEquals(State.IDLE, model.state()); assertFalse(model.delivered());
        }
        @Override public void close() { links.values().forEach(MemoryIpcLink::close); assertEquals(0, budget.used()); }
    }

    private static void drain(MemoryIpcLink link) {
        for (int i = 0; i < 10_000 && (link.sending() || link.buffered() > 0) && !link.closed(); i++) {
            link.writeStep(3); link.readStep(5);
        }
        assertFalse(link.sending()); assertEquals(0, link.buffered());
    }
    private static Envelope changed(Frame original, Id ticket, Id channel, Id sender, Direction direction, long sequence) {
        Envelope e = original.envelope(); return new Envelope(e.session(), ticket, channel, sender, direction, sequence);
    }
    private static MemoryIpcLink generic(BufferBudget budget, int capacity, Sink sink, Runnable end) {
        return new MemoryIpcLink(LIMITS, budget.account(10_000), capacity, drainSink(sink), reason -> end.run());
    }
    /** Expliziter Daten-Drain nur für Modelltests; kein produktiver Payloadspeicher. */
    private static Sink drainSink(Sink sink) {
        return new Sink() {
            public boolean accept(Frame frame, long bytes) { return sink.accept(frame, bytes); }
            public boolean dataByte(Envelope envelope, int value) { return true; }
        };
    }

    @Test void happyByteSequenceReusesExistingLifecycleAndKeepsResultInternal() {
        try (Fixture f = new Fixture()) {
            f.running(); String text = "ä漢😀"; long bytes = utf8Length(text);
            f.send(Direction.W_H, new ResultBegin(bytes, 3, 2), "");
            f.send(Direction.W_H, new ResultChunk(bytes), text); f.send(Direction.W_H, Type.RESULT_END);
            f.send(Direction.H_S, Type.RESULT_AVAILABLE); assertFalse(f.model.delivered());
            assertTrue(f.model.worker().alive); assertEquals(State.RUNNING, f.model.state());
            f.model.workerEnded(f.ticket, 0);
            f.send(Direction.H_S, new GroupEnd(f.kernel.observe(FakeJobKernel.Role.H, f.model.inner())), "");
            f.send(Direction.H_S, Type.RESOURCES_CLOSED); f.model.hostEnded(f.ticket, false);
            IoEnd incomplete = new IoEnd(f.ticket, true, true, false, true, true, true);
            assertFalse(f.model.finish(f.ticket, f.kernel.observe(FakeJobKernel.Role.S, f.model.outer()), incomplete));
            assertEquals(State.CLEANING, f.model.state()); // Physische Kanalendbarriere nicht erfinden.
        }
    }

    @Test void fullRingAndUnresponsiveReceiverCannotGrowQueueOrReservations() {
        BufferBudget budget = new BufferBudget(20_000); int[] received = {0};
        try (MemoryIpcLink link = generic(budget, 11, (f, s) -> { received[0]++; return true; }, () -> { })) {
            Frame data = BinaryIpcCodecTest.frame(new Data("x".repeat(300)));
            assertTrue(link.send(data)); long held = budget.used(); assertEquals(11, link.writeStep(999));
            for (int i = 0; i < 100; i++) {
                assertFalse(link.send(data)); assertEquals(0, link.writeStep(999)); assertEquals(held, budget.used());
            }
            assertEquals(11, link.buffered()); assertEquals(0, received[0]); drain(link);
            assertEquals(1, received[0]); assertTrue(budget.used() < held);
        }
        assertEquals(0, budget.used());
    }

    @Test void frameLargerThanRingStreamsWithoutDecoderFrameBuffer() {
        BufferBudget budget = new BufferBudget(20_000); long[] count = {0};
        try (MemoryIpcLink link = generic(budget, 3, (f, s) -> { count[0] = ((DataCount) f.payload()).bytes(); return true; }, () -> { })) {
            long fixed = budget.used(); String text = "漢".repeat(100); assertTrue(link.send(BinaryIpcCodecTest.frame(new Data(text))));
            assertEquals(fixed + HEADER + 2L * text.length(), budget.used());
            drain(link); assertEquals(300, count[0]); assertEquals(fixed, budget.used());
            assertEquals(fixed + HEADER + 200, budget.peak());
        }
        assertEquals(0, budget.used());
    }

    @Test void globalAndPerChannelBudgetsAreIndependentAndRefusalIsBackpressure() {
        long fixed = LIMITS.decoderReservation() + 7;
        BufferBudget budget = new BufferBudget(2 * fixed + HEADER);
        var a = budget.account(2 * fixed + HEADER); var b = budget.account(2 * fixed + HEADER);
        try (MemoryIpcLink first = new MemoryIpcLink(LIMITS, a, 7, (f, s) -> true, reason -> { });
             MemoryIpcLink second = new MemoryIpcLink(LIMITS, b, 7, (f, s) -> true, reason -> { })) {
            Frame frame = BinaryIpcCodecTest.frame(new Control(Type.HELLO, ""));
            assertTrue(first.send(frame)); assertFalse(second.send(frame));
            assertEquals(2 * fixed + HEADER, budget.used());
            drain(first); assertTrue(second.send(frame)); drain(second);
        }
        assertEquals(0, budget.used());
        BufferBudget other = new BufferBudget(20_000); var local = other.account(fixed + HEADER - 1);
        try (MemoryIpcLink link = new MemoryIpcLink(LIMITS, local, 7, (f, s) -> true, reason -> { })) {
            assertFalse(link.send(BinaryIpcCodecTest.frame(new Control(Type.HELLO, "")))); assertEquals(fixed, local.used());
        }
    }

    @Test void failedConstructionReleasesAlreadyReservedRing() {
        BufferBudget budget = new BufferBudget(200);
        assertThrows(IllegalStateException.class, () -> new MemoryIpcLink(LIMITS, budget.account(200), 100, (f, s) -> true, reason -> { }));
        assertEquals(0, budget.used());
    }

    @Test void halfCloseDrainsSenderAndDoesNotCloseReverseDirection() {
        BufferBudget budget = new BufferBudget(20_000); int[] messages = {0}, ends = {0};
        try (MemoryIpcLink forward = generic(budget, 7, (f, s) -> { messages[0]++; return true; }, () -> ends[0]++);
             MemoryIpcLink reverse = generic(budget, 7, (f, s) -> true, () -> { })) {
            Frame frame = BinaryIpcCodecTest.frame(new Control(Type.STATUS, "ok"));
            assertTrue(forward.send(frame)); forward.halfClose(); assertFalse(forward.send(frame)); drain(forward);
            forward.readStep(1); assertTrue(forward.closed()); assertNull(forward.error());
            assertEquals(1, messages[0]); assertEquals(1, ends[0]);
            assertFalse(reverse.closed()); assertTrue(reverse.send(frame)); drain(reverse);
            forward.close(); assertEquals(1, ends[0]);
        }
        assertEquals(0, budget.used());
    }

    @ParameterizedTest @ValueSource(ints = {1, 95, 97})
    void interruptionDuringFrameReleasesAllReservationsAndIsTerminal(int bytes) {
        BufferBudget budget = new BufferBudget(20_000); int[] ends = {0};
        MemoryIpcLink link = generic(budget, 128, (f, s) -> true, () -> ends[0]++);
        byte[] frame = BinaryIpcCodecTest.encode(BinaryIpcCodecTest.frame(new Control(Type.ERROR, "abcd")), LIMITS);
        assertEquals(bytes, link.offerRaw(frame, 0, bytes)); link.readStep(bytes); link.halfClose(); link.readStep(1);
        assertEquals(BinaryIpcCodec.Error.TRUNCATED, link.error()); assertTrue(link.closed());
        assertEquals(0, budget.used()); assertEquals(1, ends[0]);
        assertEquals(0, link.offerRaw(frame, 0, frame.length)); assertFalse(link.send(BinaryIpcCodecTest.frame(new Data("x"))));
    }

    @Test void abortWhileSenderRetainsDataReleasesBothDirectionsWithoutRetry() {
        BufferBudget budget = new BufferBudget(20_000); int[] received = {0};
        MemoryIpcLink link = generic(budget, 3, (f, s) -> { received[0]++; return true; }, () -> { });
        assertTrue(link.send(BinaryIpcCodecTest.frame(new Data("x".repeat(300))))); link.writeStep(2); link.close(); link.close();
        assertEquals(0, budget.used()); assertEquals(0, received[0]); assertEquals(BinaryIpcCodec.Error.ABORTED, link.error());
    }

    @Test void sinkAndEndCallbackFailuresHaveBoundedDiagnosticsAndNoLeaks() {
        BufferBudget budget = new BufferBudget(20_000);
        MemoryIpcLink link = generic(budget, 7, (f, s) -> { throw new IllegalStateException("private-content"); },
                () -> { throw new IllegalStateException("private-end"); });
        assertTrue(link.send(BinaryIpcCodecTest.frame(new Control(Type.HELLO, "")))); drain(link);
        assertTrue(link.closed()); assertEquals(BinaryIpcCodec.Error.SINK, link.error()); assertEquals(0, budget.used());
        assertFalse(link.error().name().contains("private"));
    }

    @Test void formalFrameWithWrongTicketCannotFreeAnyGroup() {
        try (Fixture f = new Fixture()) {
            Frame good = f.frame(Direction.S_H, new Signal(Type.HELLO), ""); Envelope e = good.envelope();
            f.reject(Direction.S_H, new Frame(changed(good, new Id(9, 9), e.channel(), e.sender(), e.direction(), e.sequence()), good.payload()));
            assertTrue(f.model.host().alive); assertEquals(FakeRecoveryJournal.State.UNCERTAIN, f.journal.state());
        }
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3})
    void rightTicketDoesNotAuthorizeWrongChannelSenderDirectionOrSession(int wrong) {
        try (Fixture f = new Fixture()) {
            Frame good = f.frame(Direction.S_H, new Signal(Type.HELLO), ""); Envelope e = good.envelope();
            Envelope bad = new Envelope(wrong == 3 ? new Id(8, 8) : e.session(), e.ticket(),
                    wrong == 0 ? new Id(8, 8) : e.channel(), wrong == 1 ? new Id(8, 8) : e.sender(),
                    wrong == 2 ? Direction.H_S : e.direction(), e.sequence());
            f.reject(Direction.S_H, new Frame(bad, good.payload())); assertEquals(State.QUARANTINED, f.model.state());
        }
    }

    @Test void claimedSenderIdCannotReplaceActualLocalSenderCapability() {
        try (Fixture f = new Fixture()) {
            WatchdogWireBridge bridge = new WatchdogWireBridge(f.model, Direction.S_H, new Object(),
                    new Id(1, 1), new Id(1, 2), new Id(1, 100), new Id(1, 20), new Id(1, 30), f.registry, drainSink((frame, n) -> true));
            try (MemoryIpcLink link = new MemoryIpcLink(LIMITS, f.budget.account(10_000), 7, bridge, bridge::transportEnded)) {
                assertTrue(link.send(bridge.outbound(1, new Signal(Type.HELLO), ""))); drain(link);
                assertEquals(State.QUARANTINED, f.model.state()); assertTrue(link.closed());
            }
        }
    }

    @ParameterizedTest @ValueSource(longs = {1, 3})
    void duplicateOrSkippedSequenceIsRejectedByExistingModel(long sequence) {
        try (Fixture f = new Fixture()) {
            f.send(Direction.S_H, Type.HELLO);
            Frame good = f.frame(Direction.S_H, new Signal(Type.LEASE), ""); Envelope e = good.envelope();
            f.reject(Direction.S_H, new Frame(changed(good, e.ticket(), e.channel(), e.sender(), e.direction(), sequence), good.payload()));
        }
    }

    @Test void formalMessageInWrongLifecyclePhaseDoesNotStartWorker() {
        try (Fixture f = new Fixture()) {
            f.reject(Direction.S_H, f.frame(Direction.S_H, new Signal(Type.START), "")); assertNull(f.model.worker());
        }
    }

    @Test void delayedResultAfterCleanupOrGroupAbortCannotRestoreSuccess() {
        for (boolean groupEnded : new boolean[]{false, true}) {
            try (Fixture f = new Fixture()) {
                f.running(); Frame late = f.frame(Direction.W_H, new ResultBegin(8, 2, 2), "");
                f.model.stop(f.ticket);
                if (groupEnded) f.kernel.endGroup(f.model.inner());
                f.reject(Direction.W_H, late); assertFalse(f.model.delivered()); assertEquals(State.QUARANTINED, f.model.state());
            }
        }
    }

    @Test void byteEofDuringHandshakeIsNotAnEndProof() {
        try (Fixture f = new Fixture()) {
            MemoryIpcLink link = f.link(Direction.S_H); link.halfClose(); link.readStep(1);
            assertTrue(link.closed()); assertNull(link.error()); assertTrue(f.model.host().alive);
            assertEquals(State.QUARANTINED, f.model.state()); assertNull(f.model.worker());
        }
    }

    @Test void completeResultBytesAndCleanTransportEofStillDoNotProveGroupEnd() {
        try (Fixture f = new Fixture()) {
            f.running(); f.send(Direction.W_H, new ResultBegin(3, 1, 1), "");
            f.send(Direction.W_H, new ResultChunk(3), "abc"); f.send(Direction.W_H, Type.RESULT_END);
            MemoryIpcLink link = f.link(Direction.W_H); link.halfClose(); link.readStep(1);
            assertTrue(link.closed()); assertNull(link.error()); assertTrue(f.model.worker().alive);
            assertFalse(f.model.delivered()); assertEquals(State.QUARANTINED, f.model.state());
        }
    }

    @Test void timeoutRemainsModelDecisionWhileMemoryChannelHasNoProgress() {
        try (Fixture f = new Fixture()) {
            f.running(); MemoryIpcLink link = f.link(Direction.W_H);
            assertTrue(link.send(f.frame(Direction.W_H, new ResultBegin(8, 2, 2), ""))); link.writeStep(999);
            long held = f.budget.used(); f.clock.advance(120); f.model.tick();
            assertEquals(State.CLEANING, f.model.state()); assertEquals(held, f.budget.used());
            f.clock.advance(15); f.model.tick(); assertEquals(State.QUARANTINED, f.model.state());
            link.close(); assertFalse(f.model.delivered()); assertTrue(f.model.worker().alive);
        }
    }

    @Test void replayIdentityRegistryIsBoundedAndRejectsRebinding() {
        WireIdentityRegistry registry = new WireIdentityRegistry(2); Object capability = new Object(); Id id = new Id(1, 1);
        registry.bind(WireIdentityRegistry.Domain.TICKET, id, capability);
        registry.bind(WireIdentityRegistry.Domain.TICKET, id, capability); assertEquals(1, registry.size());
        assertThrows(IllegalArgumentException.class, () -> registry.bind(WireIdentityRegistry.Domain.TICKET, id, new Object()));
        assertThrows(IllegalArgumentException.class, () -> registry.bind(WireIdentityRegistry.Domain.TICKET, new Id(1, 2), capability));
        registry.bind(WireIdentityRegistry.Domain.CHANNEL, id, new Object());
        assertThrows(IllegalStateException.class, () -> registry.bind(WireIdentityRegistry.Domain.SENDER, id, new Object()));
        assertEquals(2, registry.size());
    }

    @Test void interruptedResultPayloadPoisonsOnlyTheActiveConnectionAndPreservesGroupOwnership() {
        try (Fixture f = new Fixture()) {
            f.running(); f.send(Direction.W_H, new ResultBegin(8, 1, 1), "");
            byte[] bytes = BinaryIpcCodecTest.encode(f.frame(Direction.W_H, new ResultChunk(8), "abcdefgh"), LIMITS);
            MemoryIpcLink link = f.link(Direction.W_H);
            for (int at = 0; at < HEADER + 1;) {
                int count = link.offerRaw(bytes, at, HEADER + 1 - at); assertTrue(count > 0);
                at += count; link.readStep(count);
            }
            link.halfClose(); link.readStep(1);
            assertEquals(BinaryIpcCodec.Error.TRUNCATED, link.error()); assertEquals(State.QUARANTINED, f.model.state());
            assertTrue(f.model.worker().alive); assertEquals(FakeJobKernel.HandleState.OPEN, f.model.inner().state);
        }
    }

    @Test void completeSuccessNotificationAfterStopCannotRestoreRunning() {
        try (Fixture f = new Fixture()) {
            f.running(); f.send(Direction.W_H, new ResultBegin(1, 1, 1), "");
            f.send(Direction.W_H, new ResultChunk(1), "x"); f.send(Direction.W_H, Type.RESULT_END);
            Frame success = f.frame(Direction.H_S, new Signal(Type.RESULT_AVAILABLE), ""); f.model.stop(f.ticket);
            f.reject(Direction.H_S, success); assertFalse(f.model.delivered());
        }
    }

    @Test void decodedDataCannotExceedCumulativeResultDeclaredToExistingModel() {
        try (Fixture f = new Fixture()) {
            f.running(); f.send(Direction.W_H, new ResultBegin(1, 1, 1), "");
            f.reject(Direction.W_H, f.frame(Direction.W_H, new ResultChunk(2), "xx"));
            assertEquals(State.QUARANTINED, f.model.state()); assertTrue(f.model.worker().alive);
        }
    }

    @Test void formallyValidEmptyGroupClaimCannotOverrideActualLiveMembership() {
        try (Fixture f = new Fixture()) {
            f.running(); f.model.stop(f.ticket);
            Frame envelope = f.frame(Direction.H_S, new Signal(Type.STOP_ACCEPTED), "");
            Frame lie = new Frame(envelope.envelope(), new GroupProof(new Id(1, 30), f.model.inner().job.revision, true, true, 0, 0));
            f.reject(Direction.H_S, lie); assertTrue(f.model.worker().alive);
            assertEquals(FakeJobKernel.HandleState.OPEN, f.model.inner().state); assertFalse(f.model.delivered());
        }
    }
}
