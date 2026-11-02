package de.mangalib.windowsadapter;

import java.util.Objects;
import static de.mangalib.windowsadapter.KernelCalls.*;
import static de.mangalib.windowsadapter.OuterJobCustodian.*;

/** Kalte C-Verdrahtung bestehender Kernel-/Prozessports. Kein DLL-Lader, PID-Lookup oder Start. */
final class ScNativeCustodianPort implements OuterJobCustodian.Port {
    interface EmptyFence { Fence read(Control held); }
    record AcquiredControl(Resource localJob,int rights,boolean sameTransferredObject,boolean nonInherited) { }
    private final KernelCalls kernel;
    private final HeldProcessLedger processes;
    private final Owner owner;
    private final Resource localJob;
    private final StartIntentStore.Binding binding;
    private final EmptyFence fence;
    private final Object controlToken=new Object();
    private Control control;
    private boolean claimed, closing, closed;
    ScNativeCustodianPort(KernelCalls kernel,HeldProcessLedger processes,Owner owner,AcquiredControl acquired,
                          StartIntentStore.Binding binding,EmptyFence fence) {
        this.kernel=Objects.requireNonNull(kernel); this.processes=Objects.requireNonNull(processes);
        this.owner=Objects.requireNonNull(owner); this.localJob=Objects.requireNonNull(acquired).localJob();
        this.binding=Objects.requireNonNull(binding); this.fence=Objects.requireNonNull(fence);
        if(localJob==null || localJob.kind!=Kind.JOB || acquired.rights()!=JOB_RIGHTS
                || !acquired.sameTransferredObject() || !acquired.nonInherited()) throw new IllegalArgumentException("sc-local-job");
    }
    public Control duplicateControl(Resource source,StartIntentStore.Binding expected,int rights) {
        synchronized(this) {
            if(claimed || source!=localJob || !binding.equals(expected) || rights!=JOB_RIGHTS) return null;
            claimed=true;
        }
        // Keine zweite Duplikation: native Acquisition muss diese reduzierte Kopie bereits registriert haben.
        if(!new Limits(true,false,0,0,0).equals(kernel.readLimits(localJob))) return null;
        Control held=new Control(controlToken,localJob,binding,JOB_RIGHTS,true,true,true,true);
        synchronized(this) { control=held; } return held;
    }
    public ProcessEnd ownerEnd(Owner expected,long remaining) {
        if(expected!=owner || remaining<=0) return null;
        return processes.inspect(owner);
    }
    public Root duplicateRoot(Control held,Resource source,int rights) { throw new UnsupportedOperationException("empty-K-only"); }
    public boolean terminate(Control held,long remaining) {
        require(held); if(remaining<=0) return false; kernel.terminate(localJob); return true;
    }
    public End inspectEnd(Control held,Root root,long remaining) {
        require(held); if(root!=null || remaining<=0) return null;
        return new End(held,kernel.observe(localJob,1,remaining),fence.read(held),null);
    }
    public boolean closeRoot(Root root) { return false; }
    public boolean closeControl(Control held) {
        synchronized(this) { require(held); if(closing || closed) return false; closing=true; }
        // closing bleibt auch bei unbekanntem Fehler gesetzt; kein zweiter Close.
        kernel.close(localJob); synchronized(this) { closed=true; } return true;
    }
    public boolean closeOwner(Owner expected) { return expected==owner && processes.close(owner.handle()); }
    private synchronized void require(Control held) {
        if(held==null || held!=control || closed) throw new IllegalStateException("sc-control-not-owned");
    }
}
