package de.mangalib.windowsadapter;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import static de.mangalib.windowsadapter.OuterJobCustodian.*;

/** C-Rollenlaufzeit. Kontrollspur bleibt unabhängig von blockierter Kommunikation aufrufbar. */
final class ScCustodian {
    /** C-lokale, vom gebundenen nativen Transferledger ausgegebene Fähigkeit, kein Wire-Handle. */
    record Adoption(UUID transfer, KernelCalls.Resource localJob, boolean nativeCopyVerified) { }
    interface DeliveryPort {
        Adoption takeVerifiedLocalCopy(UUID transfer);
        /** Getrennte Endbarriere: niemals ein einzelnes Close als globalen K-Endbeleg verwenden. */
        boolean copiesAndLaunchesSettled();
    }
    private final OuterJobCustodian core;
    private final ScChannel channel;
    private final ScChannel.Binding binding;
    private final DeliveryPort deliveries;
    private UUID transfer;
    private Object receipt;
    private boolean bindAttempted, bound, attempted, acknowledged, stopping, endSent, closedSent;
    ScCustodian(OuterJobCustodian core,ScChannel channel,ScChannel.Binding binding,DeliveryPort deliveries) {
        this.core=Objects.requireNonNull(core); this.channel=Objects.requireNonNull(channel); this.binding=Objects.requireNonNull(binding);
        this.deliveries=Objects.requireNonNull(deliveries);
    }
    boolean bind() {
        synchronized(this) { if(bindAttempted || stopping) { stop(); return false; } bindAttempted=true; }
        core.pollOwner();
        if(core.report().owner()!=Signal.ALIVE || core.report().revoked()) { stop(); return false; }
        try { channel.send(ScChannel.Kind.BOUND,binding.intent().session()); synchronized(this) { bound=true; } return true; }
        catch(IOException e) { stop(); return false; }
    }
    boolean adopt() {
        synchronized(this) { if(!bound || attempted || stopping) { stop(); return false; } attempted=true; }
        try {
            UUID announced=channel.receive(ScChannel.Kind.ADOPT,null).transfer();
            Adoption acquired=deliveries.takeVerifiedLocalCopy(announced);
            if(acquired==null || !acquired.nativeCopyVerified() || !announced.equals(acquired.transfer())
                    || acquired.localJob()==null || acquired.localJob().kind!=KernelCalls.Kind.JOB) { stop(); return false; }
            synchronized(this) { transfer=announced; }
            core.pollOwner();
            Object retained=core.retain(binding.intent(),binding.anchor(),acquired.localJob());
            synchronized(this) { receipt=retained; }
            // Widerruf überholt auch ein blockiertes retain; kein spätes Ack eröffnet READY.
            if(stopped() || retained==null || !core.live(retained)) { stop(); return false; }
            channel.send(ScChannel.Kind.ACK,transfer);
            synchronized(this) { if(stopping) return false; acknowledged=true; }
            return true;
        } catch(IOException | RuntimeException e) { stop(); return false; }
    }
    void receiveStop() {
        try { channel.receive(ScChannel.Kind.STOP,transfer); }
        catch(IOException e) { core.communicationLost(); }
        finally { stop(); }
    }
    void stop() { synchronized(this) { stopping=true; } core.stop(); }
    /** Muss auf eigener Spur laufen: Kommunikationsverlust ist KEIN Ownerende. */
    void controlStep() {
        core.pollOwner();
        if(core.report().revoked()) synchronized(this) { stopping=true; }
        core.terminateStep();
    }
    /** Einmalige statische Kein-H-Startfence; weitere Kopien sperren den Abschluss. */
    void finishStep() {
        Object held;
        synchronized(this) { held=receipt; if(!stopping || held==null || closedSent) return; }
        if(!deliveries.copiesAndLaunchesSettled()) return;
        synchronized(this) { if(!endSent) { core.recordReturn(held,null); endSent=true; } }
        core.terminateStep(); core.inspectEnd(); core.closeResources();
    }
    /** Endmeldung auf eigener Ausgabespur; keine Meldung aus bloßer Terminate-Erfolgsantwort. */
    boolean sendEnd() {
        Report r=core.report();
        synchronized(this) { if(closedSent || !r.groupEnded() || !r.resourcesClosed() || r.callsOutstanding()!=0
                || r.termination()!=Termination.ACCEPTED
                || !deliveries.copiesAndLaunchesSettled()) return false; closedSent=true; }
        try {
            // stop() sperrt Eingaben. Für Endbelege braucht der Transport seine eigene Ausgabespur.
            channel.send(ScChannel.Kind.END,transfer); channel.send(ScChannel.Kind.CLOSED,transfer); channel.close(); return true;
        } catch(IOException e) { return false; }
    }
    synchronized boolean acknowledged() { return acknowledged && !stopping; }
    private synchronized boolean stopped() { return stopping; }
    Report report() { return core.report(); }
    /** Vor Adoption: keine fremden Handles suchen. Nur eigenes C-Ende kann unbekannte Teilkopien schließen. */
    boolean exitWithoutAdoptionRequired() {
        synchronized(this) { return receipt==null && core.report().owner()==Signal.ENDED; }
    }
}
