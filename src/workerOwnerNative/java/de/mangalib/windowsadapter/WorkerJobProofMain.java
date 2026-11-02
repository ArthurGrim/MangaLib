package de.mangalib.windowsadapter;

import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.lang.reflect.*;
import java.io.IOException;
import static de.mangalib.windowsadapter.KernelCalls.*;

/** Nur gesondert freigegebener synthetischer Einzelversuch; keinerlei Spring-/Scraperstart. */
public final class WorkerJobProofMain {
    private static WorkerJobOwner retained;
    private WorkerJobProofMain(){ }
    public static void main(String[] args){
        int exit=78;boolean mayOwnResources=false,closed=false;Path dir=null;String phase="PREFLIGHT";
        var firstFailure=new AtomicReference<String>();
        long origin=System.nanoTime(),latestStart=origin+Duration.ofSeconds(12).toNanos();
        long overallDeadline=origin+Duration.ofSeconds(37).toNanos();
        try{
            if(args.length!=3 || !args[0].equals("--worker-job-proof-v1"))throw new IllegalArgumentException();
            Path root=Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
            dir=WorkerJobProof.verify(root,args[1],args[2]);
            phase="BINDING";
            // Keine nativen Aufrufe/Job-/Workerstarts, bevor der äußere Besitzer seine
            // ursprüngliche Prozessinstanz identifiziert und gebunden hat.
            long bindingDeadline=System.nanoTime()+Duration.ofSeconds(5).toNanos();String binding=null;
            while(System.nanoTime()<bindingDeadline){
                if(Files.exists(dir.resolve("binding"))){binding=WorkerJobProof.read(dir.resolve("binding"),512);break;}
                LockSupport.parkNanos(1_000_000);
            }
            String prefix="JOB1|"+args[1]+"|"+args[2]+"|"+ProcessHandle.current().pid()+"|";
            if(binding==null || !binding.startsWith(prefix) || !binding.substring(prefix.length()).matches("[1-9][0-9]{0,18}\n"))throw new IllegalStateException();
            // Direkt vor dem nativen Lader nochmals alle unveränderlichen Artefakte prüfen.
            WorkerJobProof.verify(root,args[1],args[2]);
            // Gemeinsame dauerhafte Admission auch über mehrere Versuchkennungen.
            // Kein frischer Namespace darf einen unbestätigten echten Job umgehen.
            var journal=new WorkerJournal(root.resolve(".gradle/t02-worker-job-proof/import-slot"));
            if(!journal.clear())throw new IllegalStateException();
            mayOwnResources=true;
            phase="NATIVE_LOAD";
            var prepared=Win64NativeBindings.prepareStdio(Win64NativeBindings.Host.current(),new Win64NativeBindings.JnaStdioLoader());
            phase="FACTORY";
            if(System.nanoTime()>=latestStart)throw new IllegalStateException("proof-start-expired");
            var factory=SimpleWorkerNative.synthetic(prepared,root,dir,System::nanoTime);var count=new AtomicInteger();
            retained=new WorkerJobOwner(journal,(deadline,allowed)->{
                if(count.incrementAndGet()!=1 || System.nanoTime()>=latestStart)throw new IllegalStateException("proof-single-start");
                return diagnosed(factory.open(Math.min(deadline,latestStart),()->allowed.getAsBoolean() && System.nanoTime()<latestStart),firstFailure);
            },System::nanoTime,new WorkerJobOwner.Budgets(Duration.ofSeconds(10).toNanos(),Duration.ofSeconds(15).toNanos(),
                    Duration.ofSeconds(15).toNanos(),Duration.ofSeconds(8).toNanos()));
            phase="EXECUTE";var task=new FutureTask<>(()->retained.execute(WorkerWire.Operation.SERIES,"900001"));
            var runner=new Thread(task,"synthetic-job-proof");runner.setDaemon(true);runner.start();
            while(!task.isDone() && System.nanoTime()<overallDeadline){
                if(Files.exists(dir.resolve("stop")))retained.requestStop();
                retained.state();LockSupport.parkNanos(1_000_000);
            }
            WorkerJobOwner.Result result=task.isDone()?task.get():null;
            if(result==null)retained.requestStop();
            var state=retained.state();boolean journalClear=journal.clear();
            closed=result!=null && result.resourcesClosed() && journalClear;
            boolean pass=WorkerJobProof.accepted(result,state,journalClear,count.get());
            String report="WORKER_JOB_RESULT_V1\n"+args[1]+"\n"+args[2]+"\n"+(pass?"JOB_PROOF_PASS":"PARTIAL/STOP")+"\n"+state+"\n"+
                    (result==null || result.worker()==null?"0\n0":result.worker().pid()+"\n"+result.worker().creationTime())+"\n"+
                    (result==null || result.exitCode()==null?"UNKNOWN":result.exitCode())+"\n"+
                    (result!=null && result.groupEnded())+"\n"+closed+"\n"+(result!=null && result.channelEnded())+"\n"+
                    (result==null?0:result.assignedTotal())+"\n"+count.get()+"\n"+journalClear+"\n"+
                    (result==null || result.ticket()==null?"UNKNOWN":result.ticket())+"\n";
            phase="REPORT";
            WorkerJobProof.write(dir,"job-diagnostic.txt",WorkerJobProof.describe(retained.diagnostic()));
            if(firstFailure.get()!=null)WorkerJobProof.write(dir,"job-native-failure.txt",firstFailure.get());
            WorkerJobProof.write(dir,"job-result.txt",report);exit=pass?0:78;
        }catch(Exception | LinkageError error){
            // Keine Rohdaten/Stacktraces. Bei unbekanntem Erwerb bleiben JVM/Referenzen
            // erhalten, statt über System.exit implizites Jobcleanup als Erfolg auszugeben.
            if(dir!=null)try{WorkerJobProof.write(dir,"job-failure.txt",WorkerJobProof.diagnostic(phase,error));}catch(Exception ignored){ }
        }
        if(mayOwnResources && !closed){while(true)LockSupport.parkNanos(1_000_000_000);}
        System.exit(exit);
    }
    /** Nur Diagnose dekorieren; identische Fähigkeiten, Cleanup- und Fristsemantik delegieren. */
    private static WorkerJobOwner.Session diagnosed(WorkerJobOwner.Session base,AtomicReference<String> failure){
        KernelCalls kernel=base.kernel();
        KernelCalls traced=(KernelCalls)Proxy.newProxyInstance(WorkerJobProofMain.class.getClassLoader(),new Class<?>[]{KernelCalls.class},(proxy,method,args)->{
            try{
                Object value=method.invoke(kernel,args);
                if(value instanceof StartResult r && r.status()==StartStatus.FAILED && kernel instanceof Win64ProcessKernelCalls nativeKernel){
                    Failure cause=nativeKernel.startFailure();if(cause!=null)failure.compareAndSet(null,WorkerJobProof.diagnostic(method.getName(),cause));
                }
                return value;
            }catch(InvocationTargetException e){failure.compareAndSet(null,WorkerJobProof.diagnostic(method.getName(),e.getCause()));throw e.getCause();}
        });
        return new WorkerJobOwner.Session(){
            public KernelCalls kernel(){return traced;}
            public String executable(){return base.executable();}
            public WorkerWire.Reply exchange(WorkerWire.Request request,Runnable bound)throws IOException{return base.exchange(request,bound);}
            public void cancelStarts(){base.cancelStarts();}
            public boolean settled(){return base.settled();}
            public boolean channelEnded(){return base.channelEnded();}
            public boolean closeIo(long remaining){return base.closeIo(remaining);}
            public boolean resourcesClosed(){return base.resourcesClosed();}
            public boolean healthy(){return base.healthy();}
            public java.util.Map<String,String> diagnostic(){return base.diagnostic();}
            public int exitCode(Resource process){return base.exitCode(process);}
        };
    }
}
