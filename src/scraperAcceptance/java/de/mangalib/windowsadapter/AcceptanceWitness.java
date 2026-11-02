package de.mangalib.windowsadapter;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static de.mangalib.windowsadapter.KernelCalls.*;

/** Nur Abnahmediagnose am eigenen Job. Keine globale Prozesssuche oder Terminierung.
 * PID-Snapshot ist nur Suchhilfe; Beleg erst aus gehaltenem Handle + Zeit + Jobmitgliedschaft. */
final class AcceptanceWitness {
    final List<Identity> identities=new ArrayList<>();
    int samplingFailures;
    final AtomicInteger open=new AtomicInteger();
    Resource job;
    List<String> required=List.of();
    boolean continuous;
    int minimumFirefox=1;
    volatile long lastReadySample,terminateAt;
    volatile int terminateCalls;
    volatile boolean exchangeDone=true;
    WorkerJobOwner.Session decorate(WorkerJobOwner.Session base){
        KernelCalls kernel=base.kernel();
        var traced=(KernelCalls)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{KernelCalls.class},(p,m,a)->{
            try{Object value=m.invoke(kernel,a);if(m.getName().equals("createJob"))job=(Resource)value;
                if(m.getName().equals("prepareCleanupControl") && value!=null){
                    CleanupControl control=(CleanupControl)value;
                    return new CleanupControl(){
                        public void cancelStarts(){control.cancelStarts();}
                        public void terminate(long remaining){terminateCalls++;terminateAt=System.nanoTime();control.terminate(remaining);}
                        public void release(){control.release();}
                    };
                }
                return value;}
            catch(InvocationTargetException e){throw e.getCause();}
        });
        return new WorkerJobOwner.Session(){
            public KernelCalls kernel(){return traced;}
            public String executable(){return base.executable();}
            public WorkerWire.Reply exchange(WorkerWire.Request request,Runnable bound)throws IOException{
                exchangeDone=false;
                var task=new FutureTask<>(()->{try{return base.exchange(request,bound);}finally{exchangeDone=true;}});var thread=new Thread(task,"acceptance-exchange");thread.setDaemon(true);thread.start();
                try{
                    while(!task.isDone()){
                        sample(kernel);try{TimeUnit.MILLISECONDS.sleep(100);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("acceptance-interrupted");}
                    }
                    return task.get();
                }catch(ExecutionException e){throw new IOException("acceptance-exchange");}
                catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("acceptance-interrupted");}
            }
            public void cancelStarts(){base.cancelStarts();}
            public boolean settled(){return exchangeDone && open.get()==0 && base.settled();}
            public boolean channelEnded(){return base.channelEnded();}
            public boolean closeIo(long remaining){return exchangeDone && open.get()==0 && base.closeIo(remaining);}
            public boolean resourcesClosed(){return exchangeDone && open.get()==0 && base.resourcesClosed();}
            public boolean healthy(){return base.healthy();}
            public Map<String,String> diagnostic(){return base.diagnostic();}
            public int exitCode(Resource process){return base.exitCode(process);}
        };
    }
    void sample(KernelCalls kernel){
        if(job==null || samplingFailures>=10 || terminateAt!=0 || (!continuous && !required.isEmpty() && required.stream().allMatch(this::observed)))return;
        try{
            var snapshot=kernel.observe(job,256,100_000_000);
            if(!snapshot.complete() || !snapshot.stable())return; // Zusatzmessung, kein Ersatz der strikten Abschlussprüfung.
            var current=new ArrayList<Identity>();
            for(long pid:snapshot.pids()){
                Resource process=null;
                try{
                    process=kernel.openProcess(job,pid);open.incrementAndGet();
                    Identity id=kernel.identity(process);
                    if(id.pid()==pid && id.creationTime()>0 && kernel.member(process,job)){
                        current.add(id);if(!identities.contains(id) && identities.size()<256)identities.add(id);
                    }
                }finally{if(process!=null){kernel.close(process);open.decrementAndGet();}}
            }
            long sampled=System.nanoTime();
            if(ready(current) && terminateAt==0)lastReadySample=sampled;
        }catch(RuntimeException failure){samplingFailures=Math.min(1000,samplingFailures+1);}
    }
    boolean observed(String image){return identities.stream().anyMatch(i->i.image().equalsIgnoreCase(image));}
    boolean ready(List<Identity> current){
        return !required.isEmpty() && required.stream().allMatch(image->current.stream()
                .filter(id->id.image().equalsIgnoreCase(image)).map(Identity::pid).distinct().count()>=(image.toLowerCase(Locale.ROOT).endsWith("firefox.exe")?minimumFirefox:1));
    }
}
