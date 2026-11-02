package de.mangalib.windowsadapter;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static de.mangalib.windowsadapter.KernelCalls.*;
import static de.mangalib.windowsadapter.OuterJobCustodian.*;
import static de.mangalib.windowsadapter.EmptyCustodianBootstrap.*;
import static de.mangalib.windowsadapter.Win64TransferContract.*;

/**
 * Einmaliger nativer Transfervertrag, kein Lifecycle/Timer/Recoveryautomat.
 * Remote Zahlenwerte stammen NUR aus DuplicateHandle, nie aus einem Ack.
 * Kein S-Close für C-Handles. Die lokale Prüfkopie gehört dem S-Kernelledger.
 */
final class HandleTransferPort {
    enum CopyState { NONE, PRESENT, ADOPTED, CLOSED, UNCERTAIN }
    static final class Delivery {
        private final UUID id = UUID.randomUUID();
        private final Object attempt, token = new Object();
        private final StartIntentStore.Binding binding;
        private final StartIntentStore.Anchor anchor;
        private final Resource source;
        private final long remoteValue;
        private Delivery(Object attempt, StartIntentStore.Binding binding, StartIntentStore.Anchor anchor, Resource source, long remoteValue) {
            this.attempt = attempt; this.binding = binding; this.anchor = anchor; this.source = source; this.remoteValue = remoteValue;
        }
        UUID id() { return id; }
    }
    record Ack(UUID transfer, Object attempt, StartIntentStore.Binding binding, StartIntentStore.Anchor anchor,
               Peer peer, Object heldTarget, long sequence, Control adoptedControl) { }
    record Status(boolean outstanding, boolean validated, boolean acknowledged, boolean revoked,
                  CopyState target, CopyState temporary, boolean quarantine) { }
    /** Nur native S-Verdrahtung: Wert stammt aus bestätigter eigener Duplicate-Rückgabe. Kein Ack-Import. */
    record NativeDelivery(UUID transfer,long targetValue) { }
    NativeDelivery exportNative(Delivery issued,Peer peer) {
        synchronized(lock) {
            if(issued==null || issued!=delivery || peer!=target || !validated || revoked || claimed)throw failure();
            return new NativeDelivery(issued.id,issued.remoteValue);
        }
    }
    private final Object lock = new Object();
    private final Raw raw;
    private final ProcessLedger processes;
    private final SourceLedger jobs;
    private final Owner sourceProcess;
    private final Peer target;
    private final StartIntentStore.Binding binding;
    private final BooleanSupplier allowed;
    private boolean attempted, outstanding, validated, acknowledged, revoked, quarantine, claimed;
    private Delivery delivery;
    private Control claimedControl;
    // Bei fehlgeschlagener Registryübernahme bleibt auch der native Teilrückgabewert
    // gebunden erhalten; das ist ausdrücklich KEINE lokale Closeberechtigung.
    private long pendingTemporary;
    private long uncertainRemoteValue;
    private UseScope unconfirmedUses;
    private CopyState remote = CopyState.NONE, temporary = CopyState.NONE;
    HandleTransferPort(Raw raw, ProcessLedger processes, SourceLedger jobs, Owner sourceProcess,
                       Peer target, StartIntentStore.Binding binding, BooleanSupplier allowed) {
        this.raw = Objects.requireNonNull(raw); this.processes = Objects.requireNonNull(processes); this.jobs = Objects.requireNonNull(jobs);
        this.sourceProcess = Objects.requireNonNull(sourceProcess); this.target = Objects.requireNonNull(target);
        this.binding = Objects.requireNonNull(binding); this.allowed = Objects.requireNonNull(allowed);
    }
    Delivery transfer(Object attempt, StartIntentStore store, StartIntentStore.Proof proof, Resource source, Peer peer, Owner owner) {
        synchronized (lock) { if (attempted || revoked) return null; attempted = outstanding = true; }
        try {
            if (attempt == null || owner != sourceProcess || peer != target || source == null || source.kind != Kind.JOB
                    || !binding.equals(store.binding()) || !store.valid(proof) || !permitted()
                    || raw.currentProcessId() != owner.identity().pid()) throw failure();
            try (UseScope uses = new UseScope()) {
                ProcessUse s = uses.add(processes.borrow(owner.handle())), c = uses.add(processes.borrow(peer.process().handle()));
                Win64TransferContract.JobUse k = uses.add(jobs.borrowTransferSource(source));
                if (s.capability() != owner.handle() || c.capability() != peer.process().handle() || !word(s.value()) || !word(c.value())
                        || s.value() == c.value() || s.access() != PROCESS_ACCESS || c.access() != PROCESS_ACCESS
                        || owner.identity().pid() == peer.process().identity().pid() || k.resource() != source || !word(k.value())
                        || !new Limits(true,false,0,0,0).equals(k.limits()) || !raw.nonInherited(k.value())) throw failure();
                requireProcess(s,owner); requireProcess(c,peer.process());
                if (!permitted()) throw failure();
                Duplicate result;
                try { result = raw.duplicate(s.value(),k.value(),c.value(),JOB_RIGHTS,false,0); }
                catch (RuntimeException unknown) { uses.retain(); synchronized (lock) { remote = CopyState.UNCERTAIN; } throw unknown; }
                synchronized (lock) {
                    if (result != null && result.success() && word(result.value())) {
                        remote = CopyState.PRESENT; delivery = new Delivery(attempt,binding,proof.anchor(),source,result.value());
                    } else { uncertainRemoteValue = result == null ? 0 : result.value();
                        remote = result != null && !result.success() && result.value() == 0 ? CopyState.NONE : CopyState.UNCERTAIN; throw failure(); }
                }
                requireProcess(c,peer.process());
                if (!permitted()) throw failure();
                Duplicate back;
                try { back = raw.duplicate(c.value(),delivery.remoteValue,s.value(),4,false,0); }
                catch (RuntimeException unknown) { uses.retain(); synchronized (lock) { temporary = CopyState.UNCERTAIN; } throw unknown; }
                synchronized (lock) { pendingTemporary = back == null ? 0 : back.value(); }
                if (back == null || !back.success() || !word(back.value()) || back.value() == s.value()
                        || back.value() == c.value() || back.value() == k.value()) {
                    synchronized (lock) { temporary = CopyState.UNCERTAIN; } throw failure();
                }
                synchronized (lock) { temporary = CopyState.PRESENT; pendingTemporary = back.value(); }
                LocalCopy local;
                try { local = k.registerLocalCopy(back.value()); }
                catch (RuntimeException unregistered) { synchronized (lock) { temporary = CopyState.UNCERTAIN; } throw unregistered; }
                boolean same = false, closed = false;
                try {
                    same = local != null && local.resource() != source && local.resource().kind == Kind.JOB
                            && local.resource().scope == source.scope && local.value() == back.value()
                            && raw.nonInherited(local.value()) && raw.sameLocalObject(k.value(),local.value());
                } finally {
                    try { if (local != null) { local.close(); closed = true; } }
                    finally { synchronized (lock) { temporary = closed ? CopyState.CLOSED : CopyState.UNCERTAIN; if (closed) pendingTemporary = 0; } }
                }
                if (!same || !closed) throw failure();
                requireProcess(s,owner); requireProcess(c,peer.process());
                if (!permitted() || !store.valid(proof)) throw failure();
            }
            synchronized (lock) { if (revoked) return null; validated = true; return delivery; }
        } catch (RuntimeException failure) { cancel(); return null; }
        finally { synchronized (lock) { outstanding = false; } }
    }
    /** C-seitige vertrauenswürdige Zustellung, kein IPC-Handle-Import aus Zahlenwerten. */
    Control claim(Delivery issued, Peer peer) {
        synchronized (lock) {
            if (issued != delivery || issued == null || peer != target || claimed || !validated || revoked) { cancel(); return null; }
            claimed = true;
            claimedControl = new Control(issued.token,issued.source,binding,JOB_RIGHTS,true,true,true,true);
            return claimedControl;
        }
    }
    Ack targetAck(Delivery issued, Peer peer, Control control) {
        synchronized (lock) {
            if (issued != delivery || issued == null || peer != target || control != claimedControl || !claimed || revoked) return null;
            return new Ack(issued.id,issued.attempt,binding,issued.anchor,peer,peer.process().handle(),1,control);
        }
    }
    boolean acknowledge(Ack ack) {
        synchronized (lock) {
            if (outstanding || !validated || revoked || acknowledged || delivery == null || ack == null
                    || !delivery.id.equals(ack.transfer()) || delivery.attempt != ack.attempt() || !binding.equals(ack.binding())
                    || !delivery.anchor.equals(ack.anchor()) || ack.peer() != target || ack.heldTarget() != target.process().handle()
                    || ack.sequence() != 1 || ack.adoptedControl() != claimedControl || !claimed) { cancel(); return false; }
        }
        try (ProcessUse c = processes.borrow(target.process().handle())) {
            if (c.capability() != target.process().handle() || c.access() != PROCESS_ACCESS || !word(c.value()) || !permitted()) throw failure();
            requireProcess(c,target.process());
            synchronized (lock) {
                if (revoked || acknowledged) { cancel(); return false; }
                acknowledged = true; remote = CopyState.ADOPTED; return true;
            }
        } catch (RuntimeException failure) { cancel(); return false; }
    }
    boolean confirms(Acceptance answer) {
        synchronized (lock) {
            return acknowledged && !revoked && answer != null && answer.attempt() == delivery.attempt
                    && answer.peer() == target && answer.owner() == sourceProcess && answer.copy() == claimedControl
                    && delivery.binding.equals(answer.binding()) && delivery.anchor.equals(answer.anchor());
        }
    }
    /** Nur Meldung des gebundenen C-Ledgers, niemals CloseHandle(remoteValue) in S. */
    void targetClosed(Control control, Peer peer, boolean confirmed) {
        synchronized (lock) {
            if (!acknowledged || control != claimedControl || peer != target || remote != CopyState.ADOPTED) { cancel(); return; }
            remote = confirmed ? CopyState.CLOSED : CopyState.UNCERTAIN;
            if (!confirmed) cancel();
        }
    }
    void cancel() { synchronized (lock) { revoked = quarantine = true; } }
    Status status() { synchronized (lock) { return new Status(outstanding || unconfirmedUses != null,validated,acknowledged,revoked,remote,temporary,quarantine); } }
    /** Nutzungsreservierungen derselben Ledger, keine neue Handle-Registry. */
    private final class UseScope implements AutoCloseable {
        private final java.util.List<Borrow> uses = new java.util.ArrayList<>(3);
        private boolean unreturned;
        <T extends Borrow> T add(T use) { uses.add(Objects.requireNonNull(use)); return use; }
        void retain() { synchronized (lock) { unreturned = true; unconfirmedUses = this; } }
        public void close() {
            if (unreturned) return; // Keine angenommene Native-Rückkehr und kein Reset durch Timeout.
            RuntimeException first = null;
            for (int i = uses.size()-1; i >= 0; i--) {
                try { uses.get(i).close(); }
                catch (RuntimeException failure) { if (first == null) first = failure; else first.addSuppressed(failure); }
            }
            if (first != null) throw first;
        }
    }
    private void requireProcess(ProcessUse use, Owner expected) {
        ProcessState state = raw.inspectProcess(use.value());
        if (state == null || !state.alive() || !state.nonInherited() || !expected.identity().equals(state.identity())) throw failure();
    }
    private boolean permitted() { synchronized (lock) { if (revoked) return false; } return allowed.getAsBoolean(); }
    private static boolean word(long value) { return value != 0 && value != -1; }
    private static Failure failure() { return new Failure("unconfirmed-handle-transfer",0); }
}
