package de.mangalib.windowsadapter;

import de.mangalib.service.*;
import java.io.*;
import java.util.Map;

/** Kein Spring/DB/Anwendungsstart. Exakt ein gebundener Auftrag; stdout nur Binärprotokoll. */
public final class LiveScraperWorkerMain {
    private LiveScraperWorkerMain(){}
    public static void main(String[] args){
        // Legacy-Ausgaben und Driverdiagnostik dürfen weder Protokoll noch unbeschränkte Logs füllen.
        OutputStream protocol=System.out;
        System.setOut(new PrintStream(OutputStream.nullOutputStream()));
        System.setErr(new PrintStream(OutputStream.nullOutputStream()));
        boolean success=false;
        try{
            success=WorkerImportRunner.serve(System.in,protocol,bind(()->{
                try{ScraperBrowser.initialize(args);}
                catch(Exception failure){throw new IllegalStateException("scraper-initialization");}
            }),LiveScraperWorkerMain::recordFailure);
        }catch(Exception | LinkageError | java.util.ServiceConfigurationError failure){recordFailure(failure);}
        finally{ScraperBrowser.close();}
        System.exit(success?0:1);
    }
    /** Nur begrenzte Typkette; keine Messages, URLs, Stacktraces oder Seiteninhalte. */
    private static void recordFailure(Throwable failure){
        StringBuilder evidence=new StringBuilder("WORKER_FAILURE_V1\n");
        for(int i=0;i<4 && failure!=null;i++,failure=failure.getCause()){
            String name=failure.getClass().getName();
            evidence.append(name.matches("[A-Za-z0-9_$.]{1,160}")?name:"OTHER").append('\n');
        }
        try{java.nio.file.Files.writeString(java.nio.file.Path.of(System.getProperty("java.io.tmpdir"),"scraper-failure.evidence"),evidence.toString(),java.nio.file.StandardOpenOption.CREATE_NEW);}
        catch(IOException ignored){}
    }
    /** Speicher-IPC-Tests ersetzen nur die Browserinitialisierung und die beiden Extraktoren. */
    static WorkerImportRunner.Scrapers bind(Runnable initialize){
        return new WorkerImportRunner.Scrapers(){
            private boolean used;
            private void prepare(){if(used)throw new IllegalStateException("scraper-single-request");used=true;initialize.run();}
            public Map<String,String> series(String index){prepare();return MangaScraper.scrapeMangaData(index);}
            public Map<String,String> volume(String index){prepare();return BandScraper.scrapeBandData(index);}
        };
    }
}
