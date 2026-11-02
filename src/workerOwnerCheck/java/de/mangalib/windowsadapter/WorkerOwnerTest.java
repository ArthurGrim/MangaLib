package de.mangalib.windowsadapter;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static de.mangalib.windowsadapter.KernelCalls.*;

class WorkerOwnerTest {
    @TempDir Path temporary;
    final AtomicLong now=new AtomicLong(100);
    final WorkerJobOwner.Budgets budgets=new WorkerJobOwner.Budgets(100_000_000,2_000_000_000,1_000_000_000,200_000_000);
    WorkerJobOwner owner;FakeSession last;int factories;
    java.util.function.Consumer<FakeSession> configure=f->{};
    WorkerJobOwner create()throws Exception{
        return new WorkerJobOwner(new WorkerJournal(temporary),(deadline,permit)->{
            factories++;last=new FakeSession(permit);configure.accept(last);return last;
        },now::get,budgets);
    }
    final class FakeSession implements WorkerJobOwner.Session {
        final FakeKernelCalls raw=new FakeKernelCalls();final KernelCalls kernel;
        final java.util.function.BooleanSupplier allowed;
        volatile boolean cancelled,unknown,ioClosed,channel,failIo,ioFailure,error,endedLate,leak,unknownOnIoFailure,failTermination;
        volatile boolean requireRootEndBeforeSnapshot,rootEndObserved;
        volatile int terminated;boolean rootKilled;String image="C:/synthetic/java.exe";
        CountDownLatch entered,release;String blocked;
        CountDownLatch termEntered,termRelease;
        java.util.function.Consumer<FakeSession> afterReply=f->{};
        FakeSession(java.util.function.BooleanSupplier allowed){
            this.allowed=allowed;
            kernel=(KernelCalls)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{KernelCalls.class},(p,m,args)->{
                if(m.getName().equals("prepareCleanupControl"))return new CleanupControl(){
                    public void cancelStarts(){cancelled=true;}
                    public void terminate(long left){
                        if(failTermination)throw new Failure("SECRET",5);
                        synchronized(raw){terminated++;if(raw.lastStart!=null && raw.handles.get(raw.lastStart.process()).alive)rootKilled=true;}
                        if(termEntered!=null){termEntered.countDown();try{assertTrue(termRelease.await(3,TimeUnit.SECONDS));}catch(InterruptedException e){throw new IllegalStateException();}}
                        synchronized(raw){raw.terminate((Resource)args[0]);}
                    }
                    public void release(){ }
                };
                if(m.getName().equals(blocked)){
                    entered.countDown();assertTrue(release.await(3,TimeUnit.SECONDS));
                }
                synchronized(raw){
                    if(m.getName().equals("observe") && requireRootEndBeforeSnapshot && !rootEndObserved)
                        return new Observation(true,false,0,List.of(),0,1);
                    try{Object result=m.invoke(raw,args);if(m.getName().equals("waitEnded")){
                        now.addAndGet(1_000_000);
                        if(Boolean.TRUE.equals(result) && raw.lastStart!=null && args[0]==raw.lastStart.process())rootEndObserved=true;
                    }return result;}
                    catch(InvocationTargetException e){throw e.getCause();}
                }
            });
        }
        public KernelCalls kernel(){return kernel;}
        long addUnopenedChild(Resource job,String image){
            Resource fixture=raw.addProcess(job,image);long pid=raw.handles.get(fixture).pid;
            // OS-Nachfahren erzeugen keine automatischen Handles im Owner. Erst openProcess erwirbt eine Kopie.
            raw.handles.remove(fixture);return pid;
        }
        public String executable(){return image;}
        public WorkerWire.Reply exchange(WorkerWire.Request request,Runnable bound)throws IOException{
            if(entered!=null && blocked==null){entered.countDown();try{assertTrue(release.await(3,TimeUnit.SECONDS));}
                catch(InterruptedException e){throw new IOException();}}
            var requestBytes=new ByteArrayOutputStream();WorkerWire.request(requestBytes,request);
            var response=new ByteArrayOutputStream();
            if(error){var parsed=WorkerWire.request(new ByteArrayInputStream(requestBytes.toByteArray()));WorkerWire.reply(response,parsed,new WorkerWire.Reply(false,Map.of()));}
            else SyntheticWorkerMain.serve(new ByteArrayInputStream(requestBytes.toByteArray()),response);
            if(ioFailure){if(unknownOnIoFailure)unknown=true;throw new IOException("untrusted-text-must-not-escape");}
            var result=WorkerWire.reply(new ByteArrayInputStream(response.toByteArray()),request,bound);channel=true;
            synchronized(raw){if(!endedLate)raw.processes.forEach(p->p.alive=false);afterReply.accept(this);}
            return result;
        }
        public void cancelStarts(){cancelled=true;}
        public boolean settled(){return !unknown;}
        public boolean channelEnded(){return channel;}
        public Map<String,String> diagnostic(){return Map.of("FakeUnknown",Boolean.toString(unknown));}
        public boolean closeIo(long left){ioClosed=!failIo;return ioClosed;}
        public boolean resourcesClosed(){return ioClosed && !leak && raw.closed.contains(raw.lastJob) && raw.handles.keySet().stream().allMatch(raw.closed::contains);}
        public int exitCode(Resource p){synchronized(raw){assertFalse(raw.handles.get(p).alive);return rootKilled?1:0;}}
    }
    @ParameterizedTest @EnumSource(WorkerWire.Operation.class)
    void normalRoundtripClosesEveryResourceBeforeNewAdmission(WorkerWire.Operation operation)throws Exception{
        owner=create();var r=owner.execute(operation,"900001");
        assertEquals(WorkerJobOwner.Status.SUCCESS,r.status());assertEquals(Map.of("synthetic","true"),r.values());
        assertTrue(r.groupEnded());assertTrue(r.resourcesClosed());assertTrue(r.channelEnded());assertEquals(0,r.exitCode());
        assertNotNull(r.ticket());assertNotNull(r.worker());assertEquals(100,r.worker().creationTime());assertEquals(1,r.assignedTotal());
        assertEquals(0,last.terminated);assertTrue(last.ioClosed);assertEquals(WorkerJobOwner.State.IDLE,owner.state());
        assertFalse(Files.exists(temporary.resolve("pending")));
        assertEquals(WorkerJobOwner.Status.SUCCESS,owner.execute(WorkerWire.Operation.VOLUME,"900002").status());assertEquals(2,factories);
    }
    @Test void reportedWorkerFailureCanRecoverOnlyAfterFullCleanup()throws Exception{
        configure=f->f.error=true;owner=create();var r=owner.execute(WorkerWire.Operation.SERIES,"1");
        assertEquals(WorkerJobOwner.Status.ERROR,r.status());assertTrue(r.resourcesClosed());assertTrue(r.groupEnded());
        assertTrue(r.values().isEmpty());assertEquals(WorkerJobOwner.State.IDLE,owner.state());
    }
    @Test void returnedProtocolExceptionIsNotAStuckJavaLease()throws Exception{
        configure=f->f.ioFailure=true;owner=create();var r=owner.execute(WorkerWire.Operation.VOLUME,"1");
        assertEquals(WorkerJobOwner.Status.ERROR,r.status());assertTrue(r.resourcesClosed());assertTrue(r.groupEnded());
        assertFalse(r.channelEnded());assertEquals(WorkerJobOwner.State.IDLE,owner.state());assertEquals(1,last.terminated);
        assertFalse(r.toString().contains("untrusted-text"));
    }
    @ParameterizedTest @EnumSource(value=FakeKernelCalls.Fault.class,names={"BEFORE_ASSIGNMENT","AFTER_ASSIGNMENT"})
    void knownFailedCreateRetainsAndCleansItsPartialResources(FakeKernelCalls.Fault fault)throws Exception{
        configure=f->f.raw.faults.add(fault);owner=create();var r=owner.execute(WorkerWire.Operation.VOLUME,"1");
        assertEquals(WorkerJobOwner.Status.ERROR,r.status());assertTrue(r.groupEnded());assertTrue(r.resourcesClosed());
        assertEquals(0,last.raw.count("resume"));assertEquals(WorkerJobOwner.State.IDLE,owner.state());
        if(fault==FakeKernelCalls.Fault.AFTER_ASSIGNMENT){assertNotNull(r.worker());assertEquals(1,r.exitCode());}
        else {assertNull(r.worker());assertNull(r.exitCode());}
    }
    @ParameterizedTest @ValueSource(strings={"io-close","unknown","close-process","identity","pending","wrong-image","hidden-leak"})
    void incompleteEvidenceIsStickyAcrossRequestsAndRestart(String fault)throws Exception{
        configure=f->{switch(fault){
            case "io-close"->f.failIo=true;case "unknown"->f.unknown=true;
            case "close-process"->f.raw.faults.add(FakeKernelCalls.Fault.CLOSE_PROCESS);
            case "identity"->f.raw.faults.add(FakeKernelCalls.Fault.IDENTITY);
            case "wrong-image"->f.image="C:/foreign/java.exe";
            case "hidden-leak"->f.leak=true;
            default->f.raw.pendingStart=true;
        }};
        owner=create();var r=owner.execute(WorkerWire.Operation.SERIES,"1");assertEquals(WorkerJobOwner.Status.ERROR,r.status());
        assertEquals(WorkerJobOwner.State.QUARANTINED,owner.state());assertTrue(r.values().isEmpty());
        if(fault.equals("unknown"))assertEquals(0,last.raw.count("create"));
        assertEquals(WorkerJobOwner.Status.UNAVAILABLE,owner.execute(WorkerWire.Operation.VOLUME,"2").status());assertEquals(1,factories);
        assertEquals(WorkerJobOwner.State.QUARANTINED,create().state());
        now.addAndGet(10_000_000_000L);owner.requestStop();assertEquals(WorkerJobOwner.State.QUARANTINED,owner.state());
    }
    @Test void persistedPendingAlonePreventsStartAfterOwnerLoss()throws Exception{
        new WorkerJournal(temporary).begin(UUID.randomUUID());owner=create();
        assertEquals(WorkerJobOwner.Status.UNAVAILABLE,owner.execute(WorkerWire.Operation.SERIES,"1").status());assertEquals(0,factories);
    }
    @Test void cachedDiagnosticsDoNotPerformFreshKernelQueriesOrChangeAdmission()throws Exception{
        owner=create();assertEquals(WorkerJobOwner.Status.SUCCESS,owner.execute(WorkerWire.Operation.SERIES,"1").status());
        long queries=last.raw.count("observe");var d=owner.diagnostic();
        assertEquals(queries,last.raw.count("observe"));assertEquals(WorkerJobOwner.State.IDLE,d.state());
        assertNotNull(d.lastObservation());assertTrue(d.issues().isEmpty());
        assertThrows(UnsupportedOperationException.class,()->d.session().put("Injected","true"));
    }
    @Test void normalRootExitIsConfirmedBeforeTheFirstGroupSnapshot()throws Exception{
        configure=f->f.requireRootEndBeforeSnapshot=true;owner=create();var result=owner.execute(WorkerWire.Operation.SERIES,"900001");
        assertTrue(last.rootEndObserved);assertEquals(WorkerJobOwner.Status.SUCCESS,result.status());
        assertTrue(result.groupEnded());assertTrue(result.resourcesClosed());assertEquals(WorkerJobOwner.State.IDLE,owner.state());
    }
    @Test void protocolFailureDiagnosticPreservesPhaseButNotExceptionMessage()throws Exception{
        configure=f->f.ioFailure=true;owner=create();owner.execute(WorkerWire.Operation.SERIES,"1");
        var d=owner.diagnostic();assertEquals("EXCHANGE",d.issues().getFirst().phase());
        assertEquals("RUN_EXCEPTION",d.issues().getFirst().kind());
        assertFalse(WorkerJobProof.describe(d).contains("untrusted-text"));
        assertEquals(WorkerJobOwner.State.IDLE,d.state());
    }
    @Test void unsettledExchangeReproducesObservedStopShapeWithoutClaimingItsRealCause()throws Exception{
        configure=f->{f.ioFailure=true;f.unknownOnIoFailure=true;};owner=create();var result=owner.execute(WorkerWire.Operation.SERIES,"900001");
        assertEquals(WorkerJobOwner.State.QUARANTINED,owner.state());assertNotNull(result.worker());
        assertFalse(result.groupEnded());assertFalse(result.resourcesClosed());assertFalse(result.channelEnded());
        assertNull(result.exitCode());assertEquals(0,result.assignedTotal());
        var d=owner.diagnostic();assertEquals("true",d.session().get("FakeUnknown"));
        assertTrue(d.issues().stream().anyMatch(i->i.kind().equals("SESSION_OR_ACQUISITION_UNSETTLED")));
        assertNull(d.lastObservation());assertEquals(WorkerJobOwner.Status.UNAVAILABLE,owner.execute(WorkerWire.Operation.VOLUME,"2").status());
        assertEquals(1,factories);assertTrue(Files.exists(temporary.resolve("quarantine")));
    }
    @Test void failedControlKeepsNumericCauseAndQuarantineWithoutRawOperation()throws Exception{
        configure=f->{f.ioFailure=true;f.failTermination=true;};owner=create();owner.execute(WorkerWire.Operation.SERIES,"1");
        var d=owner.diagnostic();assertTrue(d.issues().stream().anyMatch(i->i.kind().equals("CONTROL_FAILURE") && i.nativeCode()==5));
        assertFalse(WorkerJobProof.describe(d).contains("SECRET"));assertEquals(WorkerJobOwner.State.QUARANTINED,d.state());
    }
    @Test void unstableObservationIsRetainedAsDiagnosticAndNeverAccepted()throws Exception{
        configure=f->f.afterReply=s->s.raw.observations.add(new Observation(true,false,0,List.of(),0,1));
        owner=create();owner.execute(WorkerWire.Operation.SERIES,"1");var d=owner.diagnostic();
        assertNotNull(d.lastObservation());assertFalse(d.lastObservation().stable());
        assertTrue(d.issues().stream().anyMatch(i->i.kind().equals("GROUP_OBSERVATION_INVALID")));
        assertEquals(WorkerJobOwner.State.QUARANTINED,d.state());
    }
    @Test void twoOwnerInstancesCannotUseTheSameJournalConcurrently()throws Exception{
        var j1=new WorkerJournal(temporary);var j2=new WorkerJournal(temporary);j1.begin(UUID.randomUUID());
        assertThrows(IOException.class,()->j2.begin(UUID.randomUUID()));assertFalse(j1.clear());
    }
    @ParameterizedTest @ValueSource(strings={"native-time-changed","foreign-member","unstable-snapshot"})
    void identityOrGroupUncertaintyNeverGetsAConvenientTolerance(String fault)throws Exception{
        configure=f->f.afterReply=s->{switch(fault){
            case "native-time-changed"->s.raw.processes.get(0).birth+=2;
            case "foreign-member"->{
                Resource foreignJob=new Resource(new Scope(),Kind.JOB,999);
                long pid=s.addUnopenedChild(foreignJob,"C:/synthetic/java.exe");
                s.raw.observations.add(new Observation(true,true,1,List.of(pid),1,1));
            }
            default->s.raw.observations.add(new Observation(true,false,0,List.of(),0,1));
        }};
        owner=create();var r=owner.execute(WorkerWire.Operation.SERIES,"1");assertEquals(WorkerJobOwner.Status.ERROR,r.status());
        assertEquals(WorkerJobOwner.State.QUARANTINED,owner.state());assertFalse(r.resourcesClosed());
    }
    @Test void verifiedOwnJobMemberNeedsNoNameAllowlist()throws Exception{
        configure=f->f.afterReply=s->s.addUnopenedChild(s.raw.lastJob,"C:/synthetic/conhost.exe");
        owner=create();var r=owner.execute(WorkerWire.Operation.VOLUME,"1");
        assertTrue(r.groupEnded());assertTrue(r.resourcesClosed());assertEquals(1,last.terminated);
        // Eigentum kommt aus gehaltener Identität UND privater Mitgliedschaft, nicht dem Namen.
        assertTrue(last.raw.count("open-process")>0);
    }
    @Test void timeoutWhileCommunicationIsBlockedReachesOnlyOwnControl()throws Exception{
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        configure=f->{f.entered=entered;f.release=release;};owner=create();
        var answer=new AtomicReference<WorkerJobOwner.Result>();Thread work=new Thread(()->answer.set(owner.execute(WorkerWire.Operation.VOLUME,"1")));work.start();
        try{
            assertTrue(entered.await(2,TimeUnit.SECONDS));now.addAndGet(110_000_000);
            for(int i=0;i<100 && last.terminated==0;i++)Thread.sleep(2);
            assertEquals(1,last.terminated);assertTrue(work.isAlive());
            assertEquals(WorkerJobOwner.Status.BUSY,owner.execute(WorkerWire.Operation.SERIES,"2").status());
        }finally{release.countDown();work.join(3000);}
        assertFalse(work.isAlive());assertNotEquals(WorkerJobOwner.Status.SUCCESS,answer.get().status());
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void expiredImportWithBrowserDescendantsReleasesSlotOnlyAfterConfirmedCleanup(boolean failClose)throws Exception{
        configure=f->{f.failIo=failClose;f.afterReply=s->{
            s.raw.processes.forEach(p->p.alive=true);
            s.addUnopenedChild(s.raw.lastJob,"C:/synthetic/geckodriver.exe");
            s.addUnopenedChild(s.raw.lastJob,"C:/synthetic/firefox.exe");
            s.addUnopenedChild(s.raw.lastJob,"C:/synthetic/firefox.exe");
            now.addAndGet(budgets.volume()+1);
        };};
        owner=create();var r=owner.execute(WorkerWire.Operation.VOLUME,"2");
        assertEquals(WorkerJobOwner.Status.ERROR,r.status());assertTrue(r.values().isEmpty());
        assertEquals(1,last.terminated);assertTrue(r.groupEnded());
        assertTrue(last.raw.processes.stream().noneMatch(p->p.alive));
        assertEquals(!failClose,r.resourcesClosed());
        if(failClose){
            assertEquals(WorkerJobOwner.State.QUARANTINED,owner.state());
            assertTrue(Files.exists(temporary.resolve("pending")));assertTrue(Files.exists(temporary.resolve("quarantine")));
            assertEquals(WorkerJobOwner.Status.UNAVAILABLE,owner.execute(WorkerWire.Operation.SERIES,"1").status());assertEquals(1,factories);
        }else{
            assertEquals(WorkerJobOwner.State.IDLE,owner.state());assertFalse(Files.exists(temporary.resolve("pending")));
        }
    }
    @Test void shutdownDuringWorkNeverPublishesTheAlreadyProducedPayload()throws Exception{
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        configure=f->{f.entered=entered;f.release=release;};owner=create();
        var answer=new AtomicReference<WorkerJobOwner.Result>();Thread work=new Thread(()->answer.set(owner.execute(WorkerWire.Operation.SERIES,"1")));work.start();
        try{assertTrue(entered.await(2,TimeUnit.SECONDS));owner.shutdown();}finally{release.countDown();work.join(3000);}
        assertFalse(work.isAlive());assertNotEquals(WorkerJobOwner.Status.SUCCESS,answer.get().status());assertTrue(answer.get().values().isEmpty());
        assertNotEquals(WorkerJobOwner.State.IDLE,owner.state());
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void knownDelayedControlReturnIsObservedWithinTheSameCleanupBudget(boolean expired)throws Exception{
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var termEntered=new CountDownLatch(1);var termRelease=new CountDownLatch(1);
        configure=f->{f.entered=entered;f.release=release;f.termEntered=termEntered;f.termRelease=termRelease;};owner=create();
        var answer=new AtomicReference<WorkerJobOwner.Result>();Thread work=new Thread(()->answer.set(owner.execute(WorkerWire.Operation.VOLUME,"1")));work.start();
        try{
            assertTrue(entered.await(2,TimeUnit.SECONDS));owner.requestStop();assertTrue(termEntered.await(2,TimeUnit.SECONDS));
            release.countDown();Thread.sleep(20);assertTrue(work.isAlive());
            synchronized(last.raw){assertEquals(0,last.raw.count("observe"));}
            if(expired){
                now.addAndGet(300_000_000);assertEquals(WorkerJobOwner.State.QUARANTINED,owner.state());
                assertEquals(WorkerJobOwner.Status.UNAVAILABLE,owner.execute(WorkerWire.Operation.SERIES,"2").status());
            }
        }finally{release.countDown();termRelease.countDown();work.join(3000);}
        assertFalse(work.isAlive());assertEquals(!expired,answer.get().groupEnded());assertEquals(!expired,answer.get().resourcesClosed());
        assertEquals(1,last.terminated);assertEquals(expired?WorkerJobOwner.State.QUARANTINED:WorkerJobOwner.State.IDLE,owner.state());
    }
    @Test void foreignJournalCompletionDoesNotDeleteTheStartsperre()throws Exception{
        var j=new WorkerJournal(temporary);j.begin(UUID.randomUUID());assertThrows(IOException.class,()->j.complete(UUID.randomUUID()));assertFalse(j.clear());
    }
    @Test void quarantineCannotDisappearEvenAfterAValidLateCompletion()throws Exception{
        var j=new WorkerJournal(temporary);var id=UUID.randomUUID();j.begin(id);j.quarantine();
        assertThrows(IOException.class,()->j.complete(id));assertFalse(j.clear());assertTrue(Files.exists(temporary.resolve("pending")));
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"0","-1","abc","2147483648","https://example.invalid"," 1"})
    void invalidIndexCannotOpenSessionOrJournal(String index)throws Exception{
        owner=create();assertThrows(IllegalArgumentException.class,()->owner.execute(WorkerWire.Operation.VOLUME,index));assertEquals(0,factories);
        assertTrue(new WorkerJournal(temporary).clear());
    }
    @ParameterizedTest @ValueSource(strings={"exchange","startSuspended","resume"})
    void concurrentStopNeverRestoresSuccessAndCannotAdmitSecondWorker(String point)throws Exception{
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        configure=f->{f.entered=entered;f.release=release;f.blocked=point.equals("exchange")?null:point;};owner=create();
        var answer=new AtomicReference<WorkerJobOwner.Result>();var thrown=new AtomicReference<Throwable>();
        Thread work=new Thread(()->{try{answer.set(owner.execute(WorkerWire.Operation.SERIES,"1"));}catch(Throwable e){thrown.set(e);}},"fake-worker-owner");work.start();
        try{
            assertTrue(entered.await(2,TimeUnit.SECONDS));
            assertEquals(WorkerJobOwner.Status.BUSY,owner.execute(WorkerWire.Operation.VOLUME,"2").status());
            owner.requestStop();assertEquals(1,factories);
        }finally{release.countDown();work.join(3000);}
        assertFalse(work.isAlive(),()->"Unfinished fake worker: "+Arrays.toString(work.getStackTrace()));assertNull(thrown.get());assertNotEquals(WorkerJobOwner.Status.SUCCESS,answer.get().status());
        if(point.equals("startSuspended"))assertEquals(0,last.raw.count("resume"));
        assertTrue(last.terminated<=1);
    }
    @Test void expiredCleanupCannotBeResetByLateCreateOrNewRequest()throws Exception{
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        configure=f->{f.entered=entered;f.release=release;f.blocked="startSuspended";};owner=create();
        Thread work=new Thread(()->owner.execute(WorkerWire.Operation.VOLUME,"1"));work.start();
        try{assertTrue(entered.await(2,TimeUnit.SECONDS));owner.requestStop();now.addAndGet(300_000_000);
            for(int i=0;i<100 && owner.state()!=WorkerJobOwner.State.QUARANTINED;i++)Thread.sleep(2);
            assertEquals(WorkerJobOwner.State.QUARANTINED,owner.state());
        }finally{release.countDown();work.join(3000);}
        assertFalse(work.isAlive());assertEquals(0,last.raw.count("resume"));assertEquals(WorkerJobOwner.State.QUARANTINED,owner.state());
    }
    @Test void normalEofWithDelayedExitDoesNotInventEndProof()throws Exception{
        configure=f->f.endedLate=true;owner=create();var r=owner.execute(WorkerWire.Operation.SERIES,"1");
        assertEquals(WorkerJobOwner.Status.ERROR,r.status());assertFalse(r.groupEnded());assertFalse(r.resourcesClosed());assertNull(r.exitCode());
        assertEquals(WorkerJobOwner.State.QUARANTINED,owner.state());
    }
    @Test void shutdownRejectsEvenAnOtherwiseCleanOwner()throws Exception{
        owner=create();owner.shutdown();assertEquals(WorkerJobOwner.State.CLOSED,owner.state());
        assertEquals(WorkerJobOwner.Status.UNAVAILABLE,owner.execute(WorkerWire.Operation.SERIES,"1").status());assertEquals(0,factories);
    }
    @Test void unitClasspathCannotLoadJnaOrNativeFactory()throws Exception{
        assertThrows(ClassNotFoundException.class,()->Class.forName("com.sun.jna.Native"));
        assertThrows(ClassNotFoundException.class,()->Class.forName("de.mangalib.windowsadapter.SimpleWorkerNative"));
    }
    @ParameterizedTest @ValueSource(strings={"throw","null","partial-directory"})
    void unreturnedFactoryCannotClaimConfirmedGroupEnd(String fault)throws Exception{
        var attempts=new AtomicInteger();
        owner=new WorkerJobOwner(new WorkerJournal(temporary),(deadline,allowed)->{
            attempts.incrementAndGet();
            if(fault.equals("null"))return null;
            if(fault.equals("partial-directory"))try{Files.createDirectory(temporary.resolve("partial-factory"));}
            catch(IOException e){throw new UncheckedIOException(e);}
            throw new IllegalStateException("untrusted-factory-message");
        },now::get,budgets);
        var r=owner.execute(WorkerWire.Operation.SERIES,"1");
        assertEquals(WorkerJobOwner.Status.ERROR,r.status());assertFalse(r.groupEnded());assertFalse(r.resourcesClosed());
        assertTrue(Files.exists(temporary.resolve("pending")));assertEquals(WorkerJobOwner.State.QUARANTINED,owner.state());
        assertEquals(WorkerJobOwner.Status.UNAVAILABLE,owner.execute(WorkerWire.Operation.VOLUME,"2").status());assertEquals(1,attempts.get());
        assertEquals(WorkerJobOwner.State.QUARANTINED,create().state());
        assertFalse(r.toString().contains("untrusted-factory-message"));
    }
    @Test void failedCompetingAdmissionCannotQuarantineTheOwnerOfAnotherPendingTicket()throws Exception{
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        configure=f->{f.entered=entered;f.release=release;};owner=create();var rival=create();
        var answer=new AtomicReference<WorkerJobOwner.Result>();var failure=new AtomicReference<Throwable>();
        Thread work=new Thread(()->{try{answer.set(owner.execute(WorkerWire.Operation.SERIES,"1"));}catch(Throwable e){failure.set(e);}});
        work.start();
        try{
            assertTrue(entered.await(2,TimeUnit.SECONDS));String pending=Files.readString(temporary.resolve("pending"));
            assertEquals(WorkerJobOwner.Status.ERROR,rival.execute(WorkerWire.Operation.VOLUME,"2").status());
            assertEquals(pending,Files.readString(temporary.resolve("pending")));assertFalse(Files.exists(temporary.resolve("quarantine")));
            assertEquals(1,factories);assertEquals(WorkerJobOwner.State.QUARANTINED,rival.state());
        }finally{release.countDown();work.join(3000);}
        assertFalse(work.isAlive());assertNull(failure.get());assertEquals(WorkerJobOwner.Status.SUCCESS,answer.get().status());
        assertEquals(WorkerJobOwner.State.IDLE,owner.state());assertTrue(new WorkerJournal(temporary).clear());
        assertEquals(WorkerJobOwner.Status.UNAVAILABLE,rival.execute(WorkerWire.Operation.VOLUME,"2").status());
    }
    @Test void blockedFactoryDeadlineIsObservedWithoutAnAlreadyStartedControlThread()throws Exception{
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        owner=new WorkerJobOwner(new WorkerJournal(temporary),(deadline,allowed)->{
            factories++;entered.countDown();
            try{assertTrue(release.await(3,TimeUnit.SECONDS));}catch(InterruptedException e){throw new IllegalStateException(e);}
            last=new FakeSession(allowed);return last;
        },now::get,budgets);
        var result=new AtomicReference<WorkerJobOwner.Result>();var failure=new AtomicReference<Throwable>();
        Thread work=new Thread(()->{try{result.set(owner.execute(WorkerWire.Operation.SERIES,"1"));}catch(Throwable e){failure.set(e);}});
        work.start();
        try{
            assertTrue(entered.await(2,TimeUnit.SECONDS));now.addAndGet(budgets.start()+1);
            assertEquals(WorkerJobOwner.State.ACTIVE,owner.state()); // Noch im begrenzten Cleanupfenster.
            now.addAndGet(budgets.cleanup()+1);
            assertEquals(WorkerJobOwner.State.QUARANTINED,owner.state());
            assertEquals(WorkerJobOwner.Status.UNAVAILABLE,owner.execute(WorkerWire.Operation.VOLUME,"2").status());
            assertEquals(1,factories);assertEquals(WorkerJobOwner.State.QUARANTINED,create().state());
        }finally{release.countDown();work.join(3000);}
        assertFalse(work.isAlive());assertNull(failure.get());assertFalse(result.get().resourcesClosed());
        assertEquals(0,last.raw.count("create"));assertTrue(Files.exists(temporary.resolve("pending")));
        assertEquals(WorkerJobOwner.State.QUARANTINED,owner.state());
    }
    @Test void deadlineBeforeFactoryAcquisitionClosesOnlyItsOwnJournalWithoutFalseQuarantine()throws Exception{
        owner=new WorkerJobOwner(new WorkerJournal(temporary),(deadline,allowed)->{
            factories++;throw new AssertionError("factory-must-not-run");
        },()->{
            if(Files.exists(temporary.resolve("pending")))now.updateAndGet(n->Math.max(n,budgets.start()+101));
            return now.get();
        },budgets);
        var r=owner.execute(WorkerWire.Operation.SERIES,"1");
        assertEquals(0,factories);assertEquals(WorkerJobOwner.Status.ERROR,r.status());
        assertTrue(r.groupEnded());assertTrue(r.resourcesClosed());assertFalse(r.channelEnded());
        assertNull(r.worker());assertNull(r.exitCode());assertEquals(WorkerJobOwner.State.IDLE,owner.state());
        assertTrue(new WorkerJournal(temporary).clear());
    }
}
