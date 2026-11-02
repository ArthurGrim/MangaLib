package de.mangalib.windowsadapter;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static de.mangalib.windowsadapter.Win64Layouts.*;

/** Native Teilimplementierung ausschließlich für leere private Jobs. */
class Win64KernelCalls implements KernelCalls, Win64TransferContract.SourceLedger {
    interface RawApi {
        Pointer create();
        boolean set(Pointer job, int type, Pointer buffer, int size);
        boolean query(Pointer job, int type, Pointer buffer, int size, IntByReference returned);
        boolean close(Pointer job);
        int error();
    }

    /** Keine Start-/Zuordnungs-/Terminate-/Wait-Funktionen in dieser DLL-Bindung. */
    interface Kernel32 extends StdCallLibrary {
        Pointer CreateJobObjectW(Pointer security, WString name);
        boolean SetInformationJobObject(Pointer job, int type, Pointer buffer, int size);
        boolean QueryInformationJobObject(Pointer job, int type, Pointer buffer, int size, IntByReference returned);
        boolean CloseHandle(Pointer job);
        int GetLastError();
    }

    private static final class NativeApi implements RawApi {
        private final Kernel32 api;
        NativeApi() { api = Native.load("kernel32", Kernel32.class); }
        public Pointer create() { return api.CreateJobObjectW(null, null); }
        public boolean set(Pointer j, int t, Pointer p, int n) { return api.SetInformationJobObject(j, t, p, n); }
        public boolean query(Pointer j, int t, Pointer p, int n, IntByReference r) {
            return api.QueryInformationJobObject(j, t, p, n, r);
        }
        public boolean close(Pointer j) { return api.CloseHandle(j); }
        // JNA sichert den Thread-GetLastError unmittelbar nach dem DLL-Aufruf.
        public int error() { return Native.getLastError(); }
    }

    private enum State { OPEN, CLOSED, UNCERTAIN }
    private static final class Entry {
        final Pointer pointer;
        State state = State.OPEN;
        int users;
        Entry(Pointer pointer) { this.pointer = pointer; }
    }
    private final RawApi api;
    private final boolean allowPidGrowth;
    private final Map<Resource, Entry> handles = new IdentityHashMap<>();
    private final List<String> audit = new ArrayList<>();
    private int creations;
    private int buffers;
    private long transferSerial = 10_000;
    /** Ausschließlich lokale Registryänderungen; niemals ein RawApi-Aufruf unter diesem Lock. */
    final Object stateLock = new Object();

    final class JobUse implements AutoCloseable {
        final Pointer pointer;
        private final Entry entry;
        private boolean released;
        JobUse(Entry entry) { this.entry = entry; pointer = entry.pointer; }
        @Override public void close() {
            synchronized (stateLock) {
                if (!released) { entry.users--; released = true; }
            }
        }
    }
    final JobUse useJob(Resource job) {
        synchronized (stateLock) {
            owned(job); Entry entry = handles.get(job); entry.users++; return new JobUse(entry);
        }
    }
    /** Geteiltes bestehendes Handleledger, kein separater Zahlenwert-/Close-Registry. */
    @Override public Win64TransferContract.JobUse borrowTransferSource(Resource source) {
        JobUse held = useJob(source);
        return new Win64TransferContract.JobUse() {
            private boolean released;
            public Resource resource() { return source; }
            public long value() { if (released) throw new Failure("transfer-source-released",0); return Pointer.nativeValue(held.pointer); }
            public Limits limits() { if (released) throw new Failure("transfer-source-released",0); return readLimits(source); }
            public Win64TransferContract.LocalCopy registerLocalCopy(long value) {
                Resource copy;
                Pointer pointer = new Pointer(value);
                synchronized (stateLock) {
                    if (released || value == 0 || value == -1 || handles.values().stream().anyMatch(e -> e.pointer.equals(pointer)))
                        throw new Failure("unconfirmed-local-transfer-copy",0);
                    copy = new Resource(source.scope, Kind.JOB, transferSerial++);
                    handles.put(copy,new Entry(pointer));
                }
                return new Win64TransferContract.LocalCopy() {
                    public Resource resource() { return copy; }
                    public long value() { return value; }
                    public void close() { Win64KernelCalls.this.close(copy); }
                };
            }
            public void close() { if (!released) { released = true; held.close(); } }
        };
    }
    private void trace(String message) { synchronized (stateLock) { audit.add(message); } }
    /** Nur C-lokaler registrierter Erwerb vom gebundenen nativen S-Kanal; kein allgemeiner IPC-Handleimport. */
    Resource registerTransferredControl(long value) {
        synchronized(stateLock) {
            Pointer pointer=new Pointer(value);
            if(value==0 || value==-1 || handles.values().stream().anyMatch(e->e.pointer.equals(pointer)))
                throw new Failure("transferred-control-alias",0);
            Resource resource=new Resource(new Scope(),Kind.JOB,transferSerial++);
            handles.put(resource,new Entry(pointer));return resource;
        }
    }
    boolean confirmedClosed(Resource resource) {
        synchronized(stateLock) { Entry e=handles.get(resource);return e!=null && e.state==State.CLOSED && e.users==0; }
    }
    int additionalOpenCopies(Resource source) {
        synchronized(stateLock){return (int)handles.entrySet().stream().filter(e->e.getKey()!=source
                && e.getKey().scope==source.scope && e.getValue().state!=State.CLOSED).count();}
    }

