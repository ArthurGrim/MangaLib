package de.mangalib.windowsadapter;

import java.io.IOException;
import java.util.*;
import java.util.function.*;
import static de.mangalib.windowsadapter.KernelCalls.*;
import static de.mangalib.windowsadapter.OuterJobCustodian.*;
import static de.mangalib.windowsadapter.EmptyCustodianBootstrap.*;

/** Konkrete S-Verbindung: ein Transfer, eine Acksequenz, ein getrennter Enddialog. */
final class ScNativeConnection implements TransferConfirmation {
    private final ScNativeWire wire;
    private final ScStartProvider provider;
    private final ScStartProvider.Calls calls;
    private final HeldProcessLedger ledger;
    private final Peer peer;
    private final Owner owner;
    private final HandleTransferPort transfer;
    private final Predicate<Resource> sourceClosed;
    private final ToIntFunction<Resource> extraCopies;
    private final LongSupplier clock;
    private final long deadline;
    private final ScRevocationFence fence;
    private Resource source;
    private Object attempt;
    private HandleTransferPort.Delivery delivery;
    private Control copy;
    private boolean attempted,ack,stopSent,endAttempted,ended;
    private volatile boolean stopped;
    ScNativeConnection(ScNativeWire wire,ScStartProvider provider,ScStartProvider.Calls calls,HeldProcessLedger ledger,Peer peer,Owner owner,
                       Win64TransferContract.SourceLedger jobs,StartIntentStore.Binding binding,Predicate<Resource> sourceClosed,ToIntFunction<Resource> extraCopies,LongSupplier clock,long deadline) {
        this(wire,provider,calls,ledger,peer,owner,jobs,binding,sourceClosed,extraCopies,clock,deadline,null);
    }
    ScNativeConnection(ScNativeWire wire,ScStartProvider provider,ScStartProvider.Calls calls,HeldProcessLedger ledger,Peer peer,Owner owner,
                       Win64TransferContract.SourceLedger jobs,StartIntentStore.Binding binding,Predicate<Resource> sourceClosed,ToIntFunction<Resource> extraCopies,LongSupplier clock,long deadline,ScRevocationFence fence) {
        this.wire=wire;this.provider=provider;this.calls=calls;this.ledger=ledger;this.peer=peer;this.owner=owner;
        this.sourceClosed=sourceClosed;this.extraCopies=extraCopies;this.clock=clock;this.deadline=deadline;this.fence=fence;
        transfer=new HandleTransferPort(calls,ledger,jobs,owner,peer,binding,this::allowed);
    }
    private boolean allowed(){return !stopped && (fence==null || fence.allowed()) && clock.getAsLong()<deadline;}
    public ProcessEnd inspectPeer(Peer expected){
        if(expected!=peer || wire.failed())return new ProcessEnd(peer.process().handle(),peer.process().identity(),Signal.UNKNOWN);
        return ledger.inspect(peer.process());
    }
    public Acceptance takeOver(Object token,StartIntentStore store,StartIntentStore.Proof proof,Resource job,Peer expected,Owner s) {
        synchronized(this){if(attempted || stopped || expected!=peer || s!=owner)return null;attempted=true;source=job;attempt=token;}
        delivery=transfer.transfer(token,store,proof,job,peer,owner);if(delivery==null)return null;
        try {
            var grant=transfer.exportNative(delivery,peer);
            // Einzig diese private Copy-Provenienz erzeugt den C-lokalen Wert. Nie aus ACK rekonstruieren.
            if(fence==null)wire.send("GRANT",grant.transfer()+":"+Long.toUnsignedString(grant.targetValue(),16));
            else fence.run(()->{
                try{wire.send("GRANT",grant.transfer()+":"+Long.toUnsignedString(grant.targetValue(),16));}
                catch(IOException e){throw new java.io.UncheckedIOException(e);}
            });
            String received=fence==null?wire.receive("ACK"):fence.call(()->{
                try{return wire.receive("ACK");}catch(IOException e){throw new java.io.UncheckedIOException(e);}
            });
            if(!received.equals(delivery.id().toString()) || !allowed() || !store.valid(proof))return reject();
            copy=transfer.claim(delivery,peer);
            if(copy==null || !transfer.acknowledge(transfer.targetAck(delivery,peer,copy)))return reject();
            ProcessEnd alive=ledger.inspect(owner);
            if(!store.valid(proof))return reject();
            java.util.function.Supplier<Acceptance> accept=()->{
                synchronized(this){if(!allowed() || alive.signal()!=Signal.ALIVE)return reject();ack=true;}
                return new Acceptance(token,store.binding(),proof.anchor(),peer,owner,copy,2,true,1,true,alive);
            };
            return fence==null?accept.get():fence.call(accept);
        } catch(IOException | RuntimeException e){return reject();}
    }
    private Acceptance reject(){requestStop(attempt,deadline);return null;}
    public synchronized boolean confirmsTransfer(Acceptance answer){return ack && allowed() && transfer.confirms(answer);}
    public synchronized void requestStop(Object token,long limit){stopped=true;transfer.cancel();}
    public void controlStep(){
        synchronized(this){if(!stopped || stopSent || !ack)return;stopSent=true;}
        try{wire.send("STOP",delivery.id().toString());}catch(IOException e){/* Kein Endbeleg nach Kanalfehler. */}
    }
    public Inventory inventory(Object token,Resource job){
        var t=transfer.status();boolean verified=token==attempt && job==source && ack && !wire.failed() && !t.outstanding()
                && t.temporary()==HandleTransferPort.CopyState.CLOSED && (t.target()==HandleTransferPort.CopyState.ADOPTED || t.target()==HandleTransferPort.CopyState.CLOSED);
        int extra=extraCopies.applyAsInt(job);
        return new Inventory(token,job,verified && extra==0,!sourceClosed.test(job),!ended,extra,true);
    }
    public EndReceipt finishEmpty(Object token,Resource job){
        synchronized(this){if(token!=attempt || job!=source || !ack || !sourceClosed.test(job))return null;if(endAttempted)return ended?receipt():null;endAttempted=true;}
        try{
            wire.send("FENCE",delivery.id().toString());wire.finishOutput();
            if(!wire.receive("GROUP_END").equals(delivery.id().toString()) || !wire.receive("CLOSED").equals(delivery.id().toString()))return null;
            wire.finishInput();transfer.targetClosed(copy,peer,true);ended=true;return receipt();
        }catch(IOException | RuntimeException e){return null;}
    }
    private EndReceipt receipt(){return new EndReceipt(attempt,source,peer,new Report(true,Signal.ALIVE,true,Termination.ACCEPTED,true,true,true,0,1,deadline));}
    public Release releaseSupervisor(Object token,Peer expected){
        if(token!=attempt || expected!=peer || !ended || wire.outstanding())return null;
        // Query darf warten, aber weder einen Java-Lock noch ein fremdes Prozessobjekt verwenden.
        while(clock.getAsLong()<deadline){
            ProcessEnd end=ledger.inspect(peer.process());
            if(end.signal()==Signal.ENDED && calls.preparationSettled()){
                boolean closed=provider.releaseEnded() && provider.lastExitCode()==0;return new Release(token,peer,end,closed,closed);
            }
            if(end.signal()==Signal.ENDED){java.util.concurrent.locks.LockSupport.parkNanos(1_000_000);continue;}
            if(end.signal()!=Signal.ALIVE)return null;
            java.util.concurrent.locks.LockSupport.parkNanos(1_000_000);
        }
        return null;
    }
}
