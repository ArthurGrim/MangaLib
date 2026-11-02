package de.mangalib.windowsadapter;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

import static de.mangalib.windowsadapter.KernelCalls.*;

/**
 * Kern für genau eine K/H-Sitzung. Kein main, OS-Port, Executor oder Supervisor.
 * S besitzt weiterhin seinen Launch-/Ledgerhandle; C besitzt eine separat
 * bestätigte Kontrollkopie. H/W erhalten keine davon. Ein eigener OS-Prozess
 * und dessen unabhängiger Bootstrap/Übertragungsweg sind hier NICHT implementiert.
 */
final class OuterJobCustodian implements StartLeaseGuard.Custodian {
    static final int JOB_RIGHTS = 0x0004 | 0x0008; // QUERY | TERMINATE, kein ASSIGN/SET.
    static final int PROCESS_RIGHTS = 0x100000 | 0x1000; // SYNCHRONIZE | QUERY_LIMITED_INFORMATION.
    enum Signal { ALIVE, ENDED, UNKNOWN }
    enum Termination { NONE, RUNNING, ACCEPTED, FAILED, OVERDUE }
    enum Command { PROGRESS, ABORT }
    record Owner(Object handle, Identity identity, int rights, boolean privateNonInherited) { }
    record ProcessEnd(Object handle, Identity identity, Signal signal) { }
    record Control(Object handle, Resource source, StartIntentStore.Binding binding, int rights,
                   boolean sameObject, boolean privateNonInherited, boolean killOnLastClose, boolean noBreakaway) { }
    record Root(Object handle, Resource source, Identity identity, int rights,
                boolean sameObject, boolean privateNonInherited, boolean memberOfK) { }
    /**
     * Kein bloßes Peer-Ack: der spätere Port muss diese Barriere unabhängig
     * begründen. Owner-Signal allein beweist KEIN Ende eines ausstehenden Create.
     */
    record Fence(Control control, boolean sourceCopiesClosed, boolean startsSettled,
                 boolean launchResourcesClosed, boolean channelsEnded) {
        boolean complete(Control expected) {
            return control == expected && sourceCopiesClosed && startsSettled && launchResourcesClosed && channelsEnded;
        }
    }
    record End(Control control, Observation group, Fence fence, ProcessEnd root) { }
    /** Fester Kontrollvertrag; kein Stringkommando und keine Scraper-Nutzdaten. */
    record Message(Object peer, UUID channel, StartIntentStore.Binding binding, StartIntentStore.Anchor anchor,
                   long sequence, Command command) { }
    record Report(boolean controlReachable, Signal owner, boolean revoked, Termination termination,
                  boolean groupEnded, boolean resourcesClosed, boolean quarantine,
                  int callsOutstanding, int terminations, long cleanupDeadline) { }

    /**
     * Vertrauensgrenze: Implementierung muss eigene Prozess-/Jobhandlekopien,
     * gleiche OS-Objekte, Rechte, Identität und nicht vererbbare Handles bestätigen.
     * Teilallokationen verbleiben bei unklarem Transfer im Portledger. Keine
     * numerischen PIDs öffnen/terminieren, kein DUPLICATE_CLOSE_SOURCE. Ein Fake
     * bestätigt ausschließlich Modellannahmen, keine Windows-Authentisierung.
     */
    interface Port {
        Control duplicateControl(Resource source, StartIntentStore.Binding binding, int rights);
        ProcessEnd ownerEnd(Owner owner, long remaining);
        Root duplicateRoot(Control control, Resource source, int rights);
        boolean terminate(Control control, long remaining);
        End inspectEnd(Control control, Root root, long remaining);
        boolean closeRoot(Root root);
        boolean closeControl(Control control);
        boolean closeOwner(Owner owner);
    }

