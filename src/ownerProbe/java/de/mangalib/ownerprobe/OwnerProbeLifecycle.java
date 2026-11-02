package de.mangalib.ownerprobe;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.LongSupplier;

/** Ein Mainthread, begrenzte Dateikontrolle, kein weiterer Prozess und keine native Ressource. */
final class OwnerProbeLifecycle {
    private OwnerProbeLifecycle(){ }
    static int run(Path dir,String id,String artifact,long ownPid,LongSupplier elapsedMs,Runnable pause)throws IOException{
        String key=null;
        while(elapsedMs.getAsLong()<5000){
            String binding=OwnerProbeFiles.message(dir,"binding");
            if(binding!=null){
                String[] p=binding.stripTrailing().split("\\|",-1);
                if(p.length!=5 || !p[0].equals("OP1") || !p[1].equals(id) || !p[2].equals(artifact)
                        || !p[3].equals(Long.toString(ownPid)) || !p[4].matches("[1-9][0-9]{0,18}"))throw new IOException("owner-binding");
                try{if(Long.parseLong(p[4])<=0)throw new NumberFormatException();}catch(NumberFormatException e){throw new IOException("owner-binding");}
                key=String.join("|",p);if(!binding.equals(key+"\n"))throw new IOException("owner-binding");break;
            }
            pause.run();
        }
        if(key==null)return 78;
        boolean ready=false;
        while(elapsedMs.getAsLong()<15000){
            String stop=OwnerProbeFiles.message(dir,"stop");
            if(stop!=null){if(!stop.equals("STOP|"+key+"\n"))throw new IOException("owner-stop");
                OwnerProbeFiles.write(dir,"closed","CLOSED|"+key+"|STOP\n");return 78;}
            if(!ready){OwnerProbeFiles.write(dir,"ready","READY|"+key+"\n");ready=true;}
            String finish=OwnerProbeFiles.message(dir,"finish");
            if(finish!=null){if(!finish.equals("FINISH|"+key+"\n"))throw new IOException("owner-finish");
                OwnerProbeFiles.write(dir,"closed","CLOSED|"+key+"|NORMAL\n");return 0;}
            pause.run();
        }
        return 78; // Eigener begrenzter Ausstieg, keine fremde Terminierung und kein Retry.
    }
}
