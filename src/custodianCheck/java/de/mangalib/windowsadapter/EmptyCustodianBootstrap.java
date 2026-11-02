package de.mangalib.windowsadapter;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;

import static de.mangalib.windowsadapter.KernelCalls.*;
import static de.mangalib.windowsadapter.OuterJobCustodian.*;

/**
 * Einmaliger S/C-Bootstrap ausschließlich für LEERES K. Kein C-/H-Startpfad,
 * kein Admissionautomat. Alle Prozess-/Transferbelege kommen vom injizierten
 * Port; Objektidentität ist hier nur ein Fakevertrag, keine OS-Authentisierung.
 */
final class EmptyCustodianBootstrap {
    enum Phase { NEW, PREPARING, AWAITING_ACK, READY, CLEANING, FINISHED }
    enum Ownership { NONE, OWNED, CLOSED, UNCERTAIN }
    record Peer(Owner process, Object principal, UUID channel) { }
    record Acceptance(Object attempt, StartIntentStore.Binding binding, StartIntentStore.Anchor anchor,
                      Peer peer, Owner owner, Control copy, int totalCopies, boolean inheritanceExcluded, long sequence,
                      boolean monitoringLive, ProcessEnd ownerProof) { }
    record Inventory(Object attempt, Resource source, boolean verified, boolean supervisorCopy,
                     boolean custodianCopy, int extraCopies, boolean inheritanceExcluded) { }
    record EndReceipt(Object attempt, Resource source, Peer peer, Report report) { }
    record Release(Object attempt, Peer peer, ProcessEnd custodianEnd, boolean channelsClosed, boolean localPeerCopyClosed) { }
    record Result(Phase phase, boolean ready, Ownership supervisorHandle, boolean groupEnded,
                  boolean custodianResourcesClosed, boolean supervisorResourcesClosed,
                  boolean journalTerminal, boolean technicalCompletion, boolean quarantine,
                  long cleanupDeadline, boolean operationOutstanding) { }
    /**
     * Kein zweiter Kernelport: K-create/configure/readback/close nutzt KernelCalls.
     * Dieser Port bindet ausschließlich die Gegenstelle/Übernahme/Endbelege.
     * Abort ist eine nicht blockierende lokale Zustellung, KEIN nativer Kill.
     * Eine echte Implementierung/Handleduplikation/IPC existiert noch nicht.
     */
    interface Connection {
        ProcessEnd inspectPeer(Peer expected);
        Acceptance takeOver(Object attempt, StartIntentStore store, StartIntentStore.Proof proof,
                            Resource source, Peer peer, Owner owner);
        void requestStop(Object attempt, long absoluteDeadline);
        void controlStep();
        Inventory inventory(Object attempt, Resource source);
        EndReceipt finishEmpty(Object attempt, Resource source);
        Release releaseSupervisor(Object attempt, Peer peer);
    }
    interface TransferConfirmation extends Connection { boolean confirmsTransfer(Acceptance answer); }
    private static final Limits LIMITS = new Limits(true, false, 0, 0, 0);
    private final Object lock = new Object(), attempt = new Object();
    private final Scope scope = new Scope();
    private final StartIntentStore store;
    private final KernelCalls kernel;
    private final Connection connection;
    private final Peer peer;
    private final Owner owner;
    private final LongSupplier clock;
    private final long deadline, cleanupBudget;
    private Phase phase = Phase.NEW;
    private Ownership ownership = Ownership.NONE;
    private Resource source;
    private StartIntentStore.Proof proof;
    private boolean busy, cleaning, stop, failed, acknowledged, ready, groupEnded,
            cClosed, sResourcesClosed, journalTerminal, terminalAttempted, releaseAttempted, creationAttempted;
    private long cleanupDeadline = Long.MAX_VALUE;