    private final Object lock = new Object(), receipt = new Object();
    private final StartIntentStore.Binding binding;
    private final StartIntentStore.Anchor anchor;
    private final BooleanSupplier prepared;
    private final Owner owner;
    private final Object peer;
    private final UUID channel;
    private final Port port;
    private final LongSupplier clock;
    private final long operationDeadline, cleanupBudget, progressBudget;
    private long lastProgress, cleanupDeadline = Long.MAX_VALUE, sequence, epoch, lastTermEpoch = -1;
    private Resource source, returnedSource;
    private Control control;
    private Root root;
    private Signal ownerSignal = Signal.UNKNOWN;
    private Termination termination = Termination.NONE;
    private boolean retained, adopting, transferring, checkingOwner, checkingEnd, closing,
            revoked, quarantine, dead, returned, groupEnded, resourcesClosed, closeAttempted, terminating, transferUncertain;
    private int terminations;

    OuterJobCustodian(StartIntentStore.Binding binding, StartIntentStore.Anchor anchor, BooleanSupplier prepared,
                      Owner owner, Object peer, UUID channel, Port port, LongSupplier clock,
                      long operationDeadline, long cleanupBudget, long progressBudget) {
        this.binding = Objects.requireNonNull(binding); this.anchor = Objects.requireNonNull(anchor);
        this.prepared = Objects.requireNonNull(prepared); this.owner = Objects.requireNonNull(owner);
        this.peer = Objects.requireNonNull(peer); this.channel = Objects.requireNonNull(channel);
        this.port = Objects.requireNonNull(port); this.clock = Objects.requireNonNull(clock);
        this.operationDeadline = operationDeadline; this.cleanupBudget = cleanupBudget; this.progressBudget = progressBudget;
        lastProgress = clock.getAsLong();
        if (operationDeadline <= lastProgress || cleanupBudget <= 0 || progressBudget <= 0
                || channel.equals(new UUID(0, 0)) || !identity(owner.identity()) || owner.handle() == null
                || owner.rights() != PROCESS_RIGHTS || !owner.privateNonInherited()
                || anchor.revision() != 1 || anchor.hash() == null || !anchor.hash().matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("custodian-bootstrap");
    }

    @Override public Object retain(StartIntentStore.Binding requested, StartIntentStore.Anchor revision, Resource job) {
        synchronized (lock) {
            tickLocked();
            if (retained || revoked || dead || ownerSignal != Signal.ALIVE || !binding.equals(requested)
                    || !anchor.equals(revision) || job == null || job.kind != Kind.JOB) { stopLocked(); return null; }
            retained = adopting = true; source = job;
        }
        try {
            if (!prepared.getAsBoolean()) { stop(); return null; }
            synchronized (lock) { tickLocked(); if (revoked || dead) return null; }
            Control acquired = port.duplicateControl(job, binding, JOB_RIGHTS);
            synchronized (lock) {
                if (!valid(acquired)) { stopLocked(); return null; }
                control = acquired;
                tickLocked();
                return revoked || dead ? null : receipt;
            }
        } catch (RuntimeException failure) { stop(); return null; }
        finally { synchronized (lock) { adopting = false; } }
    }

    @Override public boolean live(Object token) {
        synchronized (lock) {
            tickLocked();
            return token == receipt && control != null && !revoked && !dead && !resourcesClosed && !closing;
        }
    }
    @Override public void revoke(Object token) {
        synchronized (lock) { if (token != receipt) { stopLocked(); return; } stopLocked(); }
    }
    @Override public void recordReturn(Object token, Resource process) {
        Control held;
        synchronized (lock) {
            if (token != receipt || control == null || dead || closing || resourcesClosed
                    || (process != null && (process.kind != Kind.PROCESS || process.scope != source.scope))) {
                uncertainTransferLocked(); return;
            }
            if (returned) { if (returnedSource != process) uncertainTransferLocked(); return; }
            returned = true; returnedSource = process; epoch++; groupEnded = false;
            if (process == null) return; // Unabhängige Start-/K-Endbarriere bleibt trotzdem Pflicht.
            transferring = true; held = control;
        }
        try {
            Root copy = port.duplicateRoot(held, process, PROCESS_RIGHTS);
            synchronized (lock) {
                if (copy == null || copy.handle() == null || copy.handle() == owner.handle() || copy.handle() == held.handle()
                        || copy.source() != process || !identity(copy.identity()) || !copy.sameObject()
                        || !copy.privateNonInherited() || !copy.memberOfK() || copy.rights() != PROCESS_RIGHTS) uncertainTransferLocked();
                else root = copy;
            }
        } catch (RuntimeException failure) { synchronized (lock) { uncertainTransferLocked(); } }
        finally { synchronized (lock) { transferring = false; } }
    }
    @Override public void retainLate(Object token, Resource process) { revoke(token); recordReturn(token, process); }

    /** Held-Handle-Signal, kein Heartbeat-/PID-Endbeweis. Separate Query-Aufrufspur. */
    void pollOwner() {
        long budget;
        synchronized (lock) {
            tickLocked(); budget = remaining();
            if (checkingOwner || closing || resourcesClosed || dead || budget <= 0) return; checkingOwner = true;
        }
        try {
            ProcessEnd end = port.ownerEnd(owner, budget);
            synchronized (lock) {
                if (end == null || end.handle() != owner.handle() || !owner.identity().equals(end.identity()) || end.signal() == null
                        || (ownerSignal == Signal.ENDED && end.signal() != Signal.ENDED)) { stopLocked(); ownerSignal = Signal.UNKNOWN; }
                else { ownerSignal = end.signal(); if (ownerSignal != Signal.ALIVE) stopLocked(); }
            }
        } catch (RuntimeException failure) { synchronized (lock) { ownerSignal = Signal.UNKNOWN; stopLocked(); } }
        finally { synchronized (lock) { checkingOwner = false; } }
    }

    /** Enger Same-VM-Vertrag, noch KEIN authentisierter Interprozess-Bytekanal. */
    boolean receive(Message message) {
        synchronized (lock) {
            tickLocked();
            if (message == null || dead || resourcesClosed || message.peer() != peer || !channel.equals(message.channel())
                    || !binding.equals(message.binding()) || !anchor.equals(message.anchor())
                    || message.sequence() != sequence + 1 || sequence == Long.MAX_VALUE || message.command() == null) {
                stopLocked(); return false;
            }
            sequence++;
            if (message.command() == Command.ABORT) stopLocked();
            else if (!revoked) lastProgress = clock.getAsLong();
            return true;
        }
    }
    void communicationLost() { stop(); } // Kein Setzen von ownerSignal=ENDED.
    void custodianLost() { synchronized (lock) { dead = true; stopLocked(); } }
    void stop() { synchronized (lock) { stopLocked(); } }
    /** Gebundener Bootstrap darf die erste Frist nur verkürzen, niemals erneuern. */
    void stopBy(long deadline) {
        synchronized (lock) { stopLocked(); cleanupDeadline = Math.min(cleanupDeadline, deadline); }
    }
    private void stopLocked() {
        revoked = quarantine = true;
        if (cleanupDeadline == Long.MAX_VALUE) {
            long now = clock.getAsLong();
            cleanupDeadline = Math.min(operationDeadline, now > Long.MAX_VALUE - cleanupBudget ? Long.MAX_VALUE : now + cleanupBudget);
        }
    }
    private void uncertainTransferLocked() { stopLocked(); transferUncertain = true; groupEnded = false; }
    private void tickLocked() {
        long now = clock.getAsLong();
        if (now < lastProgress || now >= operationDeadline || now - lastProgress >= progressBudget) stopLocked();
        if (termination == Termination.RUNNING && now >= cleanupDeadline) termination = Termination.OVERDUE;
    }
    private long remaining() { synchronized (lock) { return Math.max(0, Math.min(operationDeadline, cleanupDeadline) - clock.getAsLong()); } }

    /** Separate Control-Aufrufspur: kein Query-/Transfermonitor und kein regulärer Flight. */
    void terminateStep() {
        Control held; long attemptEpoch, budget;
        synchronized (lock) {
            tickLocked(); budget = remaining();
            if (!revoked || dead || control == null || closing || closeAttempted || resourcesClosed
                    || terminating || termination == Termination.OVERDUE
                    || lastTermEpoch == epoch || budget <= 0 || terminations >= 2) return;
            held = control; attemptEpoch = epoch; lastTermEpoch = epoch; terminations++; terminating = true; termination = Termination.RUNNING;
        }
        boolean accepted;
        try { accepted = port.terminate(held, budget); } catch (RuntimeException failure) { accepted = false; }
        synchronized (lock) {
            terminating = false;
            termination = clock.getAsLong() >= cleanupDeadline ? Termination.OVERDUE : accepted ? Termination.ACCEPTED : Termination.FAILED;
            // Late-create benötigt einen NEUEN Endnachweis und ggf. die zweite, ereignisgebundene Terminierung.
            if (epoch != attemptEpoch || !accepted) groupEnded = false;
        }
    }

    void inspectEnd() {
        Control held; Root process; long version, budget;
        synchronized (lock) {
            tickLocked(); budget = remaining();
            if (!revoked || dead || control == null || !returned || transferUncertain || adopting || transferring || checkingEnd
                    || closing || closeAttempted || resourcesClosed || budget <= 0) return;
            checkingEnd = true; held = control; process = root; version = epoch;
        }
        boolean verified = false;
        try {
            End end = port.inspectEnd(held, process, budget);
            if (end != null && end.control() == held && end.fence() != null && end.fence().complete(held)) {
                Observation group = end.group();
                verified = group != null && group.complete() && group.stable() && group.active() == 0 && group.assigned() == 0
                        && group.pids().isEmpty() && group.total() >= (process == null ? 0 : 1)
                        && (returnedSource == null || process != null)
                        && (process == null || (end.root() != null && end.root().handle() == process.handle()
                        && process.identity().equals(end.root().identity()) && end.root().signal() == Signal.ENDED));
            }
        } catch (RuntimeException failure) { /* Unbekannter Endbeleg bleibt unbestätigt. */ }
        finally {
            synchronized (lock) {
                checkingEnd = false;
                groupEnded = verified && !dead && !transferUncertain && version == epoch && remaining() > 0 && !transferring && !adopting;
            }
        }
    }

    /** Letzte C-Kopie erst nach Startfence, leerer Gruppe und Ende aller Nutzungen. */
    void closeResources() {
        Control held; Root process;
        synchronized (lock) {
            tickLocked();
            if (!groupEnded || dead || closeAttempted || remaining() <= 0 || outstandingLocked() != 0
                    || termination == Termination.RUNNING || termination == Termination.OVERDUE) return;
            closeAttempted = closing = true; held = control; process = root;
        }
        boolean closed = false;
        try { closed = (process == null || port.closeRoot(process)) && closeAllowed() && port.closeControl(held)
                    && closeAllowed() && port.closeOwner(owner); }
        catch (RuntimeException failure) { /* Kein zweiter Close nach unklarem Rückgabewert. */ }
        finally { synchronized (lock) { closing = false; resourcesClosed = closed && remaining() > 0 && !dead; } }
    }
    Report report() {
        synchronized (lock) {
            tickLocked();
            return new Report(!dead, ownerSignal, revoked, termination, groupEnded, resourcesClosed, quarantine,
                    outstandingLocked(), terminations, cleanupDeadline);
        }
    }
    private boolean closeAllowed() {
        synchronized (lock) { return !dead && !transferUncertain && groupEnded && remaining() > 0; }
    }
    private int outstandingLocked() {
        return (adopting ? 1 : 0) + (transferring ? 1 : 0) + (checkingOwner ? 1 : 0) + (checkingEnd ? 1 : 0)
                + (closing ? 1 : 0) + (terminating ? 1 : 0);
    }
    private boolean valid(Control candidate) {
        return candidate != null && candidate.handle() != null && candidate.handle() != owner.handle()
                && candidate.source() == source && binding.equals(candidate.binding()) && candidate.rights() == JOB_RIGHTS
                && candidate.sameObject() && candidate.privateNonInherited() && candidate.killOnLastClose() && candidate.noBreakaway();
    }
    private static boolean identity(Identity id) {
        return id != null && id.pid() > 0 && id.creationTime() > 0 && id.image() != null && !id.image().isBlank() && id.image().length() <= 512;
    }
}
