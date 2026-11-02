package de.mangalib.ownerprobe;

import java.nio.file.Path;
import java.util.concurrent.locks.LockSupport;

/** Synthetischer Owner ohne Worker-/Jobcode. Nur später mit frischer OS-Einmalfreigabe starten. */
public final class OwnerProvenanceMain {
    private OwnerProvenanceMain(){ }
    public static void main(String[] args){
        run(args,"--owner-provenance-v1","OWNER_PROBE_V1");
    }
    static void run(String[] args,String flag,String profile){
        int exit=78;long origin=System.nanoTime();
        try{
            if(args.length!=3 || !args[0].equals(flag))throw new IllegalArgumentException();
            Path root=Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
            OwnerProbeFiles.verify(root,args[1],args[2],profile);
            exit=OwnerProbeLifecycle.run(OwnerProbeFiles.directory(root,args[1],profile),args[1],args[2],ProcessHandle.current().pid(),
                    ()->(System.nanoTime()-origin)/1_000_000,()->LockSupport.parkNanos(1_000_000));
        }catch(Exception | LinkageError error){ /* Keine internen Exceptions oder Umgebungswerte ausgeben. */ }
        if(exit!=0)System.err.println("OWNER_STOP_V1");
        System.exit(exit);
    }
}
