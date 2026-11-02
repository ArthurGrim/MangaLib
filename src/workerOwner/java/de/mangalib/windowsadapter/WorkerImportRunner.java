package de.mangalib.windowsadapter;

import java.io.*;
import java.util.*;

/**
 * Gemeinsamer isolierter Importablauf. Keine Selenium-/Spring-Abhängigkeit und kein Prozessstart.
 * Der spätere Worker bindet die beiden bestehenden Scraper hier ein; stdout muss ausschließlich
 * dem übergebenen Protokollstream gehören. Legacy-Logs dürfen diesen Stream nicht benutzen.
 */
final class WorkerImportRunner {
    interface Scrapers {
        Map<String,String> series(String index);
        Map<String,String> volume(String index);
    }
    private WorkerImportRunner(){ }
    static boolean serve(InputStream in,OutputStream protocol,Scrapers scrapers)throws IOException{
        return serve(in,protocol,scrapers,failure->{});
    }
    static boolean serve(InputStream in,OutputStream protocol,Scrapers scrapers,java.util.function.Consumer<Throwable> diagnostic)throws IOException{
        Objects.requireNonNull(scrapers);
        var request=WorkerWire.request(in); // Vollständiger gebundener Request vor jeglicher Scraperarbeit.
        WorkerWire.hello(protocol,request);
        boolean success;byte[] result;
        try{
            Map<String,String> values=switch(request.operation()){
                case SERIES->scrapers.series(request.index());
                case VOLUME->scrapers.volume(request.index());
            };
            result=WorkerWire.encodeReply(request,new WorkerWire.Reply(true,values));success=true;
        }catch(RuntimeException | IOException failure){
            try{diagnostic.accept(failure);}catch(RuntimeException ignored){} // Diagnose darf den Fehlervertrag nicht ersetzen.
            // Keine freie Exception oder Teilmap transportieren. Bekannt beendete Arbeit ist kein Leak.
            result=WorkerWire.encodeReply(request,new WorkerWire.Reply(false,Map.of()));success=false;
        }
        // Nach einem Schreibfehler NICHT erneut senden: der erste Frame könnte teilweise angekommen sein.
        WorkerWire.result(protocol,result);return success;
    }
}
