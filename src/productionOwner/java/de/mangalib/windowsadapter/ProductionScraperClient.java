package de.mangalib.windowsadapter;

import java.nio.file.*;
import java.util.*;

/** Produktive Fassade des bewährten Job-Owners. Konstruktion lädt keine native Bibliothek.
 * Ein gemeinsamer Owner für beide Importarten; keine automatische Quarantänefreigabe. */
public final class ProductionScraperClient implements AutoCloseable {
    private volatile WorkerJobOwner owner;
    private boolean closed;
    private final boolean enabled;
    private volatile WorkerJobOwner.Result lastResult;
    public ProductionScraperClient(){this(true);}
    private ProductionScraperClient(boolean enabled){this.enabled=enabled;}
    /** Nur paketinterner isolierter Abnahmeharness, keine alternative HTTP-Konfiguration. */
    ProductionScraperClient(WorkerJobOwner owner){this.enabled=true;this.owner=Objects.requireNonNull(owner);}
    WorkerJobOwner.Result lastResult(){return lastResult;}
    /** JUnit-Kontexte dürfen auch bei vorhandenen Live-Konfigurationswerten keinen OS-Start auslösen. */
    public static ProductionScraperClient disabled(){return new ProductionScraperClient(false);}

    public Map<String,String> series(String index){return execute(WorkerWire.Operation.SERIES,index);}
    public Map<String,String> volume(String index){return execute(WorkerWire.Operation.VOLUME,index);}

    private Map<String,String> execute(WorkerWire.Operation operation,String index){
        new WorkerWire.Request(UUID.randomUUID(),operation,index);
        try{
            var result=owner().execute(operation,index);
            lastResult=result;
            if(result.status()!=WorkerJobOwner.Status.SUCCESS || !result.groupEnded()
                    || !result.resourcesClosed() || !result.channelEnded() || !Objects.equals(result.exitCode(),0))
                throw new IllegalStateException("Import nicht verfügbar oder nicht sicher abgeschlossen.");
            return result.values();
        }catch(Exception | LinkageError failure){
            // Keine Pfade, fremden Seiteninhalte oder native Exceptiontexte in HTTP-Antworten.
            throw new IllegalStateException("Import nicht verfügbar oder nicht sicher abgeschlossen.");
        }
    }

    private synchronized WorkerJobOwner owner()throws Exception{
        if(closed || !enabled)throw new IllegalStateException("scraper-disabled");
        if(owner!=null)return owner;
        Path jar=pinned("worker"),gecko=pinned("gecko"),firefox=pinned("firefox");
        Path root=Path.of(System.getProperty("user.home"),".mangalib","scraper").toAbsolutePath().normalize();
        Files.createDirectories(root);
        var journal=new WorkerJournal(root.resolve("import-slot"));
        if(!journal.clear())throw new IllegalStateException("scraper-quarantine");
        var bindings=Win64NativeBindings.prepareStdio(Win64NativeBindings.Host.current(),new Win64NativeBindings.JnaStdioLoader());
        var factory=SimpleWorkerNative.live(bindings,root,jar,property("worker.sha256"),root,List.of("--live-scraper-v1",
                gecko.toString(),property("gecko.sha256"),firefox.toString(),property("firefox.sha256")),System::nanoTime);
        owner=new WorkerJobOwner(journal,factory,System::nanoTime,WorkerJobOwner.Budgets.candidates());
        return owner;
    }

    static Path pinned(String name)throws Exception{
        Path path=Path.of(property(name+".path"));
        SimpleWorkerNative.requirePinned(path,property(name+".sha256"));
        return path;
    }
    private static String property(String key){
        String value=System.getProperty("mangalib.scraper."+key);
        if(value==null || value.isBlank())throw new IllegalStateException("scraper-configuration");
        return value;
    }
    @Override public synchronized void close(){closed=true;if(owner!=null)owner.shutdown();}
}
