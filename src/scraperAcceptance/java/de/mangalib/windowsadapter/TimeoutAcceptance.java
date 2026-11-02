package de.mangalib.windowsadapter;

import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.LockSupport;

/** Ein lokaler Fehlerfall auf dem unveränderten produktiven Owner-/Nativepfad.
 * Kein zweiter Start zum Prüfen des Slots; Admission/Quarantäne zusätzlich mit Fakes. */
final class TimeoutAcceptance {
    static final long RUN=Duration.ofSeconds(45).toNanos(),CLEANUP=Duration.ofSeconds(30).toNanos();
    final WorkerJobOwner owner;
    final AcceptanceWitness witness=new AcceptanceWitness();
    final WorkerJournal journal;
    final Path directory;
    int starts;
    boolean closed;
    TimeoutAcceptance(AcceptanceManifest manifest,WorkerJournal journal,WorkerJobOwner.Factory nativeFactory)throws Exception {
        this.journal=journal;directory=manifest.directory;
        witness.required=List.of(manifest.file("firefox").toString(),manifest.file("gecko").toString());
        witness.continuous=true;witness.minimumFirefox=2;
        owner=new WorkerJobOwner(journal,(deadline,allowed)->{
            if(++starts!=1)throw new IllegalStateException("timeout-start-budget");
            return witness.decorate(nativeFactory.open(deadline,allowed));
        },System::nanoTime,new WorkerJobOwner.Budgets(Duration.ofSeconds(30).toNanos(),RUN,RUN,CLEANUP));
    }
    boolean run(String hash)throws Exception {
        long start=System.nanoTime();boolean pendingWhileReady=false,earlyIdle=false;
        var task=new FutureTask<>(()->owner.execute(WorkerWire.Operation.VOLUME,"2"));
        var thread=new Thread(task,"timeout-acceptance");thread.setDaemon(true);thread.start();
        long outer=start+Duration.ofSeconds(100).toNanos();
        while(!task.isDone() && System.nanoTime()<outer && !Files.exists(directory.resolve("stop"))){
            var state=owner.state();
            // Vor dem ersten Terminate darf es keinen Slotverlust bei laufendem Browser geben.
            if(witness.lastReadySample>0 && witness.terminateAt==0){
                boolean pending=!journal.clear();
                // Nachprüfen verhindert eine Verwechslung mit einem inzwischen erfolgten Cleanup.
                if(witness.terminateAt==0 && !task.isDone()){
                    pendingWhileReady|=pending && state==WorkerJobOwner.State.ACTIVE;
                    earlyIdle|=!pending || state==WorkerJobOwner.State.IDLE;
                }
            }
            LockSupport.parkNanos(1_000_000);
        }
        var lines=new ArrayList<String>();boolean pass=false;
        if(task.isDone()){
            var r=task.get();var d=owner.diagnostic();long duration=System.nanoTime()-start;
            long terminated=witness.terminateAt;
            boolean deadline=terminated>=start+RUN && terminated<start+RUN+CLEANUP;
            boolean liveWitness=witness.lastReadySample>0 && terminated>=witness.lastReadySample
                    && terminated-witness.lastReadySample<Duration.ofSeconds(2).toNanos();
            pass=accepted(r,d,starts,deadline,liveWitness,pendingWhileReady && !earlyIdle,
                    witness.terminateCalls,journal.clear(),witness.samplingFailures);
            lines.add("TIMEOUT|"+(pass?"PASS":"STOP")+"|elapsedMs="+duration/1_000_000+"|terminateAfterMs="+(terminated==0?-1:(terminated-start)/1_000_000));
            lines.add("EVIDENCE|deadline="+deadline+"|liveBeforeTermination="+liveWitness+"|pendingWhileReady="+pendingWhileReady+"|earlySlotLoss="+earlyIdle);
            lines.add("END|"+r.status()+"|group="+r.groupEnded()+"|resources="+r.resourcesClosed()+"|protocol="+r.channelEnded()+"|exit="+r.exitCode()+"|state="+owner.state());
            lines.add("PIPES|stdoutEOF="+d.session().get("StdoutEof")+"|stderrEOF="+d.session().get("StderrEof"));
            lines.add("TERMINATE|"+witness.terminateCalls+"|starts="+starts);
            for(var id:witness.identities)lines.add("MEMBER|"+id.pid()+"|"+id.creationTime()+"|"+id.image());
            for(var issue:d.issues())lines.add("ISSUE|"+issue.phase()+"|"+issue.kind()+"|"+issue.type()+"|"+issue.nativeCode());
            lines.add("GROUP|"+d.lastObservation());
        }else lines.add("TIMEOUT|STOP_OUTER_DEADLINE_OR_STOPFILE");
        owner.shutdown();
        long until=System.nanoTime()+CLEANUP;
        while(owner.state()==WorkerJobOwner.State.ACTIVE && System.nanoTime()<until)LockSupport.parkNanos(1_000_000);
        closed=owner.state()==WorkerJobOwner.State.CLOSED && journal.clear() && task.isDone();
        lines.add("CLEANUP|"+closed+"|"+owner.state()+"|"+journal.clear());
        AcceptanceManifest.writeNew(directory.resolve("acceptance-result.txt"),"SCRAPER_ACCEPTANCE_RESULT_V1\n"+hash+"\n"+(pass && closed?"PASS":"PARTIAL/STOP")+"\n"+String.join("\n",lines)+"\n");
        return pass && closed;
    }
    /** Ein erwarteter Fehler ist nur mit positivem Endnachweis ein bestandener Grenztest.
     * Protokollerfolg wird NICHT erfunden: beim Kill fehlt absichtlich die Antwort. */
    static boolean accepted(WorkerJobOwner.Result r,WorkerJobOwner.Diagnostic d,int starts,boolean deadline,
                            boolean live,boolean pending,int terminates,boolean clear,int samplingFailures){
        var o=d.lastObservation();
        return r.status()==WorkerJobOwner.Status.ERROR && r.values().isEmpty() && !r.channelEnded()
                && r.worker()!=null && r.ticket()!=null && r.exitCode()!=null && r.exitCode()!=0
                && r.groupEnded() && r.resourcesClosed() && d.state()==WorkerJobOwner.State.IDLE
                && o!=null && o.complete() && o.stable() && o.active()==0 && o.assigned()==0 && o.pids().isEmpty()
                && "true".equals(d.session().get("StdoutEof")) && "true".equals(d.session().get("StderrEof"))
                && starts==1 && terminates==1 && samplingFailures==0 && deadline && live && pending && clear;
    }
}
