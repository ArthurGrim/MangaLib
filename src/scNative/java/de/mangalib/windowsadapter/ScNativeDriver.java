package de.mangalib.windowsadapter;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.LongSupplier;
import static de.mangalib.windowsadapter.KernelCalls.*;
import static de.mangalib.windowsadapter.OuterJobCustodian.*;
import static de.mangalib.windowsadapter.EmptyCustodianBootstrap.*;

/** Vollständig isolierte native Rollenverdrahtung. Keine statische DLL-/Prozessinitialisierung. */
public final class ScNativeDriver implements ScExecutablePlatform.Driver {
    private ScNativeDriver(){ }
    public static ScExecutablePlatform.Driver create(){return new ScNativeDriver();}
    public int runSupervisor(){
        ScStartProvider provider=null;ScSupervisor supervisor=null;
        AtomicBoolean controlDone=new AtomicBoolean();
        var phase=ScBootstrapDiagnostics.Phase.MANIFEST;
        try{
            ScNativeManifest manifest=ScNativeManifest.read(true); // Frische Einmalfreigabe VOR DLL-Laden.
            long origin=System.nanoTime();LongSupplier clock=()->(System.nanoTime()-origin)/1_000_000;
            phase=ScBootstrapDiagnostics.Phase.NATIVE_LOAD;
            long deadline=15_000; // Einmalige Frist seit origin, nicht nach DLL-/Kontrollphasen erneuern.
            var bindings=Win64ScCalls.load();
            var fence=new ScRevocationFence(clock,deadline);
            var nativeCalls=new Win64ScCalls(bindings,manifest.launch(deadline),clock);
            var calls=new ScFencedCalls(nativeCalls,fence);
            var jobs=new Win64ScJobCalls(bindings);
            var gatedJobs=new ScFencedKernel(jobs,fence);
            var binding=new StartIntentStore.Binding(UUID.randomUUID(),manifest.session,UUID.randomUUID());
            phase=ScBootstrapDiagnostics.Phase.INTENT;
            try(var store=StartIntentStore.create(manifest.run.resolve("intent"),binding,p->{})){
                ScNativeWire[] wire={null};ScStartProvider[] own={null};
                var factory=new ScStartProvider.ConnectionFactory(){
                    public boolean handshake(Peer peer,Owner owner,long inherited,ScStartProvider.Calls os,StartIntentStore.Proof proof){
                        try{
                            nativeCalls.beginDiagnostics();
                            var envelope=new ScChannel.Binding(binding,proof.anchor(),peer.channel());
                            ScNativeWire.writeBoot(os.output(),new ScNativeWire.Boot(envelope,owner.identity(),peer.process().identity(),inherited));
                            wire[0]=new ScNativeWire(envelope,false,os.input(),os.output());
                            return wire[0].receive("BOUND").equals(binding.session().toString());
                        }catch(IOException e){return false;}
                    }
                    public TransferConfirmation connect(Peer peer,Owner owner,HeldProcessLedger ledger,ScStartProvider.Calls os,StartIntentStore.Proof proof){
                        Win64TransferContract.SourceLedger outsideK=source->{
                            var held=jobs.borrowTransferSource(source);
                            try(var c=ledger.borrow(peer.process().handle())){
                                IntByReference member=new IntByReference();
                                try(Memory ref=(Memory)member.getPointer()){
                                    if(!bindings.process().IsProcessInJob(new Pointer(c.value()),new Pointer(held.value()),member) || member.getValue()!=0)
                                        throw new IllegalStateException("sc-C-must-be-outside-K");
                                }
                                return held;
                            }catch(RuntimeException e){held.close();throw e;}
                        };
                        return new ScNativeConnection(wire[0],own[0],os,ledger,peer,owner,outsideK,binding,jobs::confirmedClosed,jobs::additionalOpenCopies,clock,deadline,fence);
                    }
                };
                provider=new ScStartProvider(calls,store,factory,clock,deadline,ScNativeManifest.JAVA);own[0]=provider;
                supervisor=new ScSupervisor(store,gatedJobs,provider,clock,deadline,2000,fence);
                final ScSupervisor live=supervisor;final ScStartProvider starts=provider;
                var proof=store.prepare();
                var ownS=provider.prepareOwner().identity();
                var outer=new ScOuterStop(manifest.run.getParent(),new ScOuterStop.Binding(binding,proof.anchor(),ownS),fence,()->{starts.cancel();live.stop();});
                outer.publish();
                Thread control=new Thread(()->{
                    while(!controlDone.get()){
                        outer.step();
                        if(clock.getAsLong()>=deadline){fence.revoke();starts.cancel();live.stop();return;}
                        java.util.concurrent.locks.LockSupport.parkNanos(1_000_000);
                    }
                },"sc-independent-outer-stop");control.setDaemon(true);control.start();
                AtomicBoolean finished=new AtomicBoolean();
                Thread watchdog=new Thread(()->{
                    while(!finished.get()){
                        if(clock.getAsLong()>=deadline){live.stop();starts.rollbackSuspended();return;}
                        java.util.concurrent.locks.LockSupport.parkNanos(1_000_000);
                    }
                },"sc-own-deadline");watchdog.setDaemon(true);watchdog.start();
                boolean ready=false,complete=false;
                phase=ScBootstrapDiagnostics.Phase.BOOTSTRAP;
                try{
                    while(!fence.allowed() && !fence.snapshot().revoked() && clock.getAsLong()<deadline)
                        java.util.concurrent.locks.LockSupport.parkNanos(1_000_000);
                    ready=fence.allowed() && supervisor.begin(proof) && fence.publishReady();
                    if(ready){supervisor.finish(true);complete=supervisor.result()!=null && supervisor.result().technicalCompletion();}
                    else {supervisor.finish(false);} // Vorhandene Cleanupbarrieren, kein neuer Normalpfad.
                    if(!complete){provider.cancel();provider.rollbackSuspended();if(!provider.releaseEnded())provider.releaseOwnerOnly();}
                }finally{
                    finished.set(true);controlDone.set(true);
                    long remaining=deadline-clock.getAsLong();if(remaining>0)watchdog.join(Math.min(1000,remaining));
                    remaining=deadline-clock.getAsLong();if(remaining>0)control.join(Math.min(1000,remaining));
                }
                if(!control.isAlive() && clock.getAsLong()<deadline)outer.finish();
                complete &= supervisor.result()!=null && supervisor.result().technicalCompletion()
                        && outer.closed() && !outer.failed() && fence.snapshot().outstanding()==0;
                phase=ScBootstrapDiagnostics.Phase.FINISH;
                Files.writeString(manifest.run.resolve("result.txt"),ScRunEvidence.format(manifest.session,ready,supervisor.result(),
                                provider.snapshot(),provider.acquiredIdentity(),provider.lastExitCode(),outer.closed() && !outer.failed(),fence.snapshot().outstanding()),
                        StandardOpenOption.CREATE_NEW);
                if(!complete)ScBootstrapDiagnostics.emit(System.err::println,ScExecutablePlatform.Role.SUPERVISOR,phase,null);
                return complete?0:78;
            }
        }catch(Exception | LinkageError error){
            ScBootstrapDiagnostics.emit(System.err::println,ScExecutablePlatform.Role.SUPERVISOR,phase,error);
            if(provider!=null)provider.cancel();if(supervisor!=null)supervisor.stop();return 78;
        }finally{controlDone.set(true);}
    }
    public int runCustodian(){
        try{
            var manifest=ScNativeManifest.read(false);
            var boot=ScNativeWire.readBoot(System.in);
            if(!boot.binding().intent().session().equals(manifest.session)
                    || !boot.owner().image().equalsIgnoreCase(ScNativeManifest.JAVA)
                    || !boot.child().image().equalsIgnoreCase(ScNativeManifest.JAVA))return 78;
            long origin=System.nanoTime();LongSupplier clock=()->(System.nanoTime()-origin)/1_000_000;
            var bindings=Win64ScCalls.load();long deadline=clock.getAsLong()+15_000;
            var raw=new Win64TransferBindings(bindings.process(),bindings.stdio(),bindings.symbols(),bindings.comparison());
            Set<Long> protectedHandles=new HashSet<>();
            for(int which:new int[]{-10,-11,-12}){
                long handle=Pointer.nativeValue(bindings.symbols().GetStdHandle(which));
                if(handle==0 || handle==-1 || !protectedHandles.add(handle))return 78;
                if(!bindings.stdio().SetHandleInformation(new Pointer(handle),1,0))return 78;
            }
            if(protectedHandles.contains(boot.ownerHandle()))return 78;
            var ownerState=raw.inspectProcess(boot.ownerHandle());
            if(!ownerState.alive() || !ownerState.identity().equals(boot.owner()))return 78;
            if(!bindings.stdio().SetHandleInformation(new Pointer(boot.ownerHandle()),1,0))return 78;
            // Tatsächlicher C-Selfhandle statt PID-only-Abgleich mit dem Prelude.
            var selfCalls=new Win64ScCalls(bindings,manifest.launch(deadline),clock);
            long self=selfCalls.selfCopy(PROCESS_RIGHTS);protectedHandles.add(self);
            var actualC=raw.inspectProcess(self);
            if(!actualC.alive() || !actualC.nonInherited() || !actualC.identity().equals(boot.child())
                    || actualC.identity().pid()!=raw.currentProcessId())return 78;
            protectedHandles.add(boot.ownerHandle());
            var processes=new HeldProcessLedger(raw,h->bindings.process().close(new Pointer(h)));
            Object ownerToken=processes.registerAcquired(boot.ownerHandle(),PROCESS_RIGHTS,boot.owner());
            Object selfToken=processes.registerAcquired(self,PROCESS_RIGHTS,boot.child());
            var owner=new Owner(ownerToken,boot.owner(),PROCESS_RIGHTS,true);
            var jobs=new Win64ScJobCalls(bindings);
            var wire=new ScNativeWire(boot.binding(),true,System.in,System.out);
            AtomicReference<ScNativeCustodianPort> adopted=new AtomicReference<>();
            AtomicReference<UUID> transfer=new AtomicReference<>();AtomicReference<Object> receipt=new AtomicReference<>();
            AtomicBoolean fence=new AtomicBoolean(),readerEnded=new AtomicBoolean(),inputFailed=new AtomicBoolean();
            OuterJobCustodian.Port forwarding=new Port(){
                private Port held(){return Objects.requireNonNull(adopted.get());}
                public Control duplicateControl(Resource r,StartIntentStore.Binding b,int rights){return held().duplicateControl(r,b,rights);}
                public ProcessEnd ownerEnd(Owner expected,long remaining){return expected==owner && remaining>0?processes.inspect(owner):null;}
                public Root duplicateRoot(Control c,Resource r,int rights){throw new UnsupportedOperationException("empty-K-only");}
                public boolean terminate(Control c,long remaining){return held().terminate(c,remaining);}
                public End inspectEnd(Control c,Root root,long remaining){return held().inspectEnd(c,root,remaining);}
                public boolean closeRoot(Root root){return false;}
                public boolean closeControl(Control c){return held().closeControl(c);}
                public boolean closeOwner(Owner o){return held().closeOwner(o);}
            };
            var core=new OuterJobCustodian(boot.binding().intent(),boot.binding().anchor(),
                    ()->StartIntentStore.inspect(manifest.run.resolve("intent"),boot.binding().intent(),boot.binding().anchor()).verified(),
                    owner,new Object(),boot.binding().channel(),forwarding,clock,deadline,2000,5000);
            core.pollOwner();if(core.report().owner()!=Signal.ALIVE)return 78;
            wire.send("BOUND",manifest.session.toString());
            Thread reader=new Thread(()->{
                try{
                    String grant=wire.receive("GRANT");String[] fields=grant.split(":",-1);
                    if(fields.length!=2)throw new IOException();UUID id=UUID.fromString(fields[0]);long value=Long.parseUnsignedLong(fields[1],16);
                    if(id.equals(new UUID(0,0)) || !id.toString().equals(fields[0]) || !Long.toUnsignedString(value,16).equals(fields[1])
                            || value==0 || value==-1 || protectedHandles.contains(value))throw new IOException();
                    core.pollOwner();if(core.report().owner()!=Signal.ALIVE || core.report().revoked())throw new IOException();
                    var acquired=jobs.adopt(value,raw);transfer.set(id);
                    var port=new ScNativeCustodianPort(jobs,processes,owner,acquired,boot.binding().intent(),
                            held->new Fence(held,fence.get(),true,true,readerEnded.get()));
                    if(!adopted.compareAndSet(null,port))throw new IOException();
                    Object token=core.retain(boot.binding().intent(),boot.binding().anchor(),acquired.localJob());receipt.set(token);
                    if(token==null || !core.live(token))throw new IOException();
                    wire.send("ACK",id.toString());
                    if(!wire.receive("STOP").equals(id.toString()))throw new IOException();core.stop();
                    if(!wire.receive("FENCE").equals(id.toString()))throw new IOException();wire.finishInput();fence.set(true);
                }catch(Exception e){inputFailed.set(true);core.communicationLost();}
                finally{readerEnded.set(true);}
            },"sc-bound-input");reader.setDaemon(true);reader.start();
            boolean returned=false;
            while(clock.getAsLong()<deadline){
                core.pollOwner();Report r=core.report();
                if(r.owner()==Signal.ENDED){core.stop();fence.set(true);if(receipt.get()==null)return 78;}
                if(r.revoked()){
                    core.terminateStep();
                    if(receipt.get()!=null && fence.get() && readerEnded.get()){
                        if(!returned){core.recordReturn(receipt.get(),null);returned=true;}
                        core.terminateStep();core.inspectEnd();core.closeResources();
                        r=core.report();
                        if(r.groupEnded() && r.resourcesClosed() && r.callsOutstanding()==0 && r.termination()==Termination.ACCEPTED){
                            if(inputFailed.get())return 78;
                            if(!processes.close(selfToken) || !processes.complete())return 78;
                            wire.send("GROUP_END",transfer.get().toString());wire.send("CLOSED",transfer.get().toString());wire.finishOutput();return 0;
                        }
                    }
                }
                java.util.concurrent.locks.LockSupport.parkNanos(1_000_000);
            }
            core.stop();return 78; // Kein positives Endsignal nach Deadline; main beendet nur den eigenen C.
        }catch(Exception | LinkageError error){return 78;}
    }
}
