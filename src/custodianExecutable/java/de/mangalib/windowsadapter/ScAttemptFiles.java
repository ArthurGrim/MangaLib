package de.mangalib.windowsadapter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

/** Neuer privater Namensraum; die historische globale Hashfreigabe wird nie gelesen. */
final class ScAttemptFiles {
    static final String PROPERTY="mangalib.sc.attempt";
    private ScAttemptFiles() { }
    static UUID session(String text) throws IOException {
        try {
            UUID id=UUID.fromString(text);
            if(!id.toString().equals(text) || id.equals(new UUID(0,0)))throw new IOException("sc-attempt-id");
            return id;
        } catch(IllegalArgumentException | NullPointerException error) { throw new IOException("sc-attempt-id"); }
    }
    static Path directory(Path root,String id) throws IOException {
        return root.resolve(".gradle/t02-sc-attempts").resolve(session(id).toString());
    }
    static String marker(UUID id,String hash,String approvalHash) throws IOException {
        if(!hash.matches("[0-9a-f]{64}") || !approvalHash.matches("[0-9a-f]{64}"))throw new IOException("sc-attempt-hash");
        return "SC_CONSUMED_V2\n"+id+"\n"+hash+"\n"+approvalHash+"\n";
    }
    static void verify(String approval,String consumed,UUID id,String hash,String approvalHash) throws IOException {
        if(!ScNativeManifest.requireApproval(approval,hash).equals(id)
                || !marker(id,hash,approvalHash).equals(consumed))throw new IOException("sc-attempt-consumption");
    }
}
