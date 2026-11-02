package de.mangalib.windowsadapter;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Kalte injizierte ABI-Brücke. Kein Loader/main; neue Tests rufen sie NICHT auf. */
final class Win64TransferBindings implements Win64TransferContract.Raw {
    interface CopyFunctions extends StdCallLibrary {
        int GetCurrentProcessId();
        boolean DuplicateHandle(Pointer sourceProcess, Pointer sourceHandle, Pointer targetProcess,
                                Pointer targetHandle, int desiredAccess, boolean inherit, int options);
    }
    interface ComparisonFunctions extends StdCallLibrary {
        boolean CompareObjectHandles(Pointer first, Pointer second);
    }
    // Kompatibler injizierter Fakevertrag; NIEMALS als Ein-DLL-Interface laden.
    interface Functions extends CopyFunctions, ComparisonFunctions { }
    private final Win64ProcessKernelCalls.ProcessRawApi processes;
    private final Win64StdioChannels.Api flags;
    private final CopyFunctions copies;
    private final ComparisonFunctions comparison;
    private Memory uncertainOutput;
    Win64TransferBindings(Win64ProcessKernelCalls.ProcessRawApi processes, Win64StdioChannels.Api flags, Functions copies) {
        this(processes, flags, copies, copies);
    }
    Win64TransferBindings(Win64ProcessKernelCalls.ProcessRawApi processes, Win64StdioChannels.Api flags,
                          CopyFunctions copies, ComparisonFunctions comparison) {
        this.processes = Objects.requireNonNull(processes); this.flags = Objects.requireNonNull(flags);
        this.copies = Objects.requireNonNull(copies); this.comparison = Objects.requireNonNull(comparison);
    }
    public long currentProcessId() { return Integer.toUnsignedLong(copies.GetCurrentProcessId()); }
    public Win64TransferContract.ProcessState inspectProcess(long value) {
        Pointer held = new Pointer(value);
        int pid = processes.GetProcessId(held);
        if (pid == 0) throw new KernelCalls.Failure("transfer-process-type-or-id",0);
        try (Memory birth = new Memory(8); Memory exit = new Memory(8); Memory kernel = new Memory(8);
             Memory user = new Memory(8); Memory image = new Memory(1024)) {
            IntByReference length = new IntByReference(512);
            try (Memory reference = (Memory) length.getPointer()) {
                if (!processes.GetProcessTimes(held,birth,exit,kernel,user)
                        || !processes.QueryFullProcessImageNameW(held,0,image,length)
                        || length.getValue() < 1 || length.getValue() > 511) throw new KernelCalls.Failure("transfer-process-identity",0);
                int signal = processes.WaitForSingleObject(held,0);
                if (signal != 0 && signal != 258) throw new KernelCalls.Failure("transfer-process-signal",0);
                return new Win64TransferContract.ProcessState(new KernelCalls.Identity(Integer.toUnsignedLong(pid),birth.getLong(0),
                        new String(image.getByteArray(0,length.getValue()*2),StandardCharsets.UTF_16LE)),signal == 258,nonInherited(value));
            }
        }
    }
    public boolean nonInherited(long value) {
        IntByReference information = new IntByReference();
        try (Memory reference = (Memory) information.getPointer()) {
            if (!flags.GetHandleInformation(new Pointer(value),information)) throw new KernelCalls.Failure("transfer-handle-flags",0);
            return (information.getValue() & 1) == 0;
        }
    }
    public Win64TransferContract.Duplicate duplicate(long source, long handle, long target, int access, boolean inherit, int options) {
        // Der Port gibt ausschließlich reduzierte Rechte vor. Niemals SAME_ACCESS/CLOSE_SOURCE.
        if ((access != 12 && access != 4) || inherit || options != 0) throw new IllegalArgumentException("unsafe-duplicate-options");
        Memory output;
        synchronized (this) {
            if (uncertainOutput != null) throw new IllegalStateException("previous-duplicate-outcome-unconfirmed");
            output = new Memory(8); output.clear(); uncertainOutput = output;
        }
        // Kein Monitor über dem DLL-Aufruf. Bei einer Exception den Out-Puffer
        // behalten; keine angenommene Rückkehr und keine neue Duplikation.
        boolean ok = copies.DuplicateHandle(new Pointer(source),new Pointer(handle),new Pointer(target),output,access,false,0);
        long result = Pointer.nativeValue(output.getPointer(0));
        output.close(); synchronized (this) { uncertainOutput = null; }
        return new Win64TransferContract.Duplicate(ok,result);
    }
    public boolean sameLocalObject(long first,long second) { return comparison.CompareObjectHandles(new Pointer(first),new Pointer(second)); }
}