    EmptyCustodianBootstrap(StartIntentStore store, KernelCalls kernel, Connection connection,
                            Peer peer, Owner owner, LongSupplier clock, long deadline, long cleanupBudget) {
        this.store = Objects.requireNonNull(store); this.kernel = Objects.requireNonNull(kernel);
        this.connection = Objects.requireNonNull(connection); this.peer = Objects.requireNonNull(peer);
        this.owner = Objects.requireNonNull(owner); this.clock = Objects.requireNonNull(clock);
        this.deadline = deadline; this.cleanupBudget = cleanupBudget;
        if (kernel instanceof Win64TransferContract.SourceLedger && !(connection instanceof TransferConfirmation))
            throw new IllegalArgumentException("native-source-requires-checked-transfer");
        if (deadline <= clock.getAsLong() || cleanupBudget <= 0 || !ownerSafe(owner) || !ownerSafe(peer.process())
                || peer.process().handle() == owner.handle() || peer.process().identity().pid() == owner.identity().pid()
                || peer.principal() == null || peer.channel() == null
                || peer.channel().equals(new UUID(0, 0))) throw new IllegalArgumentException("bootstrap-binding");
    }
    boolean begin() { return begin(null); }
    /** S darf den Intent vor dem separaten C-Start veröffentlichen; niemals erneut publizieren. */
    boolean begin(StartIntentStore.Proof prepared) {
        synchronized (lock) { if (phase != Phase.NEW || stop) return false; busy = true; phase = Phase.PREPARING; }
        try {
            if (!peerAlive()) throw new IOException("peer-not-bound");
            StartIntentStore.Proof published = prepared == null ? store.prepare() : prepared;
            synchronized (lock) { proof = published; }
            if (!store.valid(published) || !proceed()) throw new IOException("unconfirmed-intent");
            synchronized (lock) { creationAttempted = true; }
            Resource created = kernel.createJob(scope);
            synchronized (lock) {
                if (created == null || created.kind != Kind.JOB || created.scope != scope) throw new IOException("foreign-job");
                source = created; ownership = Ownership.OWNED;
            }
            if (!proceed()) return false;
            kernel.configure(created, LIMITS);
            if (!proceed() || !LIMITS.equals(kernel.readLimits(created)) || !empty(kernel.observe(created, 1, remaining())))
                throw new IOException("unsafe-or-nonempty-K");
            if (!proceed()) return false;
            synchronized (lock) { phase = Phase.AWAITING_ACK; }
            Acceptance answer = connection.takeOver(attempt, store, published, created, peer, owner);
            if (connection instanceof TransferConfirmation checked && !checked.confirmsTransfer(answer))
                throw new IOException("native-transfer-unconfirmed");
            if (!accept(answer) || !peerAlive() || !proceed()) throw new IOException("unconfirmed-readiness");
            synchronized (lock) { if (stop) return false; phase = Phase.READY; ready = true; return true; }
        } catch (IOException | RuntimeException failure) { abort(); return false; }
        finally { synchronized (lock) { busy = false; } }
    }
    /** Einmalige Reihenfolge/Instanz/Revision, niemals nur Textticket oder PID. */
    boolean accept(Acceptance message) {
        boolean valid;
        synchronized (lock) {
            Control copy = message == null ? null : message.copy();
            valid = phase == Phase.AWAITING_ACK && !acknowledged && !stop && proof != null
                    && message != null && message.attempt() == attempt && store.binding().equals(message.binding())
                    && proof.anchor().equals(message.anchor()) && message.peer() == peer && message.owner() == owner
                    && message.sequence() == 1 && message.totalCopies() == 2 && message.inheritanceExcluded()
                    && message.monitoringLive() && message.ownerProof() != null && message.ownerProof().handle() == owner.handle()
                    && owner.identity().equals(message.ownerProof().identity()) && message.ownerProof().signal() == Signal.ALIVE
                    && copy != null && copy.handle() != null && copy.handle() != owner.handle() && copy.handle() != peer.process().handle()
                    && copy.source() == source && store.binding().equals(copy.binding()) && copy.rights() == JOB_RIGHTS
                    && copy.sameObject() && copy.privateNonInherited() && copy.killOnLastClose() && copy.noBreakaway();
            if (valid) acknowledged = true;
        }
        if (!valid) abort();
        return valid;
    }
    void abort() { requestStop(true); }
    /** Später vom gebundenen Beobachtungspfad aufrufen, kein Heartbeat-Endbeweis. */
    boolean pollPeer() {
        try { if (peerAlive()) return true; }
        catch (RuntimeException ignored) { /* Unklare gehaltene Instanz bleibt gesperrt. */ }
        abort(); return false;
    }
    private void requestStop(boolean error) {
        long bound;
        synchronized (lock) {
            stop = true; failed |= error; ready = false;
            if (cleanupDeadline == Long.MAX_VALUE) {
                long now = clock.getAsLong();
                cleanupDeadline = Math.min(deadline, now > Long.MAX_VALUE - cleanupBudget ? Long.MAX_VALUE : now + cleanupBudget);
            }
            bound = cleanupDeadline;
        }
        try { connection.requestStop(attempt, bound); }
        catch (RuntimeException ignored) { synchronized (lock) { failed = true; } }
    }
    /** Eigenständige Kontrollspur, auch während eines blockierten S-Kernelcalls. */
    void controlStep() {
        synchronized (lock) { if (!stop) return; }
        try { connection.controlStep(); } catch (RuntimeException ignored) { synchronized (lock) { failed = true; } }
    }
    void finishStep(boolean normal) {
        requestStop(!normal);
        synchronized (lock) {
            if (busy || cleaning || phase == Phase.FINISHED || remaining() <= 0) return;
            cleaning = true; phase = Phase.CLEANING;
        }
        try {
            controlStep();
            Resource held;
            synchronized (lock) { held = source; }
            if (held != null) {
                Inventory inventory = connection.inventory(attempt, held);
                if (!inventorySafe(inventory, held)) { fail(); return; }
                if (ownership == Ownership.OWNED) {
                    if (!inventory.supervisorCopy()) {
                        // Nur unabhängiger OS-/Portbeleg über den beendeten gehaltenen S,
                        // niemals Kommunikationsverlust oder eine fremde Closebehauptung.
                        EndReceipt end = connection.finishEmpty(attempt, held);
                        if (!bound(end, held) || end.report().owner() != Signal.ENDED) { fail(); return; }
                        synchronized (lock) { ownership = Ownership.CLOSED; }
                    } else {
                        if (!empty(kernel.observe(held, 1, remaining())) || remaining() <= 0) { fail(); return; }
                        synchronized (lock) { ownership = Ownership.UNCERTAIN; }
                        kernel.close(held); // Unbekannter Close wird nie wiederholt.
                        synchronized (lock) { ownership = Ownership.CLOSED; }
                    }
                }
                if (ownership != Ownership.CLOSED || remaining() <= 0) return;
                EndReceipt end = connection.finishEmpty(attempt, held);
                if (!bound(end, held)) { fail(); return; }
                synchronized (lock) { groupEnded = end.report().groupEnded(); cClosed = end.report().resourcesClosed(); }
                Inventory after = connection.inventory(attempt, held);
                if (!groupEnded || !cClosed || !inventorySafe(after, held) || after.supervisorCopy() || after.custodianCopy()) return;
            } else {
                // Kein erfolgreicher K-Erwerb ist keine Behauptung über einen unbekannten Kernel-Teilstart.
                if (ownership != Ownership.NONE || creationAttempted) return;
                synchronized (lock) { groupEnded = cClosed = true; }
            }
            if (remaining() <= 0 || !peerAlive()) { fail(); return; }
            synchronized (lock) { if (releaseAttempted) return; releaseAttempted = true; }
            Release release = connection.releaseSupervisor(attempt, peer);
            boolean released = release != null && release.attempt() == attempt && release.peer() == peer
                    && ended(release.custodianEnd(), peer.process()) && release.channelsClosed() && release.localPeerCopyClosed();
            synchronized (lock) { sResourcesClosed = released && remaining() > 0; }
            if (!sResourcesClosed) { fail(); return; }
            synchronized (lock) { if (terminalAttempted) return; terminalAttempted = true; }
            if (failed) store.abort(); else store.complete();
            synchronized (lock) { journalTerminal = true; phase = Phase.FINISHED; }
        } catch (IOException | RuntimeException failure) { fail(); }
        finally { synchronized (lock) { cleaning = false; } }
    }
    private boolean bound(EndReceipt end, Resource held) {
        return end != null && end.attempt() == attempt && end.source() == held && end.peer() == peer && end.report() != null;
    }
    private boolean inventorySafe(Inventory value, Resource held) {
        return value != null && value.attempt() == attempt && value.source() == held && value.verified()
                && value.extraCopies() == 0 && value.inheritanceExcluded();
    }
    private boolean peerAlive() {
        ProcessEnd evidence = connection.inspectPeer(peer);
        return evidence != null && evidence.handle() == peer.process().handle()
                && peer.process().identity().equals(evidence.identity()) && evidence.signal() == Signal.ALIVE;
    }
    private boolean proceed() {
        boolean allowed;
        synchronized (lock) { allowed = !stop && clock.getAsLong() < deadline; }
        if (!allowed) abort(); return allowed;
    }
    private long remaining() { synchronized (lock) { return Math.max(0, Math.min(deadline, cleanupDeadline) - clock.getAsLong()); } }
    private void fail() { synchronized (lock) { failed = true; ready = false; } }
    Result report() {
        synchronized (lock) {
            return new Result(phase, ready && !stop && !failed, ownership, groupEnded, cClosed, sResourcesClosed,
                    journalTerminal, !failed && journalTerminal && sResourcesClosed && cClosed && groupEnded && ownership != Ownership.UNCERTAIN,
                    failed || stop, cleanupDeadline, busy || cleaning);
        }
    }
    private static boolean empty(Observation value) {
        return value != null && value.complete() && value.stable() && value.active() == 0 && value.assigned() == 0
                && value.pids().isEmpty() && value.total() == 0; // Diese Stufe darf nicht einmal frühere Nachfahren behaupten.
    }
    private static boolean ownerSafe(Owner value) {
        return value != null && value.handle() != null && value.rights() == PROCESS_RIGHTS && value.privateNonInherited()
                && value.identity() != null && value.identity().pid() > 0 && value.identity().creationTime() > 0
                && value.identity().image() != null && !value.identity().image().isBlank() && value.identity().image().length() <= 512;
    }
    private static boolean ended(ProcessEnd value, Owner expected) {
        return value != null && value.handle() == expected.handle() && expected.identity().equals(value.identity()) && value.signal() == Signal.ENDED;
    }
}
