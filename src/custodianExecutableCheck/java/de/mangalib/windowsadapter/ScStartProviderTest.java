package de.mangalib.windowsadapter;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static de.mangalib.windowsadapter.KernelCalls.*;
import static de.mangalib.windowsadapter.OuterJobCustodian.*;
import static de.mangalib.windowsadapter.EmptyCustodianBootstrap.*;
import static de.mangalib.windowsadapter.Win64TransferContract.*;
import de.mangalib.windowsadapter.ScSupervisor.BoundChild;
import static org.junit.jupiter.api.Assertions.*;

/** Native Start-/Acquisitionverträge nur als JDK-Fakes; JNA ist nicht im Prüfruntimepfad. */
@Timeout(10)
class ScStartProviderTest {
    @TempDir Path temporary;
    static final String IMAGE="C:\\Trusted\\java.exe";
    static final Identity S=new Identity(51,101,IMAGE),C=new Identity(52,102,IMAGE);
    static final StartIntentStore.Binding B=new StartIntentStore.Binding(new UUID(1,1),new UUID(2,2),new UUID(3,3));
    static final class Calls implements ScStartProvider.Calls {
        final List<String> events=new ArrayList<>();final List<Long> closed=new ArrayList<>();
        final AtomicLong time=new AtomicLong();
        boolean cAlive=true,sAlive=true,prepared,pipeClosed,resumed,settled=true,copyOpen;
        String fault="";int creates,kills,duplicates;
        CountDownLatch entered,release;
        InputStream in=InputStream.nullInputStream();OutputStream out=OutputStream.nullOutputStream();
        public long selfCopy(int access){assertEquals(PROCESS_ACCESS,access);events.add("self");return 101;}
        public long processCopy(long value,int access){
            if(value==101){assertEquals(PROCESS_RIGHTS,access);events.add("owner-copy");return 102;}
            assertEquals(201,value);assertEquals(PROCESS_ACCESS,access);events.add("reduce-C");return 203;
        }
        public void prepare(long owner){assertEquals(102,owner);prepared=true;events.add("pipes");}
        public ScStartProvider.Created create(ScStartProvider.Acquisition sink){
            creates++;assertTrue(prepared);events.add("create");
            if(entered!=null){entered.countDown();try{if(!release.await(3,TimeUnit.SECONDS))throw new IllegalStateException("fake-gate");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("fake-interrupt");}}
            if(fault.equals("throw-create")){settled=false;throw new IllegalStateException("unknown-create");}
            sink.acquired(ScStartProvider.HandleKind.PROCESS,201);events.add("registered-process");
            sink.acquired(ScStartProvider.HandleKind.THREAD,fault.equals("alias")?201:202);events.add("registered-thread");
            return new ScStartProvider.Created(!fault.equals("failed-create"),201,202,52);
        }
        public boolean resume(long thread){assertEquals(202,thread);resumed=true;events.add("resume");return !fault.equals("resume");}
        public boolean terminateProcess(long process){assertEquals(201,process,"Nur CreateProcess-Cleanupauthority besitzt TERMINATE");kills++;if(fault.equals("kill"))return false;cAlive=false;return true;}
        public boolean close(long h){closed.add(h);return !fault.equals("close");}
        public int exitCode(long h){assertFalse(cAlive);return 0;}
        public long currentProcessId(){return 51;}
        public ProcessState inspectProcess(long h){
            boolean own=h==101 || h==102;assertTrue(own || h==201 || h==203);events.add("inspect:"+h);
            if(h==201)assertTrue(events.contains("registered-thread"));
            Identity identity=own?S:C;
            if(h==203 && fault.equals("identity"))identity=new Identity(52,999,IMAGE);
            if(!own && fault.equals("same"))identity=S;
            if(!own && fault.equals("image"))identity=new Identity(52,102,"C:\\Other\\java.exe");
            return new ProcessState(identity,own?sAlive:cAlive,!fault.equals("inherit"));
        }
        public boolean nonInherited(long value){return true;}
        public Duplicate duplicate(long from,long handle,long to,int rights,boolean inherit,int options){
            assertFalse(inherit);assertEquals(0,options);duplicates++;
            if(duplicates==1){assertEquals(101,from);assertEquals(303,handle);assertEquals(203,to);assertEquals(JOB_RIGHTS,rights);copyOpen=true;
                if(fault.equals("C-during-copy"))cAlive=false;return new Duplicate(true,900);}
            assertEquals(2,duplicates);assertEquals(203,from);assertEquals(900,handle);assertEquals(101,to);assertEquals(4,rights);return new Duplicate(true,404);
        }
        public boolean sameLocalObject(long a,long b){assertEquals(303,a);assertEquals(404,b);return !fault.equals("wrong-K");}
        public InputStream input(){return in;}
        public OutputStream output(){return out;}
        public boolean closePipes(){pipeClosed=true;return !fault.equals("pipe-close");}
        public boolean preparationSettled(){return settled;}
    }
    final class Fixture implements AutoCloseable {
        final Calls calls=new Calls();
        final StartIntentStore store=StartIntentStore.create(temporary.resolve(UUID.randomUUID().toString()),B,p->{});
        final StartIntentStore.Proof proof=store.prepare();
        ScStartProvider provider;
        Peer peer;Owner owner;
        boolean handshake=true;
        CountDownLatch handshakeEntered,handshakeRelease;
        Fixture() throws Exception {
            provider=new ScStartProvider(calls,store,new ScStartProvider.ConnectionFactory(){
                public boolean handshake(Peer c,Owner s,long inherited,ScStartProvider.Calls os,StartIntentStore.Proof p){
                    assertTrue(store.valid(p));assertTrue(calls.resumed);peer=c;owner=s;calls.events.add("bound");
                    if(handshakeEntered!=null){handshakeEntered.countDown();try{if(!handshakeRelease.await(3,TimeUnit.SECONDS))throw new IllegalStateException();}
                        catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException();}}
                    return handshake;
                }
                public TransferConfirmation connect(Peer c,Owner s,HeldProcessLedger ledger,ScStartProvider.Calls os,StartIntentStore.Proof p){
                    return inertConnection(c,s);
                }
            },calls.time::get,1000,IMAGE);
        }
        BoundChild start(){return provider.startAndBind(B,proof);}
        public void close() throws Exception{store.close();}
    }
    static TransferConfirmation inertConnection(Peer peer,Owner owner){return new TransferConfirmation(){
        public boolean confirmsTransfer(Acceptance a){return false;}
        public ProcessEnd inspectPeer(Peer p){return new ProcessEnd(peer.process().handle(),peer.process().identity(),Signal.ALIVE);}
        public Acceptance takeOver(Object a,StartIntentStore st,StartIntentStore.Proof pr,Resource r,Peer p,Owner o){return null;}
        public void requestStop(Object a,long d){}
        public void controlStep(){}
        public Inventory inventory(Object a,Resource r){return null;}
        public EndReceipt finishEmpty(Object a,Resource r){return null;}
        public Release releaseSupervisor(Object a,Peer p){return null;}
    };}
    @Test void successfulStartRegistersBeforeQueryReducesRightsAndSeparatesChannelAndProcessEnd() throws Exception {
        try(var f=new Fixture()){
            var bound=f.start();assertNotNull(bound);assertTrue(bound.channelBound());assertEquals(1,f.calls.creates);assertEquals(0,f.calls.duplicates);
            assertTrue(f.calls.events.indexOf("registered-thread")<f.calls.events.indexOf("inspect:201"));
            assertTrue(f.calls.events.indexOf("reduce-C")<f.calls.events.indexOf("resume"));
            try(var held=f.provider.ledger().borrow(bound.peer().process().handle())){assertEquals(PROCESS_ACCESS,held.access());}
            // Ein bewusst offener Nutzungsbeleg sperrt auch nach Prozessende Close.
            var use=f.provider.ledger().borrow(bound.peer().process().handle());f.calls.cAlive=false;
            assertFalse(f.provider.releaseEnded());use.close();assertFalse(f.provider.snapshot().handlesClosed());
            assertNull(f.start());assertEquals(1,f.calls.creates);
        }
    }
    @Test void fullyReleasedAcquisitionsCloseEachOwnHandleOnce() throws Exception {
        try(var f=new Fixture()){
            assertNotNull(f.start());assertFalse(f.provider.releaseEnded());f.calls.cAlive=false;
            assertTrue(f.provider.releaseEnded());assertTrue(f.provider.snapshot().ended());assertTrue(f.calls.pipeClosed);
            assertEquals(Set.of(101L,102L,201L,202L,203L),new HashSet<>(f.calls.closed));assertEquals(5,f.calls.closed.size());
        }
    }
    @ParameterizedTest @ValueSource(strings={"identity","same","image","inherit","alias","failed-create","resume","close","throw-create"})
    void unconfirmedStartNeverBecomesBoundAndNeverRetries(String fault) throws Exception {
        try(var f=new Fixture()){
            f.calls.fault=fault;assertNull(f.start());assertFalse(f.provider.snapshot().bound());assertTrue(f.provider.snapshot().quarantine());
            int starts=f.calls.creates;assertNull(f.start());assertEquals(starts,f.calls.creates);
            if(fault.equals("throw-create")){assertFalse(f.provider.releaseEnded());assertEquals(0,f.calls.kills);}
        }
    }
    @Test void missingOrForeignIntentPreventsEvenSelfAcquisition() throws Exception {
        try(var f=new Fixture()){
            assertNull(f.provider.startAndBind(new StartIntentStore.Binding(UUID.randomUUID(),B.session(),B.group()),f.proof));
            assertTrue(f.calls.events.isEmpty());assertFalse(f.calls.prepared);
        }
        try(var f=new Fixture()){f.store.abort();assertNull(f.start());assertTrue(f.calls.events.isEmpty());}
    }
    @Test void missingBoundHandshakeCannotAuthorizeKAndCanOnlyRollbackItsHeldC() throws Exception {
        try(var f=new Fixture()){
            f.handshake=false;assertNull(f.start());assertFalse(f.provider.snapshot().bound());
            f.provider.rollbackSuspended();assertEquals(1,f.calls.kills);assertTrue(f.provider.snapshot().ended());
            f.provider.rollbackSuspended();assertEquals(1,f.calls.kills);
        }
    }
    @Test void blockedHandshakeDoesNotHoldIndependentRollbackAuthorityOrAllowEarlyClose() throws Exception {
        try(var f=new Fixture()){
            f.handshakeEntered=new CountDownLatch(1);f.handshakeRelease=new CountDownLatch(1);
            AtomicReference<BoundChild> answer=new AtomicReference<>();AtomicReference<Throwable> error=new AtomicReference<>();
            Thread t=new Thread(()->{try{answer.set(f.start());}catch(Throwable e){error.set(e);}});t.start();
            try{
                assertTrue(f.handshakeEntered.await(3,TimeUnit.SECONDS));f.provider.cancel();f.provider.rollbackSuspended();
                assertEquals(1,f.calls.kills);assertTrue(f.provider.snapshot().ended());assertFalse(f.provider.releaseEnded());
                assertFalse(f.calls.closed.contains(201L));
            }finally{f.handshakeRelease.countDown();t.join(2500);if(t.isAlive()){t.interrupt();t.join(500);}}
            assertFalse(t.isAlive());assertNull(error.get());assertNull(answer.get());assertTrue(f.provider.releaseEnded());
        }
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void lateCreateAfterStopOrDeadlineRetainsAndRollsBackOnlyKnownSuspendedInstance(boolean deadline) throws Exception {
        try(var f=new Fixture()){
            f.calls.entered=new CountDownLatch(1);f.calls.release=new CountDownLatch(1);
            AtomicReference<BoundChild> result=new AtomicReference<>();AtomicReference<Throwable> error=new AtomicReference<>();
            Thread t=new Thread(()->{try{result.set(f.start());}catch(Throwable e){error.set(e);}});t.start();
            try{
                assertTrue(f.calls.entered.await(3,TimeUnit.SECONDS));assertTrue(f.provider.snapshot().outstanding());
                if(deadline)f.calls.time.set(1001);else f.provider.cancel();
                assertFalse(f.provider.releaseEnded());assertEquals(0,f.calls.kills);
            }finally{f.calls.release.countDown();t.join(2500);if(t.isAlive()){t.interrupt();t.join(500);}}
            assertFalse(t.isAlive());assertNull(error.get());assertNull(result.get());assertFalse(f.calls.resumed);assertEquals(1,f.calls.kills);
            assertTrue(f.provider.snapshot().ended());assertTrue(f.provider.releaseEnded());
        }
    }
    @Test void failedTerminationUnknownCloseAndOutstandingPreparationNeverProveCompletion() throws Exception {
        try(var f=new Fixture()){
            f.handshake=false;assertNull(f.start());f.calls.fault="kill";f.provider.rollbackSuspended();
            assertFalse(f.provider.snapshot().ended());assertFalse(f.provider.releaseEnded());assertEquals(1,f.calls.kills);
        }
        try(var f=new Fixture()){
            assertNotNull(f.start());f.calls.cAlive=false;f.calls.fault="close";assertFalse(f.provider.releaseEnded());
            int closes=f.calls.closed.size();assertFalse(f.provider.releaseEnded());assertEquals(closes,f.calls.closed.size());
        }
        try(var f=new Fixture()){assertNotNull(f.start());f.calls.cAlive=false;f.calls.settled=false;assertFalse(f.provider.releaseEnded());}
    }
    @Test void bootPreludeIsBoundedAndPinsBothInstancesWithoutAcceptingExtraCommands() throws Exception {
        var binding=new ScChannel.Binding(B,new StartIntentStore.Anchor(1,"a".repeat(64)),new UUID(4,4));
        var boot=new ScNativeWire.Boot(binding,S,C,102);var bytes=new ByteArrayOutputStream();ScNativeWire.writeBoot(bytes,boot);
        assertEquals(boot,ScNativeWire.readBoot(new ByteArrayInputStream(bytes.toByteArray())));
        byte[] bad=bytes.toByteArray();java.nio.ByteBuffer.wrap(bad).putInt(1537);
        assertThrows(IOException.class,()->ScNativeWire.readBoot(new ByteArrayInputStream(bad)));
        var same=new ByteArrayOutputStream();ScNativeWire.writeBoot(same,new ScNativeWire.Boot(binding,S,S,102));
        assertThrows(IOException.class,()->ScNativeWire.readBoot(new ByteArrayInputStream(same.toByteArray())));
    }
    @Test void nativeWireNeedsRealChannelEndAndEofAndRejectsReplayAndWrongRoles() throws Exception {
        var binding=new ScChannel.Binding(B,new StartIntentStore.Anchor(1,"a".repeat(64)),new UUID(4,4));
        var bytes=new ByteArrayOutputStream();var sender=new ScNativeWire(binding,true,InputStream.nullInputStream(),bytes);
        sender.send("BOUND",B.session().toString());sender.finishOutput();
        var receiver=new ScNativeWire(binding,false,new ByteArrayInputStream(bytes.toByteArray()),OutputStream.nullOutputStream());
        assertEquals(B.session().toString(),receiver.receive("BOUND"));receiver.finishInput();assertFalse(receiver.failed());
        assertThrows(IOException.class,()->receiver.receive("BOUND"));
        assertThrows(IOException.class,()->new ScNativeWire(binding,false,InputStream.nullInputStream(),OutputStream.nullOutputStream()).send("ACK","x"));
        var truncated=new ScNativeWire(binding,false,new ByteArrayInputStream(Arrays.copyOf(bytes.toByteArray(),bytes.size()-1)),OutputStream.nullOutputStream());
        truncated.receive("BOUND");assertThrows(IOException.class,truncated::finishInput);
    }
    @Test void noNativeFactoryOrJnaAppearsInFakeRuntime() throws Exception {
        assertThrows(ClassNotFoundException.class,()->Class.forName("com.sun.jna.Native"));
        assertEquals(64,ScExecutablePlatform.nativeRun(ScExecutablePlatform.Role.SUPERVISOR,new String[]{"--unknown"}));
        assertEquals(78,ScExecutablePlatform.nativeRun(ScExecutablePlatform.Role.CUSTODIAN,new String[]{"--sc-native-empty-v1"}));
    }
    @Test void launchManifestAllowsOnlyFixedIsolatedRootsAndAnExactFreshApproval() throws Exception {
        var cp=new ArrayList<Path>();
        for(String set:List.of("scNative","custodianExecutable","jobAdapter","communicationHost"))cp.add(temporary.resolve("build/classes/java/"+set));
        cp.add(temporary.resolve("cache/jna-5.17.0.jar"));ScNativeManifest.requireClasspath(temporary,cp);
        var wrong=new ArrayList<>(cp);wrong.set(0,temporary.resolve("build/classes/java/main"));
        assertThrows(IOException.class,()->ScNativeManifest.requireClasspath(temporary,wrong));
        var extra=new ArrayList<>(cp);extra.add(temporary.resolve("extra.jar"));
        assertThrows(IOException.class,()->ScNativeManifest.requireClasspath(temporary,extra));
        String hash="a".repeat(64);UUID id=UUID.randomUUID();assertEquals(id,ScNativeManifest.requireApproval(id+"\n"+hash,hash));
        assertThrows(IOException.class,()->ScNativeManifest.requireApproval(id+"\n"+"b".repeat(64),hash));
        assertThrows(IOException.class,()->ScNativeManifest.requireApproval(new UUID(0,0)+"\n"+hash,hash));
        assertThrows(IOException.class,()->ScNativeManifest.requireApproval(id+"\n"+hash+"\nextra",hash));
    }
    @ParameterizedTest @ValueSource(strings={"normal","wrong-ack","duplicate-ack","missing-end","C-during-copy","wrong-K","extra-copy"})
    void ownStartedCapabilityTransferAndBoundBinaryConversationRemainOneAttempt(String fault) throws Exception {
        try(var f=new Fixture()){
            var toC=new PipedInputStream(2048);var sOut=new PipedOutputStream(toC);
            var toS=new PipedInputStream(2048);var cOut=new PipedOutputStream(toS);
            f.calls.in=toS;f.calls.out=sOut;f.calls.fault=fault;
            AtomicReference<Throwable> error=new AtomicReference<>();AtomicBoolean adopted=new AtomicBoolean(),sourceClosed=new AtomicBoolean();
            ScNativeWire[] wire={null};ScNativeConnection[] connection={null};Object attempt=new Object();
            Resource k=new Resource(new Scope(),Kind.JOB,303);
            Win64TransferContract.SourceLedger jobs=source->{
                assertSame(k,source);return new Win64TransferContract.JobUse(){
                    public Resource resource(){return k;}
                    public long value(){return 303;}
                    public Limits limits(){return new Limits(true,false,0,0,0);}
                    public LocalCopy registerLocalCopy(long value){assertEquals(404,value);return new LocalCopy(){
                        public Resource resource(){return new Resource(k.scope,Kind.JOB,404);}
                        public long value(){return 404;}
                        public void close(){ }
                    };}
                    public void close(){ }
                };
            };
            Thread child=new Thread(()->{
                try{
                    var boot=ScNativeWire.readBoot(toC);assertEquals(S,boot.owner());assertEquals(C,boot.child());assertEquals(102,boot.ownerHandle());
                    var c=new ScNativeWire(boot.binding(),true,toC,cOut);c.send("BOUND",B.session().toString());
                    String grant=c.receive("GRANT");String[] parts=grant.split(":",-1);assertEquals("384",parts[1]); // 900, nur synthetische C-Tabelle.
                    UUID id=UUID.fromString(parts[0]);adopted.set(true);
                    c.send("ACK",fault.equals("wrong-ack")?UUID.randomUUID().toString():id.toString());
                    if(fault.equals("wrong-ack"))return;
                    if(fault.equals("duplicate-ack"))c.send("ACK",id.toString());
                    assertEquals(id.toString(),c.receive("STOP"));assertEquals(id.toString(),c.receive("FENCE"));
                    assertTrue(sourceClosed.get());c.finishInput();
                    if(fault.equals("missing-end"))return;
                    c.send("GROUP_END",id.toString());c.send("CLOSED",id.toString());c.finishOutput();
                }catch(IOException expected){/* Abgebrochene negative Sitzung: kein Endbeleg. */}
                catch(Throwable failure){error.set(failure);}
                finally{f.calls.cAlive=false;try{cOut.close();}catch(IOException ignored){}}
            },"fake-C-memory-only");child.setDaemon(true);child.start();
            var provider=new ScStartProvider(f.calls,f.store,new ScStartProvider.ConnectionFactory(){
                public boolean handshake(Peer peer,Owner owner,long inherited,ScStartProvider.Calls calls,StartIntentStore.Proof proof){
                    try{
                        var envelope=new ScChannel.Binding(B,proof.anchor(),peer.channel());
                        ScNativeWire.writeBoot(sOut,new ScNativeWire.Boot(envelope,owner.identity(),peer.process().identity(),inherited));
                        wire[0]=new ScNativeWire(envelope,false,toS,sOut);return wire[0].receive("BOUND").equals(B.session().toString());
                    }catch(IOException e){return false;}
                }
                public TransferConfirmation connect(Peer peer,Owner owner,HeldProcessLedger ledger,ScStartProvider.Calls calls,StartIntentStore.Proof proof){
                    // Provider wird erst nach Konstruktion über f.provider veröffentlicht.
                    return connection[0]=new ScNativeConnection(wire[0],f.provider,calls,ledger,peer,owner,jobs,B,
                            source->sourceClosed.get(),source->fault.equals("extra-copy")?1:0,f.calls.time::get,1000);
                }
            },f.calls.time::get,1000,IMAGE);f.provider=provider;
            try{
                BoundChild bound=f.start();assertNotNull(bound);
                Acceptance answer=bound.connection().takeOver(attempt,f.store,f.proof,k,bound.peer(),bound.owner());
                if(List.of("wrong-ack","C-during-copy","wrong-K").contains(fault)){
                    assertNull(answer);assertFalse(bound.connection().confirmsTransfer(answer));return;
                }
                assertNotNull(answer);assertTrue(adopted.get());assertTrue(bound.connection().confirmsTransfer(answer));
                Inventory inventory=connection[0].inventory(attempt,k);
                if(fault.equals("extra-copy")){assertFalse(inventory.verified());assertEquals(1,inventory.extraCopies());return;}
                connection[0].requestStop(attempt,1000);connection[0].controlStep();sourceClosed.set(true);
                EndReceipt end=connection[0].finishEmpty(attempt,k);
                if(fault.equals("duplicate-ack") || fault.equals("missing-end")){assertNull(end);return;}
                assertNotNull(end);assertTrue(end.report().groupEnded());assertTrue(end.report().resourcesClosed());
                child.join(2000);assertFalse(child.isAlive());
                Release release=connection[0].releaseSupervisor(attempt,bound.peer());assertNotNull(release);assertTrue(release.channelsClosed());
                assertEquals(Signal.ENDED,release.custodianEnd().signal());assertTrue(provider.snapshot().handlesClosed());
            }finally{
                sOut.close();toC.close();cOut.close();toS.close();child.join(2000);if(child.isAlive()){child.interrupt();child.join(500);}
                assertFalse(child.isAlive());assertNull(error.get());
            }
        }
    }
}
