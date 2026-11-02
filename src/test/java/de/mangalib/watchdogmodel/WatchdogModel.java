package de.mangalib.watchdogmodel;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import static de.mangalib.watchdogmodel.FakeJobKernel.*;

/**
 * Ausschließlich sequenzielles Vertragsmodell im Testbereich, kein ausführbarer Host.
 * Objektidentität simuliert Capabilities; weder Windows-Authentisierung noch echte
 * monotone Interprozesszeit, Framing, Blockaden oder Persistenz werden implementiert.
 */
final class WatchdogModel {
    enum State { UNAVAILABLE, IDLE, PREPARING, HOST_STARTING, HANDSHAKE, HOST_READY,
        WORKER_STARTING, RUNNING, CLEANING, QUARANTINED }
    enum Operation { VOLUME, SERIES }
    enum Direction { S_H, H_S, H_W, W_H }
    enum ChannelState { OPEN, END_EXPECTED, EOF_CONFIRMED }
    enum Type { HELLO, READY, START, STARTED, STATUS, LEASE, STOP, STOP_ACCEPTED,
        RESULT_END, RESULT_AVAILABLE, RESOURCES_CLOSED, ERROR, CHANNEL_END }
    sealed interface Body permits Signal, ResultBegin, ResultChunk, GroupEnd { }
    record Signal(Type type) implements Body { }
    record ResultBegin(long bytes, int fields, int depth) implements Body { }
    record ResultChunk(long bytes) implements Body { }
    record GroupEnd(Observation evidence) implements Body { }
    record Message(Ticket ticket, int version, long sequence, long declaredBytes,
                   long deliveredBytes, Body body) { }
    record IoEnd(Ticket ticket, boolean readers, boolean writer, boolean channels,
                 boolean callbacks, boolean nativeResources, boolean temporaryResources) {
        boolean complete() { return readers && writer && channels && callbacks && nativeResources && temporaryResources; }
    }

    /** Generation ist Diagnose; nur dieselbe, nie neu konstruierbare Instanz berechtigt. */
    static final class Ticket {
        final long generation;
        final Operation operation;
        private Ticket(long generation, Operation operation) { this.generation = generation; this.operation = operation; }
    }
    static final class Channel {
        final Ticket ticket;
        final Direction direction;
        final Object principal;
        private long sequence;
        private ChannelState state = ChannelState.OPEN;
        private Channel(Ticket ticket, Direction direction, Object principal) {
            this.ticket = ticket; this.direction = direction; this.principal = principal;
        }
    }
    /** Einheiten Sekunden, kein System.nanoTime, Thread, Sleep oder realer Timer. */
    static final class Clock {
        private long now;
        long now() { return now; }
        void advance(long seconds) {
            if (seconds < 0) throw new IllegalArgumentException("monotone-clock");
            now = Math.addExact(now, seconds);
        }
    }
    static final long CONTROL_BYTES = 4096, CHUNK_BYTES = 65536;
    // Nur Transport-Messkandidat, keine neue fachliche Begrenzung produktiver Importmaps.
    static final long RESULT_CANDIDATE = 32L * 1024 * 1024;
    static final int FIELDS = 30_032, DEPTH = 4;

    private final Clock clock;
    private final FakeJobKernel kernel;
    private final FakeRecoveryJournal journal;
    private final Object supervisor = new Object();
    private final Map<Direction, Channel> channels = new EnumMap<>(Direction.class);
    private State state = State.UNAVAILABLE;
    private Ticket ticket;
    private long generation, startDeadline, operationDeadline, lastLease;
    private Long cleanupDeadline;
    private Handle outer, inner;
    private Start hostStart, workerStart;
    private Instance host, worker;
    private boolean hello, startSent, workerCommand, resultStarted, resultComplete,
            resultAvailable, workerSuccess, innerEnd, innerClosed, supervisorAlive = true, hostResponsive = true;
    private long resultRemaining, resultTotal;
    private boolean channelProofsRequired, workerExitObserved;
    private boolean innerTerminationFailed, outerTerminationFailed;
    private boolean hostProbe, probeSuccess, probeStartReserved;

