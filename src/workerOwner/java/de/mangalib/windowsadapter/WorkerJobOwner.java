package de.mangalib.windowsadapter;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.locks.LockSupport;
import java.util.function.*;
import static de.mangalib.windowsadapter.KernelCalls.*;

/**
 * Ein gemeinsamer Reihen-/Bandslot mit genau einem Ressourcenbesitzer. Keine OS-Factory,
 * keine Recovery-API. Ein blockierter Owner hat bewusst keinen unabhängigen Custodian.
 */
final class WorkerJobOwner {
    enum State { IDLE, ACTIVE, QUARANTINED, CLOSED }
    enum Status { SUCCESS, ERROR, BUSY, UNAVAILABLE }
    record Budgets(long start,long series,long volume,long cleanup) {
        Budgets {
            if(start<=0 || series<start || volume<start || cleanup<=0)throw new IllegalArgumentException("worker-budgets");
        }
        static Budgets candidates(){return new Budgets(Duration.ofSeconds(30).toNanos(),Duration.ofMinutes(60).toNanos(),
                Duration.ofSeconds(120).toNanos(),Duration.ofSeconds(15).toNanos());}
    }
    record Result(Status status,Map<String,String> values,boolean groupEnded,boolean resourcesClosed,
                  boolean channelEnded,Integer exitCode,UUID ticket,Identity worker,long assignedTotal) {
        Result {values=Collections.unmodifiableMap(new LinkedHashMap<>(values));}
    }
    record Issue(String phase,String kind,String type,int nativeCode) { }
    /** Nur gespeicherte Diagnosewerte, keine neue OS-Beobachtung oder Freigabegrundlage. */
    record Diagnostic(State state,String phase,List<Issue> issues,Observation lastObservation,Map<String,String> session) {
        Diagnostic {issues=List.copyOf(issues);session=Map.copyOf(session);}
    }
    interface Session {
        KernelCalls kernel();
        String executable();
        WorkerWire.Reply exchange(WorkerWire.Request request,Runnable bound)throws IOException;
        /** Nur lokales Startfencing, kein blockierendes I/O oder Nativecall. */
        void cancelStarts();
        boolean settled();
        boolean channelEnded();
        boolean closeIo(long remaining);
        /** Bilanz des tatsächlichen Adapters, zusätzlich zum Ownerledger. */
        boolean resourcesClosed();
        default boolean healthy(){return true;}
        /** Ausschließlich gecachte Werte; darf keine Nativecalls oder blockierenden Locks ausführen. */
        default Map<String,String> diagnostic(){return Map.of();}
        int exitCode(Resource process);
    }
    interface Factory {Session open(long startDeadline,BooleanSupplier permitted);}
    private enum Held { OPEN, UNCERTAIN, CLOSED }
    private static final Limits LIMITS=new Limits(true,false,0,0,0); // Synthetisches Profil, keine erfundenen Quoten.
    private final WorkerJournal journal;
    private final Factory factory;
    private final LongSupplier clock;
    private final Budgets budgets;
    private State state;
    private Work active;
    private Work lastDiagnostic;
    private boolean shutdown;
    private final class Work {
        final UUID id=UUID.randomUUID();final Scope scope=new Scope();
        final Map<Resource,Held> held=new LinkedHashMap<>();
        final Map<Resource,Identity> identities=new LinkedHashMap<>();
        final long startDeadline,deadline;
        volatile Session session;volatile Resource job;volatile CleanupControl control;
        Resource root;boolean startPending,identityFailure,unknownAcquisition;
        volatile boolean journalOwned;
        volatile String phase="ADMISSION";
        volatile Observation lastObservation;
        final List<Issue> issues=new ArrayList<>();
        volatile boolean stopped,started,exchangeEnded,finished,graceful,cancelled;
        boolean terminateAttempted,controlBusy,controlFailed,closing;volatile boolean quarantined;
        int normalCalls;long cleanupDeadline=Long.MAX_VALUE,assignedTotal;
        Work(WorkerWire.Operation operation){long now=clock.getAsLong();startDeadline=add(now,budgets.start());
            deadline=add(now,operation==WorkerWire.Operation.SERIES?budgets.series():budgets.volume());}
    }
    WorkerJobOwner(WorkerJournal journal,Factory factory,LongSupplier clock,Budgets budgets)throws IOException{
        this.journal=Objects.requireNonNull(journal);this.factory=Objects.requireNonNull(factory);
        this.clock=Objects.requireNonNull(clock);this.budgets=Objects.requireNonNull(budgets);
        state=journal.clear()?State.IDLE:State.QUARANTINED;
    }
    synchronized State state(){
        // Factory/open kann vor dem Start der Kontrollspur blockieren. Auch Status-/Admission-
        // Abfragen müssen dann die Frist erkennen, ohne Nativecalls oder einen zweiten Owner.
        if(active!=null && !active.stopped && (clock.getAsLong()>=active.deadline
                || (!active.started && clock.getAsLong()>=active.startDeadline)))revoke(active);
        // Auch bei blockierter Kontrollspur darf ein weiterer Aufrufer keinen neuen Import freigeben.
        if(active!=null && active.stopped && clock.getAsLong()>=active.cleanupDeadline){
            active.quarantined=true;state=State.QUARANTINED;
        }
        return state;
    }
    Diagnostic diagnostic(){
        Work w;State s;List<Issue> issues;
        synchronized(this){w=active==null?lastDiagnostic:active;s=state;issues=w==null?List.of():List.copyOf(w.issues);}
        Map<String,String> session=Map.of();
        if(w!=null && w.session!=null)try{session=w.session.diagnostic();}catch(RuntimeException e){session=Map.of("DiagnosticUnavailable","true");}
        return new Diagnostic(s,w==null?"NONE":w.phase,issues,w==null?null:w.lastObservation,session);
    }
    private synchronized void issue(Work w,String kind,Throwable error){
        if(w.issues.size()>=8)return;
        String type=error==null?"NONE":error.getClass().getName();
        if(type.length()>160 || !(type.equals("NONE") || type.startsWith("java.") || type.equals("de.mangalib.windowsadapter.KernelCalls$Failure")))type="OTHER";
        w.issues.add(new Issue(w.phase,kind,type,error instanceof Failure f?f.code:0));
    }
    /** Ein STOP ist lokal und endgültig; ein ACK/Return/Timer kann ihn nicht zurücknehmen. */
    synchronized void requestStop(){if(active!=null)revoke(active);}
    synchronized void shutdown(){shutdown=true;if(active==null){if(state==State.IDLE)state=State.CLOSED;}else revoke(active);}
    private void revoke(Work w){w.cancelled=true;w.graceful=false;stop(w);}
    private void stop(Work w){w.stopped=true;if(w.cleanupDeadline==Long.MAX_VALUE)w.cleanupDeadline=add(clock.getAsLong(),budgets.cleanup());}
    private synchronized boolean permitted(Work w){
        if(active!=w || w.stopped || state!=State.ACTIVE || shutdown)return false;
        if(clock.getAsLong()>=w.deadline || (!w.started && clock.getAsLong()>=w.startDeadline)){
            revoke(w);return false;
        }
        return true;
    }
    /** Kurzer Commitlock, niemals über der delegierten Arbeit gehalten. */
    private <T> T normal(Work w,Supplier<T> action){
        synchronized(this){if(!permitted(w))throw new IllegalStateException("worker-revoked");w.normalCalls++;}
        try{return action.get();}finally{synchronized(this){w.normalCalls--;}}
        // Zurückgekehrte Javaarbeit ist beendet. Native-Ungewissheit bleibt im Session-/Erwerbsledger.
    }
    Result execute(WorkerWire.Operation operation,String index){
        Objects.requireNonNull(operation);
        // Ungültiger Request vor Journal, Slot oder Factory; keine neuen technischen Seiteneffekte.
        new WorkerWire.Request(UUID.randomUUID(),operation,index);
        Work w;
        synchronized(this){
            state();
            if(state!=State.IDLE || shutdown)return rejected(state==State.ACTIVE?Status.BUSY:Status.UNAVAILABLE);
            w=new Work(operation);active=w;lastDiagnostic=w;state=State.ACTIVE;
        }
        WorkerWire.Reply reply=null;boolean clean=false,group=false,io=false;Integer exit=null;
        Thread controlThread=null;
        try{
            w.phase="JOURNAL";journal.begin(w.id); // Durabel vor der ersten möglichen Erwerbsoperation.
            w.journalOwned=true;
            // Auch Factory-Teilressourcen (z.B. Tempverzeichnis) sind ohne Rückgabe unbestätigt.
            w.phase="FACTORY";w.session=normal(w,()->{w.unknownAcquisition=true;return factory.open(w.startDeadline,()->permitted(w));});
            if(w.session==null)throw new IllegalStateException("worker-session");
            w.unknownAcquisition=false;
            w.phase="SESSION_CHECK";normal(w,()->{
                if(!w.session.settled() || !w.session.healthy())throw new IllegalStateException("worker-session-unconfirmed");
                return null;
            });
            controlThread=new Thread(()->{
                while(!w.finished && !w.quarantined){controlTick(w);LockSupport.parkNanos(1_000_000);}
            },"worker-job-control");controlThread.setDaemon(true);controlThread.start();
            KernelCalls k=w.session.kernel();
            w.phase="CREATE_JOB";w.job=normal(w,()->{w.unknownAcquisition=true;return k.createJob(w.scope);});
            acquire(w,w.job,Kind.JOB);w.unknownAcquisition=false;
            w.phase="CONTROL";w.control=normal(w,()->k.prepareCleanupControl(w.job));
            if(w.control==null)throw new IllegalStateException("worker-control");
            w.phase="LIMITS";normal(w,()->{k.configure(w.job,LIMITS);return null;});
            if(!LIMITS.equals(normal(w,()->k.readLimits(w.job))))throw new IllegalStateException("worker-limits");
            w.phase="PREPARE";normal(w,()->{k.prepareLaunch(w.job,r->{
                if(r.kind!=Kind.ATTRIBUTES && r.kind!=Kind.MEMORY){w.identityFailure=true;throw new IllegalStateException("worker-preparation-kind");}
                acquire(w,r,r.kind);
            });return null;});
            List<Resource> preparation=w.held.keySet().stream().filter(r->r.kind==Kind.ATTRIBUTES || r.kind==Kind.MEMORY).toList();
            w.phase="START";StartResult created=normal(w,()->{
                w.startPending=true;return k.startSuspended(w.job,preparation,r->{
                    if(r.kind!=Kind.PROCESS && r.kind!=Kind.THREAD){w.identityFailure=true;throw new IllegalStateException("worker-start-kind");}
                    acquire(w,r,r.kind);
                });
            });
            if(created==null || created.status()==StartStatus.PENDING)throw new IllegalStateException("worker-start-pending");
            if(created.process()!=null)acquire(w,created.process(),Kind.PROCESS);
            if(created.thread()!=null)acquire(w,created.thread(),Kind.THREAD);
            w.startPending=false;
            w.root=created.process();
            if(created.status()!=StartStatus.SUSPENDED)throw new IllegalStateException("worker-start-failed");
            w.phase="IDENTITY";remember(w,w.root,null);
            if(!w.identities.get(w.root).image().equalsIgnoreCase(w.session.executable())){
                w.identityFailure=true;throw new IllegalStateException("worker-image");
            }
            w.phase="RESUME";normal(w,()->{k.resume(created.thread());return null;});
            if(!permitted(w))throw new IllegalStateException("worker-late-resume");
            w.phase="EXCHANGE";reply=normal(w,()->{
                try{return w.session.exchange(new WorkerWire.Request(w.id,operation,index),()->{
                    synchronized(this){if(!permitted(w))throw new IllegalStateException("worker-late-hello");w.started=true;}
                });}
                catch(IOException e){throw new java.io.UncheckedIOException(e);}
            });
            if(reply==null || !permitted(w))throw new IllegalStateException("worker-result");
            w.exchangeEnded=true;
        }catch(RuntimeException | IOException failure){issue(w,"RUN_EXCEPTION",failure);}
        finally{
            synchronized(this){w.graceful=w.exchangeEnded && !w.stopped;stop(w);}
            if(w.session!=null)w.session.cancelStarts();
            controlTick(w);
            try{
                w.phase="GROUP_CLEANUP";group=cleanupGroup(w);
                if(group){
                    w.phase="EXIT_CODE";if(w.root!=null)exit=w.session.exitCode(w.root);
                    w.phase="RESOURCE_CLOSE";clean=closeResources(w);
                    io=w.session!=null && w.session.channelEnded();
                }
            }catch(RuntimeException failure){issue(w,"CLEANUP_EXCEPTION",failure);clean=false;}
            w.finished=true;
            if(controlThread!=null){
                long remaining=remaining(w);
                try{if(remaining>0)controlThread.join(Duration.ofNanos(Math.min(1_000_000_000L,remaining)));}
                catch(InterruptedException interrupted){Thread.currentThread().interrupt();clean=false;}
                if(controlThread.isAlive())clean=false;
            }
            synchronized(this){clean &= !w.quarantined && !w.controlFailed && w.normalCalls==0 && !w.controlBusy;}
            clean &= remaining(w)>0;
            if(clean && w.journalOwned){try{journal.complete(w.id);}catch(IOException failure){clean=false;}}
            if(!w.journalOwned)clean=false;
            clean &= remaining(w)>0;
            if(!clean)quarantine(w);
            synchronized(this){
                if(clean){active=null;state=shutdown?State.CLOSED:State.IDLE;}
                // Ungeklärte Ressourcen bleiben am Owner erreichbar, kein spätes automatisches IDLE.
            }
        }
        boolean success=clean && group && io && !w.cancelled && w.exchangeEnded && reply!=null && reply.success() && exit!=null && exit==0 && w.session.healthy();
        return new Result(success?Status.SUCCESS:Status.ERROR,success?reply.values():Map.of(),group,clean,io,exit,
                w.id,w.identities.get(w.root),w.assignedTotal);
    }
    private void controlTick(Work w){
        synchronized(this){
            if((!w.exchangeEnded && clock.getAsLong()>=w.deadline) || (!w.started && clock.getAsLong()>=w.startDeadline)
                    || (w.session!=null && !w.session.healthy()))revoke(w);
        }
        if(w.stopped){
            if(w.session!=null)w.session.cancelStarts();
            if(remaining(w)<=0){quarantine(w);return;}
            if(!w.graceful)terminate(w);
        }
    }
    private void terminate(Work w){
        CleanupControl c;
        synchronized(this){
            if(w.control==null || w.terminateAttempted || w.closing || remaining(w)<=0)return;
            w.terminateAttempted=true;w.controlBusy=true;c=w.control;
        }
        try{c.cancelStarts();c.terminate(remaining(w));}
        catch(RuntimeException failure){issue(w,"CONTROL_FAILURE",failure);synchronized(this){w.controlFailed=true;}}
        finally{synchronized(this){w.controlBusy=false;}}
    }
    private boolean cleanupGroup(Work w){
        if(w.session==null)return !w.unknownAcquisition && w.held.isEmpty();
        // Eine angenommene Terminierung darf asynchron zurückkehren. Dieselbe Restfrist
        // nutzen, statt einen regulär noch laufenden eigenen Kontrollaufruf vorschnell aufzugeben.
        while(true){
            synchronized(this){if(!w.controlBusy)break;}
            if(remaining(w)<=0)return false;LockSupport.parkNanos(1_000_000);
        }
        if(w.unknownAcquisition || w.startPending || !w.session.settled()){issue(w,"SESSION_OR_ACQUISITION_UNSETTLED",null);return false;}
        if(w.job==null)return w.held.isEmpty();
        KernelCalls k=w.session.kernel();
        // Beim regulären Antwortende zuerst den gehaltenen Root abwarten.
        // Sonst kann gerade sein normaler Exit die allererste Gruppenabfrage
        // zwischen Accounting und PID-Liste instabil machen. Kein Retry und
        // keine Toleranz für einen anschließend weiterhin instabilen Snapshot.
        if(w.graceful && w.root!=null){
            while(remaining(w)>0 && !k.waitEnded(w.root,Math.min(remaining(w),1_000_000)))LockSupport.parkNanos(1_000_000);
            if(remaining(w)<=0)return false;
        }
        Observation before=observation(w);
        for(long pid:before.pids()){
            if(w.identities.values().stream().noneMatch(i->i.pid()==pid)){
                Resource p=k.openProcess(w.job,pid);acquire(w,p,Kind.PROCESS);remember(w,p,pid);
            }
        }
        // Verbliebene eigene Nachfahren nach bestätigtem Root-Ende terminieren.
        if(observation(w).active()>0)terminate(w);
        while(remaining(w)>0){
            boolean ended=true;
            for(Resource p:w.held.keySet().stream().filter(r->r.kind==Kind.PROCESS).toList()){
                if(!w.identities.containsKey(p))remember(w,p,null);
                if(!w.identities.get(p).equals(k.identity(p))){w.identityFailure=true;return false;}
                ended &= k.waitEnded(p,Math.min(remaining(w),1_000_000));
            }
            Observation after=observation(w);
            synchronized(this){if(ended && after.active()==0 && !w.controlBusy)return !w.identityFailure && !w.controlFailed;}
            LockSupport.parkNanos(1_000_000);
        }
        return false;
    }
    private Observation observation(Work w){
        long left=remaining(w);if(left<=0)throw new IllegalStateException("worker-cleanup-expired");
        Observation o=w.session.kernel().observe(w.job,256,left);
        w.lastObservation=o;
        if(o==null || !o.complete() || !o.stable() || o.active()<0 || o.assigned()!=o.active()
                || o.pids().size()!=o.active() || o.pids().size()>256 || o.total()<o.active() || o.total()<w.assignedTotal
                || (w.root!=null && o.total()<1)
                || o.pids().stream().anyMatch(p->p==null || p<=0) || o.pids().stream().distinct().count()!=o.pids().size())
            {issue(w,"GROUP_OBSERVATION_INVALID",null);throw new IllegalStateException("worker-group-unconfirmed");}
        w.assignedTotal=o.total();
        return o;
    }
    private void remember(Work w,Resource p,Long expected){
        KernelCalls k=w.session.kernel();Identity i=k.identity(p);
        if(i==null || i.pid()<=0 || i.creationTime()<=0 || i.image()==null || i.image().isBlank()
                || (expected!=null && i.pid()!=expected) || !k.member(p,w.job) || w.identities.size()>=256){
            w.identityFailure=true;throw new IllegalStateException("worker-identity");
        }
        w.identities.put(p,i);
    }
    private void acquire(Work w,Resource r,Kind kind){
        if(r==null || r.scope!=w.scope || r.kind!=kind){w.identityFailure=true;throw new IllegalStateException("worker-resource");}
        if(w.held.containsKey(r))return;w.held.put(r,Held.OPEN);
    }
    private boolean closeResources(Work w){
        synchronized(this){if(w.controlBusy || w.normalCalls!=0)return false;w.closing=true;}
        if(w.session==null)return !w.unknownAcquisition && w.held.isEmpty();
        if(remaining(w)<=0 || !w.session.settled())return false;
        // Nach bestätigtem Gruppenende zuerst Reader/Writer und Pipes, dann Handleledger.
        if(!w.session.closeIo(remaining(w))){issue(w,"IO_CLOSE_UNCONFIRMED",null);return false;}
        for(Kind kind:List.of(Kind.THREAD,Kind.PROCESS,Kind.ATTRIBUTES,Kind.MEMORY,Kind.JOB)){
            if(kind==Kind.JOB && w.control!=null)w.control.release();
            for(var e:w.held.entrySet())if(e.getKey().kind==kind && e.getValue()==Held.OPEN){
                if(remaining(w)<=0)return false;e.setValue(Held.UNCERTAIN);
                w.session.kernel().close(e.getKey());e.setValue(Held.CLOSED);
            }
        }
        return remaining(w)>0 && w.held.values().stream().allMatch(h->h==Held.CLOSED)
                && w.session.settled() && w.session.resourcesClosed();
    }
    private void quarantine(Work w){
        synchronized(this){w.quarantined=true;state=State.QUARANTINED;}
        // Fehlende Admission berechtigt nicht zum Verändern des Journals eines anderen Owners.
        // Ein ggf. teilweise angelegtes pending bleibt unangetastet und weiterhin startsperrend.
        if(!w.journalOwned)return;
        try{journal.quarantine();}catch(IOException failure){/* pending bleibt als zweite Startsperre stehen. */}
    }
    private long remaining(Work w){synchronized(this){return Math.max(0,w.cleanupDeadline-clock.getAsLong());}}
    private static long add(long now,long budget){return Math.addExact(now,budget);}
    private static Result rejected(Status s){return new Result(s,Map.of(),false,false,false,null,null,null,0);}
}
