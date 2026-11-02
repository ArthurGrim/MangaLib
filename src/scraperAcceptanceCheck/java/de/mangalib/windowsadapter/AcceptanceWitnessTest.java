package de.mangalib.windowsadapter;

import org.junit.jupiter.api.Test;
import java.util.List;
import static de.mangalib.windowsadapter.KernelCalls.*;
import static org.junit.jupiter.api.Assertions.*;

class AcceptanceWitnessTest {
    @Test void wrapperCannotConfirmResourcesWhileExchangeOrReadHandlesRemainOutstanding(){
        var k=new FakeKernelCalls();var w=new AcceptanceWitness();
        var base=new WorkerJobOwner.Session(){
            public KernelCalls kernel(){return k;}public String executable(){return "C:/fake/java.exe";}
            public WorkerWire.Reply exchange(WorkerWire.Request r,Runnable bound){throw new AssertionError("not part of this fake");}
            public void cancelStarts(){}public boolean settled(){return true;}public boolean channelEnded(){return true;}
            public boolean closeIo(long n){return true;}public boolean resourcesClosed(){return true;}public int exitCode(Resource p){return 0;}
        };
        var decorated=w.decorate(base);assertTrue(decorated.settled());
        assertNull(decorated.kernel().prepareCleanupControl(k.createJob(new Scope()))); // Fehlender Kontrollpfad darf nicht durch Diagnostik verdeckt werden.
        w.exchangeDone=false;
        assertFalse(decorated.settled());assertFalse(decorated.resourcesClosed());assertFalse(decorated.closeIo(1));
        w.exchangeDone=true;w.open.set(1);assertFalse(decorated.resourcesClosed());w.open.set(0);assertTrue(decorated.resourcesClosed());
    }
    @Test void nativeIdentityAndMembershipAreBothRequiredAndOwnHandlesClose(){
        var k=new FakeKernelCalls();var w=new AcceptanceWitness();w.job=k.createJob(new Scope());
        k.addProcess(w.job,"C:/known/firefox.exe");w.sample(k);
        assertTrue(w.observed("C:/known/firefox.exe"));assertEquals(1,w.identities.size());assertEquals(0,w.open.get());
        assertEquals(1,k.count("close"));assertEquals(0,k.count("terminate"));
    }
    @Test void foreignProcessWithMatchingNameIsNeverAccepted(){
        var k=new FakeKernelCalls();var w=new AcceptanceWitness();w.job=k.createJob(new Scope());
        var foreign=k.createJob(new Scope());var p=k.addProcess(foreign,"C:/known/firefox.exe");long pid=k.identity(p).pid();
        k.observations.add(new Observation(true,true,1,List.of(pid),1,1));w.sample(k);
        assertTrue(w.identities.isEmpty());assertEquals(0,k.count("terminate"));assertEquals(0,w.open.get());
    }
    @Test void unstableOrIncompleteSnapshotsAreNotIdentityEvidence(){
        var k=new FakeKernelCalls();var w=new AcceptanceWitness();w.job=k.createJob(new Scope());k.addProcess(w.job,"C:/firefox.exe");
        k.observations.add(new Observation(true,false,1,List.of(100L),1,1));w.sample(k);
        assertTrue(w.identities.isEmpty());assertEquals(0,k.count("open-process"));
    }
    @Test void failedIdentityDoesNotLeakTheAcquiredReadHandle(){
        var k=new FakeKernelCalls();var w=new AcceptanceWitness();w.job=k.createJob(new Scope());k.addProcess(w.job,"C:/firefox.exe");
        k.faults.add(FakeKernelCalls.Fault.IDENTITY);w.sample(k);
        assertTrue(w.identities.isEmpty());assertEquals(1,k.count("close"));assertEquals(0,w.open.get());assertEquals(1,w.samplingFailures);
    }
    @Test void failedCloseRemainsUnconfirmed(){
        var k=new FakeKernelCalls();var w=new AcceptanceWitness();w.job=k.createJob(new Scope());k.addProcess(w.job,"C:/firefox.exe");
        k.faults.add(FakeKernelCalls.Fault.CLOSE_PROCESS);w.sample(k);assertEquals(1,w.open.get());assertEquals(1,w.samplingFailures);
    }
}