    WatchdogModel(Clock clock, FakeJobKernel kernel, FakeRecoveryJournal journal) {
        this.clock = java.util.Objects.requireNonNull(clock);
        this.kernel = java.util.Objects.requireNonNull(kernel);
        this.journal = java.util.Objects.requireNonNull(journal);
    }

    boolean initialize(FakeRecoveryJournal.Baseline baseline) {
        if (state != State.UNAVAILABLE) return false;
        state = journal.claim(supervisor, baseline) ? State.IDLE : State.QUARANTINED;
        return state == State.IDLE;
    }

    Optional<Ticket> admit(Operation operation) {
        if (state != State.IDLE || operation == null || !supervisorAlive) return Optional.empty();
        long start = Math.addExact(clock.now(), 30);
        long end = Math.addExact(clock.now(), operation == Operation.VOLUME ? 120 : 3600);
        Ticket next = new Ticket(Math.incrementExact(generation), operation);
        if (!journal.intent(supervisor, next)) { state = State.QUARANTINED; return Optional.empty(); }
        generation = next.generation; ticket = next; startDeadline = start; operationDeadline = end;
        cleanupDeadline = null; outer = null; inner = null; host = null; worker = null;
        hostStart = null; workerStart = null; channels.clear();
        hello = startSent = workerCommand = resultStarted = resultComplete = resultAvailable = false;
        workerSuccess = innerEnd = innerClosed = false; resultRemaining = resultTotal = 0;
        hostResponsive = true;
        channelProofsRequired = workerExitObserved = false;
        innerTerminationFailed = outerTerminationFailed = false;
        hostProbe = probeSuccess = probeStartReserved = false;
        state = State.PREPARING;
        return Optional.of(ticket);
    }

    /** Enger H-ohne-J/W-Prüfumfang, kein produktiver Importauftrag. */
    Optional<Ticket> admitHostProbe() {
        Optional<Ticket> admitted = admit(Operation.VOLUME);
        if (admitted.isPresent()) hostProbe = true;
        return admitted;
    }
    boolean reserveHostProbeStart(Ticket current) {
        tick();
        if (!active(current) || !hostProbe || state != State.PREPARING || probeStartReserved || !intentConfirmed()) return false;
        probeStartReserved = true; return true;
    }

    void prepareOuter(Ticket current) {
        phase(current, State.PREPARING);
        if (hostProbe && !probeStartReserved) { protocolFault(); throw new IllegalStateException("probe-start-reservation-required"); }
        outer = kernel.create(ticket, Role.S, Kind.K, null);
        journal.bind(supervisor, ticket, outer.job, null);
        kernel.configure(Role.S, outer);
    }

    void beginHost(Ticket current) {
        phase(current, State.PREPARING);
        hostStart = kernel.begin(Role.S, outer, Role.H, null); state = State.HOST_STARTING;
    }

    boolean hostReturned(Ticket current, Identity identity) {
        if (!active(current) || hostStart == null || hostStart.returned) return false;
        tick();
        host = kernel.returnStart(hostStart, identity);
        if (state != State.HOST_STARTING) return false; // Später Erwerb bleibt owned, niemals Resume.
        if (!intentConfirmed()) return protocolFault();
        kernel.resume(Role.S, outer, host);
        channels.put(Direction.S_H, new Channel(ticket, Direction.S_H, supervisor));
        channels.put(Direction.H_S, new Channel(ticket, Direction.H_S, host));
        lastLease = clock.now(); state = State.HANDSHAKE;
        return true;
    }

