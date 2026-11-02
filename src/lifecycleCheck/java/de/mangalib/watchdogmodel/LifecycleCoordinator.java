package de.mangalib.watchdogmodel;

import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import static de.mangalib.watchdogmodel.BinaryIpcCodec.*;
import static de.mangalib.watchdogmodel.FakeJobKernel.*;
import static de.mangalib.watchdogmodel.WatchdogModel.*;

/**
 * Isolierte Verdrahtung: Lifecycle ausschließlich im WatchdogModel, Byteprüfung
 * im bestehenden Codec/Bridgepfad. Jeder Kernel-/Kanalendbeleg bleibt ein eigenes
 * injiziertes Fakeereignis. Kein Host-main, ProcessBuilder, Thread oder OS-Port.
 */
final class LifecycleCoordinator implements AutoCloseable {
    // Zusätzliche Gruppen-/Ressourcenmeldungen: kein stiller Mischbetrieb mit
    // dem historischen reinen stdio-Probeskript ohne K/J.
    static final String PROFILE = SyntheticProtocol.PROFILE + ":K-H-J";
    final WatchdogModel model;
    private final Id session;
    private final BufferBudget budget = new BufferBudget(16_384);
    private final Map<Direction, WatchdogWireBridge> bridges = new EnumMap<>(Direction.class);
    private final Map<Direction, MemoryIpcLink> links = new EnumMap<>(Direction.class);
    private WireIdentityRegistry registry;
    private ResultSink result;

    LifecycleCoordinator(Id session, Clock clock, FakeJobKernel kernel, FakeRecoveryJournal journal) {
        // Frische, pro Supervisor gebundene Bootstrap-ID vom injizierten Testendpunkt.
        // Keine aus lokaler Auftragsgeneration abgeleitete globale Peeridentität.
        this.session = Objects.requireNonNull(session);
        model = new WatchdogModel(Objects.requireNonNull(clock), Objects.requireNonNull(kernel), Objects.requireNonNull(journal));
    }

    boolean initialize(FakeRecoveryJournal.Baseline baseline) { return model.initialize(baseline); }

    Optional<Ticket> request(Operation operation) {
        return bind(model.admit(operation));
    }
    Optional<Ticket> requestHostProbe() { return bind(model.admitHostProbe()); }
    private Optional<Ticket> bind(Optional<Ticket> admitted) {
        if (admitted.isPresent()) {
            // Kein Reset zum Entsperren: erst das Modell darf einen neuen Auftrag annehmen.
            bridges.clear(); links.clear(); registry = new WireIdentityRegistry(64); result = new ResultSink();
        }
        return admitted;
    }

    boolean prepareOuter(Ticket ticket) { return invoke(ticket, () -> { model.prepareOuter(ticket); return true; }); }
    boolean beginHost(Ticket ticket) { return invoke(ticket, () -> { model.beginHost(ticket); return true; }); }
    boolean hostReturned(Ticket ticket, Identity identity) { return invoke(ticket, () -> model.hostReturned(ticket, identity)); }
    boolean prepareInner(Ticket ticket) { return invoke(ticket, () -> { model.prepareInner(ticket); return true; }); }
    boolean beginWorker(Ticket ticket) { return invoke(ticket, () -> { model.beginWorker(ticket); return true; }); }
    boolean workerReturned(Ticket ticket, Identity identity) { return invoke(ticket, () -> model.workerReturned(ticket, identity)); }
    boolean workerEnded(Ticket ticket, int exit) { return invoke(ticket, () -> model.workerEnded(ticket, exit)); }
    boolean hostEnded(Ticket ticket, boolean crashed) { return invoke(ticket, () -> model.hostEnded(ticket, crashed)); }
    boolean stop(Ticket ticket) { return model.stop(ticket); }
    boolean escalate(Ticket ticket) { return model.escalate(ticket); }
    void tick() { model.tick(); }
    boolean finish(Ticket ticket, Observation outerEnd, IoEnd io) { return invoke(ticket, () -> model.finish(ticket, outerEnd, io)); }
    boolean retire(Ticket ticket, Observation outerEnd, IoEnd io) { return invoke(ticket, () -> model.retireAfterIncident(ticket, outerEnd, io)); }

