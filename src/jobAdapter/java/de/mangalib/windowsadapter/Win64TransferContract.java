package de.mangalib.windowsadapter;

import static de.mangalib.windowsadapter.KernelCalls.*;

/** JDK-Portformen, keine JNA-Initialisierung oder aus IPC rekonstruierte Capability. */
interface Win64TransferContract {
    int PROCESS_ACCESS = 0x100000 | 0x1000 | 0x40; // SYNCHRONIZE | QUERY_LIMITED_INFORMATION | DUP_HANDLE.
    record ProcessState(Identity identity, boolean alive, boolean nonInherited) { }
    record Duplicate(boolean success, long value) { }
    interface Borrow extends AutoCloseable { @Override void close(); }
    interface ProcessUse extends Borrow {
        Object capability();
        long value();
        int access(); // Bestätigte Erzeugungs-/Duplikationsprovenienz, kein erfundener GetGrantedAccess-Aufruf.
    }
    interface ProcessLedger { ProcessUse borrow(Object heldCapability); }
    interface LocalCopy {
        Resource resource();
        long value();
        void close(); // Bestehendes lokales Kernelledger: UNKNOWN wird nicht erneut geschlossen.
    }
    interface JobUse extends Borrow {
        Resource resource();
        long value();
        Limits limits();
        LocalCopy registerLocalCopy(long value);
    }
    interface SourceLedger { JobUse borrowTransferSource(Resource source); }
    interface Raw {
        long currentProcessId();
        ProcessState inspectProcess(long heldProcess);
        boolean nonInherited(long localHandle);
        Duplicate duplicate(long sourceProcess, long sourceHandle, long targetProcess, int access, boolean inherit, int options);
        boolean sameLocalObject(long first, long second);
    }
}
