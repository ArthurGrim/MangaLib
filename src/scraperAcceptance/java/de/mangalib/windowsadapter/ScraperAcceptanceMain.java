package de.mangalib.windowsadapter;

import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.LockSupport;

/** Expliziter separater Abnahmeeinstieg. Kein Spring, keine DB, kein automatischer Starttask. */
public final class ScraperAcceptanceMain {
    private static WorkerJobOwner retained;
    public static void main(String[] args){
        Path directory=null;boolean closed=true;int exit=78;String phase="MANIFEST";
        try{
            if(args.length!=3 || !args[0].equals("--scraper-acceptance-v1"))throw new IllegalArgumentException("acceptance-arguments");
            var manifest=AcceptanceManifest.read(Path.of(args[1]),args[2]);directory=manifest.directory;
            manifest.verifyConsumption();
            long outer=System.nanoTime()+Duration.ofMillis(450_000).toNanos();
            // Nur die Original-Startsession schreibt diese native Handle-/Zeitbindung.
            Path binding=directory.resolve("binding");long wait=System.nanoTime()+Duration.ofSeconds(10).toNanos();
            while(!Files.exists(binding) && System.nanoTime()<wait)LockSupport.parkNanos(1_000_000);
            String[] b=Files.readString(binding).split("\\|",-1);
            if(b.length!=4 || !b[0].equals("ACCEPTANCE1") || !b[1].equals(manifest.hash)
                    || Long.parseLong(b[2])!=ProcessHandle.current().pid() || !b[3].matches("[1-9][0-9]*\n"))throw new IllegalStateException("acceptance-binding");
            var journal=new WorkerJournal(Path.of(manifest.values.get("slot")));
            if(!journal.clear())throw new IllegalStateException("acceptance-prior-import");
            closed=false;
            phase="NATIVE_PREPARATION";
            var bindings=Win64NativeBindings.prepareStdio(Win64NativeBindings.Host.current(),new Win64NativeBindings.JnaStdioLoader());
            var live=SimpleWorkerNative.live(bindings,directory,manifest.file("worker"),manifest.fileHash("worker"),directory,
                    List.of("--live-scraper-v1",manifest.file("gecko").toString(),manifest.fileHash("gecko"),manifest.file("firefox").toString(),manifest.fileHash("firefox")),System::nanoTime);
            if(manifest.values.get("profile").equals("BROWSER_TIMEOUT_V1")){
                var timeout=new TimeoutAcceptance(manifest,journal,live);retained=timeout.owner;
                boolean pass=timeout.run(manifest.hash);closed=timeout.closed;exit=pass?0:78;
            }else{
            var witnesses=new ArrayList<AcceptanceWitness>();
            int maximum=manifest.values.get("profile").equals("LIVE_IMPORTS_V1")?2:1;
            retained=new WorkerJobOwner(journal,(deadline,permitted)->{
                if(witnesses.size()>=maximum)throw new IllegalStateException("acceptance-start-budget");
                var witness=new AcceptanceWitness();witness.required=List.of(manifest.file("firefox").toString(),manifest.file("gecko").toString());
                witnesses.add(witness);return witness.decorate(live.open(deadline,permitted));
            },System::nanoTime,new WorkerJobOwner.Budgets(Duration.ofSeconds(30).toNanos(),Duration.ofSeconds(180).toNanos(),Duration.ofSeconds(120).toNanos(),Duration.ofSeconds(15).toNanos()));
            var client=new ProductionScraperClient(retained);var lines=new ArrayList<String>();boolean pass=true;
            for(int i=0;i<maximum;i++){
                phase="IMPORT_"+i;
                final int index=i;
                var task=new FutureTask<>(()->maximum==1?client.volume("1"):index==0?client.series(manifest.values.get("series")):client.volume(manifest.values.get("volume")));
                Thread runner=new Thread(task,"acceptance-import");runner.setDaemon(true);runner.start();
                while(!task.isDone() && System.nanoTime()<outer && !Files.exists(directory.resolve("stop"))){retained.state();LockSupport.parkNanos(1_000_000);}
                if(!task.isDone()){client.close();pass=false;lines.add("CASE|"+i+"|STOP_OUTER_DEADLINE_OR_STOPFILE");break;}
                Map<String,String> data;
                try{data=task.get();}catch(ExecutionException failure){
                    pass=false;lines.add("CASE|"+i+"|STOP_TASK_FAILURE|phase="+retained.diagnostic().phase());
                    lines.add("WORKER_FAILURE|"+retained.diagnostic().session().getOrDefault("WorkerFailure","NOT_MEASURED"));
                    for(var issue:retained.diagnostic().issues())lines.add("ISSUE|"+issue.phase()+"|"+issue.kind()+"|"+issue.type()+"|"+issue.nativeCode());break;
                }
                var result=client.lastResult();var witness=witnesses.get(i);var diagnostic=retained.diagnostic();
                boolean observed=witness.observed(manifest.file("firefox").toString()) && witness.observed(manifest.file("gecko").toString());
                boolean fields=maximum==1?"PASS".equals(data.get("BoundaryResult")):
                        data.get("Titel")!=null && !data.get("Titel").isBlank() && !data.get("Titel").equals("Unbekannt") && data.size()>=2;
                boolean network=networkEvidence(diagnostic.session().getOrDefault("ProxyEvidence",""),maximum==2);
                pass=observed && fields && network && result!=null && result.resourcesClosed() && result.groupEnded() && result.channelEnded()
                        && Objects.equals(result.exitCode(),0) && journal.clear() && retained.state()==WorkerJobOwner.State.IDLE;
                lines.add("CASE|"+i+"|"+(pass?"PASS":"STOP")+"|fields="+data.size()+"|ticket="+(result==null?"NONE":result.ticket()));
                lines.add("NETWORK|"+diagnostic.session().getOrDefault("ProxyEvidence","NOT_MEASURED"));
                lines.add("END|"+result.groupEnded()+"|"+result.resourcesClosed()+"|"+result.channelEnded()+"|"+result.exitCode()+"|active="+diagnostic.lastObservation().active());
                for(var id:witness.identities)lines.add("MEMBER|"+id.pid()+"|"+id.creationTime()+"|"+id.image());
                lines.add("SAMPLING|failures="+witness.samplingFailures+"|HistoricalIdentities=NOT_MEASURED");
                if(maximum==1)data.forEach((key,value)->lines.add("BOUNDARY|"+key+"|"+value));
                if(!pass)break; // Kein zweiter Import nach unzureichendem ersten Beleg.
            }
            client.close();
            long cleanup=System.nanoTime()+Duration.ofSeconds(16).toNanos();
            while(retained.state()==WorkerJobOwner.State.ACTIVE && System.nanoTime()<cleanup)LockSupport.parkNanos(1_000_000);
            closed=(retained.state()==WorkerJobOwner.State.IDLE || retained.state()==WorkerJobOwner.State.CLOSED) && journal.clear();
            lines.add("CLEANUP|"+closed+"|"+retained.state()+"|"+journal.clear());
            phase="REPORT";
            AcceptanceManifest.writeNew(directory.resolve("acceptance-result.txt"),"SCRAPER_ACCEPTANCE_RESULT_V1\n"+manifest.hash+"\n"+(pass && closed?"PASS":"PARTIAL/STOP")+"\n"+String.join("\n",lines)+"\n");
            exit=pass && closed?0:78;
            }
        }catch(Exception | LinkageError failure){
            if(directory!=null)try{AcceptanceManifest.writeNew(directory.resolve("failure.txt"),"ACCEPTANCE_FAILURE_V1\n"+phase+"\n"+failure.getClass().getName()+"\n"+(failure instanceof KernelCalls.Failure f?f.code:0)+"\n");}catch(Exception ignored){}
        }
        // Unbestätigte eigene Ressourcen bleiben erreichbar. Kein implizites OS-Cleanup als PASS.
        if(!closed){while(true)LockSupport.parkNanos(1_000_000_000);}
        System.exit(exit);
    }
    static boolean networkEvidence(String value,boolean live){
        if(!value.matches("SCRAPER_PROXY_V1\\|[0-9]{1,4}\\|[0-9]{1,4}\\|[0-9]{1,4}\\|[0-9]{1,4}\\|"))return false;
        String[] parts=value.split("\\|");
        int accepted=Integer.parseInt(parts[1]),dns=Integer.parseInt(parts[2]),connected=Integer.parseInt(parts[3]);
        return accepted>0 && accepted>=dns && dns>=connected && (live?connected>0:dns==0 && connected==0);
    }
}