    /** Testseam: kein Native.load. Der echte Factorypfad validiert zusätzlich ABI/JNA. */
    Win64KernelCalls(RawApi api, String os, int pointerBytes) {
        this(api, os, pointerBytes, true);
    }

    private Win64KernelCalls(RawApi api, String os, int pointerBytes, boolean allowPidGrowth) {
        requirePlatform(os, pointerBytes);
        this.api = java.util.Objects.requireNonNull(api);
        this.allowPidGrowth = allowPidGrowth;
    }

    static void requirePlatform(String os, int pointerBytes) {
        if (os == null || !os.startsWith("Windows") || pointerBytes != 8) {
            throw new UnsupportedOperationException("Only Windows x64 is supported");
        }
    }

    static Win64KernelCalls nativeEmptyJobPort() {
        requirePlatform(System.getProperty("os.name"), Native.POINTER_SIZE);
        if (Native.LONG_SIZE != 4 || Native.WCHAR_SIZE != 2 || !Native.VERSION.equals("5.17.0")
                || !System.getProperty("java.specification.version").equals("21")) {
            throw new IllegalStateException("Expected Win64/JNA 5.17.0");
        }
        Win64Layouts.verify();
        return new Win64KernelCalls(new NativeApi(), System.getProperty("os.name"), Native.POINTER_SIZE, false);
    }

    @Override public Resource createJob(Scope scope) {
        java.util.Objects.requireNonNull(scope);
        synchronized (stateLock) { if (creations++ != 0) throw new Failure("single-create-consumed", 0); }
        Pointer pointer = api.create();
        if (pointer == null || Pointer.nativeValue(pointer) == 0 || Pointer.nativeValue(pointer) == -1L) {
            int error = api.error();
            trace("CreateJobObjectW failed error=" + error);
            throw new Failure("CreateJobObjectW", error);
        }
        Resource resource = new Resource(scope, Kind.JOB, 1);
        synchronized (stateLock) { handles.put(resource, new Entry(pointer)); }
        trace("CreateJobObjectW private=true inherited=false handle=0x" + Long.toHexString(Pointer.nativeValue(pointer)));
        return resource;
    }

    @Override public void configure(Resource job, Limits limits) {
        try (JobUse use = useJob(job)) {
            Pointer pointer = use.pointer;
            if (!limits.killOnClose() || limits.breakaway() || limits.activeProcessCandidate() != 0
                || limits.commitBytes() != 0 || limits.cpuRateCandidate() != 0) {
                throw new Failure("quotas-not-authorized", 0);
            }
            try (Memory memory = allocate(144)) {
                memory.setInt(16, KILL_ON_CLOSE);
                if (!api.set(pointer, EXTENDED_LIMITS, memory, 144)) {
                    int error = api.error(); throw new Failure("SetInformationJobObject", error);
                }
                trace("SetInformationJobObject class=9 bytes=144 flags=0x2000 quotas=none");
            }
        }
    }

    @Override public Limits readLimits(Resource job) {
        try (JobUse use = useJob(job); Memory memory = allocate(144)) {
            query(use.pointer, EXTENDED_LIMITS, memory, 144);
            int flags = memory.getInt(16);
            if (flags != KILL_ON_CLOSE || memory.getInt(40) != 0
                    || memory.getLong(112) != 0 || memory.getLong(120) != 0) {
                throw new Failure("unexpected-job-limits", 0);
            }
            trace("readback flags=0x" + Integer.toHexString(flags) + " activeLimit=0 jobMemory=0");
            return new Limits(true, false, 0, 0, 0);
        }
    }

    @Override public Observation observe(Resource job, int capacity, long remaining) {
        if (capacity < 1 || capacity > 256 || remaining <= 0) throw new Failure("invalid-observation-budget", 0);
        try (JobUse use = useJob(job)) {
            Pointer pointer = use.pointer;
            long[] before = accounting(pointer);
            List<Long> pids = pidList(pointer, capacity);
            long[] after = accounting(pointer);
            // Dieser Port ist absichtlich NICHT für laufende Prozessgruppen freigegeben.
            if (before[0] != 0 || before[1] != 0 || before[2] != 0 || after[0] != 0
                || after[1] != 0 || after[2] != 0 || !pids.isEmpty()) {
                throw new Failure("nonempty-job-not-authorized", 0);
            }
            trace("empty-job accounting total=0 active=0 terminated=0 pids=[]");
            return new Observation(true, true, 0, pids, 0, 0);
        }
    }

