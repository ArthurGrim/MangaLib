package de.mangalib.watchdogmodel;

import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static de.mangalib.watchdogmodel.BinaryIpcCodec.*;
import static de.mangalib.watchdogmodel.FakeJobKernel.Role;
import static de.mangalib.watchdogmodel.WatchdogModel.*;
import static org.junit.jupiter.api.Assertions.*;

/** Deterministische Abschlussordnung; ausschließlich Speicherbytes und symbolische Jobs. */
class WatchdogClosureTest {
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
            assertTrue(model.initialize(journal.baseline()));
            ticket = model.admit(Operation.VOLUME).orElseThrow();
            model.prepareOuter(ticket); model.beginHost(ticket);
            assertTrue(model.hostReturned(ticket, new FakeJobKernel.Identity(100, 1, "host")));
        }
        WatchdogWireBridge bridge(Direction d) {
            return bridges.computeIfAbsent(d, key -> {
                Sink content = new Sink() {
                    public boolean accept(Frame frame, long bytes) { return true; }
                    public boolean dataByte(Envelope e, int b) { return true; }
                };
                return new WatchdogWireBridge(model, key, model.channel(key).principal,
                        new Id(1, 1), new Id(1, 2), new Id(1, 100 + key.ordinal()),
                        new Id(1, key == Direction.S_H ? 20 : key == Direction.W_H ? 22 : 21),
                        new Id(1, 30), registry, content);
            });
        }
        MemoryIpcLink link(Direction d) {
            return links.computeIfAbsent(d, key -> new MemoryIpcLink(LIMITS, budget.account(10_000),
                    17, bridge(key), bridge(key)::transportEnded));
        }
        Frame frame(Direction d, Body body, String text) { return bridge(d).outbound(model.nextSequence(d), body, text); }
        void send(Direction d, Body body, String text) {
            MemoryIpcLink link = link(d); assertTrue(link.send(frame(d, body, text))); drain(link);
            assertFalse(link.closed(), () -> "unexpected transport end: " + link.error());
        }
        void send(Direction d, Type type) { send(d, new Signal(type), ""); }
        void running() {
            send(Direction.S_H, Type.HELLO); send(Direction.H_S, Type.READY);
            model.prepareInner(ticket); send(Direction.S_H, Type.START); model.beginWorker(ticket);
            assertTrue(model.workerReturned(ticket, new FakeJobKernel.Identity(101, 2, "worker")));
            send(Direction.H_W, Type.START); send(Direction.W_H, Type.STARTED);
        }
        void result() {
            send(Direction.W_H, new ResultBegin(7, 2, 2), "");
            send(Direction.W_H, new ResultChunk(7), "ä漢xy");
            send(Direction.W_H, Type.RESULT_END);
        }
        void eof(Direction d) { link(d).halfClose(); link(d).readStep(1); }
        void end(Direction d) {
            send(d, Type.CHANNEL_END); eof(d); assertNull(link(d).error());
            assertTrue(link(d).closed()); assertNotEquals(State.QUARANTINED, model.state());
        }
        void innerComplete() {
            end(Direction.W_H); end(Direction.H_W); send(Direction.H_S, Type.RESULT_AVAILABLE);
            assertTrue(model.workerEnded(ticket, 0));
            send(Direction.H_S, new GroupEnd(kernel.observe(Role.H, model.inner())), "");
            send(Direction.H_S, Type.RESOURCES_CLOSED);
        }
        void channelsComplete() { innerComplete(); end(Direction.S_H); end(Direction.H_S); }
        IoEnd io() { return new IoEnd(ticket, true, true, true, true, true, true); }
        boolean finish(IoEnd io) { return model.finish(ticket, kernel.observe(Role.S, model.outer()), io); }
        void quarantined() {
            assertEquals(State.QUARANTINED, model.state()); assertFalse(model.delivered());
            assertTrue(model.admit(Operation.SERIES).isEmpty());
        }
        @Override public void close() { links.values().forEach(MemoryIpcLink::close); assertEquals(0, budget.used()); }
    }

    private static void drain(MemoryIpcLink link) {
        for (int i = 0; i < 10_000 && !link.closed() && (link.sending() || link.buffered() > 0); i++) {
            link.writeStep(3); link.readStep(5);
        }
        assertFalse(link.sending()); assertEquals(0, link.buffered());
    }
    private static void reject(Fixture f, Direction d, Frame frame) {
        assertTrue(f.link(d).send(frame)); drain(f.link(d));
        assertNotNull(f.link(d).error()); assertFalse(f.model.delivered());
    }

    @Test void completeWireClosureRequiresGroupsResourcesHandlesAndJournalBeforeDelivery() {
        try (Fixture f = new Fixture()) {
            f.running(); f.result(); f.channelsComplete();
            assertEquals(State.CLEANING, f.model.state()); assertFalse(f.model.delivered());
            assertFalse(f.finish(f.io())); // H lebt noch in K.
            assertTrue(f.model.hostEnded(f.ticket, false));
            assertTrue(f.finish(f.io())); assertTrue(f.model.delivered());
            assertTrue(f.kernel.closed(f.ticket)); assertEquals(0, f.budget.used());
            assertEquals(FakeRecoveryJournal.State.COMPLETED, f.journal.state());
        }
    }

    @Test void workerChannelEndIsOnlyCommunicationEvidenceWhileGroupsRemainAlive() {
        try (Fixture f = new Fixture()) {
            f.running(); f.result(); f.end(Direction.W_H);
            assertEquals(State.RUNNING, f.model.state()); assertTrue(f.model.worker().alive);
            assertTrue(f.model.host().alive); assertFalse(f.finish(f.io()));
            assertNull(f.model.cleanupDeadline()); assertEquals(7, f.model.resultBytes());
        }
    }

    @Test void abstractIoEndCannotReplaceMissingWireChannelEvidence() {
        try (Fixture f = new Fixture()) {
            f.running(); f.result(); f.send(Direction.H_S, Type.RESULT_AVAILABLE);
            f.model.workerEnded(f.ticket, 0);
            f.send(Direction.H_S, new GroupEnd(f.kernel.observe(Role.H, f.model.inner())), "");
            f.send(Direction.H_S, Type.RESOURCES_CLOSED); f.model.hostEnded(f.ticket, false);
            assertFalse(f.finish(f.io())); assertEquals(State.CLEANING, f.model.state());
        }
    }

    @Test void lastCloseMarkerWithoutEofStillPreventsCompletion() {
        try (Fixture f = new Fixture()) {
            f.running(); f.result(); f.innerComplete(); f.end(Direction.S_H);
            f.send(Direction.H_S, Type.CHANNEL_END); f.model.hostEnded(f.ticket, false);
            assertFalse(f.finish(f.io())); assertFalse(f.model.delivered());
            f.eof(Direction.H_S); assertTrue(f.finish(f.io())); assertTrue(f.model.delivered());
        }
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3, 4, 5})
    void everyExistingIoComponentRemainsMandatoryAfterAllChannelProofs(int missing) {
        try (Fixture f = new Fixture()) {
            f.running(); f.result(); f.channelsComplete(); f.model.hostEnded(f.ticket, false);
            IoEnd io = new IoEnd(f.ticket, missing != 0, missing != 1, missing != 2,
                    missing != 3, missing != 4, missing != 5);
            assertFalse(f.finish(io)); assertEquals(State.CLEANING, f.model.state());
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void closeOrJournalFailureCannotBeHealedByAlreadyCorrectEof(boolean journalFailure) {
        try (Fixture f = new Fixture()) {
            f.running(); f.result(); f.channelsComplete(); f.model.hostEnded(f.ticket, false);
            f.kernel.failClose = !journalFailure; f.journal.failCompletion = journalFailure;
            assertFalse(f.finish(f.io())); f.quarantined();
            assertFalse(f.model.eof(f.model.channel(Direction.H_S))); f.quarantined();
        }
    }

    @ParameterizedTest @EnumSource(Direction.class)
    void prematureChannelEofDuringOperationIsNotAGroupEnd(Direction d) {
        try (Fixture f = new Fixture()) {
            f.running(); f.eof(d); f.quarantined();
            assertTrue(f.model.worker().alive); assertTrue(f.model.host().alive);
            assertFalse(f.finish(f.io()));
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void eofBeforeOrDuringHandshakeNeverAuthorizesNormalCompletion(boolean hello) {
        try (Fixture f = new Fixture()) {
            if (hello) f.send(Direction.S_H, Type.HELLO);
            f.eof(Direction.H_S); f.quarantined(); assertNull(f.model.worker());
        }
    }

    @Test void eofDuringPartialResultDoesNotInventResultEnd() {
        try (Fixture f = new Fixture()) {
            f.running(); f.send(Direction.W_H, new ResultBegin(7, 2, 2), "");
            f.send(Direction.W_H, new ResultChunk(1), "x"); f.eof(Direction.W_H);
            f.quarantined(); assertEquals(7, f.model.resultBytes());
        }
    }

    @ParameterizedTest @ValueSource(ints = {1, 95, 97})
    void partialFrameAfterCloseMarkerIsNotCleanEof(int length) {
        try (Fixture f = new Fixture()) {
            f.running(); f.result(); f.send(Direction.W_H, Type.CHANNEL_END);
            byte[] bytes = BinaryIpcCodecTest.encode(f.frame(Direction.W_H, new Signal(Type.STATUS), "ab"), LIMITS);
            MemoryIpcLink link = f.link(Direction.W_H);
            for (int at = 0; at < length;) {
                int count = link.offerRaw(bytes, at, length - at); assertTrue(count > 0);
                at += count; link.readStep(count);
            }
            f.eof(Direction.W_H); assertEquals(BinaryIpcCodec.Error.TRUNCATED, link.error());
            f.quarantined(); assertFalse(f.model.eof(f.model.channel(Direction.W_H)));
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void duplicateMarkerOrLateFrameAfterMarkerPoisonsTheChannel(boolean duplicate) {
        try (Fixture f = new Fixture()) {
            f.running(); f.result(); f.send(Direction.W_H, Type.CHANNEL_END);
            reject(f, Direction.W_H, f.frame(Direction.W_H,
                    new Signal(duplicate ? Type.CHANNEL_END : Type.STATUS), ""));
            f.quarantined(); assertFalse(f.model.eof(f.model.channel(Direction.W_H)));
        }
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3, 4})
    void wrongTicketSenderChannelSequenceOrSessionCannotCloseTheChannel(int wrong) {
        try (Fixture f = new Fixture()) {
            f.running(); f.result(); Frame good = f.frame(Direction.W_H, new Signal(Type.CHANNEL_END), "");
            Envelope e = good.envelope(); Id bad = new Id(9, 9);
            Envelope corrupt = new Envelope(wrong == 4 ? bad : e.session(), wrong == 0 ? bad : e.ticket(),
                    wrong == 2 ? bad : e.channel(), wrong == 1 ? bad : e.sender(), e.direction(),
                    wrong == 3 ? e.sequence() + 1 : e.sequence());
            reject(f, Direction.W_H, new Frame(corrupt, good.payload())); f.quarantined();
            assertTrue(f.model.worker().alive); assertFalse(f.finish(f.io()));
        }
    }

    @ParameterizedTest @EnumSource(value = Type.class, names = {"RESULT_AVAILABLE", "CHANNEL_END"})
    void lateSuccessOrCloseAfterStopCannotRestoreSuccess(Type type) {
        try (Fixture f = new Fixture()) {
            f.running(); f.result(); f.model.stop(f.ticket);
            Direction d = type == Type.RESULT_AVAILABLE ? Direction.H_S : Direction.W_H;
            reject(f, d, f.frame(d, new Signal(type), "")); f.quarantined();
            assertFalse(f.model.eof(f.model.channel(d))); assertEquals(7, f.model.resultBytes());
        }
    }

    @Test void explicitErrorThenBoundaryEofRemainsAnIncident() {
        try (Fixture f = new Fixture()) {
            f.running(); f.result(); f.send(Direction.W_H, Type.ERROR); f.eof(Direction.W_H);
            f.quarantined(); assertFalse(f.model.workerEnded(f.ticket, 0) && f.model.delivered());
        }
    }

    @Test void abortAfterAcceptedCloseMarkerIsNotCleanEof() {
        try (Fixture f = new Fixture()) {
            f.running(); f.result(); f.send(Direction.W_H, Type.CHANNEL_END); f.link(Direction.W_H).close();
            assertEquals(BinaryIpcCodec.Error.ABORTED, f.link(Direction.W_H).error()); f.quarantined();
        }
    }

    @Test void malformedClosePayloadIsRejectedByEncoderAndDecoder() {
        try (Fixture f = new Fixture()) {
            f.running(); f.result();
            Frame good = f.frame(Direction.W_H, new Signal(Type.CHANNEL_END), "");
            assertThrows(IllegalArgumentException.class, () -> f.link(Direction.W_H).send(
                    new Frame(good.envelope(), new Control(Type.CHANNEL_END, "x"))));
            byte[] bytes = BinaryIpcCodecTest.encode(good, LIMITS);
            java.nio.ByteBuffer.wrap(bytes).putLong(8, 1);
            for (int at = 0; at < bytes.length && !f.link(Direction.W_H).closed();) {
                int count = f.link(Direction.W_H).offerRaw(bytes, at, bytes.length - at);
                assertTrue(count > 0); at += count; f.link(Direction.W_H).readStep(count);
            }
            assertEquals(BinaryIpcCodec.Error.LENGTH, f.link(Direction.W_H).error()); f.quarantined();
        }
    }

    @Test void cleanupDeadlineIsNotExtendedOrHealedBySuccessfulChannelClosure() {
        try (Fixture f = new Fixture()) {
            f.running(); f.result(); f.channelsComplete(); Long deadline = f.model.cleanupDeadline();
            f.clock.advance(15); f.model.tick(); f.quarantined();
            assertEquals(deadline, f.model.cleanupDeadline()); f.model.hostEnded(f.ticket, false);
            assertFalse(f.finish(f.io())); assertEquals(FakeRecoveryJournal.State.UNCERTAIN, f.journal.state());
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void crashCannotTurnWorkerChannelEndIntoMissingGroupEvidence(boolean supervisorCrash) {
        try (Fixture f = new Fixture()) {
            f.running(); f.result(); f.end(Direction.W_H);
            FakeJobKernel.Observation beforeCrash = f.kernel.observe(Role.S, f.model.outer());
            if (supervisorCrash) f.model.supervisorFailed(f.ticket); else f.model.hostEnded(f.ticket, true);
            // Nach Ownerverlust nicht über dessen verlorenes Handle erneut beobachten.
            f.quarantined(); assertFalse(f.model.finish(f.ticket, beforeCrash, f.io()));
            assertTrue(f.model.worker().alive);
        }
    }

    @Test void channelCloseOrderRequiresPrecedingEvidence() {
        for (Direction d : Direction.values()) {
            try (Fixture f = new Fixture()) {
                f.running(); // W_H: Ergebnis fehlt; andere: vorangehender EOF-/J-Beleg fehlt.
                reject(f, d, f.frame(d, new Signal(Type.CHANNEL_END), "")); f.quarantined();
            }
        }
    }

    @Test void missingInnerGroupEvidencePreventsHostChannelClosureAndEventuallyQuarantines() {
        try (Fixture f = new Fixture()) {
            f.running(); f.result(); f.end(Direction.W_H); f.end(Direction.H_W);
            f.send(Direction.H_S, Type.RESULT_AVAILABLE); f.model.workerEnded(f.ticket, 0);
            assertFalse(f.finish(f.io()));
            f.clock.advance(15); f.model.tick(); f.quarantined();
            assertFalse(f.model.eof(f.model.channel(Direction.H_S)));
            assertEquals(FakeJobKernel.HandleState.OPEN, f.model.inner().state);
        }
    }

    @Test void incompleteOuterObservationCannotBeReplacedByAllFourChannelEnds() {
        try (Fixture f = new Fixture()) {
            f.running(); f.result(); f.channelsComplete(); f.model.hostEnded(f.ticket, false);
            FakeJobKernel.Observation observed = f.kernel.observe(Role.S, f.model.outer());
            FakeJobKernel.Observation incomplete = new FakeJobKernel.Observation(observed.job(), observed.revision(),
                    false, observed.stable(), observed.active(), observed.members(), observed.accounting());
            assertFalse(f.model.finish(f.ticket, incomplete, f.io()));
            assertFalse(f.model.delivered()); assertEquals(FakeJobKernel.HandleState.OPEN, f.model.outer().state);
        }
    }

    @Test void deliveredOutcomeAndNextTicketCannotBeChangedByOldFramesOrRepeatedExit() {
        try (Fixture f = new Fixture()) {
            f.running(); f.result(); f.channelsComplete(); f.model.hostEnded(f.ticket, false);
            assertFalse(f.model.workerEnded(f.ticket, 7)); // Erster Exit bleibt maßgeblich.
            assertTrue(f.finish(f.io())); assertTrue(f.model.delivered());
            Channel old = f.model.channel(Direction.W_H);
            assertFalse(f.model.accept(old, old.principal, new Message(f.ticket, 1, 99, 96, 96, new Signal(Type.ERROR))));
            assertTrue(f.model.delivered()); assertEquals(7, f.model.resultBytes());
            byte[] bytes = {1}; assertEquals(0, f.link(Direction.W_H).offerRaw(bytes, 0, 1));
            assertFalse(f.link(Direction.W_H).send(f.frame(Direction.W_H, new Signal(Type.STATUS), "")));
            Ticket next = f.model.admit(Operation.SERIES).orElseThrow();
            assertFalse(f.model.eof(old)); assertFalse(f.model.workerEnded(f.ticket, 0));
            assertSame(next, f.model.ticket()); assertEquals(State.PREPARING, f.model.state());
        }
    }
}
