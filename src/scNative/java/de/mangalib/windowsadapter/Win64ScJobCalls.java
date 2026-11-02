package de.mangalib.windowsadapter;

import static de.mangalib.windowsadapter.KernelCalls.*;

/** Bestehendes Jobledger + bestehendes TerminateJobObject, kein Workerstart. */
final class Win64ScJobCalls extends Win64KernelCalls {
    private final Win64ScCalls.Bindings bindings;
    Win64ScJobCalls(Win64ScCalls.Bindings bindings){super(bindings.process(),"Windows x64",8);this.bindings=bindings;}
    @Override public void terminate(Resource job){
        try(JobUse held=useJob(job)){if(!bindings.process().TerminateJobObject(held.pointer,73))throw new Failure("sc-terminate-K",0);}
    }
    ScNativeCustodianPort.AcquiredControl adopt(long value,Win64TransferBindings raw){
        if(!raw.nonInherited(value))throw new Failure("sc-inherited-K",0);
        Resource local=registerTransferredControl(value);
        if(!new Limits(true,false,0,0,0).equals(readLimits(local)))throw new Failure("sc-control-limits",0);
        Observation empty=observe(local,1,1);
        if(!empty.complete() || !empty.stable() || empty.active()!=0 || empty.assigned()!=0 || empty.total()!=0 || !empty.pids().isEmpty())throw new Failure("sc-nonempty-K",0);
        return new ScNativeCustodianPort.AcquiredControl(local,OuterJobCustodian.JOB_RIGHTS,true,true);
    }
}