    void prepareInner(Ticket current) {
        phase(current, State.HOST_READY);
        if (hostProbe) { protocolFault(); throw new IllegalStateException("probe-has-no-inner-job"); }
        if (inner != null) throw new IllegalStateException("inner-already-created");
        if (host == null || !kernel.matches(host, host.identity) || !kernel.member(host, outer.job)) {
            protocolFault(); throw new IllegalStateException("host-membership-unconfirmed");
        }
        inner = kernel.create(ticket, Role.H, Kind.J, host);
        journal.bind(supervisor, ticket, outer.job, inner.job);
        kernel.configure(Role.H, inner);
    }

    void beginWorker(Ticket current) {
        phase(current, State.HOST_READY);
        if (!startSent) throw new IllegalStateException("start-command-required");
        workerStart = kernel.begin(Role.H, inner, Role.W, host); state = State.WORKER_STARTING;
    }

    boolean workerReturned(Ticket current, Identity identity) {
        if (!active(current) || workerStart == null || workerStart.returned) return false;
        tick(); worker = kernel.returnStart(workerStart, identity);
        if (state != State.WORKER_STARTING) return false;
        if (!intentConfirmed()) return protocolFault();
        kernel.resume(Role.H, inner, worker);
        channels.put(Direction.H_W, new Channel(ticket, Direction.H_W, host));
        channels.put(Direction.W_H, new Channel(ticket, Direction.W_H, worker));
        return true; // Noch kein RUNNING, Worker-Handshake fehlt.
    }

    boolean startFailed(Ticket current, Role role) {
        if (!active(current)) return false;
        Start pending = role == Role.H ? hostStart : role == Role.W ? workerStart : null;
        if (pending == null || pending.returned) return false;
        tick(); kernel.failStart(pending); stop(current); return true;
    }

    boolean accept(Channel channel, Object principal, Message message) {
        // Alte Aufträge können keine neue Gruppe terminieren oder freigeben.
        if (message == null || !active(message.ticket())) return false;
        tick();
        if (channel == null || channels.get(channel.direction) != channel || channel.ticket != ticket
                || channel.principal != principal || message.version() != 1
                || message.sequence() != channel.sequence + 1 || message.body() == null
                || message.declaredBytes() < 1 || message.deliveredBytes() != message.declaredBytes()) return protocolFault();
        if (channel.state != ChannelState.OPEN) return protocolFault();
        if (principal instanceof Instance process && (!process.alive || !kernel.matches(process, process.identity))) return protocolFault();
        long limit = message.body() instanceof ResultChunk ? CHUNK_BYTES : CONTROL_BYTES;
        if (message.declaredBytes() > limit) return protocolFault();
        channel.sequence++;
        try {
            if (!dispatch(channel.direction, message)) return protocolFault();
        } catch (IllegalStateException failure) { return protocolFault(); }
        return true;
    }

    private boolean dispatch(Direction direction, Message message) {
        Body body = message.body();
        if (body instanceof Signal signal) return signal(direction, signal.type());
        if (body instanceof ResultBegin result) {
            if (direction != Direction.W_H || state != State.RUNNING || resultStarted
                    || result.bytes() < 1 || result.bytes() > RESULT_CANDIDATE
                    || result.fields() < 0 || result.fields() > FIELDS || result.depth() < 1 || result.depth() > DEPTH) return false;
            resultStarted = true; resultRemaining = result.bytes(); resultTotal = result.bytes(); return true;
        }
        if (body instanceof ResultChunk chunk) {
            if (direction != Direction.W_H || state != State.RUNNING || !resultStarted || resultComplete
                    || chunk.bytes() < 1 || chunk.bytes() > resultRemaining || chunk.bytes() > message.deliveredBytes()) return false;
            resultRemaining -= chunk.bytes(); return true;
        }
        if (body instanceof GroupEnd end) {
            if (direction != Direction.H_S || state != State.CLEANING || innerEnd || inner == null
                    || !kernel.provesEmpty(Role.H, inner, end.evidence())) return false;
            innerEnd = true; return true;
        }
        return false;
    }

