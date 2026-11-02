package de.mangalib.windowsadapter;

import java.io.InputStream;
import java.io.OutputStream;
import static de.mangalib.windowsadapter.Win64TransferContract.*;

/** Bestehender S-Port mit derselben atomaren Lease für Create/Resume/Kopierphasen. */
final class ScFencedCalls implements ScStartProvider.Calls {
    private final ScStartProvider.Calls calls;
    final ScRevocationFence fence;
    ScFencedCalls(ScStartProvider.Calls calls,ScRevocationFence fence){this.calls=calls;this.fence=fence;}
    public boolean admissionAllowed(){return fence.allowed();}
    public void cancelAdmission(){fence.revoke();}
    public <T> T authorized(java.util.function.Supplier<T> phase){return fence.call(phase);}
    // Nur eigene S-Kontroll-Capability für den anfangs geschlossenen Bootstrap erwerben.
    public long selfCopy(int access){return calls.selfCopy(access);}
    public long processCopy(long p,int access){return fence.call(()->calls.processCopy(p,access));}
    public void prepare(long owner){fence.run(()->calls.prepare(owner));}
    public ScStartProvider.Created create(ScStartProvider.Acquisition register){return fence.call(()->calls.create(register));}
    public boolean resume(long thread){return fence.call(()->calls.resume(thread));}
    public Duplicate duplicate(long s,long h,long t,int access,boolean inherit,int flags){return fence.call(()->calls.duplicate(s,h,t,access,inherit,flags));}
    public long currentProcessId(){return calls.currentProcessId();}
    public ProcessState inspectProcess(long h){return calls.inspectProcess(h);}
    public boolean nonInherited(long h){return calls.nonInherited(h);}
    public boolean sameLocalObject(long a,long b){return calls.sameLocalObject(a,b);}
    public boolean terminateProcess(long p){return calls.terminateProcess(p);}
    public boolean close(long h){return calls.close(h);}
    public int exitCode(long h){return calls.exitCode(h);}
    public InputStream input(){return calls.input();}
    public OutputStream output(){return calls.output();}
    public boolean closePipes(){return calls.closePipes();}
    public boolean preparationSettled(){return fence.snapshot().outstanding()==0 && calls.preparationSettled();}
}
