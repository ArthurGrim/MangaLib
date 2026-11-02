package de.mangalib.windowsadapter;

import de.mangalib.service.BrowserBoundaryProbe;
import de.mangalib.service.BrowserTimeoutProbe;
import java.io.*;
import java.util.Map;

/** Nur dediziertes Abnahme-JAR; keine Live-Datenziele in diesem Profil. */
public final class BoundaryWorkerMain {
    public static void main(String[] args){
        OutputStream protocol=System.out;System.setOut(new PrintStream(OutputStream.nullOutputStream()));System.setErr(new PrintStream(OutputStream.nullOutputStream()));
        boolean success=false;
        try{success=WorkerImportRunner.serve(System.in,protocol,new WorkerImportRunner.Scrapers(){
            public Map<String,String> series(String index){throw new IllegalArgumentException("boundary-operation");}
            public Map<String,String> volume(String index){
                if(index.equals("2")){
                    try{BrowserTimeoutProbe.hold(args);}catch(Exception failure){throw new IllegalStateException("timeout-probe-start",failure);}
                    throw new IllegalStateException("timeout-probe-returned");
                }
                if(!index.equals("1"))throw new IllegalArgumentException("boundary-index");return BrowserBoundaryProbe.run(args);
            }
        });}catch(Exception failure){}
        System.exit(success?0:1);
    }
}
