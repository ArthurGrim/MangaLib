package de.mangalib.windowsadapter;

import java.util.List;
import java.util.function.Consumer;
import static de.mangalib.windowsadapter.KernelCalls.*;

/** Derselbe prüfbare Leer-K-Port für native Verdrahtung und injizierte Fakes. */
final class ScFencedKernel implements KernelCalls,Win64TransferContract.SourceLedger {
    private final KernelCalls calls;
    private final ScRevocationFence fence;
    ScFencedKernel(KernelCalls calls,ScRevocationFence fence){this.calls=calls;this.fence=fence;}
    public Resource createJob(Scope scope){return fence.call(()->calls.createJob(scope));}
    public void configure(Resource job,Limits limits){fence.run(()->calls.configure(job,limits));}
    public Limits readLimits(Resource job){return calls.readLimits(job);}
    public Observation observe(Resource job,int capacity,long remaining){return calls.observe(job,capacity,remaining);}
    public Observation awaitEmptyJob(Resource job,int capacity,long remaining){return calls.awaitEmptyJob(job,capacity,remaining);}
    public void terminate(Resource job){calls.terminate(job);}
    public void close(Resource resource){calls.close(resource);}
    public Win64TransferContract.JobUse borrowTransferSource(Resource source){return ((Win64TransferContract.SourceLedger)calls).borrowTransferSource(source);}
    public void prepareLaunch(Resource job,Consumer<Resource> sink){throw new UnsupportedOperationException("empty-K-only");}
    public StartResult startSuspended(Resource job,List<Resource> p,Consumer<Resource> sink){throw new UnsupportedOperationException("empty-K-only");}
    public void resume(Resource thread){throw new UnsupportedOperationException("empty-K-only");}
    public Resource openProcess(Resource job,long pid){throw new UnsupportedOperationException("empty-K-only");}
    public Identity identity(Resource process){throw new UnsupportedOperationException("empty-K-only");}
    public boolean member(Resource process,Resource job){throw new UnsupportedOperationException("empty-K-only");}
    public boolean waitEnded(Resource process,long remaining){throw new UnsupportedOperationException("empty-K-only");}
}
