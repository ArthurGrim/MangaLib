package de.mangalib.windowsadapter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Getrennter begrenzter lokaler Kontrollbriefkasten im bereits gebundenen privaten Versuchordner. */
final class ScOuterStop {
    record Binding(StartIntentStore.Binding intent,StartIntentStore.Anchor anchor,KernelCalls.Identity supervisor){
        Binding {
            if(anchor.revision()!=1 || !anchor.hash().matches("[a-f0-9]{64}") || supervisor.pid()<=0 || supervisor.creationTime()<=0
                    || !ScNativeManifest.JAVA.equalsIgnoreCase(supervisor.image()))throw new IllegalArgumentException("sc-control-binding");
        }
        String key(){return "SCSTOP1|"+intent.session()+"|"+intent.ticket()+"|"+intent.group()+"|"+anchor.revision()+"|"+anchor.hash()+"|"+supervisor.pid()+"|"+supervisor.creationTime();}
    }
    private final Path directory;
    final Binding binding;
    private final ScRevocationFence fence;
    private final Runnable localStop;
    private final AtomicBoolean notified=new AtomicBoolean();
    private volatile boolean failed,acknowledged,closed;
    ScOuterStop(Path directory,Binding binding,ScRevocationFence fence,Runnable localStop){this.directory=directory;this.binding=binding;this.fence=fence;this.localStop=localStop;}
    void publish() throws IOException {write("outer-binding","BIND|"+binding.key()+"\n");}
    /** Aufruf nur aus unabhängiger Kontrollspur, nie aus dem blockierenden Bootstrapreader. */
    void step(){
        if(failed)return;
        try {
            String stop=read("outer-stop");
            if(stop!=null){
                if(!stop.equals("STOP|"+binding.key()+"\n"))throw new IOException("sc-control-rejected");
                fence.revoke();
                if(!acknowledged){
                    // ACK beweist NUR die gemeinsame Revokation, keine Rückkehr oder Cleanup.
                    write("outer-ack","REVOKED|"+binding.key()+"|"+fence.snapshot().outstanding()+"\n");
                    acknowledged=true;notifyStop();
                }
                return;
            }
            if(!fence.snapshot().admitted() && !fence.snapshot().revoked()){
                String admit=read("outer-admit");
                if(admit!=null){
                    if(!admit.equals("ADMIT|"+binding.key()+"\n"))throw new IOException("sc-control-rejected");
                    fence.admit();
                    if(fence.allowed())write("outer-bound","BOUND|"+binding.key()+"\n");
                }
            }
        } catch(IOException | RuntimeException failure){failed=true;fence.revoke();notifyStop();}
    }
    private void notifyStop(){if(notified.compareAndSet(false,true))try{localStop.run();}catch(RuntimeException failure){failed=true;}}
    void finish() throws IOException {fence.revoke();write("outer-end","CLOSED|"+binding.key()+"\n");closed=true;}
    boolean failed(){return failed;}
    boolean closed(){return closed;}
    private String read(String name) throws IOException {
        Path file=directory.resolve(name);
        if(!Files.exists(file,LinkOption.NOFOLLOW_LINKS))return null;
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) || Files.size(file)>512)throw new IOException("sc-control-size");
        byte[] bytes;
        try(var input=Files.newInputStream(file)){bytes=input.readNBytes(513);}
        if(bytes.length>512)throw new IOException("sc-control-size");
        if(bytes.length==0 || bytes[bytes.length-1]!=10)return null; // Teilpublikation ist keine Nachricht.
        return new String(bytes,StandardCharsets.US_ASCII);
    }
    private void write(String name,String text) throws IOException {
        if(text.length()>512)throw new IOException("sc-control-size");
        Files.writeString(directory.resolve(name),text,StandardCharsets.US_ASCII,StandardOpenOption.CREATE_NEW);
    }
}