    private boolean signal(Direction direction, Type type) {
        if (type == null) return false;
        return switch (type) {
            case HELLO -> {
                if (direction != Direction.S_H || state != State.HANDSHAKE || hello) yield false;
                hello = true; yield true;
            }
            case READY -> {
                if (direction != Direction.H_S || state != State.HANDSHAKE || !hello) yield false;
                state = State.HOST_READY; yield true;
            }
            case START -> {
                if (direction == Direction.S_H && state == State.HOST_READY && !startSent) {
                    startSent = true; if (hostProbe) state = State.RUNNING; yield true;
                }
                if (direction == Direction.H_W && state == State.WORKER_STARTING && !workerCommand) { workerCommand = true; yield true; }
                yield false;
            }
            case STARTED -> {
                if (direction != Direction.W_H || state != State.WORKER_STARTING || !workerCommand) yield false;
                state = State.RUNNING; yield true;
            }
            case STATUS -> state == State.RUNNING && (direction == Direction.W_H || direction == Direction.H_S);
            case LEASE -> {
                if (direction != Direction.S_H || state == State.QUARANTINED || state == State.CLEANING) yield false;
                lastLease = clock.now(); yield true;
            }
            case STOP -> {
                if (direction != Direction.S_H) yield false;
                stop(ticket); yield true;
            }
            case STOP_ACCEPTED -> direction == Direction.H_S && state == State.CLEANING;
            case RESULT_END -> {
                if (direction != Direction.W_H || state != State.RUNNING || !resultStarted || resultComplete || resultRemaining != 0) yield false;
                resultComplete = true; yield true;
            }
            case RESULT_AVAILABLE -> {
                if (hostProbe) {
                    if (direction != Direction.H_S || state != State.RUNNING || !startSent || resultAvailable) yield false;
                    resultComplete = resultAvailable = true; yield true;
                }
                if (direction != Direction.H_S || state != State.RUNNING || !resultComplete || resultAvailable) yield false;
                resultAvailable = true; yield true;
            }
            case RESOURCES_CLOSED -> {
                if (direction != Direction.H_S || state != State.CLEANING || !innerEnd || innerClosed) yield false;
                kernel.close(Role.H, inner);
                innerClosed = kernel.closed(inner.job);
                yield innerClosed;
            }
            case ERROR -> {
                if (direction != Direction.H_S && direction != Direction.W_H) yield false;
                stop(ticket); yield true;
            }
            case CHANNEL_END -> {
                if (hostProbe) {
                    if (state != State.RUNNING || !resultAvailable
                            || direction != Direction.S_H && direction != Direction.H_S
                            || direction == Direction.H_S && !ended(Direction.S_H)) yield false;
                    channelProofsRequired = true; channels.get(direction).state = ChannelState.END_EXPECTED;
                    if (direction == Direction.H_S) stop(ticket);
                    yield true;
                }
                // Nur lokale Kommunikationsordnung, niemals ein Job-/Handle-Endnachweis.
                if (!workerSuccess && state != State.RUNNING || !resultComplete) yield false;
                boolean allowed = switch (direction) {
                    case W_H -> state == State.RUNNING;
                    case H_W -> state == State.RUNNING && ended(Direction.W_H);
                    case S_H -> state == State.CLEANING && workerSuccess && resultAvailable
                            && innerEnd && innerClosed && ended(Direction.W_H) && ended(Direction.H_W);
                    case H_S -> state == State.CLEANING && workerSuccess && resultAvailable
                            && innerEnd && innerClosed && ended(Direction.S_H);
                };
                if (!allowed) yield false;
                channelProofsRequired = true;
                channels.get(direction).state = ChannelState.END_EXPECTED;
                yield true;
            }
        };
    }

