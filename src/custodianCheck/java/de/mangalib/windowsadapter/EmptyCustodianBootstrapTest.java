package de.mangalib.windowsadapter;

import java.io.IOException;
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
import static org.junit.jupiter.api.Assertions.*;

/** Kein echter S/C-Prozess: alle Handles, Signale, Kanäle und Vererbung sind Fakes. */
@Timeout(10)
class EmptyCustodianBootstrapTest {
    @TempDir Path temporary;
    static final StartIntentStore.Binding BINDING = new StartIntentStore.Binding(new UUID(11,11), new UUID(12,12), new UUID(13,13));
    static final Identity S = new Identity(51,101,"C:/synthetic/S.exe"), C = new Identity(52,102,"C:/synthetic/C.exe");
    static final class Gate {
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        void pause() {
            entered.countDown();
            try { if (!release.await(3,TimeUnit.SECONDS)) throw new IllegalStateException("fake-timeout"); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("fake-interrupted"); }
        }
    }
    final class Fixture implements AutoCloseable, Connection, OuterJobCustodian.Port {
        final List<String> events = new ArrayList<>();
        final Object sh = new Object(), ch = new Object(), ck = new Object();
        final Owner owner = new Owner(sh,S,PROCESS_RIGHTS,true);
        final Peer peer = new Peer(new Owner(ch,C,PROCESS_RIGHTS,true),new Object(),new UUID(14,14));
        final FakeKernelCalls fake = new FakeKernelCalls();
        final StartIntentStore store;
        final EmptyCustodianBootstrap bootstrap;
        final KernelCalls kernel;
        OuterJobCustodian custodian;
        Object attempt, receipt;
        Resource source;
        Control control;
        Acceptance answer;
        boolean cCopy, sAlive = true, cAlive = true, transferBusy, kernelBusy, channelClosed, ownerCopyClosed, emptyReported;
        boolean missingEnd, killFails, unknownClose, releaseFails;
        int extraCopies, lastCloseKills, terminations, releases;
        long now;
        String bad = "", block = "";
        Gate gate;
        Fixture(String fault) throws IOException {
            bad = fault;
            store = StartIntentStore.create(temporary.resolve(UUID.randomUUID().toString()),BINDING,p -> {
                events.add("intent-"+p.name()); if (bad.equals("intent") && p == StartIntentStore.Point.FORCED) throw new IOException("fake-crash");
            });
            kernel = (KernelCalls) Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{KernelCalls.class},(p,m,args) -> {
                String name = m.getName();
                assertTrue(List.of("createJob","configure","readLimits","observe","close").contains(name),"H/J/W path forbidden: "+name);
                events.add(name); kernelBusy = true;
                try {
                    if (name.equals("createJob")) assertTrue(events.contains("intent-PUBLISHED"));
                    if (name.equals(block) && gate != null) gate.pause();
                    Object result;
                    try { result = m.invoke(fake,args); } catch (InvocationTargetException failure) { throw failure.getCause(); }
                    if (name.equals("close")) { if (!cCopy && extraCopies == 0) lastCloseKills++; if (unknownClose) throw new Failure("fake-unknown-close",0); }
                    return result;
                } finally { kernelBusy = false; }
            });
            bootstrap = new EmptyCustodianBootstrap(store,kernel,this,peer,owner,()->now,1000,100);
        }
        Fixture() throws IOException { this(""); }
        boolean sCopy() { return fake.lastJob != null && !fake.closed.contains(fake.lastJob); }
        public ProcessEnd inspectPeer(Peer expected) {
            assertSame(peer,expected); events.add("peer");
            return new ProcessEnd(ch,bad.equals("peer-id") ? new Identity(52,999,C.image()) : C,
                    bad.equals("peer-dead") || !cAlive ? Signal.ENDED : Signal.ALIVE);
        }
        public Acceptance takeOver(Object token,StartIntentStore supplied,StartIntentStore.Proof proof,Resource k,Peer expected,Owner s) {
            assertSame(store,supplied); assertTrue(store.valid(proof)); assertSame(fake.lastJob,k);
            assertTrue(events.indexOf("configure") < events.indexOf("readLimits")); assertTrue(events.contains("observe"));
            attempt = token; source = k; transferBusy = true;
            try {
                custodian = new OuterJobCustodian(BINDING,proof.anchor(),()->store.valid(proof),owner,peer.principal(),peer.channel(),this,()->now,1000,100,500);
                custodian.pollOwner(); receipt = custodian.retain(BINDING,proof.anchor(),source);
                if (block.equals("transfer") && gate != null) gate.pause();
                if (bad.equals("C-transfer")) { cAlive = false; custodian.custodianLost(); }
                if (bad.equals("ack-missing") || receipt == null) return null;
                answer = new Acceptance(bad.equals("ack-attempt") ? new Object() : token,
                        bad.equals("ack-ticket") ? new StartIntentStore.Binding(new UUID(99,99),BINDING.session(),BINDING.group()) : BINDING,
                        bad.equals("ack-revision") ? new StartIntentStore.Anchor(2,proof.anchor().hash()) : proof.anchor(),
                        bad.equals("ack-C") ? new Peer(peer.process(),peer.principal(),peer.channel()) : peer,
                        bad.equals("ack-owner") ? new Owner(sh,new Identity(51,999,S.image()),PROCESS_RIGHTS,true) : s,
                        control,bad.equals("copies") ? 3 : 2,!bad.equals("inherit"),bad.equals("sequence") ? 2 : 1,
                        custodian.live(receipt),ownerEnd(owner,100));
                events.add("ack"); return answer;
            } finally { transferBusy = false; }
        }
        public void requestStop(Object token,long deadline) { if (attempt == null) attempt = token; assertSame(attempt,token); if (custodian != null) custodian.stopBy(deadline); }
        public void controlStep() { if (custodian != null) custodian.terminateStep(); }
        public Inventory inventory(Object token,Resource k) { return new Inventory(token,k,!bad.equals("inventory"),sCopy(),cCopy,extraCopies,!bad.equals("inherit")); }
        public EndReceipt finishEmpty(Object token,Resource k) {
            assertSame(attempt,token);
            if (custodian == null) return new EndReceipt(token,k,peer,new Report(true,sAlive?Signal.ALIVE:Signal.ENDED,true,Termination.NONE,!sCopy(),!cCopy,true,0,0,100));
            custodian.pollOwner();
            if (receipt != null && !emptyReported) {
                emptyReported = true;
                custodian.recordReturn(receipt,null); // Einmalige Meldung: statisch kein CreateProcess-Pfad.
            }
            custodian.terminateStep(); custodian.inspectEnd(); custodian.closeResources();
            return new EndReceipt(token,k,peer,custodian.report());
        }
        public Release releaseSupervisor(Object token,Peer expected) {
            releases++; assertFalse(sCopy()); assertFalse(cCopy); assertEquals(0,extraCopies);
            cAlive = false; channelClosed = true;
            return new Release(token,expected,new ProcessEnd(ch,bad.equals("release-id")?S:C,
                    bad.equals("release-alive")?Signal.ALIVE:Signal.ENDED),!releaseFails,!releaseFails);
        }
        public Control duplicateControl(Resource s,StartIntentStore.Binding binding,int rights) {
            events.add("duplicate"); assertSame(source,s); assertEquals(JOB_RIGHTS,rights); assertFalse(cCopy); cCopy = true;
            if (bad.equals("duplicate")) throw new Failure("fake-unknown-duplicate",0);
            control = new Control(ck,s,binding,rights,true,true,true,true); return control;
        }
        public ProcessEnd ownerEnd(Owner expected,long budget) {
            assertSame(owner,expected); assertTrue(budget > 0);
            return new ProcessEnd(sh,bad.equals("owner-id") ? C : S,sAlive?Signal.ALIVE:Signal.ENDED);
        }
        public Root duplicateRoot(Control c,Resource r,int rights) { throw new AssertionError("no H/root transfer in empty stage"); }
        public boolean terminate(Control c,long budget) { assertSame(control,c); assertTrue(budget > 0); terminations++; return !killFails; }
        public End inspectEnd(Control c,Root root,long budget) {
            assertSame(control,c); assertNull(root);
            return new End(c,new Observation(!missingEnd,true,0,List.of(),0,0),
                    new Fence(c,!sCopy() && extraCopies == 0,!transferBusy && !kernelBusy,true,true),null);
        }
        public boolean closeRoot(Root r) { throw new AssertionError("no root in empty stage"); }
        public boolean closeControl(Control c) { assertSame(control,c); assertTrue(cCopy); cCopy = false; if (!sCopy() && extraCopies == 0) lastCloseKills++; return true; }
        public boolean closeOwner(Owner s) { assertSame(owner,s); ownerCopyClosed = true; return true; }
        void ownerExit() { sAlive = false; if (sCopy()) { fake.closed.add(fake.lastJob); if (!cCopy && extraCopies == 0) lastCloseKills++; } if (custodian != null) custodian.pollOwner(); }
        void custodianExit() { cAlive = false; if (cCopy) { cCopy = false; if (!sCopy() && extraCopies == 0) lastCloseKills++; } if (custodian != null) custodian.custodianLost(); }
        @Override public void close() throws IOException { store.close(); }
    }
    @Test void orderedBootstrapAndNormalEmptyCompletionKeepOwnershipAndEndProofsSeparate() throws Exception {
        try (var f = new Fixture()) {
            assertTrue(f.bootstrap.begin()); assertTrue(f.bootstrap.report().ready()); assertEquals(2,(f.sCopy()?1:0)+(f.cCopy?1:0));
            assertTrue(f.events.indexOf("intent-PUBLISHED") < f.events.indexOf("createJob"));
            assertTrue(f.events.indexOf("readLimits") < f.events.indexOf("duplicate"));
            assertTrue(f.events.indexOf("duplicate") < f.events.indexOf("ack"));
            f.bootstrap.finishStep(true); assertTrue(f.bootstrap.report().technicalCompletion());
            assertTrue(f.bootstrap.report().groupEnded()); assertTrue(f.ownerCopyClosed); assertTrue(f.channelClosed);
            assertEquals(1,f.lastCloseKills); assertTrue(f.bootstrap.report().quarantine());
            f.bootstrap.finishStep(true); assertEquals(1,f.releases); assertFalse(f.bootstrap.begin());
        }
    }
    @Test void closingEitherCopyAloneNeverProvesKillOnCloseOrGroupEnd() throws Exception {
        try (var f = new Fixture()) {
            assertTrue(f.bootstrap.begin()); f.fake.close(f.fake.lastJob);
            assertTrue(f.cCopy); assertEquals(0,f.lastCloseKills); assertFalse(f.bootstrap.report().groupEnded());
        }
        try (var f = new Fixture()) {
            assertTrue(f.bootstrap.begin()); f.custodianExit();
            assertTrue(f.sCopy()); assertEquals(0,f.lastCloseKills); assertFalse(f.bootstrap.report().groupEnded());
            f.bootstrap.abort(); f.bootstrap.finishStep(false); assertFalse(f.bootstrap.report().technicalCompletion());
        }
    }
    @ParameterizedTest @ValueSource(strings={"intent","peer-id","peer-dead","owner-id","duplicate","C-transfer","ack-missing","ack-attempt","ack-ticket","ack-revision","ack-C","ack-owner","copies","inherit","sequence"})
    void invalidIntentIdentityTransferOrReadinessNeverBecomesReady(String fault) throws Exception {
        try (var f = new Fixture(fault)) {
            assertFalse(f.bootstrap.begin()); assertFalse(f.bootstrap.report().ready()); assertTrue(f.bootstrap.report().quarantine());
            f.bootstrap.finishStep(false); assertFalse(f.bootstrap.report().technicalCompletion());
            assertFalse(f.bootstrap.begin());
        }
    }
    @ParameterizedTest @ValueSource(strings={"CREATE","CONFIGURE","READ_LIMITS","MISMATCH_LIMITS","OBSERVE"})
    void eachKernelBootstrapFailurePreservesLedgerAndDeniesReadiness(String fault) throws Exception {
        try (var f = new Fixture()) {
            f.fake.faults.add(FakeKernelCalls.Fault.valueOf(fault)); assertFalse(f.bootstrap.begin());
            assertFalse(f.cCopy); f.bootstrap.finishStep(false); assertFalse(f.bootstrap.report().technicalCompletion());
            assertTrue(f.bootstrap.report().quarantine());
        }
    }
    @Test void oldOrOutOfOrderAndDuplicateAckCannotGrantReadiness() throws Exception {
        try (var f = new Fixture()) { assertFalse(f.bootstrap.accept(null)); assertFalse(f.bootstrap.begin()); }
        try (var f = new Fixture()) {
            assertTrue(f.bootstrap.begin()); assertFalse(f.bootstrap.accept(f.answer));
            assertFalse(f.bootstrap.report().ready()); assertTrue(f.bootstrap.report().quarantine());
        }
    }
    @Test void ownerExitDuringOrAfterTransferIsNotCommunicationLoss() throws Exception {
        try (var f = new Fixture()) {
            assertTrue(f.bootstrap.begin()); f.ownerExit(); f.bootstrap.abort(); f.bootstrap.controlStep();
            assertEquals(Signal.ENDED,f.custodian.report().owner()); assertTrue(f.terminations > 0);
            f.bootstrap.finishStep(false); assertTrue(f.bootstrap.report().groupEnded());
            assertTrue(f.bootstrap.report().custodianResourcesClosed()); assertFalse(f.bootstrap.report().technicalCompletion());
        }
        try (var f = new Fixture()) {
            assertTrue(f.bootstrap.begin()); f.bootstrap.abort(); f.bootstrap.controlStep();
            assertEquals(Signal.ALIVE,f.custodian.report().owner()); assertTrue(f.sAlive);
        }
    }
    @ParameterizedTest @ValueSource(strings={"createJob","configure","readLimits","observe","transfer"})
    void blockedBootstrapAllowsAbortAndCapturesLateOwnershipWithoutReadiness(String stage) throws Exception {
        try (var f = new Fixture()) {
            f.block = stage; f.gate = new Gate(); var result = new AtomicReference<Boolean>(); var error = new AtomicReference<Throwable>();
            Thread t = new Thread(()->{try{result.set(f.bootstrap.begin());}catch(Throwable e){error.set(e);}}); t.setDaemon(true); t.start();
            try {
                assertTrue(f.gate.entered.await(2,TimeUnit.SECONDS)); f.bootstrap.abort(); f.bootstrap.controlStep();
                assertTrue(f.bootstrap.report().operationOutstanding()); assertFalse(f.bootstrap.report().ready());
                if (stage.equals("transfer")) assertEquals(1,f.terminations); else assertEquals(0,f.terminations);
                f.bootstrap.finishStep(false); assertFalse(f.bootstrap.report().technicalCompletion());
            } finally { f.gate.release.countDown(); t.join(2500); if(t.isAlive()){t.interrupt();t.join(500);} }
            assertFalse(t.isAlive()); assertNull(error.get()); assertEquals(Boolean.FALSE,result.get());
        }
    }
    @ParameterizedTest @ValueSource(strings={"extra-copy","inventory","group-end","close","release","deadline"})
    void incompleteCleanupNeverReportsTechnicalCompletion(String fault) throws Exception {
        try (var f = new Fixture()) {
            assertTrue(f.bootstrap.begin());
            if(fault.equals("extra-copy"))f.extraCopies=1;
            if(fault.equals("inventory"))f.bad="inventory";
            if(fault.equals("group-end"))f.missingEnd=true;
            if(fault.equals("close"))f.unknownClose=true;
            if(fault.equals("release"))f.releaseFails=true;
            if(fault.equals("deadline")){f.bootstrap.abort();f.now=100;}
            f.bootstrap.finishStep(true); assertFalse(f.bootstrap.report().technicalCompletion());
            if(fault.equals("close"))assertEquals(Ownership.UNCERTAIN,f.bootstrap.report().supervisorHandle());
            long closes=f.fake.events.stream().filter(e->e.operation().equals("close")).count();
            f.bootstrap.finishStep(true); assertEquals(closes,f.fake.events.stream().filter(e->e.operation().equals("close")).count());
        }
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void confirmedPeerOrOwnerExitDuringTransferNeverProducesReadiness(boolean ownerExit) throws Exception {
        try(var f=new Fixture()){
            f.block="transfer";f.gate=new Gate();var result=new AtomicReference<Boolean>();var error=new AtomicReference<Throwable>();
            Thread t=new Thread(()->{try{result.set(f.bootstrap.begin());}catch(Throwable e){error.set(e);}});t.setDaemon(true);t.start();
            try{assertTrue(f.gate.entered.await(2,TimeUnit.SECONDS));if(ownerExit)f.ownerExit();else f.custodianExit();}
            finally{f.gate.release.countDown();t.join(2500);if(t.isAlive()){t.interrupt();t.join(500);}}
            assertFalse(t.isAlive());assertNull(error.get());assertEquals(Boolean.FALSE,result.get());
            assertFalse(f.bootstrap.report().ready());assertTrue(f.bootstrap.report().quarantine());
        }
    }
    @Test void nonemptyOrForeignJobCannotReachHandleTransfer() throws Exception {
        try(var f=new Fixture()){
            f.fake.observations.add(new Observation(true,true,1,List.of(999L),1,1));
            assertFalse(f.bootstrap.begin());assertFalse(f.cCopy);
        }
        try(var f=new Fixture()){
            f.fake.substituteJob=new Resource(new Scope(),Kind.JOB,1);
            assertFalse(f.bootstrap.begin());f.bootstrap.finishStep(false);
            assertFalse(f.cCopy);assertTrue(f.fake.closed.isEmpty());assertFalse(f.bootstrap.report().technicalCompletion());
        }
    }
    @Test void sharedDeadlineCanOnlyBeShortenedAndTerminationFailureDoesNotProveGroupEnd() throws Exception {
        try(var f=new Fixture()){
            assertTrue(f.bootstrap.begin());f.killFails=true;f.bootstrap.abort();f.bootstrap.controlStep();
            assertEquals(Termination.FAILED,f.custodian.report().termination());assertFalse(f.bootstrap.report().groupEnded());
            f.now=20;f.bootstrap.abort();assertEquals(100,f.custodian.report().cleanupDeadline());
            f.custodian.stopBy(50);f.custodian.stopBy(500);assertEquals(50,f.custodian.report().cleanupDeadline());
        }
    }
    @ParameterizedTest @ValueSource(strings={"peer-dead","peer-id"})
    void peerFailureAfterReadinessRevokesOnlyTheBoundSession(String fault) throws Exception {
        try(var f=new Fixture()){
            assertTrue(f.bootstrap.begin());f.bad=fault;assertFalse(f.bootstrap.pollPeer());
            assertFalse(f.bootstrap.report().ready());assertTrue(f.bootstrap.report().quarantine());
            assertEquals(Signal.ALIVE,f.custodian.report().owner());
        }
    }
    @ParameterizedTest @ValueSource(strings={"release-id","release-alive"})
    void wrongCustodianEndProofPreventsTerminalJournalAndTechnicalCompletion(String fault) throws Exception {
        try(var f=new Fixture()){
            assertTrue(f.bootstrap.begin());f.bad=fault;f.bootstrap.finishStep(true);
            assertFalse(f.bootstrap.report().supervisorResourcesClosed());assertFalse(f.bootstrap.report().journalTerminal());
            assertFalse(f.bootstrap.report().technicalCompletion());assertEquals(1,f.releases);
            f.bootstrap.finishStep(true);assertEquals(1,f.releases);
        }
    }
    @Test void twoHandleCopiesOfOneProcessAreNotAnIndependentCustodian() throws Exception {
        try(var f=new Fixture()){
            Peer sameProcess=new Peer(new Owner(new Object(),S,PROCESS_RIGHTS,true),new Object(),new UUID(15,15));
            assertThrows(IllegalArgumentException.class,()->new EmptyCustodianBootstrap(f.store,f.kernel,f,sameProcess,f.owner,()->f.now,1000,100));
            assertNull(f.fake.lastJob);assertFalse(f.cCopy);
        }
    }
}
