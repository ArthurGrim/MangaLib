package de.mangalib.windowsadapter;

import java.io.*;
import java.util.Map;

/** Ausschließlich synthetischer künftiger Prüfworker. Kein Selenium, Spring, DB oder Netzwerk. */
public final class SyntheticWorkerMain {
    private SyntheticWorkerMain(){ }
    static void serve(InputStream in,OutputStream out)throws IOException{
        WorkerImportRunner.serve(in,out,new WorkerImportRunner.Scrapers(){
            public Map<String,String> series(String index){return Map.of("synthetic","true");}
            public Map<String,String> volume(String index){return Map.of("synthetic","true");}
        });
    }
    public static void main(String[] args)throws IOException{
        if(args.length!=1 || !args[0].equals("--synthetic-worker-v1"))throw new IOException("worker-profile");
        serve(System.in,System.out);
    }
}