    boolean stop(Ticket current) {
        if (!active(current)) return false;
        if (cleanupDeadline == null) cleanupDeadline = Math.addExact(clock.now(), 15);
        if (state != State.QUARANTINED) state = State.CLEANING;
        if (!innerTerminationFailed && inner != null && inner.state == HandleState.OPEN
                && supervisorAlive && hostResponsive && host != null && host.alive) {
            try { kernel.terminate(Role.H, inner); }
            catch (IllegalStateException failure) { innerTerminationFailed = true; quarantine(); }
        }
        return true;
    }

    boolean escalate(Ticket current) {
        if (!active(current) || !supervisorAlive || outer == null || outer.state != HandleState.OPEN || outerTerminationFailed) return false;
        stop(current);
        if (clock.now() >= cleanupDeadline) { quarantine(); return false; }
        try { kernel.terminate(Role.S, outer); }
        catch (IllegalStateException failure) { outerTerminationFailed = true; quarantine(); return false; }
        return true; // Nur Anfrage, keine Endbehauptung.
    }

    void tick() {
        if (!active(ticket) || !supervisorAlive) return;
        if (cleanupDeadline == null && (clock.now() >= operationDeadline
                || state != State.RUNNING && clock.now() >= startDeadline
                || host != null && clock.now() - lastLease >= 5)) stop(ticket);
        if (cleanupDeadline != null && clock.now() >= cleanupDeadline) quarantine();
    }

    boolean workerEnded(Ticket current, int exitCode) {
        if (!active(current) || worker == null || workerExitObserved) return false;
        tick();
        workerExitObserved = true;
        workerSuccess = state == State.RUNNING && exitCode == 0 && resultComplete && resultAvailable;
        kernel.end(worker); stop(current); return true;
    }

    boolean hostEnded(Ticket current, boolean crashed) {
        if (!active(current) || host == null) return false;
        kernel.end(host);
        if (hostProbe) {
            probeSuccess = !crashed && state == State.CLEANING && resultAvailable && allChannelsEnded();
            stop(current); if (!probeSuccess) quarantine(); return true;
        }
        if (crashed || !innerClosed) {
            if (inner != null && inner.state == HandleState.OPEN) kernel.crashOwner(Role.H, inner);
            stop(current); quarantine();
        }
        return true;
    }

    void supervisorFailed(Ticket current) {
        if (!active(current)) return;
        // Ein Absturz führt gerade KEINEN geordneten Supervisor-Stop mehr aus.
        supervisorAlive = false; stop(current);
        if (outer != null && outer.state == HandleState.OPEN) kernel.crashOwner(Role.S, outer);
        journal.loseWriter(supervisor); state = State.QUARANTINED;
    }

    /** Nur ein Fake-Schedulingereignis; keine echte nicht abbrechbare Blockade erzeugen. */
    void blockHost(Ticket current) { if (active(current)) hostResponsive = false; }

    /**
     * Auch nach Incident eigene Ressourcen geordnet abschließen, ohne Recovery oder
     * Slotfreigabe zu behaupten. Verlorene H-Evidenz wird durch K-Ende nicht erfunden.
     */
    boolean retireAfterIncident(Ticket current, Observation outerEnd, IoEnd io) {
        if (!active(current) || state != State.QUARANTINED || !supervisorAlive || outer == null
                || outer.state != HandleState.OPEN
                || io == null || io.ticket() != ticket || !io.complete() || host != null && host.alive
                || inner != null && !kernel.closed(inner.job) || !kernel.provesEmpty(Role.S, outer, outerEnd)) return false;
        try { kernel.close(Role.S, outer); return kernel.closed(ticket); }
        catch (IllegalStateException failure) { return false; }
    }

    boolean eof(Channel channel) {
        return eof(channel, true);
    }

