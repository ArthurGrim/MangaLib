package de.mangalib.windowsadapter;

import java.io.IOException;
import java.util.Objects;
import static de.mangalib.windowsadapter.KernelCalls.*;
import static de.mangalib.windowsadapter.OuterJobCustodian.*;
import static de.mangalib.windowsadapter.EmptyCustodianBootstrap.*;

/** S-seitige feste stdio-Verdrahtung des vorhandenen TransferCheckedConnection. */
final class ScTransferReceiver implements TransferCheckedConnection.Receiver {
    /**
     * Vertrauenswürdige lokale OS-Verdrahtung, KEIN zweiter frei verbundener Kanal.
     * Der C-Provider muss die Delivery nativ in seiner Tabelle registrieren und
     * die Belege auf den selbst gestarteten gehaltenen Peer zurückführen.
     */
    interface RemoteLedger extends Connection {
        void deliver(HandleTransferPort.Delivery issued);
        Acceptance adopted(Object attempt,StartIntentStore store,StartIntentStore.Proof proof,Resource source,
                           Peer peer,Owner owner,Control surrogate);
    }
    private final ScChannel channel;
    private final RemoteLedger remote;
    private HandleTransferPort transfer;
    private HandleTransferPort.Delivery delivery;
    private HandleTransferPort.Ack ack;
    private boolean attempted, revoked;
    private boolean stopSent, endAttempted;
    private EndReceipt endReceipt;
    ScTransferReceiver(ScChannel channel,RemoteLedger remote) { this.channel=Objects.requireNonNull(channel);this.remote=Objects.requireNonNull(remote); }
    public synchronized void issued(HandleTransferPort.Delivery issued,HandleTransferPort port) {
        if(delivery!=null || revoked) throw new IllegalStateException("sc-delivery-once");
        delivery=Objects.requireNonNull(issued);transfer=Objects.requireNonNull(port);
    }
    public Acceptance takeOver(Object attempt,StartIntentStore store,StartIntentStore.Proof proof,Resource source,Peer peer,Owner owner) {
        synchronized(this) { if(attempted || revoked || delivery==null) return null;attempted=true; }
        try {
            remote.deliver(delivery);
            channel.send(ScChannel.Kind.ADOPT,delivery.id());
            channel.receive(ScChannel.Kind.ACK,delivery.id());
            synchronized(this) { if(revoked) return null; }
            Control surrogate=transfer.claim(delivery,peer);
            if(surrogate==null) return null;
            Acceptance answer=remote.adopted(attempt,store,proof,source,peer,owner,surrogate);
            if(answer==null || answer.copy()!=surrogate) { transfer.cancel();return null; }
            synchronized(this) { if(revoked) return null;ack=transfer.targetAck(delivery,peer,surrogate); }
            return answer;
        } catch(IOException | RuntimeException failure) { transfer.cancel();return null; }
    }
    public synchronized HandleTransferPort.Ack transferAck(){return ack;}
    public ProcessEnd inspectPeer(Peer peer){
        if(channel.failed())return new ProcessEnd(peer.process().handle(),peer.process().identity(),Signal.UNKNOWN);
        return remote.inspectPeer(peer);
    }
    public void requestStop(Object attempt,long deadline) {
        synchronized(this) { revoked=true;if(transfer!=null)transfer.cancel(); }
        remote.requestStop(attempt,deadline);
    }
    public void controlStep(){
        boolean send;
        synchronized(this) { send=revoked && delivery!=null && !stopSent; if(send)stopSent=true; }
        if(send) try { channel.send(ScChannel.Kind.STOP,delivery.id()); } catch(IOException failure) { channel.cancel(); }
        remote.controlStep();
    }
    public Inventory inventory(Object attempt,Resource source){return remote.inventory(attempt,source);}
    public EndReceipt finishEmpty(Object attempt,Resource source){
        synchronized(this) { if(endAttempted)return endReceipt; }
        EndReceipt end=remote.finishEmpty(attempt,source);
        if(end==null || end.report()==null || !end.report().groupEnded() || !end.report().resourcesClosed())return null;
        synchronized(this) { if(endAttempted)return endReceipt; endAttempted=true; }
        try {
            channel.receive(ScChannel.Kind.END,delivery.id());channel.receive(ScChannel.Kind.CLOSED,delivery.id());
            if(!channel.confirmEof())return null;
            synchronized(this) { endReceipt=end; } return end;
        } catch(IOException | RuntimeException failure) { channel.cancel();return null; }
    }
    public Release releaseSupervisor(Object attempt,Peer peer){
        Release receipt=remote.releaseSupervisor(attempt,peer);
        if(receipt==null)return null;
        boolean closed=false;
        try { channel.close();closed=!channel.outstanding(); } catch(IOException failure) { channel.cancel(); }
        return new Release(receipt.attempt(),receipt.peer(),receipt.custodianEnd(),receipt.channelsClosed()&&closed,receipt.localPeerCopyClosed());
    }
}
