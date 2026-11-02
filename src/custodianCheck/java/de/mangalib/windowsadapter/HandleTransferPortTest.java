package de.mangalib.windowsadapter;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static de.mangalib.windowsadapter.KernelCalls.*;
import static de.mangalib.windowsadapter.OuterJobCustodian.*;
import static de.mangalib.windowsadapter.EmptyCustodianBootstrap.*;
import static de.mangalib.windowsadapter.Win64TransferContract.*;
import static org.junit.jupiter.api.Assertions.*;

/** Alle Zahlenwerte sind Fake-Adressen in getrennten S-/C-Tabellen; kein JNA. */
@Timeout(10)
class HandleTransferPortTest {
    @TempDir Path temporary;
    static final StartIntentStore.Binding BINDING = new StartIntentStore.Binding(new UUID(11,11),new UUID(12,12),new UUID(13,13));
    static final Identity S = new Identity(51,101,"C:/synthetic/S.exe"), C = new Identity(52,102,"C:/synthetic/C.exe");
    static final class Gate {
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        void pause() {
            entered.countDown();
            try { if(!release.await(3,TimeUnit.SECONDS))throw new IllegalStateException("fake-timeout"); }
            catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("fake-interrupted");}
        }
    }
    static final class Bank implements Raw, ProcessLedger, SourceLedger {
        Owner owner = new Owner(new Object(),S,PROCESS_RIGHTS,true);
        Peer peer = new Peer(new Owner(new Object(),C,PROCESS_RIGHTS,true),new Object(),new UUID(14,14));
        Resource source = new Resource(new Scope(),Kind.JOB,1);
        final List<Long> localCloses = new ArrayList<>();
        final List<String> calls = new ArrayList<>();
        String fault = "";
        int duplicates, processUses, jobUses;
        long remoteValue = 900;
        long backValue = 404;
        boolean alive = true, sourceClosed, remoteOpen, tempOpen;
        Gate gate;
        public ProcessUse borrow(Object capability) {
            if(capability!=owner.handle() && capability!=peer.process().handle())throw new Failure("foreign-process",0);
            processUses++;
            return new ProcessUse(){boolean closed;
                public Object capability(){return capability;}
                public long value(){return capability==owner.handle()?101:202;}
                public int access(){return fault.equals("access")?PROCESS_RIGHTS:PROCESS_ACCESS;}
                public void close(){assertFalse(closed);closed=true;processUses--;}
            };
        }
        public Win64TransferContract.JobUse borrowTransferSource(Resource job){
            if(job!=source || sourceClosed)throw new Failure("foreign-job",0);jobUses++;
            return new Win64TransferContract.JobUse(){boolean closed;
                public Resource resource(){return source;}
                public long value(){return 303;}
                public Limits limits(){return new Limits(true,false,fault.equals("limits")?4:0,0,0);}
                public LocalCopy registerLocalCopy(long value){
                    assertEquals(backValue,value);if(fault.equals("register"))throw new Failure("unknown-register",0);
                    Resource local=new Resource(source.scope,Kind.JOB,2);
                    return new LocalCopy(){boolean attempted;
                        public Resource resource(){return local;}
                        public long value(){return backValue;}
                        public void close(){assertFalse(attempted);attempted=true;localCloses.add(backValue);
                            if(fault.equals("close"))throw new Failure("unknown-close",0);tempOpen=false;}
                    };
                }
                public void close(){assertFalse(closed);closed=true;jobUses--;}
            };
        }
        public ProcessState inspectProcess(long value){
            assertTrue(value==101 || value==202);calls.add("process:"+value);
            if(gate!=null && fault.equals("block-inspect") && duplicates>0)gate.pause();
            Identity identity=value==101?S:C;
            if(fault.equals("identity") && value==202)identity=new Identity(52,999,C.image());
            return new ProcessState(identity,value==101 || alive,!fault.equals("process-inherit"));
        }
        public long currentProcessId(){return fault.equals("wrong-context")?52:51;}
        public boolean nonInherited(long value){assertTrue(value==303 || value==backValue);return !fault.equals("inherit");}
        public Duplicate duplicate(long from,long handle,long to,int access,boolean inherit,int options){
            assertFalse(inherit);assertEquals(0,options);duplicates++;
            if(duplicates==1){
                assertEquals(101,from);assertEquals(303,handle);assertEquals(202,to);assertEquals(JOB_RIGHTS,access);
                if(gate!=null && fault.equals("block-copy"))gate.pause();
                if(fault.equals("duplicate-exception"))throw new Failure("unknown-duplicate",0);
                if(fault.equals("duplicate-false"))return new Duplicate(false,0);
                if(fault.equals("duplicate-partial")){remoteOpen=true;return new Duplicate(false,900);}
                if(fault.equals("duplicate-invalid"))return new Duplicate(true,0);
                remoteOpen=true;
                if(fault.equals("exit-during"))alive=false;
                return new Duplicate(true,remoteValue);
            }
            assertEquals(2,duplicates);assertEquals(202,from);assertEquals(remoteValue,handle);assertEquals(101,to);assertEquals(4,access);
            if(fault.equals("roundtrip-exception"))throw new Failure("unknown-roundtrip",0);
            if(fault.equals("roundtrip"))return new Duplicate(false,0);
            tempOpen=true;return new Duplicate(true,backValue);
        }
        public boolean sameLocalObject(long first,long second){assertEquals(303,first);assertEquals(backValue,second);return !fault.equals("wrong-group");}
    }
    final class Fixture implements AutoCloseable {
        final Bank bank=new Bank();
        final StartIntentStore store=StartIntentStore.create(temporary.resolve(UUID.randomUUID().toString()),BINDING,p->{});
        final StartIntentStore.Proof proof=store.prepare();
        final Object attempt=new Object();
        final HandleTransferPort port=new HandleTransferPort(bank,bank,bank,bank.owner,bank.peer,BINDING,()->true);
        Fixture() throws Exception { }
        HandleTransferPort.Delivery transfer(){return port.transfer(attempt,store,proof,bank.source,bank.peer,bank.owner);}
        @Override public void close() throws Exception {store.close();}
    }
    @Test void duplicateRoundtripAndAckAreDifferentProofsAndSOnlyClosesItsOwnTemporaryCopy() throws Exception{
        try(var f=new Fixture()){
            var delivery=f.transfer();assertNotNull(delivery);assertFalse(f.port.status().acknowledged());
            assertEquals(HandleTransferPort.CopyState.PRESENT,f.port.status().target());
            assertEquals(List.of(404L),f.bank.localCloses);assertEquals(0,f.bank.jobUses);assertEquals(0,f.bank.processUses);
            Control control=f.port.claim(delivery,f.bank.peer);assertNotNull(control);
            assertTrue(f.port.acknowledge(f.port.targetAck(delivery,f.bank.peer,control)));
            f.bank.sourceClosed=true;assertTrue(f.bank.remoteOpen); // S-Close ist kein letzter K-Close.
            assertEquals(HandleTransferPort.CopyState.ADOPTED,f.port.status().target());
            f.port.targetClosed(control,f.bank.peer,true);assertEquals(HandleTransferPort.CopyState.CLOSED,f.port.status().target());
            assertEquals(List.of(404L),f.bank.localCloses);assertFalse(f.bank.localCloses.contains(900L));
            assertNull(f.transfer());assertEquals(2,f.bank.duplicates);
        }
    }
    @ParameterizedTest @ValueSource(strings={"wrong-context","access","limits","identity","inherit","process-inherit","duplicate-exception","duplicate-false","duplicate-partial","duplicate-invalid","exit-during","roundtrip","roundtrip-exception","register","wrong-group","close"})
    void nativeAndLedgerFaultsNeverAuthorizeTargetOwnershipOrRetry(String fault) throws Exception{
        try(var f=new Fixture()){
            f.bank.fault=fault;assertNull(f.transfer());assertTrue(f.port.status().quarantine());
            assertFalse(f.port.status().acknowledged());int count=f.bank.duplicates;
            assertNull(f.transfer());assertEquals(count,f.bank.duplicates);assertFalse(f.bank.localCloses.contains(900L));
            if(fault.endsWith("exception")){
                assertEquals(2,f.bank.processUses);assertEquals(1,f.bank.jobUses);assertTrue(f.port.status().outstanding());
            }else{assertEquals(0,f.bank.processUses);assertEquals(0,f.bank.jobUses);}
            if(fault.equals("close")){assertEquals(HandleTransferPort.CopyState.UNCERTAIN,f.port.status().temporary());assertEquals(List.of(404L),f.bank.localCloses);}
        }
    }
    @ParameterizedTest @ValueSource(ints={0,1,2,3,4,5,6})
    void foreignOldOrDuplicateAckNeverProducesASecondOwnershipConfirmation(int invalid) throws Exception{
        try(var f=new Fixture()){
            var d=f.transfer();var c=f.port.claim(d,f.bank.peer);var ack=f.port.targetAck(d,f.bank.peer,c);
            var bad=new HandleTransferPort.Ack(invalid==0?UUID.randomUUID():ack.transfer(),invalid==1?new Object():ack.attempt(),
                    invalid==2?new StartIntentStore.Binding(new UUID(99,99),BINDING.session(),BINDING.group()):BINDING,
                    invalid==3?new StartIntentStore.Anchor(2,f.proof.anchor().hash()):f.proof.anchor(),
                    invalid==4?new Peer(f.bank.peer.process(),new Object(),f.bank.peer.channel()):f.bank.peer,
                    f.bank.peer.process().handle(),invalid==5?2:1,c);
            if(invalid==6)assertTrue(f.port.acknowledge(ack));
            assertFalse(f.port.acknowledge(bad));assertTrue(f.port.status().quarantine());assertEquals(2,f.bank.duplicates);
        }
    }
    @Test void missingAckTargetDeathAndUnknownCClosureCannotBeHealedByCommunication() throws Exception{
        try(var f=new Fixture()){
            var d=f.transfer();var c=f.port.claim(d,f.bank.peer);var ack=f.port.targetAck(d,f.bank.peer,c);
            assertFalse(f.port.status().acknowledged());f.bank.alive=false;assertFalse(f.port.acknowledge(ack));
            assertTrue(f.port.status().quarantine());assertTrue(f.bank.remoteOpen);
        }
        try(var f=new Fixture()){
            var d=f.transfer();var c=f.port.claim(d,f.bank.peer);assertTrue(f.port.acknowledge(f.port.targetAck(d,f.bank.peer,c)));
            f.port.targetClosed(c,f.bank.peer,false);assertEquals(HandleTransferPort.CopyState.UNCERTAIN,f.port.status().target());
            assertFalse(f.port.acknowledge(f.port.targetAck(d,f.bank.peer,c)));assertTrue(f.bank.localCloses.stream().allMatch(v->v==404));
        }
    }
    @ParameterizedTest @ValueSource(strings={"block-copy","block-inspect"})
    void cleanupDoesNotWaitOnTransferMonitorAndLateResultCannotBeAcknowledged(String block) throws Exception{
        try(var f=new Fixture()){
            f.bank.fault=block;f.bank.gate=new Gate();var result=new AtomicReference<HandleTransferPort.Delivery>();var error=new AtomicReference<Throwable>();
            Thread t=new Thread(()->{try{result.set(f.transfer());}catch(Throwable e){error.set(e);}});t.setDaemon(true);t.start();
            try{assertTrue(f.bank.gate.entered.await(2,TimeUnit.SECONDS));f.port.cancel();assertTrue(f.port.status().outstanding());
                assertTrue(f.bank.jobUses>0);assertFalse(f.port.status().acknowledged());}
            finally{f.bank.gate.release.countDown();t.join(2500);if(t.isAlive()){t.interrupt();t.join(500);}}
            assertFalse(t.isAlive());assertNull(error.get());assertNull(result.get());assertTrue(f.port.status().revoked());
            assertEquals(0,f.bank.jobUses);assertEquals(0,f.bank.processUses);assertFalse(f.bank.localCloses.contains(900L));
        }
    }
    @Test void wrongSourceKindOrEndedTargetNeverCallsDuplicateHandle() throws Exception{
        try(var f=new Fixture()){
            assertNull(f.port.transfer(f.attempt,f.store,f.proof,new Resource(new Scope(),Kind.THREAD,1),f.bank.peer,f.bank.owner));assertEquals(0,f.bank.duplicates);
        }
        try(var f=new Fixture()){f.bank.alive=false;assertNull(f.transfer());assertEquals(0,f.bank.duplicates);}
    }
    @Test void sameNumericHandleValueInDifferentProcessesNeverBecomesAnSLocalClose() throws Exception{
        try(var f=new Fixture()){
            f.bank.remoteValue=303;var d=f.transfer();assertNotNull(d);
            var c=f.port.claim(d,f.bank.peer);assertTrue(f.port.acknowledge(f.port.targetAck(d,f.bank.peer,c)));
            assertEquals(List.of(404L),f.bank.localCloses);assertFalse(f.bank.sourceClosed);assertTrue(f.bank.remoteOpen);
        }
    }
    @Test void aConfirmedLocalRoundtripCopyMayHaveTheSameNumberAsTheRemoteCopy() throws Exception{
        try(var f=new Fixture()){
            f.bank.backValue=900;var d=f.transfer();assertNotNull(d);
            var c=f.port.claim(d,f.bank.peer);assertTrue(f.port.acknowledge(f.port.targetAck(d,f.bank.peer,c)));
            assertEquals(List.of(900L),f.bank.localCloses);assertTrue(f.bank.remoteOpen);assertFalse(f.bank.tempOpen);
            assertEquals(HandleTransferPort.CopyState.ADOPTED,f.port.status().target());
        }
    }
    @ParameterizedTest @ValueSource(longs={101,202,303})
    void invalidRoundtripAliasesNeverCloseTheHeldProcessOrSourceJob(long alias) throws Exception{
        try(var f=new Fixture()){
            f.bank.backValue=alias;assertNull(f.transfer());assertTrue(f.bank.localCloses.isEmpty());
            assertEquals(HandleTransferPort.CopyState.UNCERTAIN,f.port.status().temporary());
            assertTrue(f.port.status().quarantine());assertFalse(f.bank.sourceClosed);
        }
    }

    /** Bestehenden C-Kern/Empty-Fake wiederverwenden, aber KEINE zweite native Kopie erzeugen. */
    final class Receiver implements TransferCheckedConnection.Receiver {
        final EmptyCustodianBootstrapTest.Fixture f;
        final Bank bank;
        HandleTransferPort.Delivery delivery;HandleTransferPort transfer;HandleTransferPort.Ack ack;
        boolean omitAck, unknownClose;
        Receiver(EmptyCustodianBootstrapTest.Fixture f,Bank bank){this.f=f;this.bank=bank;}
        public void issued(HandleTransferPort.Delivery d,HandleTransferPort p){delivery=d;transfer=p;}
        public ProcessEnd inspectPeer(Peer p){return f.inspectPeer(p);}
        public Acceptance takeOver(Object attempt,StartIntentStore store,StartIntentStore.Proof proof,Resource source,Peer peer,Owner owner){
            f.attempt=attempt;f.source=source;
            OuterJobCustodian.Port cport=new OuterJobCustodian.Port(){
                public Control duplicateControl(Resource k,StartIntentStore.Binding b,int rights){f.cCopy=true;return f.control=transfer.claim(delivery,peer);}
                public ProcessEnd ownerEnd(Owner o,long r){return f.ownerEnd(o,r);}
                public Root duplicateRoot(Control c,Resource r,int rights){throw new AssertionError("no H");}
                public boolean terminate(Control c,long r){return f.terminate(c,r);}
                public End inspectEnd(Control c,Root r,long t){return f.inspectEnd(c,r,t);}
                public boolean closeRoot(Root r){throw new AssertionError("no H");}
                public boolean closeControl(Control c){boolean closed=f.closeControl(c);bank.remoteOpen=false;transfer.targetClosed(c,peer,closed&&!unknownClose);return closed&&!unknownClose;}
                public boolean closeOwner(Owner o){return f.closeOwner(o);}
            };
            f.custodian=new OuterJobCustodian(BINDING,proof.anchor(),()->store.valid(proof),owner,peer.principal(),peer.channel(),cport,()->f.now,1000,100,500);
            f.custodian.pollOwner();f.receipt=f.custodian.retain(BINDING,proof.anchor(),source);
            if(f.receipt==null)return null;
            ack=transfer.targetAck(delivery,peer,f.control);
            return new Acceptance(attempt,BINDING,proof.anchor(),peer,owner,f.control,2,true,1,f.custodian.live(f.receipt),f.ownerEnd(owner,100));
        }
        public HandleTransferPort.Ack transferAck(){return omitAck?null:ack;}
        public void requestStop(Object a,long d){f.requestStop(a,d);}
        public void controlStep(){f.controlStep();}
        public Inventory inventory(Object a,Resource s){return f.inventory(a,s);}
        public EndReceipt finishEmpty(Object a,Resource s){return f.finishEmpty(a,s);}
        public Release releaseSupervisor(Object a,Peer p){return f.releaseSupervisor(a,p);}
    }
    @ParameterizedTest @ValueSource(strings={"normal","missing-ack","extra-copy","unknown-close"})
    void bootstrapUsesNativeTransferReceiptAndPreservesItsExistingCleanupBarrier(String scenario) throws Exception{
        var previous=new EmptyCustodianBootstrapTest();previous.temporary=temporary;
        try(var f=previous.new Fixture()){
            var bank=new Bank();bank.owner=f.owner;bank.peer=f.peer;
            SourceLedger jobs=s->{bank.source=s;return bank.borrowTransferSource(s);};
            var transfer=new HandleTransferPort(bank,bank,jobs,f.owner,f.peer,BINDING,()->true);
            var receiver=new Receiver(f,bank);receiver.omitAck=scenario.equals("missing-ack");receiver.unknownClose=scenario.equals("unknown-close");
            var connection=new TransferCheckedConnection(transfer,receiver);
            KernelCalls kernel=(KernelCalls)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{KernelCalls.class,SourceLedger.class},(p,m,args)->{
                if(m.getName().equals("borrowTransferSource"))return jobs.borrowTransferSource((Resource)args[0]);
                try{return m.invoke(f.kernel,args);}catch(InvocationTargetException e){throw e.getCause();}
            });
            var bootstrap=new EmptyCustodianBootstrap(f.store,kernel,connection,f.peer,f.owner,()->f.now,1000,100);
            assertEquals(!scenario.equals("missing-ack"),bootstrap.begin());assertEquals(2,bank.duplicates);
            if(scenario.equals("extra-copy"))f.extraCopies=1;
            bootstrap.finishStep(!scenario.equals("missing-ack"));
            assertEquals(scenario.equals("normal"),bootstrap.report().technicalCompletion());
            assertFalse(bank.localCloses.contains(900L));assertEquals(List.of(404L),bank.localCloses);
            if(scenario.equals("normal"))assertEquals(HandleTransferPort.CopyState.CLOSED,transfer.status().target());
        }
    }
    @Test void nativeSourceMarkerCannotUseTheHistoricalUncheckedConnection() throws Exception{
        var previous=new EmptyCustodianBootstrapTest();previous.temporary=temporary;
        try(var f=previous.new Fixture()){
            KernelCalls marked=(KernelCalls)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{KernelCalls.class,SourceLedger.class},(p,m,a)->{throw new AssertionError("constructor must reject first");});
            assertThrows(IllegalArgumentException.class,()->new EmptyCustodianBootstrap(f.store,marked,f,f.peer,f.owner,()->f.now,1000,100));
        }
    }
}