    /** Transportfehler/Abbruch sind ausdrücklich kein reguläres Boundary-EOF. */
    boolean eof(Channel channel, boolean cleanBoundary) {
        if (channel == null || channels.get(channel.direction) != channel || !active(channel.ticket)) return false;
        tick();
        if (state == State.QUARANTINED) return false;
        if (!cleanBoundary || channel.state == ChannelState.OPEN) return protocolFault();
        channel.state = ChannelState.EOF_CONFIRMED;
        return true;
    }

    /** Wire-Harness benötigt eigene Kanalbelege zusätzlich zum abstrakten IoEnd-Observer. */
    void requireChannelEndProofs(Ticket current) {
        if (!active(current)) throw new IllegalStateException("inactive-wire-ticket");
        channelProofsRequired = true;
    }

    private boolean ended(Direction direction) {
        Channel channel = channels.get(direction);
        return channel != null && channel.state == ChannelState.EOF_CONFIRMED;
    }

    private boolean allChannelsEnded() {
        if (hostProbe) return ended(Direction.S_H) && ended(Direction.H_S);
        for (Direction direction : Direction.values()) if (!ended(direction)) return false;
        return true;
    }

    boolean finish(Ticket current, Observation outerEnd, IoEnd io) {
        if (!active(current)) return false;
        tick();
        if (state != State.CLEANING || !supervisorAlive || io == null || io.ticket() != ticket || !io.complete()
                || hostProbe && !probeSuccess
                || channelProofsRequired && !allChannelsEnded()
                || outer == null
                || host != null && host.alive || inner != null && (!innerEnd || !innerClosed)
                || !kernel.provesEmpty(Role.S, outer, outerEnd)) return false;
        try {
            kernel.close(Role.S, outer);
            if (!kernel.closed(ticket) || !journal.complete(supervisor,
                    journal.endCandidate(ticket, outer.job, inner == null ? null : inner.job))) { quarantine(); return false; }
        } catch (IllegalStateException failure) { quarantine(); return false; }
        state = State.IDLE; return true;
    }

    /** Lokaler Adapter-/Bindungsfehler bleibt im selben Lifecycle fail-closed. */
    boolean controlFailed(Ticket current) { return active(current) && protocolFault(); }

    private boolean protocolFault() { stop(ticket); quarantine(); return false; }
    private void quarantine() { state = State.QUARANTINED; journal.uncertain(supervisor); }
    private boolean active(Ticket current) {
        return current != null && current == ticket && state != State.IDLE && state != State.UNAVAILABLE;
    }
    private void phase(Ticket current, State expected) {
        tick(); if (!active(current) || state != expected) throw new IllegalStateException("wrong-start-phase");
        if (outer != null && outer.state != HandleState.OPEN || inner != null && inner.state != HandleState.OPEN) {
            protocolFault(); throw new IllegalStateException("start-handle-unconfirmed");
        }
        if (!intentConfirmed()) { protocolFault(); throw new IllegalStateException("intent-unconfirmed"); }
    }
    private boolean intentConfirmed() {
        return journal.confirmsIntent(supervisor, ticket, outer == null ? null : outer.job, inner == null ? null : inner.job);
    }
    State state() { return state; }
    Ticket ticket() { return ticket; }
    Object supervisor() { return supervisor; }
    Channel channel(Direction direction) { return channels.get(direction); }
    Handle outer() { return outer; }
    Handle inner() { return inner; }
    Instance host() { return host; }
    Instance worker() { return worker; }
    Start pendingWorker() { return workerStart; }
    long nextSequence(Direction direction) { return channel(direction).sequence + 1; }
    long startDeadline() { return startDeadline; }
    long operationDeadline() { return operationDeadline; }
    Long cleanupDeadline() { return cleanupDeadline; }
    long resultBytes() { return resultTotal; }
    boolean delivered() { return state == State.IDLE && workerSuccess; }
    boolean hostProbe() { return hostProbe; }
    boolean hostProbeEndConfirmed(Ticket current) {
        return active(current) && hostProbe && resultAvailable && allChannelsEnded() && state == State.CLEANING;
    }
}
