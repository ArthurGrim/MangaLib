package de.mangalib.windowsadapter;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;
import static de.mangalib.windowsadapter.KernelCalls.*;
import static de.mangalib.windowsadapter.OuterJobCustodian.*;
import static de.mangalib.windowsadapter.EmptyCustodianBootstrap.*;
import static de.mangalib.windowsadapter.Win64TransferContract.*;
import de.mangalib.windowsadapter.ScSupervisor.BoundChild;

/** Konkreter einmaliger C-Startvertrag; alle OS-Aufrufe injiziert, kein Worker-Fallback. */
final class ScStartProvider implements ScSupervisor.StartPort {
    enum HandleKind { PROCESS, THREAD }
    interface Acquisition { void acquired(HandleKind kind,long value); }
    record Created(boolean success,long process,long thread,long pid) { }
    record Snapshot(boolean attempted,boolean outstanding,boolean bound,boolean revoked,boolean ended,
                    boolean handlesClosed,boolean quarantine) { }
    interface Calls extends Raw {
        default boolean admissionAllowed(){return true;} // Unveränderte historische Fakeports.
        default void cancelAdmission(){ }
        default <T> T authorized(java.util.function.Supplier<T> phase){return phase.get();}
        long selfCopy(int access);
        long processCopy(long held,int access);
        void prepare(long ownerCopy);
        Created create(Acquisition register);
        boolean resume(long thread);
        boolean terminateProcess(long held);
        boolean close(long held);
        int exitCode(long held);
        InputStream input();
        OutputStream output();
        boolean closePipes();
        boolean preparationSettled();
    }
    interface ConnectionFactory {
        TransferConfirmation connect(Peer peer,Owner owner,HeldProcessLedger processes,Calls calls,StartIntentStore.Proof proof);
        /** Prelude + BOUND aus genau den eigenen Pipeenden, nicht aus beliebigem Peer. */
        boolean handshake(Peer peer,Owner owner,long inheritedOwner,Calls calls,StartIntentStore.Proof proof);
    }
    private final Calls calls;
    private final StartIntentStore store;
    private final HeldProcessLedger ledger;
    private final ConnectionFactory connections;
    private final LongSupplier clock;
    private final long deadline;
    private final String image;
    private boolean attempted,busy,revoked,bound,uncertain,ended,closed,terminateAttempted;
    private boolean controlBusy,releaseBusy;
    private boolean releaseAttempted;
    private int lastExitCode=Integer.MIN_VALUE;
    private volatile long process,originalProcess,thread,self,inherited;
    private Object selfToken,cToken;
    private volatile Identity cIdentity;
    private final java.util.Set<Long> closeAttempted=new java.util.HashSet<>();
    private final java.util.Set<Long> confirmedClosed=new java.util.HashSet<>();
    ScStartProvider(Calls calls,StartIntentStore store,ConnectionFactory connections,LongSupplier clock,long deadline,String image) {
        this.calls=Objects.requireNonNull(calls);this.store=Objects.requireNonNull(store);this.connections=Objects.requireNonNull(connections);
        this.clock=Objects.requireNonNull(clock);this.deadline=deadline;this.image=Objects.requireNonNull(image);
        ledger=new HeldProcessLedger(calls,calls::close);
        if(deadline<=clock.getAsLong() || image.isBlank())throw new IllegalArgumentException("sc-start-config");
    }
    public BoundChild startAndBind(StartIntentStore.Binding binding,StartIntentStore.Proof proof) {
        synchronized(this) { if(attempted || revoked)return null;attempted=busy=true; }
        try {
            if(!store.binding().equals(binding) || !store.valid(proof) || !allowed())throw failure();
            ProcessState s=prepareOwner();
            inherited=calls.processCopy(self,PROCESS_RIGHTS);
            if(!word(inherited) || inherited==self || !checked(inherited,s.identity()).alive())throw failure();
            calls.prepare(inherited);
            if(!allowed() || !store.valid(proof))throw failure();
            // Teilhandles werden durch den Adapter VOR jeder weiteren nativen Abfrage registriert.
            Created result=calls.create(this::register);
            if(result==null || !result.success() || process!=result.process() || thread!=result.thread()
                    || !word(process) || !word(thread) || process==thread || result.pid()<=0)throw failure();
            ProcessState c=checked(process,null);cIdentity=c.identity();
            if(cIdentity.pid()!=result.pid() || cIdentity.pid()==s.identity().pid() || !image.equalsIgnoreCase(cIdentity.image()))throw failure();
            if(!allowed() || !store.valid(proof)) { rollbackSuspended();return null; }
            long reduced=calls.processCopy(process,PROCESS_ACCESS);
            if(!word(reduced) || reduced==process || reduced==thread || reduced==self || reduced==inherited)throw failure();
            // Auch diese Kopie bleibt bei fehlgeschlagener Identitätsprüfung im Ledger erhalten.
            cToken=ledger.registerAcquired(reduced,PROCESS_ACCESS,cIdentity);
            // CreateProcess liefert die eigene Cleanupauthority mit TERMINATE.
            // Die reduzierte DUP_HANDLE-Capability besitzt TERMINATE ausdrücklich NICHT.
            // Original bis zum bestätigten C-Ende getrennt halten, niemals den reduzierten Handle terminieren.
            process=reduced;
            if(!allowed()) { rollbackSuspended();return null; }
            if(!calls.resume(thread))throw failure();
            if(!closeOwn(thread))throw failure();
            var owner=new Owner(selfToken,s.identity(),PROCESS_RIGHTS,true);
            var peer=new Peer(new Owner(cToken,cIdentity,PROCESS_RIGHTS,true),new Object(),UUID.randomUUID());
            if(!allowed() || !calls.authorized(()->connections.handshake(peer,owner,inherited,calls,proof))
                    || !allowed() || !store.valid(proof) || !checked(process,cIdentity).alive())throw failure();
            if(!closeOwn(inherited))throw failure();
            TransferConfirmation connection=calls.authorized(()->connections.connect(peer,owner,ledger,calls,proof));
            if(connection==null || !allowed())throw failure();
            synchronized(this) { if(revoked || !calls.admissionAllowed())throw failure();bound=true; }
            return new BoundChild(peer,owner,connection,true);
        } catch(RuntimeException failure) { cancel();return null; }
        finally { synchronized(this) { busy=false; } }
    }
    private synchronized void register(HandleKind kind,long value) {
        if(!word(value) || value==self || value==inherited || value==process || value==thread) {uncertain=true;throw failure();}
        if(kind==HandleKind.PROCESS && process==0){process=originalProcess=value;}
        else if(kind==HandleKind.THREAD && thread==0)thread=value;
        else {uncertain=true;throw failure();}
    }
    private ProcessState checked(long held,Identity expected) {
        if(!word(held))throw failure();
        ProcessState s=calls.inspectProcess(held);
        if(s==null || !s.alive() || !s.nonInherited() || (expected!=null && !expected.equals(s.identity())))throw failure();
        return s;
    }
    private synchronized boolean allowed(){return !revoked && calls.admissionAllowed() && clock.getAsLong()<deadline;}
    public void cancel(){calls.cancelAdmission();synchronized(this){revoked=true;}} // Keine native Operation.
    ProcessState prepareOwner(){
        if(selfToken!=null)return checked(self,null);
        self=calls.selfCopy(PROCESS_ACCESS);
        ProcessState s=checked(self,null);
        if(s.identity().pid()!=calls.currentProcessId() || !image.equalsIgnoreCase(s.identity().image()))throw failure();
        selfToken=ledger.registerAcquired(self,PROCESS_ACCESS,s.identity());return s;
    }
    boolean releaseOwnerOnly(){
        synchronized(this){if(attempted || busy || releaseAttempted)return false;releaseAttempted=true;}
        boolean done=(selfToken==null || ledger.close(selfToken)) && ledger.complete();
        synchronized(this){closed=done;return done;}
    }
    /** Eigene suspendierte oder fehlgeschlagene Instanz, nie PID-/Namenssuche. */
    void rollbackSuspended() {
        long held;
        synchronized(this){
            revoked=true;
            // Teilstart ist noch kein identifizierter Cleanup-Kandidat. Ohne Instanzbeleg
            // keinen nativen Versuch verbrauchen; die spätere bekannte Rückgabe bleibt behandelbar.
            if(terminateAttempted || releaseBusy || !word(originalProcess) || cIdentity==null)return;
            terminateAttempted=controlBusy=true;held=originalProcess;
        }
        try {
            ProcessState state=calls.inspectProcess(held);
            if(state==null || cIdentity==null || !cIdentity.equals(state.identity()))return;
            if(state.alive() && !calls.terminateProcess(held))return;
            ProcessState after=calls.inspectProcess(held);
            synchronized(this){ended=after!=null && !after.alive() && cIdentity.equals(after.identity());}
        } catch(RuntimeException unknown){synchronized(this){uncertain=true;}}
        finally{synchronized(this){controlBusy=false;}}
    }
    /** Erst gehaltenes Ende+Exitcode, dann eigene Handles/Pipes; unklare Rückgabe nie wiederholen. */
    boolean releaseEnded() {
        synchronized(this){if(releaseAttempted)return closed;if(busy || controlBusy || releaseBusy || process==0 || cIdentity==null)return false;releaseBusy=true;}
        try {
            ProcessState end=calls.inspectProcess(process);
            if(end==null || end.alive() || !cIdentity.equals(end.identity()))return false;
            int exit=calls.exitCode(process);
            if(!calls.preparationSettled())return false;
            synchronized(this){ended=true;lastExitCode=exit;releaseAttempted=true;}
            boolean done=(cToken==null || ledger.close(cToken)) && closeOwn(originalProcess) && closeOwn(thread) && closeOwn(inherited);
            done &= selfToken==null?closeOwn(self):ledger.close(selfToken);
            done &= calls.preparationSettled() && calls.closePipes() && ledger.complete();
            synchronized(this){closed=done && !uncertain;return closed;}
        } catch(RuntimeException unknown){synchronized(this){uncertain=true;}return false;}
        finally{synchronized(this){releaseBusy=false;}}
    }
    private boolean closeOwn(long value) {
        if(value==0)return true;
        synchronized(this){if(confirmedClosed.contains(value))return true;if(!closeAttempted.add(value))return false;}
        boolean ok=calls.close(value);
        synchronized(this){if(ok)confirmedClosed.add(value);else uncertain=true;}return ok;
    }
    synchronized Snapshot snapshot(){return new Snapshot(attempted,busy,bound,revoked,ended,closed,uncertain || revoked);}
    HeldProcessLedger ledger(){return ledger;}
    synchronized int lastExitCode(){return lastExitCode;}
    Identity acquiredIdentity(){return cIdentity;} // Nur bereits gehalten belegte Instanz, niemals PID-Suche.
    private static boolean word(long value){return value!=0 && value!=-1;}
    private static IllegalStateException failure(){return new IllegalStateException("sc-start-unconfirmed");}
}