    final long[] accounting(Pointer pointer) {
        try (Memory memory = allocate(48)) {
            query(pointer, BASIC_ACCOUNTING, memory, 48);
            return new long[]{Integer.toUnsignedLong(memory.getInt(36)),
                    Integer.toUnsignedLong(memory.getInt(40)), Integer.toUnsignedLong(memory.getInt(44))};
        }
    }

    final List<Long> pidList(Pointer pointer, int maximum) {
        int capacity = Math.min(4, maximum);
        // Höchstens ein begrenztes Grow, niemals Retry-bis-stabil.
        for (int attempt = 0; attempt < 2; attempt++) {
            try (Memory memory = allocate(8L + 8L * capacity)) {
                IntByReference returned = new IntByReference();
                try (Memory returnedMemory = (Memory) returned.getPointer()) {
                boolean ok = api.query(pointer, PROCESS_IDS, memory, (int) memory.size(), returned);
                int error = ok ? 0 : api.error();
                trace("QueryInformationJobObject class=3 capacity=" + capacity + " ok=" + ok
                        + " returned=" + returned.getValue() + " error=" + error);
                if (ok) return decodePids(memory, returned.getValue(), capacity);
                long needed = Integer.toUnsignedLong(memory.getInt(0));
                if (allowPidGrowth && attempt == 0 && (error == INSUFFICIENT_BUFFER || error == MORE_DATA)
                        && needed > capacity && needed <= maximum) {
                    capacity = (int) needed;
                } else throw new Failure("QueryInformationJobObject:3", error);
                }
            }
        }
        throw new Failure("pid-list-not-complete", 0);
    }

    private void query(Pointer job, int type, Memory buffer, int expected) {
        IntByReference returned = new IntByReference();
        try (Memory returnedMemory = (Memory) returned.getPointer()) {
        if (!api.query(job, type, buffer, expected, returned)) {
            int error = api.error(); throw new Failure("QueryInformationJobObject:" + type, error);
        }
        trace("QueryInformationJobObject class=" + type + " bytes=" + expected + " returned=" + returned.getValue());
        if (returned.getValue() != expected) throw new Failure("unexpected-return-length:" + type, 0);
        }
    }

    private Memory allocate(long bytes) {
        return new Buffer(bytes);
    }

    private final class Buffer extends Memory {
        private boolean counted;
        Buffer(long size) {
            super(size);
            synchronized (stateLock) { buffers++; counted = true; }
            try { clear(); } catch (RuntimeException | Error failure) { close(); throw failure; }
        }
        @Override public synchronized void close() {
            if (counted) { super.close(); counted = false; synchronized (stateLock) { buffers--; } }
        }
    }

    final Pointer owned(Resource resource) {
        synchronized (stateLock) {
        Entry entry = handles.get(resource);
        if (resource == null || resource.kind != Kind.JOB || entry == null || entry.state != State.OPEN) {
            throw new Failure("unowned-or-closed-job", 0);
        }
        return entry.pointer;
        }
    }

    @Override public void close(Resource job) {
        Pointer pointer;
        Entry entry;
        synchronized (stateLock) {
            pointer = owned(job); entry = handles.get(job);
            if (entry.users != 0) throw new Failure("job-in-use", 0);
            entry.state = State.UNCERTAIN; // Auch unerwartete Exceptions erlauben keinen zweiten Close.
        }
        if (!api.close(pointer)) {
            int error = api.error(); trace("CloseHandle failed error=" + error);
            throw new Failure("CloseHandle", error);
        }
        synchronized (stateLock) { entry.state = State.CLOSED; }
        trace("CloseHandle success own-job=true");
    }

    int outstandingHandles() { synchronized (stateLock) { return (int) handles.values().stream().filter(e -> e.state != State.CLOSED).count(); } }
    int outstandingBuffers() { synchronized (stateLock) { return buffers; } }
    List<String> audit() { synchronized (stateLock) { return List.copyOf(audit); } }

    private static Failure unsupported() { return new Failure("operation-not-authorized-in-B-ADAPTER-2", 50); }
    @Override public void prepareLaunch(Resource j, Consumer<Resource> a) { throw unsupported(); }
    @Override public StartResult startSuspended(Resource j, List<Resource> p, Consumer<Resource> a) { throw unsupported(); }
    @Override public void resume(Resource t) { throw unsupported(); }
    @Override public Resource openProcess(Resource j, long pid) { throw unsupported(); }
    @Override public Identity identity(Resource p) { throw unsupported(); }
    @Override public boolean member(Resource p, Resource j) { throw unsupported(); }
    @Override public void terminate(Resource j) { throw unsupported(); }
    @Override public boolean waitEnded(Resource p, long r) { throw unsupported(); }
    @Override public Observation awaitEmptyJob(Resource j, int c, long r) { return observe(j, c, r); }
}
