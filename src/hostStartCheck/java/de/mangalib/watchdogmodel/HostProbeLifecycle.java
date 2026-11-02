package de.mangalib.watchdogmodel;

import java.util.function.LongSupplier;
import de.mangalib.windowsadapter.ControlHostStartAdapter;

import static de.mangalib.watchdogmodel.WatchdogModel.*;
import static de.mangalib.watchdogmodel.FakeJobKernel.Role;

/** Schmale H-only-Fakebindung an dasselbe Lifecyclemodell, keine Ersatzpolicy. */
public final class HostProbeLifecycle implements ControlHostStartAdapter.Authority, AutoCloseable {
    private final LongSupplier nanos;
    private final Clock clock = new Clock();
    private final FakeJobKernel kernel = new FakeJobKernel();
    private final FakeRecoveryJournal journal = new FakeRecoveryJournal(FakeRecoveryJournal.State.CLEAN);
    private final LifecycleCoordinator coordinator = new LifecycleCoordinator(new BinaryIpcCodec.Id(41, 1), clock, kernel, journal);
    private Ticket ticket;
    public HostProbeLifecycle(LongSupplier nanos) { this.nanos = nanos; coordinator.initialize(journal.baseline()); }
    private void tick() {
        long seconds = nanos.getAsLong() / 1_000_000_000L;
        if (seconds < clock.now()) throw new IllegalArgumentException("nonmonotone-clock");
        clock.advance(seconds - clock.now()); coordinator.tick();
    }
    public synchronized Object admit() {
        tick(); Ticket next = coordinator.requestHostProbe().orElse(null);
        if (next != null) ticket = next;
        return next;
    }
    public synchronized boolean reserveStart(Object owner) { tick(); return owner == ticket && coordinator.model.reserveHostProbeStart(ticket); }
    public synchronized boolean intentConfirmed(Object owner) {
        tick(); return owner == ticket && ticket != null && coordinator.model.state() == State.PREPARING
                && journal.confirmsIntent(coordinator.model.supervisor(), ticket, null, null);
    }
    public synchronized boolean jobPrepared(Object owner) {
        tick(); return owner == ticket && coordinator.prepareOuter(ticket) && coordinator.beginHost(ticket);
    }
    public synchronized boolean resumeAllowed(Object owner) {
        tick(); return owner == ticket && ticket != null && coordinator.model.state() == State.HOST_STARTING
                && journal.confirmsIntent(coordinator.model.supervisor(), ticket, coordinator.model.outer().job, null);
    }
    public synchronized boolean resumed(Object owner, long pid, long birth, String image) {
        tick(); return owner == ticket && coordinator.hostReturned(ticket, new FakeJobKernel.Identity(pid, birth, image));
    }
    public synchronized long cleanupDeadline(Object owner) {
        tick(); if (owner != ticket) throw new IllegalArgumentException("foreign-ticket");
        coordinator.stop(ticket); return Math.multiplyExact(coordinator.model.cleanupDeadline(), 1_000_000_000L);
    }
    public synchronized boolean communicationEnded(Object owner) { tick(); return owner == ticket && coordinator.model.hostProbeEndConfirmed(ticket) && coordinator.reservedBytes() == 0; }
    public synchronized boolean finish(Object owner, int exit) {
        tick(); if (owner != ticket || !communicationEnded(owner)) return false;
        coordinator.hostEnded(ticket, exit != 0);
        return coordinator.finish(ticket, kernel.observe(Role.S, coordinator.model.outer()),
                new IoEnd(ticket, true, true, true, true, true, true));
    }
    public synchronized void failed(Object owner) { tick(); if (owner == ticket) coordinator.model.controlFailed(ticket); }
    public synchronized boolean handshake() {
        return ready() && coordinator.send(Direction.S_H, Type.START);
    }
    public synchronized boolean ready() { tick(); return coordinator.send(Direction.S_H, Type.HELLO) && coordinator.send(Direction.H_S, Type.READY); }
    public synchronized boolean badHandshake() { return coordinator.send(Direction.S_H, new Signal(Type.HELLO), "wrong-profile"); }
    public synchronized boolean resultAndEnd() {
        tick();
        if (!coordinator.send(Direction.H_S, Type.RESULT_AVAILABLE) || !coordinator.send(Direction.S_H, Type.CHANNEL_END)) return false;
        coordinator.eof(Direction.S_H);
        if (!coordinator.send(Direction.H_S, Type.CHANNEL_END)) return false;
        coordinator.eof(Direction.H_S); return true;
    }
    public synchronized void earlyEof() { coordinator.eof(Direction.H_S); }
    public synchronized boolean quarantine() { tick(); return coordinator.model.state() == State.QUARANTINED; }
    public synchronized boolean slotFree() { return coordinator.model.state() == State.IDLE; }
    public synchronized void loseIntent() { journal.loseWriter(coordinator.model.supervisor()); }
    public synchronized boolean refusesInnerJob() { return !coordinator.prepareInner(ticket) && coordinator.model.inner() == null; }
    @Override public synchronized void close() { coordinator.close(); }
}