    private boolean invoke(Ticket ticket, BooleanSupplier action) {
        try { return action.getAsBoolean(); }
        catch (IllegalArgumentException | IllegalStateException failure) { return model.controlFailed(ticket); }
    }

    private WatchdogWireBridge bridge(Direction direction) {
        if (registry == null || model.ticket() == null || model.channel(direction) == null)
            throw new IllegalStateException("channel-unavailable");
        return bridges.computeIfAbsent(direction, d -> {
            long generation = model.ticket().generation;
            return new WatchdogWireBridge(model, d, model.channel(d).principal,
                    session, new Id(8, generation), new Id(10 + d.ordinal(), generation),
                    new Id(9, d == Direction.S_H ? 1 : d == Direction.W_H ? 3 : 2),
                    new Id(20, generation), registry, result);
        });
    }

    MemoryIpcLink link(Direction direction) {
        return links.computeIfAbsent(direction, d -> {
            WatchdogWireBridge bridge = bridge(d);
            Sink checked = new Sink() {
                public boolean accept(Frame frame, long bytes) {
                    if (frame.payload() instanceof Control control && !text(control.type()).equals(control.text())) return false;
                    return bridge.accept(frame, bytes);
                }
                public boolean dataByte(Envelope envelope, int value) { return bridge.dataByte(envelope, value); }
                public void discard(Envelope envelope) { bridge.discard(envelope); }
            };
            return new MemoryIpcLink(SyntheticProtocol.LIMITS, budget.account(4096), 17,
                    checked, bridge::transportEnded);
        });
    }

    Frame frame(Direction direction, Body body, String text) { return bridge(direction).outbound(model.nextSequence(direction), body, text); }
    boolean send(Direction direction, Type type) { return send(direction, new Signal(type), text(type)); }
    boolean send(Direction direction, Body body, String text) {
        return invoke(model.ticket(), () -> sendFrame(direction, frame(direction, body, text)));
    }
    boolean sendFrame(Direction direction, Frame frame) {
        return invoke(model.ticket(), () -> {
            MemoryIpcLink link = link(direction);
            if (!link.send(frame)) return false; // Backpressure, keine zweite Queue/automatische Wiederholung.
            for (int steps = 0; steps < 512 && !link.closed() && (link.sending() || link.buffered() != 0); steps++) {
                link.writeStep(3); link.readStep(5);
            }
            if (link.sending() || link.buffered() != 0) return model.controlFailed(model.ticket());
            return !link.closed();
        });
    }

    void eof(Direction direction) { MemoryIpcLink link = link(direction); link.halfClose(); link.readStep(1); }
    long reservedBytes() { return budget.used(); }
    boolean syntheticResultAccepted() { return result != null && result.accepted && !result.invalid; }

    private String text(Type type) {
        return switch (type) {
            case HELLO, READY -> model.hostProbe() ? SyntheticProtocol.PROFILE : PROFILE;
            case START -> SyntheticProtocol.REQUEST;
            case RESULT_AVAILABLE -> SyntheticProtocol.RESPONSE;
            default -> "";
        };
    }

    /** Begrenzter synthetischer Consumer, kein Importmap-Parser oder Persistenzpfad. */
    private static final class ResultSink implements Sink {
        private final byte[] expected = SyntheticProtocol.RESPONSE.getBytes(StandardCharsets.UTF_8);
        private int offset;
        private boolean accepted, invalid;
        public boolean dataByte(Envelope envelope, int value) {
            if (invalid || accepted || offset == expected.length || value != (expected[offset] & 255)) return false;
            offset++; return true;
        }
        public boolean accept(Frame frame, long bytes) {
            accepted = !invalid && !accepted && offset == expected.length
                    && frame.payload() instanceof DataCount count && count.bytes() == expected.length;
            return accepted;
        }
        public void discard(Envelope envelope) { invalid = true; accepted = false; }
    }

    /** Nur Speicherressourcen lösen; weder Gruppenende noch Slotfreigabe behaupten. */
    @Override public void close() { links.values().forEach(MemoryIpcLink::close); }
}
