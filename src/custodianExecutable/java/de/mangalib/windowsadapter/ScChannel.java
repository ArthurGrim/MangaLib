package de.mangalib.windowsadapter;

import java.io.*;
import java.util.Objects;
import java.util.UUID;
import de.mangalib.watchdogmodel.ScFrameCodec;

/** Begrenztes stdio-Profil; Peerbeweis kommt vom gehaltenen Startledger, NICHT vom Ticket. */
final class ScChannel implements AutoCloseable {
    enum Kind { BOUND, ADOPT, ACK, STOP, END, CLOSED }
    record Message(Kind kind, UUID transfer) { }
    record Binding(StartIntentStore.Binding intent, StartIntentStore.Anchor anchor, UUID channel) {
        Binding {
            Objects.requireNonNull(intent); Objects.requireNonNull(anchor); Objects.requireNonNull(channel);
            if (channel.equals(new UUID(0,0)) || anchor.revision()!=1 || anchor.hash()==null
                    || !anchor.hash().matches("[0-9a-f]{64}")) throw new IllegalArgumentException("sc-binding");
        }
    }
    interface PeerCheck { boolean heldPeerAndChannelMatch(); }
    private final Binding binding;
    private final boolean custodian;
    private final InputStream input;
    private final OutputStream output;
    private final PeerCheck peer;
    private final Object lock = new Object();
    private long incoming=1, outgoing=1;
    private int readBytes, writtenBytes;
    private boolean reading, writing, closed, inputEnd, outputEnd;
    private volatile boolean failed;
    ScChannel(Binding binding, boolean custodian, InputStream input, OutputStream output, PeerCheck peer) {
        this.binding=Objects.requireNonNull(binding); this.custodian=custodian; this.input=Objects.requireNonNull(input);
        this.output=Objects.requireNonNull(output); this.peer=Objects.requireNonNull(peer);
    }
    void send(Kind kind, UUID transfer) throws IOException {
        long seq;
        synchronized(lock) { check(); if(writing || outputEnd || outgoing>4) throw fail(); writing=true; seq=outgoing++; }
        try {
            direction(kind,custodian);
            String text="SC1|"+kind.name()+"|"+Objects.requireNonNull(transfer)+"|"+binding.intent().group()+"|"+binding.anchor().hash();
            byte[] bytes=ScFrameCodec.encode(binding.intent().session(),binding.intent().ticket(),binding.channel(),custodian,seq,text);
            if(!peer.heldPeerAndChannelMatch()) throw fail();
            synchronized(lock) { check(); writtenBytes+=bytes.length+4; if(writtenBytes>2048) throw fail(); }
            new DataOutputStream(output).writeInt(bytes.length); output.write(bytes); output.flush();
            synchronized(lock) { check(); if(kind==Kind.CLOSED) outputEnd=true; }
        } catch(RuntimeException | IOException e) { cancel(); throw new IOException("sc-channel-rejected"); }
        finally { synchronized(lock) { writing=false; } }
    }
    Message receive(Kind expected, UUID transfer) throws IOException {
        long seq;
        synchronized(lock) { check(); if(reading || inputEnd || incoming>4) throw fail(); reading=true; seq=incoming++; }
        try {
            direction(expected,!custodian);
            if(!peer.heldPeerAndChannelMatch()) throw fail();
            int size=new DataInputStream(input).readInt();
            if(size<1 || size>ScFrameCodec.MAX_FRAME) throw fail();
            synchronized(lock) { readBytes+=size+4; if(readBytes>2048) throw fail(); }
            byte[] bytes=input.readNBytes(size);
            if(bytes.length!=size) throw fail();
            String text=ScFrameCodec.decode(bytes,binding.intent().session(),binding.intent().ticket(),binding.channel(),!custodian,seq);
            UUID actual=transfer;
            if(actual==null) {
                // Nur ADOPT darf die erst NACH K-Erstellung erzeugte Transfer-ID ankündigen.
                // Dies erteilt keine Handleberechtigung: der C-Ledger muss diese ID separat kennen.
                if(expected!=Kind.ADOPT)throw fail();
                String[] fields=text.split("\\|",-1);
                if(fields.length!=5)throw fail();
                actual=UUID.fromString(fields[2]);
                if(actual.equals(new UUID(0,0)) || !actual.toString().equals(fields[2]))throw fail();
            }
            String canonical="SC1|"+expected.name()+"|"+actual+"|"+binding.intent().group()+"|"+binding.anchor().hash();
            if(!text.equals(canonical) || !peer.heldPeerAndChannelMatch()) throw fail();
            synchronized(lock) { check(); if(expected==Kind.CLOSED) inputEnd=true; }
            return new Message(expected,actual);
        } catch(RuntimeException | IOException e) { cancel(); throw new IOException("sc-channel-rejected"); }
        finally { synchronized(lock) { reading=false; } }
    }
    /** EOF gehört zum Endbeleg, nicht allein die CLOSED-Nachricht. */
    boolean confirmEof() throws IOException {
        synchronized(lock) { check(); if(!inputEnd || reading) throw fail(); reading=true; }
        try { if(input.read()!=-1) throw fail(); synchronized(lock) { check(); return true; } }
        catch(IOException e) { cancel(); throw new IOException("sc-end-rejected"); }
        finally { synchronized(lock) { reading=false; } }
    }
    void cancel() { synchronized(lock) { failed=true; } }
    boolean outstanding() { synchronized(lock) { return reading || writing; } }
    boolean failed() { synchronized(lock) { return failed; } }
    private void check() throws IOException { if(failed || closed) throw fail(); }
    private IOException fail() { failed=true; return new IOException("sc-channel-rejected"); }
    private static void direction(Kind kind, boolean fromC) throws IOException {
        if(kind==null || (fromC != (kind==Kind.BOUND || kind==Kind.ACK || kind==Kind.END || kind==Kind.CLOSED)))
            throw new IOException("sc-direction");
    }
    @Override public void close() throws IOException {
        synchronized(lock) { if(closed) return; closed=true; }
        IOException error=null;
        try { input.close(); } catch(IOException e) { error=e; }
        try { output.close(); } catch(IOException e) { if(error==null) error=e; }
        if(error!=null) { cancel(); throw new IOException("sc-close-unconfirmed"); }
    }
}
