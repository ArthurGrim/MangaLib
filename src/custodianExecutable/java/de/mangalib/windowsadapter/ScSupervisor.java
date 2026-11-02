package de.mangalib.windowsadapter;

import java.util.Objects;
import java.util.function.LongSupplier;
import static de.mangalib.windowsadapter.EmptyCustodianBootstrap.*;
import static de.mangalib.windowsadapter.OuterJobCustodian.*;

/** Einmaliger S-Einstieg. Nur leeres K; kein H/J/W, kein Admission-/Restartautomat. */
final class ScSupervisor {
    /** Nur der eigene suspendierte Start und dessen gehaltenes Ergebnis dürfen diese Bindung erzeugen. */
    record BoundChild(Peer peer, Owner owner, TransferConfirmation connection, boolean channelBound) { }
    interface StartPort {
        BoundChild startAndBind(StartIntentStore.Binding binding, StartIntentStore.Proof proof);
        /** Muss Teilstarts auch nach lokaler Widerrufung behalten, kein PID-Kill. */
        void cancel();
    }
    private final StartIntentStore store;
    private final KernelCalls kernel;
    private final StartPort starts;
    private final LongSupplier clock;
    private final long deadline, cleanupBudget;
    private final ScRevocationFence fence;
    private boolean attempted, stopped, busy;
    private EmptyCustodianBootstrap bootstrap;
    ScSupervisor(StartIntentStore store,KernelCalls kernel,StartPort starts,LongSupplier clock,long deadline,long cleanupBudget) {
        this(store,kernel,starts,clock,deadline,cleanupBudget,null);
    }
    ScSupervisor(StartIntentStore store,KernelCalls kernel,StartPort starts,LongSupplier clock,long deadline,long cleanupBudget,ScRevocationFence fence) {
        this.store=Objects.requireNonNull(store); this.kernel=Objects.requireNonNull(kernel); this.starts=Objects.requireNonNull(starts);
        this.clock=Objects.requireNonNull(clock); this.deadline=deadline; this.cleanupBudget=cleanupBudget;this.fence=fence;
        if(deadline<=clock.getAsLong() || cleanupBudget<=0) throw new IllegalArgumentException("sc-deadline");
    }
    boolean begin() {
        return begin(null);
    }
    boolean begin(StartIntentStore.Proof prepared) {
        synchronized(this) { if(attempted || stopped) return false; attempted=busy=true; }
        try {
            var proof=prepared==null?store.prepare():prepared;
            if(!allowed() || !store.valid(proof)) { stop(); return false; }
            BoundChild child=starts.startAndBind(store.binding(),proof);
            if(child==null || !child.channelBound() || !allowed() || !store.valid(proof)) { stop(); return false; }
            var next=new EmptyCustodianBootstrap(store,kernel,child.connection(),child.peer(),child.owner(),clock,deadline,cleanupBudget);
            synchronized(this) { bootstrap=next; if(stopped) { next.abort(); return false; } }
            if(!next.begin(proof)) { stop(); return false; }
            if(!allowed()) { stop(); return false; }
            return true;
        } catch(Exception failure) { stop(); return false; }
        finally { synchronized(this) { busy=false; } }
    }
    private synchronized boolean allowed() { return !stopped && (fence==null || fence.allowed()) && clock.getAsLong()<deadline; }
    void stop() {
        EmptyCustodianBootstrap b;
        synchronized(this) { stopped=true; b=bootstrap; }
        // Kein Monitor über Prozess-, Kanal- oder Kernelaufrufen.
        try { starts.cancel(); } finally { if(b!=null) b.abort(); }
    }
    boolean ready() {
        EmptyCustodianBootstrap b;
        synchronized(this) { b=bootstrap; }
        if(!allowed() || b==null || !b.pollPeer()) { stop(); return false; }
        return b.report().ready();
    }
    void finish(boolean normal) {
        EmptyCustodianBootstrap b;
        synchronized(this) { stopped=true; b=bootstrap; }
        try { starts.cancel(); } finally { if(b!=null) b.finishStep(normal); }
    }
    synchronized Result result() { return bootstrap==null ? null : bootstrap.report(); }
    synchronized boolean outstanding() { return busy; }
}
