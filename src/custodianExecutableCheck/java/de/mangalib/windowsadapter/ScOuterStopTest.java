package de.mangalib.windowsadapter;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static de.mangalib.windowsadapter.KernelCalls.*;
import static de.mangalib.windowsadapter.EmptyCustodianBootstrap.*;
import static de.mangalib.windowsadapter.OuterJobCustodian.*;
import static org.junit.jupiter.api.Assertions.*;

/** Nur Fakeports/temporäre Kontrollnachrichten; keine JNA-, Prozess- oder Joboperationen. */
@Timeout(10)
class ScOuterStopTest {
    @TempDir Path temporary;
    final AtomicLong clock=new AtomicLong();
    ScRevocationFence fence(){var f=new ScRevocationFence(clock::get,1000);f.admit();return f;}
    ScOuterStop.Binding binding(){return new ScOuterStop.Binding(new StartIntentStore.Binding(new UUID(11,11),new UUID(12,12),new UUID(13,13)),
            new StartIntentStore.Anchor(1,"a".repeat(64)),new Identity(51,101,ScNativeManifest.JAVA));}
    void message(String name,String text) throws IOException {Files.writeString(temporary.resolve(name),text,StandardOpenOption.CREATE_NEW);}
    @Test void controlStartsClosedAndNeedsExactOwnerAdmission() throws Exception {
        var f=new ScRevocationFence(clock::get,1000);var c=new ScOuterStop(temporary,binding(),f,()->{});c.publish();
        assertFalse(f.allowed());message("outer-admit","ADMIT|"+binding().key()+"\n");c.step();
        assertTrue(f.allowed());assertEquals("BOUND|"+binding().key()+"\n",Files.readString(temporary.resolve("outer-bound")));
    }
    @Test void stopBeforeAdmissionIsEffectiveAndDuplicateStopNeverReopensLease() throws Exception {
        var f=new ScRevocationFence(clock::get,1000);var callbacks=new AtomicInteger();var c=new ScOuterStop(temporary,binding(),f,callbacks::incrementAndGet);c.publish();
        message("outer-stop","STOP|"+binding().key()+"\n");c.step();String ack=Files.readString(temporary.resolve("outer-ack"));
        message("outer-admit","ADMIT|"+binding().key()+"\n");c.step();f.admit();
        assertFalse(f.allowed());assertEquals(1,callbacks.get());assertEquals(ack,Files.readString(temporary.resolve("outer-ack")));
        assertFalse(f.publishReady());assertFalse(Files.exists(temporary.resolve("outer-bound")));
    }
    @ParameterizedTest @ValueSource(strings={"session","ticket","group","revision","pid","birth","oversize","command"})
    void foreignOrMalformedMessageCannotAckOrAuthorizeAnyPhase(String fault) throws Exception {
        var f=fence();var c=new ScOuterStop(temporary,binding(),f,()->{});String request="STOP|"+binding().key()+"\n";
        request=switch(fault){
            case "session" -> request.replace(binding().intent().session().toString(),UUID.randomUUID().toString());
            case "ticket" -> request.replace(binding().intent().ticket().toString(),UUID.randomUUID().toString());
            case "group" -> request.replace(binding().intent().group().toString(),UUID.randomUUID().toString());
            case "revision" -> request.replace("|1|","|2|");
            case "pid" -> request.replace("|51|","|52|");
            case "birth" -> request.replace("|101\n","|102\n");
            case "oversize" -> "x".repeat(513)+"\n";
            default -> request.replace("STOP|","EXEC|");
        };
        message("outer-stop",request);c.step();assertTrue(c.failed());assertFalse(f.allowed());
        assertFalse(Files.exists(temporary.resolve("outer-ack")));assertThrows(IllegalStateException.class,f::reserve);
    }
    @Test void partialMessageNeverAdmitsAndMissingChannelExpiresWithoutAcknowledgement() throws Exception {
        var f=new ScRevocationFence(clock::get,1000);var c=new ScOuterStop(temporary,binding(),f,()->{});
        message("outer-admit","ADMIT|"+binding().key());c.step();assertFalse(f.allowed());
        clock.set(1000);assertFalse(f.allowed());assertTrue(f.snapshot().revoked());assertFalse(Files.exists(temporary.resolve("outer-ack")));
    }
    @Test void lateMessageInOldSessionDirectoryCannotRevokeNewSession() throws Exception {
        var current=temporary.resolve("new-session");Files.createDirectory(current);var f=fence();
        var fresh=new ScOuterStop.Binding(new StartIntentStore.Binding(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID()),
                binding().anchor(),binding().supervisor());
        var control=new ScOuterStop(current,fresh,f,()->fail("Kein Cross-session-STOP"));
        message("outer-stop","STOP|"+binding().key()+"\n");control.step();
        assertTrue(f.allowed());assertFalse(control.failed());assertFalse(Files.exists(current.resolve("outer-ack")));
    }
    @ParameterizedTest @ValueSource(strings={"before-reserve","after-reserve","after-commit"})
    void stopLinearizesAgainstNativeCommitWithoutHoldingLockOverNative(String point){
        var f=fence();
        if(point.equals("before-reserve")){f.revoke();assertThrows(IllegalStateException.class,f::reserve);return;}
        var permit=f.reserve();
        if(point.equals("after-reserve")){f.revoke();assertThrows(IllegalStateException.class,permit::commit);assertEquals(0,f.snapshot().outstanding());}
        else{permit.commit();f.revoke();assertEquals(1,f.snapshot().outstanding());permit.returned();assertEquals(0,f.snapshot().outstanding());}
        assertFalse(f.publishReady());
    }
    @ParameterizedTest @ValueSource(strings={"create","resume","duplicate","createJob","configure","bootstrap"})
    void independentControlRevokesWhileRealFencedPortIsBlocked(String operation) throws Exception {
        var f=fence();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var count=new AtomicInteger();
        var failure=new AtomicReference<Throwable>();var scope=new Scope();var job=new Resource(scope,Kind.JOB,7);
        java.lang.reflect.InvocationHandler raw=(p,m,args)->{
            count.incrementAndGet();entered.countDown();assertTrue(release.await(3,TimeUnit.SECONDS));
            return switch(m.getName()){
                case "create" -> new ScStartProvider.Created(true,201,202,52);
                case "resume" -> true;
                case "duplicate" -> new de.mangalib.windowsadapter.Win64TransferContract.Duplicate(true,900);
                case "createJob" -> job;
                case "configure" -> null;
                default -> throw new AssertionError(m.getName());
            };
        };
        var calls=new ScFencedCalls((ScStartProvider.Calls)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{ScStartProvider.Calls.class},raw),f);
        var kernel=new ScFencedKernel((KernelCalls)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{KernelCalls.class},raw),f);
        Runnable invoke=switch(operation){
            case "create" -> ()->calls.create((k,v)->{});
            case "resume" -> ()->calls.resume(202);
            case "duplicate" -> ()->calls.duplicate(101,303,203,12,false,0);
            case "createJob" -> ()->kernel.createJob(scope);
            case "bootstrap" -> ()->calls.authorized(()->{count.incrementAndGet();entered.countDown();try{assertTrue(release.await(3,TimeUnit.SECONDS));}catch(InterruptedException e){throw new IllegalStateException();}return true;});
            default -> ()->kernel.configure(job,new Limits(true,false,0,0,0));
        };
        Thread work=new Thread(()->{try{invoke.run();}catch(Throwable t){failure.set(t);}},"fake-blocked-native");work.start();
        try{
            assertTrue(entered.await(2,TimeUnit.SECONDS));assertEquals(1,f.snapshot().outstanding());
            var control=new ScOuterStop(temporary,binding(),f,()->{});message("outer-stop","STOP|"+binding().key()+"\n");control.step();
            assertTrue(work.isAlive());assertTrue(Files.readString(temporary.resolve("outer-ack")).endsWith("|1\n"));
            assertFalse(f.allowed());assertFalse(f.publishReady());
        }finally{release.countDown();work.join(2000);}
        assertFalse(work.isAlive());assertNull(failure.get());assertEquals(0,f.snapshot().outstanding());
        assertThrows(IllegalStateException.class,invoke::run);assertEquals(1,count.get());
    }
    @Test void lateSuccessfulCStartRetainsOriginalCleanupAuthorityAndNeverResumes() throws Exception {
        var helper=new ScStartProviderTest();helper.temporary=temporary;
        try(var old=helper.new Fixture()){
            var f=fence();var gated=new ScFencedCalls(old.calls,f);
            var provider=new ScStartProvider(gated,old.store,new ScStartProvider.ConnectionFactory(){
                public boolean handshake(Peer p,Owner o,long h,ScStartProvider.Calls c,StartIntentStore.Proof proof){fail("Kein später Handshake");return false;}
                public TransferConfirmation connect(Peer p,Owner o,HeldProcessLedger l,ScStartProvider.Calls c,StartIntentStore.Proof proof){throw new AssertionError();}
            },old.calls.time::get,1000,ScStartProviderTest.IMAGE);
            old.calls.entered=new CountDownLatch(1);old.calls.release=new CountDownLatch(1);
            var result=new AtomicReference<ScSupervisor.BoundChild>();
            Thread work=new Thread(()->result.set(provider.startAndBind(old.store.binding(),old.proof)),"fake-late-create");work.start();
            try{assertTrue(old.calls.entered.await(2,TimeUnit.SECONDS));f.revoke();provider.cancel();assertEquals(1,f.snapshot().outstanding());}
            finally{old.calls.release.countDown();work.join(2000);}
            assertNull(result.get());assertFalse(old.calls.resumed);assertEquals(1,old.calls.creates);assertEquals(1,old.calls.kills);
            assertTrue(provider.snapshot().ended());assertTrue(provider.releaseEnded());assertEquals(0,f.snapshot().outstanding());
        }
    }
    @Test void unknownNativeExceptionRetainsOutstandingAndCannotBeTurnedIntoPass(){
        var f=fence();assertThrows(IllegalStateException.class,()->f.run(()->{throw new IllegalStateException("unknown");}));
        f.revoke();assertEquals(1,f.snapshot().outstanding());
        String report=ScRunEvidence.format(UUID.randomUUID(),false,null,null,null,Integer.MIN_VALUE,true,f.snapshot().outstanding());
        assertTrue(report.contains("complete=false"));assertTrue(report.contains("operationOutstanding=true"));
    }
    @Test void partialCreateBeforeIdentityDoesNotConsumeTheOnlyPermittedNativeCleanupAttempt() throws Exception {
        var helper=new ScStartProviderTest();helper.temporary=temporary;
        try(var old=helper.new Fixture()){
            var f=fence();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
            var raw=(ScStartProvider.Calls)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{ScStartProvider.Calls.class},(p,m,args)->{
                Object result;
                try{result=m.invoke(old.calls,args);}catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}
                if(m.getName().equals("create")){entered.countDown();assertTrue(release.await(3,TimeUnit.SECONDS));}
                return result;
            });
            var provider=new ScStartProvider(new ScFencedCalls(raw,f),old.store,new ScStartProvider.ConnectionFactory(){
                public boolean handshake(Peer p,Owner o,long h,ScStartProvider.Calls c,StartIntentStore.Proof proof){throw new AssertionError();}
                public TransferConfirmation connect(Peer p,Owner o,HeldProcessLedger l,ScStartProvider.Calls c,StartIntentStore.Proof proof){throw new AssertionError();}
            },old.calls.time::get,1000,ScStartProviderTest.IMAGE);
            var failure=new AtomicReference<Throwable>();
            Thread work=new Thread(()->{try{assertNull(provider.startAndBind(old.store.binding(),old.proof));}catch(Throwable t){failure.set(t);}},"fake-partial-return");work.start();
            try{assertTrue(entered.await(2,TimeUnit.SECONDS));f.revoke();provider.cancel();provider.rollbackSuspended();assertEquals(0,old.calls.kills);assertFalse(provider.snapshot().ended());}
            finally{release.countDown();work.join(2000);}
            assertFalse(work.isAlive());assertNull(failure.get());assertEquals(1,old.calls.kills);assertFalse(old.calls.resumed);
            assertTrue(provider.releaseEnded());provider.rollbackSuspended();assertEquals(1,old.calls.kills);
        }
    }
    @ParameterizedTest @ValueSource(strings={"create","resume","duplicate","createJob","configure","bootstrap"})
    void alreadyRevokedLeaseNeverEntersAnyNormalPort(String operation){
        var f=fence();f.revoke();var count=new AtomicInteger();
        java.lang.reflect.InvocationHandler forbidden=(p,m,a)->{count.incrementAndGet();throw new AssertionError("native-after-stop");};
        var calls=new ScFencedCalls((ScStartProvider.Calls)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{ScStartProvider.Calls.class},forbidden),f);
        var kernel=new ScFencedKernel((KernelCalls)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{KernelCalls.class},forbidden),f);
        Runnable action=switch(operation){
            case "create" -> ()->calls.create((k,v)->{});
            case "resume" -> ()->calls.resume(202);
            case "duplicate" -> ()->calls.duplicate(101,303,203,12,false,0);
            case "createJob" -> ()->kernel.createJob(new Scope());
            case "bootstrap" -> ()->calls.authorized(()->{count.incrementAndGet();throw new AssertionError();});
            default -> ()->kernel.configure(new Resource(new Scope(),Kind.JOB,7),new Limits(true,false,0,0,0));
        };
        assertThrows(IllegalStateException.class,action::run);assertEquals(0,count.get());
    }
    @ParameterizedTest @ValueSource(strings={"late-ack","lost","committed-grant"})
    void blockedNativeAckOrLostNormalChannelCannotBlockOuterRevocation(String fault) throws Exception {
        var helper=new ScStartProviderTest();helper.temporary=temporary;
        try(var old=helper.new Fixture()){
            var bound=old.start();assertNotNull(bound);var f=fence();
            var binding=new ScChannel.Binding(old.store.binding(),old.proof.anchor(),UUID.randomUUID());
            var inS=new java.io.PipedInputStream(4096);var outC=new java.io.PipedOutputStream(inS);
            var inC=new java.io.PipedInputStream(4096);var outS=new java.io.PipedOutputStream(inC);
            var waiting=new CountDownLatch(1);var release=new CountDownLatch(1);
            java.io.OutputStream output=fault.equals("committed-grant")?new java.io.FilterOutputStream(outS){
                boolean first=true;
                public void write(int value)throws IOException{if(first){first=false;waiting.countDown();try{assertTrue(release.await(3,TimeUnit.SECONDS));}catch(InterruptedException e){throw new IOException();}}out.write(value);}
            }:outS;
            var s=new ScNativeWire(binding,false,inS,output);var c=new ScNativeWire(binding,true,inC,outC);
            var bank=new HandleTransferPortTest.Bank();
            var connection=new ScNativeConnection(s,old.provider,new ScFencedCalls(old.calls,f),old.provider.ledger(),bound.peer(),bound.owner(),
                    bank,old.store.binding(),r->false,r->0,old.calls.time::get,1000,f);
            var answer=new AtomicReference<Acceptance>();var error=new AtomicReference<Throwable>();
            Thread fakeC=new Thread(()->{try{String grant=c.receive("GRANT");if(!fault.equals("committed-grant"))waiting.countDown();assertTrue(release.await(3,TimeUnit.SECONDS));
                if(fault.equals("lost"))outC.close();else c.send("ACK",grant.split(":",-1)[0]);}catch(Throwable t){error.set(t);}},"fake-C-ack");
            Thread work=new Thread(()->{try{answer.set(connection.takeOver(new Object(),old.store,old.proof,bank.source,bound.peer(),bound.owner()));}catch(Throwable t){error.set(t);}},"fake-S-ack");
            fakeC.start();work.start();
            try{
                assertTrue(waiting.await(2,TimeUnit.SECONDS));assertTrue(work.isAlive());
                var control=new ScOuterStop(temporary,binding(),f,()->connection.requestStop(null,1000));
                message("outer-stop","STOP|"+binding().key()+"\n");control.step();
                assertTrue(Files.exists(temporary.resolve("outer-ack")));assertFalse(f.allowed());assertTrue(work.isAlive());
            }finally{release.countDown();fakeC.join(2000);work.join(2000);outC.close();outS.close();inS.close();inC.close();}
            assertFalse(work.isAlive());assertFalse(fakeC.isAlive());assertNull(error.get());assertNull(answer.get());
            assertFalse(connection.confirmsTransfer(answer.get()));assertFalse(f.publishReady());
        }
    }
    @Test void readyCommitAndDeadlineCannotBeReopenedByRepeatedAdmission(){
        var f=fence();assertTrue(f.publishReady());f.revoke();assertFalse(f.snapshot().ready());f.admit();assertFalse(f.publishReady());
        var timed=fence();clock.set(1000);assertFalse(timed.allowed());clock.set(0);timed.admit();assertFalse(timed.allowed());
    }
    @Test void ackWriteFailureStillRevokesButNeverClaimsControlSuccess() throws Exception {
        var f=fence();var c=new ScOuterStop(temporary,binding(),f,()->{});Files.createDirectory(temporary.resolve("outer-ack"));
        message("outer-stop","STOP|"+binding().key()+"\n");c.step();assertTrue(c.failed());assertFalse(f.allowed());assertFalse(c.closed());
    }
    @Test void callbackFailureDoesNotRepeatStopAndCannotInventCompletion() throws Exception {
        var f=fence();var calls=new AtomicInteger();var c=new ScOuterStop(temporary,binding(),f,()->{calls.incrementAndGet();throw new IllegalStateException("unknown");});
        message("outer-stop","STOP|"+binding().key()+"\n");c.step();c.step();
        assertTrue(c.failed());assertFalse(c.closed());assertFalse(f.allowed());assertEquals(1,calls.get());
        assertTrue(Files.exists(temporary.resolve("outer-ack")),"ACK belegt nur die gemeinsame bereits wirksame Lease-Revokation");
    }
    @ParameterizedTest @ValueSource(strings={"transfer","createJob","close"})
    void outerStopReachesSupervisorWhileBootstrapOrCleanupIsBlocked(String point) throws Exception {
        var old=new ScBootstrapTest();old.temporary=temporary;
        try(var fixture=old.new SupervisorFixture()){
            var f=fence();var base=fixture.f;var checked=new TransferConfirmation(){
                public boolean confirmsTransfer(Acceptance a){return a!=null && f.allowed();}
                public ProcessEnd inspectPeer(Peer p){return base.inspectPeer(p);}
                public Acceptance takeOver(Object a,StartIntentStore s,StartIntentStore.Proof p,Resource k,Peer c,Owner o){return base.takeOver(a,s,p,k,c,o);}
                public void requestStop(Object a,long d){base.requestStop(a,d);}
                public void controlStep(){base.controlStep();}
                public Inventory inventory(Object a,Resource k){return base.inventory(a,k);}
                public EndReceipt finishEmpty(Object a,Resource k){return base.finishEmpty(a,k);}
                public Release releaseSupervisor(Object a,Peer c){return base.releaseSupervisor(a,c);}
            };
            var supervisor=new ScSupervisor(base.store,new ScFencedKernel(base.kernel,f),new ScSupervisor.StartPort(){
                public ScSupervisor.BoundChild startAndBind(StartIntentStore.Binding b,StartIntentStore.Proof p){return new ScSupervisor.BoundChild(base.peer,base.owner,checked,true);}
                public void cancel(){f.revoke();}
            },()->base.now,1000,100,f);
            if(point.equals("close"))assertTrue(supervisor.begin());
            base.block=point;base.gate=new EmptyCustodianBootstrapTest.Gate();var error=new AtomicReference<Throwable>();
            Thread work=new Thread(()->{try{if(point.equals("close"))supervisor.finish(true);else assertFalse(supervisor.begin());}catch(Throwable t){error.set(t);}},"fake-bootstrap");work.start();
            try{
                assertTrue(base.gate.entered.await(2,TimeUnit.SECONDS));var c=new ScOuterStop(temporary,binding(),f,supervisor::stop);
                message("outer-stop","STOP|"+binding().key()+"\n");c.step();assertFalse(supervisor.ready());assertTrue(work.isAlive());
                assertTrue(Files.exists(temporary.resolve("outer-ack")));assertFalse(f.publishReady());
                assertFalse(supervisor.result().technicalCompletion());
            }finally{base.gate.release.countDown();work.join(2000);}
            assertFalse(work.isAlive());assertNull(error.get());assertFalse(supervisor.ready());
        }
    }
    @Test void nativeAndLauncherWiringCannotSilentlyOmitTheMandatoryControl() throws Exception {
        String driver=Files.readString(Path.of("src/scNative/java/de/mangalib/windowsadapter/ScNativeDriver.java"));
        assertTrue(driver.contains("new ScFencedCalls(nativeCalls,fence)"));assertTrue(driver.contains("new ScFencedKernel(jobs,fence)"));
        assertTrue(driver.contains("starts.cancel();live.stop()"));assertTrue(driver.contains("fence.publishReady()"));
        assertTrue(driver.contains("supervisor.finish(false)"));assertTrue(driver.contains("supervisor.result().technicalCompletion()"));
        String script=Files.readString(Path.of("src/scNative/powershell/Start-ScEmptyOnce.ps1"));
        assertTrue(script.contains("-RequireOuterControl"));assertTrue(script.contains("StopControl ="));
        assertThrows(ClassNotFoundException.class,()->Class.forName("com.sun.jna.Native"));
    }
}
