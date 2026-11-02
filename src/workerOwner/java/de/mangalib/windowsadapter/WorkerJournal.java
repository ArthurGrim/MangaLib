package de.mangalib.windowsadapter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;

/** Persistente Startsperre. Kein Recovery durch Neustart und keine historische OS-Freigabe. */
final class WorkerJournal {
    private final Path root;
    WorkerJournal(Path root) throws IOException {
        this.root=root.toAbsolutePath().normalize();
        Files.createDirectories(this.root); check();
    }
    private void check() throws IOException {
        for(Path p=root;p!=null;p=p.getParent())
            if(Files.isSymbolicLink(p) || !p.toRealPath().equals(p))throw new IOException("worker-journal-path");
    }
    synchronized boolean clear() throws IOException {
        check();return !Files.exists(root.resolve("pending"),LinkOption.NOFOLLOW_LINKS)
                && !Files.exists(root.resolve("quarantine"),LinkOption.NOFOLLOW_LINKS);
    }
    synchronized void begin(UUID id) throws IOException {
        if(!clear())throw new IOException("worker-journal-blocked");
        create("pending",id.toString()); // CREATE_NEW schützt auch gegen konkurrierende Ownerinstanzen.
    }
    synchronized void complete(UUID id) throws IOException {
        check();Path p=root.resolve("pending");
        if(Files.exists(root.resolve("quarantine"),LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(p) || Files.size(p)!=36 || !Files.readString(p).equals(id.toString()))
            throw new IOException("worker-journal-binding");
        // Nur die eigene aktuelle Startsperre und erst NACH sämtlichen Endbelegen entfernen.
        // Ein bestehender Quarantänemarker wird niemals entfernt.
        Files.delete(p);
    }
    synchronized void quarantine() throws IOException {
        check();if(!Files.exists(root.resolve("quarantine"),LinkOption.NOFOLLOW_LINKS))
            create("quarantine","UNCONFIRMED");
    }
    private void create(String name,String value) throws IOException {
        check();
        try(var f=FileChannel.open(root.resolve(name),StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){
            ByteBuffer b=StandardCharsets.US_ASCII.encode(value);while(b.hasRemaining())f.write(b);f.force(true);
        }
    }
}
