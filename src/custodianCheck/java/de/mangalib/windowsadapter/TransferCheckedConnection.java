package de.mangalib.windowsadapter;

import static de.mangalib.windowsadapter.KernelCalls.*;
import static de.mangalib.windowsadapter.OuterJobCustodian.*;
import static de.mangalib.windowsadapter.EmptyCustodianBootstrap.*;

/** Dekoriert den vorhandenen Bootstrapkanal, ohne eigenen Lifecycle/Timer. */
final class TransferCheckedConnection implements EmptyCustodianBootstrap.TransferConfirmation {
    interface Receiver extends Connection {
        void issued(HandleTransferPort.Delivery delivery, HandleTransferPort port);
        HandleTransferPort.Ack transferAck();
    }
    private final HandleTransferPort transfer;
    private final Receiver receiver;
    TransferCheckedConnection(HandleTransferPort transfer, Receiver receiver) { this.transfer = java.util.Objects.requireNonNull(transfer); this.receiver = java.util.Objects.requireNonNull(receiver); }
    public ProcessEnd inspectPeer(Peer peer) { return receiver.inspectPeer(peer); }
    public Acceptance takeOver(Object attempt, StartIntentStore store, StartIntentStore.Proof proof, Resource source, Peer peer, Owner owner) {
        HandleTransferPort.Delivery delivery = transfer.transfer(attempt,store,proof,source,peer,owner);
        if (delivery == null) return null;
        receiver.issued(delivery,transfer);
        Acceptance answer = receiver.takeOver(attempt,store,proof,source,peer,owner);
        if (answer == null || !transfer.acknowledge(receiver.transferAck()) || !transfer.confirms(answer)) { transfer.cancel(); return null; }
        return answer;
    }
    public boolean confirmsTransfer(Acceptance answer) { return transfer.confirms(answer); }
    public void requestStop(Object attempt, long deadline) { transfer.cancel(); receiver.requestStop(attempt,deadline); }
    public void controlStep() { receiver.controlStep(); }
    public Inventory inventory(Object attempt, Resource source) {
        Inventory original = receiver.inventory(attempt,source);
        var state = transfer.status();
        if (original == null) return null;
        boolean clear = !state.outstanding() && state.temporary() != HandleTransferPort.CopyState.UNCERTAIN
                && (state.target() == HandleTransferPort.CopyState.NONE || state.target() == HandleTransferPort.CopyState.ADOPTED
                || state.target() == HandleTransferPort.CopyState.CLOSED);
        return new Inventory(original.attempt(),original.source(),original.verified() && clear,original.supervisorCopy(),original.custodianCopy(),original.extraCopies(),original.inheritanceExcluded());
    }
    public EndReceipt finishEmpty(Object attempt, Resource source) { return receiver.finishEmpty(attempt,source); }
    public Release releaseSupervisor(Object attempt, Peer peer) { return receiver.releaseSupervisor(attempt,peer); }
}
