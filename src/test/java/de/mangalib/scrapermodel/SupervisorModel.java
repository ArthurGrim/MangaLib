package de.mangalib.scrapermodel;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * Ausführbarer Vertragsentwurf, ausschließlich im Test-SourceSet.
 * Besitzt keine OS-Ressourcen und führt keine Aktionen aus. Die Zähler beschreiben
 * angeforderte Aktionen eines späteren Adapters, niemals bestätigte OS-Wirkung.
 */
final class SupervisorModel {
    enum State { UNAVAILABLE, IDLE, STARTING, RUNNING, STOPPING, VERIFYING, QUARANTINED, CLOSED }
    enum Operation { SERIES, VOLUME }
    enum Proof { UNKNOWN, CONFIRMED, FAILED }
    enum Check {
        NO_ACTIVE_POPULATION, COMPLETE_EMPTY_JOB_LIST, CONSISTENT_ZERO_ACCOUNTING,
        RETAINED_INSTANCES_ENDED, IDENTITIES_RESOLVED, IPC_READERS_ENDED,
        IPC_WRITER_ENDED, HANDLES_CLOSED, NATIVE_RESOURCES_CLOSED,
        NO_RESOURCE_CREATING_CALLBACKS, TEMPORARY_RESOURCES_CLOSED
    }

    /** Objektidentität bindet die Generation auch an diese Modellinstanz. */
    static final class Ticket {
        private final long generation;
        private final Operation operation;

        private Ticket(long generation, Operation operation) {
            this.generation = generation;
            this.operation = operation;
        }

        long generation() { return generation; }
        Operation operation() { return operation; }
    }

    /** Ein vollständiger kohärenter Nachweis, keine Mischung älterer Teilbelege. */
    record Evidence(long revision, Map<Check, Proof> checks) {
        Evidence {
            if (revision < 1) throw new IllegalArgumentException("revision");
            checks = Map.copyOf(checks);
        }

        boolean complete() {
            for (Check check : Check.values()) {
                if (checks.getOrDefault(check, Proof.UNKNOWN) != Proof.CONFIRMED) return false;
            }
            return true;
        }
    }

    /** Monotone Modellzeit in beliebigen, konsistenten Einheiten; keine Produktbudgets. */
    record Budgets(long start, long series, long volume, long cleanup) {
        Budgets {
            if (start <= 0 || series <= start || volume <= start || cleanup <= 0) {
                throw new IllegalArgumentException("budgets");
            }
        }
    }

    private final LongSupplier clock;
    private final Budgets budgets;
    private State state = State.UNAVAILABLE;
    private Ticket owner;
    private long generation;
    private long startDeadline;
    private long operationDeadline;
    private long cleanupDeadline;
    private long evidenceRevision;
    private boolean startOutstanding;
    private boolean stopRequested;
    private boolean shutdown;
    private boolean barrierConfirmed;
    private int startRequests;
    private int stopRequests;
    private int releases;

    SupervisorModel(LongSupplier clock, Budgets budgets) {
        this.clock = Objects.requireNonNull(clock);
        this.budgets = Objects.requireNonNull(budgets);
    }

    /**
     * Vertrauensgrenze für einen späteren Recovery-/Plattformnachweis, keine
     * reale Prüfung. Ein neuer Modellkonstruktor ist ausdrücklich keine Freigabe.
     * Fehlender Nachweis sperrt dauerhaft; kein Reset aus QUARANTINED.
     */
    synchronized boolean initialize(Proof startupCleared) {
        Objects.requireNonNull(startupCleared);
        if (state != State.UNAVAILABLE || shutdown) return false;
        state = startupCleared == Proof.CONFIRMED ? State.IDLE : State.QUARANTINED;
        return state == State.IDLE;
    }

