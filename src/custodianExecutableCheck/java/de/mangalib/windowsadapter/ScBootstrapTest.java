package de.mangalib.windowsadapter;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import de.mangalib.watchdogmodel.ScFrameCodec;
import static de.mangalib.windowsadapter.KernelCalls.*;
import static de.mangalib.windowsadapter.OuterJobCustodian.*;
import static de.mangalib.windowsadapter.EmptyCustodianBootstrap.*;
import static de.mangalib.windowsadapter.Win64TransferContract.*;
import static org.junit.jupiter.api.Assertions.*;

/** Ausschließlich neue temporäre Intentdateien, Fake-Kernel, Speicherkanäle und injizierte Zeit. */
@Timeout(10)
class ScBootstrapTest {
    @TempDir Path temporary;
    static final UUID TRANSFER=new UUID(77,88);
    static final StartIntentStore.Binding INTENT=EmptyCustodianBootstrapTest.BINDING;
    static final ScChannel.Binding WIRE=new ScChannel.Binding(INTENT,new StartIntentStore.Anchor(1,"a".repeat(64)),new UUID(14,14));
    static final class Pipe extends InputStream {
        final ArrayDeque<Integer> bytes=new ArrayDeque<>();
        final OutputStream out=new OutputStream() { public void write(int b) { bytes.add(b&255); } };
        public int read() { return bytes.isEmpty()?-1:bytes.remove(); }
    }
    static ScChannel channel(boolean c,InputStream in,OutputStream out) { return new ScChannel(WIRE,c,in,out,()->true); }
    static byte[] message(boolean fromC,ScChannel.Kind kind,UUID transfer) throws Exception {
        var out=new ByteArrayOutputStream(); channel(fromC,InputStream.nullInputStream(),out).send(kind,transfer); return out.toByteArray();
    }
    @Test void fixedBinaryProfileRoundtripsAllSixMessagesAndRequiresEof() throws Exception {
        for(var kind:ScChannel.Kind.values()) {
            boolean fromC=kind!=ScChannel.Kind.ADOPT && kind!=ScChannel.Kind.STOP;
            var ch=channel(!fromC,new ByteArrayInputStream(message(fromC,kind,TRANSFER)),OutputStream.nullOutputStream());
            assertEquals(kind,ch.receive(kind,TRANSFER).kind());
            if(kind==ScChannel.Kind.CLOSED) assertTrue(ch.confirmEof());
        }
    }
    @ParameterizedTest @ValueSource(strings={"session","ticket","channel","group","anchor","role","sequence","transfer","kind","oversize","negative","truncated","trailing"})
    void malformedOrForeignFramesNeverPassPeerBoundary(String fault) throws Exception {
        byte[] bytes=message(true,ScChannel.Kind.ACK,TRANSFER);
        var binding=WIRE;
        switch(fault) {
            case "session" -> bytes[4+24]^=1;
            case "ticket" -> bytes[4+40]^=1;
            case "channel" -> bytes[4+56]^=1;
            case "role" -> bytes[4+88]^=1;
            case "sequence" -> bytes[4+23]^=1;
            case "oversize" -> ByteBuffer.wrap(bytes).putInt(513);
            case "negative" -> ByteBuffer.wrap(bytes).putInt(-1);
            case "truncated" -> bytes=Arrays.copyOf(bytes,bytes.length-1);
            case "trailing" -> { bytes=Arrays.copyOf(bytes,bytes.length+1); ByteBuffer.wrap(bytes).putInt(bytes.length-4); }
            case "transfer" -> bytes=message(true,ScChannel.Kind.ACK,UUID.randomUUID());
            case "kind" -> bytes=message(true,ScChannel.Kind.BOUND,TRANSFER);
            case "group" -> binding=new ScChannel.Binding(new StartIntentStore.Binding(INTENT.ticket(),INTENT.session(),UUID.randomUUID()),WIRE.anchor(),WIRE.channel());
            case "anchor" -> binding=new ScChannel.Binding(INTENT,new StartIntentStore.Anchor(1,"b".repeat(64)),WIRE.channel());
        }
        var receiver=new ScChannel(binding,false,new ByteArrayInputStream(bytes),OutputStream.nullOutputStream(),()->true);
        assertThrows(IOException.class,()->receiver.receive(ScChannel.Kind.ACK,TRANSFER));
        assertTrue(receiver.failed());
        assertThrows(IOException.class,()->receiver.receive(ScChannel.Kind.ACK,TRANSFER));
    }
    @Test void randomTicketCannotReplacePinnedStartedPeerAndNoFrameCanCarryNativeCommands() throws Exception {
        var receiver=new ScChannel(WIRE,false,new ByteArrayInputStream(message(true,ScChannel.Kind.ACK,TRANSFER)),OutputStream.nullOutputStream(),()->false);
        assertThrows(IOException.class,()->receiver.receive(ScChannel.Kind.ACK,TRANSFER));
        assertThrows(IOException.class,()->channel(false,InputStream.nullInputStream(),OutputStream.nullOutputStream()).send(ScChannel.Kind.ACK,TRANSFER));
    }
    @Test void duplicateAckAndUnexpectedEofCannotBeRetriedOrConvertedToReadiness() throws Exception {
        byte[] one=message(true,ScChannel.Kind.ACK,TRANSFER);
        var both=new ByteArrayOutputStream(); both.write(one); both.write(one);
        var receiver=channel(false,new ByteArrayInputStream(both.toByteArray()),OutputStream.nullOutputStream());
        receiver.receive(ScChannel.Kind.ACK,TRANSFER);
        assertThrows(IOException.class,()->receiver.receive(ScChannel.Kind.ACK,TRANSFER));
        var early=channel(false,InputStream.nullInputStream(),OutputStream.nullOutputStream());
        assertThrows(IOException.class,()->early.receive(ScChannel.Kind.ACK,TRANSFER));
    }
    @Test void fixedTotalMessageBudgetAndTrailingBytesAtEofAreEnforced() throws Exception {
        var sender=channel(true,InputStream.nullInputStream(),OutputStream.nullOutputStream());
        for(int i=0;i<4;i++) sender.send(ScChannel.Kind.BOUND,TRANSFER);
        assertThrows(IOException.class,()->sender.send(ScChannel.Kind.BOUND,TRANSFER));
        var out=new ByteArrayOutputStream();out.write(message(true,ScChannel.Kind.CLOSED,TRANSFER));out.write(9);
        var receiver=channel(false,new ByteArrayInputStream(out.toByteArray()),OutputStream.nullOutputStream());
        receiver.receive(ScChannel.Kind.CLOSED,TRANSFER);assertThrows(IOException.class,receiver::confirmEof);
    }
    @Test void executableProfilesHaveNoArgumentDrivenProviderOrNativeFallback() throws Exception {
        assertNull(ScExecutablePlatform.nativeDriver());
        assertEquals(78,ScExecutablePlatform.run(ScExecutablePlatform.Role.CUSTODIAN,new String[]{ScExecutablePlatform.PROFILE},null));
        AtomicInteger calls=new AtomicInteger();
        var driver=new ScExecutablePlatform.Driver() { public int runSupervisor(){calls.incrementAndGet();return 7;} public int runCustodian(){calls.incrementAndGet();return 8;} };
        assertEquals(64,ScExecutablePlatform.run(ScExecutablePlatform.Role.CUSTODIAN,new String[]{"--pid=51"},driver));
        assertEquals(64,ScExecutablePlatform.run(ScExecutablePlatform.Role.SUPERVISOR,new String[]{ScExecutablePlatform.PROFILE,"C:/path"},driver));
        assertEquals(0,calls.get());
        assertEquals(7,ScExecutablePlatform.run(ScExecutablePlatform.Role.SUPERVISOR,new String[]{ScExecutablePlatform.PROFILE},driver));
        assertEquals(8,ScExecutablePlatform.run(ScExecutablePlatform.Role.CUSTODIAN,new String[]{ScExecutablePlatform.PROFILE},driver));
        assertThrows(ClassNotFoundException.class,()->Class.forName("com.sun.jna.Native"));
    }
    final class SupervisorFixture implements AutoCloseable {
        final EmptyCustodianBootstrapTest helper=new EmptyCustodianBootstrapTest();
        final EmptyCustodianBootstrapTest.Fixture f;
        final ScSupervisor supervisor;
        boolean cancel, bound=true;
        String fault="";
        EmptyCustodianBootstrapTest.Gate gate;
        SupervisorFixture() throws Exception {
            helper.temporary=temporary; f=helper.new Fixture();
            var checked=new TransferConfirmation() {
                public boolean confirmsTransfer(Acceptance a) { return a!=null && !fault.equals("transfer"); }
                public ProcessEnd inspectPeer(Peer p){return f.inspectPeer(p);}
                public Acceptance takeOver(Object a,StartIntentStore s,StartIntentStore.Proof p,Resource k,Peer c,Owner o) { return f.takeOver(a,s,p,k,c,o); }
                public void requestStop(Object a,long d){f.requestStop(a,d);}
                public void controlStep(){f.controlStep();}
                public Inventory inventory(Object a,Resource k){return f.inventory(a,k);}
                public EndReceipt finishEmpty(Object a,Resource k){return f.finishEmpty(a,k);}
                public Release releaseSupervisor(Object a,Peer c){return f.releaseSupervisor(a,c);}
            };
            supervisor=new ScSupervisor(f.store,f.kernel,new ScSupervisor.StartPort() {
                public ScSupervisor.BoundChild startAndBind(StartIntentStore.Binding binding,StartIntentStore.Proof proof) {
                    assertTrue(f.store.valid(proof)); assertTrue(f.events.contains("intent-PUBLISHED"));
                    assertNull(f.fake.lastJob);f.events.add("C-bound");
                    if(gate!=null)gate.pause();
                    if(fault.equals("crash"))f.cAlive=false;
                    if(fault.equals("same"))return new ScSupervisor.BoundChild(new Peer(f.owner,f.peer.principal(),f.peer.channel()),f.owner,checked,true);
                    return new ScSupervisor.BoundChild(f.peer,f.owner,checked,bound);
                }
                public void cancel(){cancel=true;}
            },()->f.now,1000,100);
        }
        public void close() throws Exception { f.close(); }
    }
    @Test void supervisorPublishesIntentThenBindsCThenCreatesEmptyKAndConfirmsAck() throws Exception {
        try(var x=new SupervisorFixture()) {
            assertTrue(x.supervisor.begin());assertTrue(x.supervisor.ready());
            assertTrue(x.f.events.indexOf("intent-PUBLISHED")<x.f.events.indexOf("C-bound"));
            assertTrue(x.f.events.indexOf("C-bound")<x.f.events.indexOf("createJob"));
            assertEquals(1,x.f.events.stream().filter("intent-PUBLISHED"::equals).count());
            x.supervisor.finish(true);assertTrue(x.supervisor.result().technicalCompletion());
            assertFalse(x.supervisor.begin());
        }
    }
    @ParameterizedTest @ValueSource(strings={"channel","same","crash","transfer","ack-missing","ack-C","ack-revision","owner-id"})
    void supervisorBindingTransferAndAckFailuresAreTerminal(String fault) throws Exception {
        try(var x=new SupervisorFixture()) {
            x.fault=fault;if(fault.equals("channel"))x.bound=false;
            if(fault.startsWith("ack") || fault.equals("owner-id"))x.f.bad=fault;
            assertFalse(x.supervisor.begin());assertFalse(x.supervisor.ready());assertTrue(x.cancel);
            assertFalse(x.supervisor.begin());
            if(fault.equals("channel") || fault.equals("same") || fault.equals("crash"))assertNull(x.f.fake.lastJob);
        }
    }
    @Test void custodianDeathAfterReadyImmediatelyRevokesSupervisorReadiness() throws Exception {
        try(var x=new SupervisorFixture()) { assertTrue(x.supervisor.begin());x.f.custodianExit();assertFalse(x.supervisor.ready()); }
    }
    @Test void lateBoundChildAfterStopOrDeadlineNeverCreatesK() throws Exception {
        for(boolean deadline:List.of(false,true)) try(var x=new SupervisorFixture()) {
            x.gate=new EmptyCustodianBootstrapTest.Gate();AtomicReference<Boolean> result=new AtomicReference<>();
            Thread t=new Thread(()->result.set(x.supervisor.begin()));t.start();
            try {
                assertTrue(x.gate.entered.await(3,TimeUnit.SECONDS));assertTrue(x.supervisor.outstanding());
                if(deadline)x.f.now=1001;else x.supervisor.stop();
                assertNull(x.f.fake.lastJob);
            } finally { x.gate.release.countDown();t.join(2500);if(t.isAlive()){t.interrupt();t.join(500);} }
            assertFalse(t.isAlive());assertEquals(false,result.get());assertNull(x.f.fake.lastJob);
        }
    }
    static final class ProcessRaw implements Raw {
        boolean alive=true, wrong, inherited;
        public long currentProcessId(){return 52;}
        public ProcessState inspectProcess(long value){assertEquals(101,value);return new ProcessState(wrong?EmptyCustodianBootstrapTest.C:EmptyCustodianBootstrapTest.S,alive,!inherited);}
        public boolean nonInherited(long v){throw new AssertionError();}
        public Duplicate duplicate(long a,long b,long c,int d,boolean e,int f){throw new AssertionError();}
        public boolean sameLocalObject(long a,long b){throw new AssertionError();}
    }
    @Test void heldLedgerPinsUntilReturnAndUnknownCloseCannotBeRetried() {
        var raw=new ProcessRaw();AtomicInteger closes=new AtomicInteger();
        var ledger=new HeldProcessLedger(raw,v->{closes.incrementAndGet();return false;});
        Object cap=ledger.registerAcquired(101,PROCESS_ACCESS,EmptyCustodianBootstrapTest.S);
        var use=ledger.borrow(cap);assertFalse(ledger.close(cap));assertEquals(0,closes.get());
        assertThrows(IllegalStateException.class,()->ledger.borrow(new Object()));
        use.close();use.close();assertFalse(ledger.close(cap));assertFalse(ledger.close(cap));assertEquals(1,closes.get());assertFalse(ledger.complete());
    }
    @ParameterizedTest @ValueSource(strings={"identity","inherit","dead","rights","zero","pseudo"})
    void invalidProcessAcquisitionNeverMintsCapability(String fault) {
        var raw=new ProcessRaw();raw.wrong=fault.equals("identity");raw.inherited=fault.equals("inherit");raw.alive=!fault.equals("dead");
        var ledger=new HeldProcessLedger(raw,v->true);
        assertThrows(RuntimeException.class,()->ledger.registerAcquired(fault.equals("zero")?0:fault.equals("pseudo")?-1:101,
                fault.equals("rights")?0:PROCESS_ACCESS,EmptyCustodianBootstrapTest.S));
    }
    final class CustodianFixture {
        final Pipe toC=new Pipe(),toS=new Pipe();
        final ScChannel s=channel(false,toS,toC.out), c=channel(true,toC,toS.out);
        final FakeKernelCalls kernel=new FakeKernelCalls();
        final Resource job=kernel.createJob(new Scope());
        final ProcessRaw raw=new ProcessRaw();
        final HeldProcessLedger processes=new HeldProcessLedger(raw,v->true);
        final Owner owner=new Owner(processes.registerAcquired(101,PROCESS_RIGHTS,EmptyCustodianBootstrapTest.S),EmptyCustodianBootstrapTest.S,PROCESS_RIGHTS,true);
        final ScNativeCustodianPort port;
        final OuterJobCustodian core;
        final ScCustodian runtime;
        boolean sourceClosed,extra,pending,missingCopy;
        long now;
        EmptyCustodianBootstrapTest.Gate gate;
        CustodianFixture() {
            kernel.configure(job,new Limits(true,false,0,0,0));
            port=new ScNativeCustodianPort(kernel,processes,owner,new ScNativeCustodianPort.AcquiredControl(job,JOB_RIGHTS,true,true),INTENT,
                    held->new Fence(held,sourceClosed&&!extra,!pending,true,true));
            core=new OuterJobCustodian(INTENT,WIRE.anchor(),()->true,owner,new Object(),WIRE.channel(),port,()->now,1000,100,500);
            runtime=new ScCustodian(core,c,WIRE,new ScCustodian.DeliveryPort() {
                public ScCustodian.Adoption takeVerifiedLocalCopy(UUID transfer){if(gate!=null)gate.pause();return missingCopy || !TRANSFER.equals(transfer)?null:new ScCustodian.Adoption(transfer,job,true);}
                public boolean copiesAndLaunchesSettled(){return sourceClosed&&!extra&&!pending;}
            });
        }
        void bound() throws Exception {assertTrue(runtime.bind());s.receive(ScChannel.Kind.BOUND,INTENT.session());}
        void adopted() throws Exception {bound();s.send(ScChannel.Kind.ADOPT,TRANSFER);assertTrue(runtime.adopt());s.receive(ScChannel.Kind.ACK,TRANSFER);}
    }
    @Test void custodianOwnsOneReducedCopyAndNormalEmptyEndRequiresEveryIndependentReceipt() throws Exception {
        var f=new CustodianFixture();f.adopted();assertTrue(f.runtime.acknowledged());
        f.s.send(ScChannel.Kind.STOP,TRANSFER);f.runtime.receiveStop();f.runtime.controlStep();f.runtime.finishStep();
        assertFalse(f.runtime.report().groupEnded());assertFalse(f.runtime.sendEnd());
        f.sourceClosed=true;f.runtime.finishStep();assertTrue(f.runtime.report().groupEnded());assertTrue(f.processes.complete());
        assertTrue(f.runtime.sendEnd());f.s.receive(ScChannel.Kind.END,TRANSFER);f.s.receive(ScChannel.Kind.CLOSED,TRANSFER);assertTrue(f.s.confirmEof());
        assertFalse(f.runtime.sendEnd());assertTrue(f.kernel.closed.contains(f.job));
    }
    @Test void communicationLossNeverFabricatesOwnerDeathAndOwnerCrashAfterAckIsObservedViaHeldCapability() throws Exception {
        var f=new CustodianFixture();f.adopted();f.runtime.receiveStop();
        assertEquals(Signal.ALIVE,f.runtime.report().owner());assertTrue(f.raw.alive);
        f.runtime.controlStep();assertEquals(Signal.ALIVE,f.runtime.report().owner());
        f.raw.alive=false;f.sourceClosed=true;f.runtime.controlStep();assertEquals(Signal.ENDED,f.runtime.report().owner());
        f.runtime.finishStep();assertTrue(f.runtime.report().groupEnded());
        assertFalse(f.runtime.sendEnd()); // beschädigter Kanal ist kein Endbeleg.
    }
    @Test void ownerCrashBeforeAdoptionRequiresOwnCExitNotInventedKAdoption() throws Exception {
        var f=new CustodianFixture();f.bound();f.raw.alive=false;f.runtime.controlStep();
        assertTrue(f.runtime.exitWithoutAdoptionRequired());assertFalse(f.runtime.acknowledged());assertFalse(f.runtime.sendEnd());
        assertFalse(f.runtime.report().groupEnded());assertFalse(f.runtime.report().resourcesClosed());
    }
    @ParameterizedTest @ValueSource(strings={"extra","pending","missing-end","terminate","close","deadline"})
    void incompleteCleanupNeverProducesConfirmedEndOrRetry(String fault) throws Exception {
        var f=new CustodianFixture();f.adopted();f.runtime.stop();f.sourceClosed=true;
        switch(fault) {
            case "extra" -> f.extra=true;
            case "pending" -> f.pending=true;
            case "missing-end" -> f.kernel.observations.add(new Observation(false,true,0,List.of(),0,0));
            case "terminate" -> f.kernel.faults.add(FakeKernelCalls.Fault.TERMINATE);
            case "close" -> f.kernel.faults.add(FakeKernelCalls.Fault.CLOSE_JOB);
            case "deadline" -> f.now=1001;
        }
        f.runtime.controlStep();f.runtime.finishStep();assertFalse(f.runtime.sendEnd());assertTrue(f.runtime.report().quarantine());
        if(fault.equals("close")) { long closes=f.kernel.events.stream().filter(e->e.operation().equals("close")).count();f.runtime.finishStep();assertEquals(closes,f.kernel.events.stream().filter(e->e.operation().equals("close")).count()); }
    }
    @Test void blockedAdoptionCanBeStoppedAndLateReturnCannotAck() throws Exception {
        var f=new CustodianFixture();f.bound();f.s.send(ScChannel.Kind.ADOPT,TRANSFER);f.gate=new EmptyCustodianBootstrapTest.Gate();
        AtomicReference<Boolean> answer=new AtomicReference<>();Thread t=new Thread(()->answer.set(f.runtime.adopt()));t.start();
        try { assertTrue(f.gate.entered.await(3,TimeUnit.SECONDS));f.runtime.stop();f.runtime.controlStep();assertFalse(f.runtime.acknowledged()); }
        finally { f.gate.release.countDown();t.join(2500);if(t.isAlive()){t.interrupt();t.join(500);} }
        assertFalse(t.isAlive());assertEquals(false,answer.get());assertEquals(-1,f.toS.read());assertFalse(f.runtime.adopt());
    }
    @Test void channelReadBlockNeverHoldsRuntimeMonitorOrClaimsOwnerDeath() throws Exception {
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        AtomicReference<Throwable> error=new AtomicReference<>();
        var receiver=channel(false,new InputStream(){ public int read() throws IOException {entered.countDown();try{if(!release.await(3,TimeUnit.SECONDS))throw new IOException();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException();}return -1;}},OutputStream.nullOutputStream());
        Thread t=new Thread(()->{try{assertThrows(IOException.class,()->receiver.receive(ScChannel.Kind.ACK,TRANSFER));}catch(Throwable failure){error.set(failure);}});t.start();
        try {assertTrue(entered.await(3,TimeUnit.SECONDS));receiver.cancel();assertTrue(receiver.failed());assertTrue(receiver.outstanding());}
        finally{release.countDown();t.join(2500);if(t.isAlive()){t.interrupt();t.join(500);}}
        assertFalse(t.isAlive());assertFalse(receiver.outstanding());assertNull(error.get());
    }
    @Test void unknownOrOverprivilegedControlAcquisitionCannotBeClaimed() {
        var f=new CustodianFixture();
        for(var acquisition:List.of(new ScNativeCustodianPort.AcquiredControl(f.job,0xffff,true,true),
                new ScNativeCustodianPort.AcquiredControl(f.job,JOB_RIGHTS,false,true),new ScNativeCustodianPort.AcquiredControl(f.job,JOB_RIGHTS,true,false)))
            assertThrows(IllegalArgumentException.class,()->new ScNativeCustodianPort(f.kernel,f.processes,f.owner,acquisition,INTENT,c->null));
        assertThrows(UnsupportedOperationException.class,()->f.port.duplicateRoot(null,null,PROCESS_RIGHTS));
    }
    @ParameterizedTest @ValueSource(strings={"normal","missing-ack","wrong-ack","missing-end"})
    void realTransferContractAndBoundWireRequireBothAdoptionAndEndEvidence(String fault) throws Exception {
        var helper=new HandleTransferPortTest();helper.temporary=temporary;
        try(var f=helper.new Fixture()) {
            var toC=new Pipe();var toS=new Pipe();
            var c=channel(true,toC,toS.out);
            HandleTransferPort.Delivery[] delivery={null};Control[] copy={null};boolean[] adopted={false};
            var output=new OutputStream() {
                public void write(int b) throws IOException {toC.out.write(b);}
                public void flush() throws IOException {
                    if(!adopted[0]) {
                        c.receive(ScChannel.Kind.ADOPT,delivery[0].id());
                        if(!fault.equals("missing-ack")) c.send(ScChannel.Kind.ACK,fault.equals("wrong-ack")?UUID.randomUUID():delivery[0].id());
                        adopted[0]=true;
                    } else {
                        c.receive(ScChannel.Kind.STOP,delivery[0].id());
                        if(!fault.equals("missing-end")) {c.send(ScChannel.Kind.END,delivery[0].id());c.send(ScChannel.Kind.CLOSED,delivery[0].id());}
                    }
                }
            };
            var s=channel(false,toS,output);
            var remote=new ScTransferReceiver.RemoteLedger() {
                public void deliver(HandleTransferPort.Delivery d){assertNull(delivery[0]);delivery[0]=d;assertFalse(f.port.status().acknowledged());}
                public Acceptance adopted(Object attempt,StartIntentStore store,StartIntentStore.Proof proof,Resource source,Peer peer,Owner owner,Control surrogate) {
                    assertTrue(adopted[0]);copy[0]=surrogate;
                    return new Acceptance(attempt,store.binding(),proof.anchor(),peer,owner,surrogate,2,true,1,true,new ProcessEnd(owner.handle(),owner.identity(),Signal.ALIVE));
                }
                public ProcessEnd inspectPeer(Peer peer){return new ProcessEnd(peer.process().handle(),peer.process().identity(),Signal.ALIVE);}
                public Acceptance takeOver(Object a,StartIntentStore st,StartIntentStore.Proof p,Resource j,Peer peer,Owner owner){throw new AssertionError("no second transfer");}
                public void requestStop(Object a,long deadline) { }
                public void controlStep(){ }
                public Inventory inventory(Object a,Resource j){return new Inventory(a,j,true,false,true,0,true);}
                public EndReceipt finishEmpty(Object a,Resource j){return new EndReceipt(a,j,f.bank.peer,new Report(true,Signal.ALIVE,true,Termination.ACCEPTED,true,true,true,0,1,100));}
                public Release releaseSupervisor(Object a,Peer peer){return new Release(a,peer,new ProcessEnd(peer.process().handle(),peer.process().identity(),Signal.ENDED),true,true);}
            };
            var connection=new TransferCheckedConnection(f.port,new ScTransferReceiver(s,remote));
            Acceptance answer=connection.takeOver(f.attempt,f.store,f.proof,f.bank.source,f.bank.peer,f.bank.owner);
            if(fault.endsWith("ack")) {assertNull(answer);assertTrue(f.port.status().quarantine());assertFalse(f.port.status().acknowledged());return;}
            assertNotNull(answer);assertTrue(connection.confirmsTransfer(answer));assertTrue(adopted[0]);
            assertEquals(2,f.bank.duplicates);assertNotNull(copy[0]);
            connection.requestStop(f.attempt,100);connection.controlStep();
            var end=connection.finishEmpty(f.attempt,f.bank.source);
            if(fault.equals("missing-end")){assertNull(end);assertEquals(Signal.UNKNOWN,connection.inspectPeer(f.bank.peer).signal());}
            else {assertNotNull(end);assertSame(end,connection.finishEmpty(f.attempt,f.bank.source));assertTrue(connection.releaseSupervisor(f.attempt,f.bank.peer).channelsClosed());}
        }
    }
    @Test void missingLocalDeliveryAndWrongOwnerProofCannotAck() throws Exception {
        var f=new CustodianFixture();f.bound();f.missingCopy=true;f.s.send(ScChannel.Kind.ADOPT,TRANSFER);
        assertFalse(f.runtime.adopt());assertFalse(f.runtime.acknowledged());assertEquals(-1,f.toS.read());
        var g=new CustodianFixture();g.raw.wrong=true;assertFalse(g.runtime.bind());assertEquals(Signal.UNKNOWN,g.runtime.report().owner());
    }
    @Test void aliveButHungOwnerExpiresProgressWithoutBecomingDeadOrExtendingCleanupBudget() throws Exception {
        var f=new CustodianFixture();f.adopted();f.now=500;f.runtime.controlStep();
        assertEquals(Signal.ALIVE,f.runtime.report().owner());assertTrue(f.runtime.report().quarantine());
        long deadline=f.runtime.report().cleanupDeadline();f.now=550;f.runtime.stop();
        assertEquals(deadline,f.runtime.report().cleanupDeadline());assertFalse(f.runtime.acknowledged());
    }
    @Test void cBindsBeforeTransferExistsButAnnouncedUuidAloneNeverGrantsControl() throws Exception {
        var f=new CustodianFixture();f.bound();assertFalse(f.runtime.acknowledged());
        f.s.send(ScChannel.Kind.ADOPT,UUID.randomUUID());assertFalse(f.runtime.adopt());
        assertEquals(-1,f.toS.read());assertTrue(f.runtime.report().quarantine());assertFalse(f.runtime.report().resourcesClosed());
    }
}