    synchronized Optional<Ticket> admit(Operation operation) {
        Objects.requireNonNull(operation);
        if (shutdown || state != State.IDLE) return Optional.empty();
        long now = clock.getAsLong();
        // Vor Zustandsänderungen rechnen: auch ein Zahlenüberlauf darf keinen halben Owner erzeugen.
        long nextStart = Math.addExact(now, budgets.start());
        long nextOperation = Math.addExact(now,
                operation == Operation.SERIES ? budgets.series() : budgets.volume());
        owner = new Ticket(Math.incrementExact(generation), operation);
        generation = owner.generation();
        startDeadline = nextStart;
        operationDeadline = nextOperation;
        evidenceRevision = 0;
        startOutstanding = true;
        stopRequested = false;
        barrierConfirmed = false;
        state = State.STARTING;
        startRequests++;
        return Optional.of(owner);
    }

    /** Native Rückkehr inklusive Resume-Verantwortung; nach Stopp niemals RUNNING. */
    synchronized boolean startReturned(Ticket ticket, boolean successful) {
        if (!owns(ticket) || !startOutstanding) return false;
        tick();
        startOutstanding = false;
        if (state == State.STARTING && successful && !shutdown) {
            state = State.RUNNING;
        } else if (state != State.QUARANTINED) {
            beginCleanup();
            state = State.VERIFYING;
        }
        return true;
    }

    /** Root-Ende/Startfehler ist lediglich Anlass für Cleanup, niemals Endbeweis. */
    synchronized boolean finished(Ticket ticket) {
        if (!owns(ticket) || state != State.RUNNING) return false;
        tick();
        requestStop();
        return true;
    }

    synchronized boolean verificationStarted(Ticket ticket) {
        if (!owns(ticket) || state != State.STOPPING) return false;
        state = State.VERIFYING;
        return true;
    }

    synchronized boolean identityUncertain(Ticket ticket) {
        if (!owns(ticket)) return false;
        requestStop();
        state = State.QUARANTINED;
        return true;
    }

    synchronized void tick() {
        if (owner == null) return;
        long now = clock.getAsLong();
        if ((state == State.STARTING && now >= startDeadline)
                || (state == State.RUNNING && now >= operationDeadline)) {
            requestStop();
        }
        if ((state == State.STOPPING || state == State.VERIFYING)
                && now >= cleanupDeadline) state = State.QUARANTINED;
    }

    /**
     * Nachweis erst nach Rückkehr aller Start-/Resume-Aktionen akzeptieren.
     * Der spätere Adapter muss Gruppenende VOR letztem Handle-Close belegen und
     * nachfolgende Ressourcenerzeugung verhindern; dieses Modell beobachtet kein OS.
     */
    synchronized boolean verify(Ticket ticket, Evidence evidence) {
        Objects.requireNonNull(evidence);
        if (!owns(ticket) || (state != State.VERIFYING && state != State.QUARANTINED)
                || startOutstanding || evidence.revision() <= evidenceRevision) return false;
        tick();
        evidenceRevision = evidence.revision();
        barrierConfirmed = evidence.complete();
        if (!barrierConfirmed || state == State.QUARANTINED) return false;
        releases++;
        owner = null;
        state = shutdown ? State.CLOSED : State.IDLE;
        return true;
    }

    synchronized void shutdown() {
        shutdown = true;
        if (owner == null && state != State.QUARANTINED) state = State.CLOSED;
        else if (owner != null) requestStop();
    }

    private boolean owns(Ticket ticket) { return ticket != null && ticket == owner; }

    private void beginCleanup() {
        if (!stopRequested) {
            cleanupDeadline = Math.addExact(clock.getAsLong(), budgets.cleanup());
            stopRequested = true;
            stopRequests++;
        }
    }

    private void requestStop() {
        beginCleanup();
        if (state != State.QUARANTINED && state != State.VERIFYING) state = State.STOPPING;
    }

    synchronized State state() { return state; }
    synchronized boolean startOutstanding() { return startOutstanding; }
    synchronized boolean barrierConfirmed() { return barrierConfirmed; }
    synchronized int startRequests() { return startRequests; }
    synchronized int stopRequests() { return stopRequests; }
    synchronized int releases() { return releases; }
    synchronized long cleanupDeadline() { return cleanupDeadline; }

    static Evidence completeEvidence(long revision) {
        var checks = new EnumMap<Check, Proof>(Check.class);
        for (Check check : Check.values()) checks.put(check, Proof.CONFIRMED);
        return new Evidence(revision, checks);
    }
}
