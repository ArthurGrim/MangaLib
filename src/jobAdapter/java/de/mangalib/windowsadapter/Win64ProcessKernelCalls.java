package de.mangalib.windowsadapter;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

import static de.mangalib.windowsadapter.Win64ProcessLayouts.*;

/**
 * Isolierte Prozessbindung. Absichtlich KEINE native Factory/Native.load und kein main.
 * Ausschließlich injizierte Fakes dürfen diesen Pfad gegenwärtig ausführen. Eigentum,
 * Tickets, gemeinsame Cleanupfrist und Quarantäne bleiben beim OwnedJobAdapter.
 */
final class Win64ProcessKernelCalls extends Win64KernelCalls {
    /** Optionaler stdio-Ledger; kein Handle wird an einen unbeschränkten Inheritpfad gegeben. */
    interface LaunchStdio {
        void prepare(Pointer privateJob);
        void attach(Pointer attributes, Pointer startup);
        boolean canStart();
        void beginStart();
        boolean returned();
        boolean canResume();
        void attributesDeleted();
    }
    /** Separate Intent-/Custodyberechtigung für den neuen stdio-Pfad, kein PID-Token. */
    interface StartAuthorization {
        boolean beforeCreate(Resource privateJob);
        boolean created(Resource privateJob, Resource heldProcess);
        boolean beforeResume(Resource privateJob, Resource heldProcess);
        boolean afterResume(Resource privateJob, Resource heldProcess);
    }
    /** Exakte Win64-ABI-Deklarationen; allein eine Deklaration lädt keine DLL. */
    interface ProcessFunctions extends StdCallLibrary {
        boolean InitializeProcThreadAttributeList(Pointer list, int count, int flags, Pointer size);
        boolean UpdateProcThreadAttribute(Pointer list, int flags, long attribute, Pointer value,
                                          long bytes, Pointer previous, Pointer returned);
        void DeleteProcThreadAttributeList(Pointer list);
        boolean CreateProcessW(WString application, Pointer command, Pointer processSecurity,
                               Pointer threadSecurity, boolean inherit, int flags, Pointer environment,
                               WString directory, Pointer startup, Pointer info);
        int ResumeThread(Pointer thread);
        Pointer OpenProcess(int access, boolean inherit, int pid);
        int GetProcessId(Pointer process);
        boolean QueryFullProcessImageNameW(Pointer process, int flags, Pointer image, IntByReference length);
        boolean GetProcessTimes(Pointer process, Pointer creation, Pointer exit, Pointer kernel, Pointer user);
        boolean GetExitCodeProcess(Pointer process, IntByReference code);
        int WaitForSingleObject(Pointer handle, int millis);
        boolean IsProcessInJob(Pointer process, Pointer job, IntByReference member);
        boolean TerminateJobObject(Pointer job, int code);
    }

    enum NativeReturn { CREATED, FAILED, OUTSTANDING }
    interface ProcessRawApi extends RawApi, ProcessFunctions {
        /** Testseam für eine noch nicht zurückgekehrte Create-Operation, kein Cancelbeweis. */
        default boolean createReturned() { return true; }
    }

    /**
     * Vorbereitete DLL-Brücke ohne eigene Ladefunktion. Der spätere Prüfhost muss
     * explizit die Job- und Prozessbindings derselben Aufrufumgebung liefern.
     * error() liest deren unmittelbar gesicherten Thread-LastError, keine zweite DLL-Abfrage.
     */
    static final class ProcessBridge implements ProcessRawApi {
        private final RawApi job;
        private final ProcessFunctions process;
        ProcessBridge(RawApi job, ProcessFunctions process) {
            this.job = Objects.requireNonNull(job); this.process = Objects.requireNonNull(process);
        }
        public Pointer create() { return job.create(); }
        public boolean set(Pointer j, int type, Pointer p, int size) { return job.set(j, type, p, size); }
        public boolean query(Pointer j, int type, Pointer p, int size, IntByReference returned) {
            return job.query(j, type, p, size, returned);
        }
        public boolean close(Pointer handle) { return job.close(handle); }
        public int error() { return job.error(); }
        public boolean InitializeProcThreadAttributeList(Pointer list, int count, int flags, Pointer size) {
            return process.InitializeProcThreadAttributeList(list, count, flags, size);
        }
        public boolean UpdateProcThreadAttribute(Pointer list, int flags, long attribute, Pointer value,
                                                 long bytes, Pointer previous, Pointer returned) {
            return process.UpdateProcThreadAttribute(list, flags, attribute, value, bytes, previous, returned);
        }
        public void DeleteProcThreadAttributeList(Pointer list) { process.DeleteProcThreadAttributeList(list); }
        public boolean CreateProcessW(WString application, Pointer command, Pointer ps, Pointer ts,
                                      boolean inherit, int flags, Pointer environment, WString directory,
                                      Pointer startup, Pointer info) {
            return process.CreateProcessW(application, command, ps, ts, inherit, flags, environment, directory, startup, info);
        }
        public int ResumeThread(Pointer thread) { return process.ResumeThread(thread); }
        public Pointer OpenProcess(int access, boolean inherit, int pid) { return process.OpenProcess(access, inherit, pid); }
        public int GetProcessId(Pointer p) { return process.GetProcessId(p); }
        public boolean QueryFullProcessImageNameW(Pointer p, int flags, Pointer image, IntByReference length) {
            return process.QueryFullProcessImageNameW(p, flags, image, length);
        }
        public boolean GetProcessTimes(Pointer p, Pointer birth, Pointer exit, Pointer kernel, Pointer user) {
            return process.GetProcessTimes(p, birth, exit, kernel, user);
        }
        public boolean GetExitCodeProcess(Pointer p, IntByReference code) { return process.GetExitCodeProcess(p, code); }
        public int WaitForSingleObject(Pointer p, int millis) { return process.WaitForSingleObject(p, millis); }
        public boolean IsProcessInJob(Pointer p, Pointer j, IntByReference member) { return process.IsProcessInJob(p, j, member); }
        public boolean TerminateJobObject(Pointer j, int code) { return process.TerminateJobObject(j, code); }
    }
    private enum State { OPEN, CLOSED, UNCERTAIN }
    private static final class Handle {
        final Pointer pointer;
        final Resource job;
        final long expectedPid;
        State state = State.OPEN;
        Identity first;
        Handle(Pointer pointer, Resource job, long expectedPid) {
            this.pointer = pointer; this.job = job; this.expectedPid = expectedPid;
        }
    }
    private static final class Frame {
        final Resource job, memory, attributes;
        final List<Memory> buffers = new ArrayList<>();
        Memory attr, command, env, startup, info;
        volatile State memoryState = State.OPEN, attributeState = State.OPEN;
        volatile int retainedBuffers;
        boolean initialized, associated, attempted, pending, settled, suspended, resumeAttempted;
        Consumer<Resource> acquired;
        Resource process, thread;
        Frame(Resource job) {
            this.job = job;
            memory = new Resource(job.scope, Kind.MEMORY, 2);
            attributes = new Resource(job.scope, Kind.ATTRIBUTES, 3);
        }
        Memory buffer(long bytes) {
            var value = new Memory(bytes);
            buffers.add(value); retainedBuffers = buffers.size(); value.clear(); return value;
        }
    }
    private final ProcessRawApi api;
    private final Win64LaunchSpec spec;
    private final LongSupplier clock;
    private final LaunchStdio stdio;
    private final StartAuthorization authorization;
    private final Map<Resource, Handle> processes = new IdentityHashMap<>();
    private volatile Frame frame;
    private long serial = 10;
    private volatile Failure startFailure;
    private Thread regularThread;
    private int regularDepth;
    private volatile boolean stopRequested;
    private boolean controlPrepared;
    private volatile boolean controlAvailable;

    /** Logische Reservierung, kein über einen Kernelaufruf gehaltener Java-Monitor. */
    private final class Flight implements AutoCloseable {
        Flight() {
            synchronized (stateLock) {
                if (regularThread != null && regularThread != Thread.currentThread()) throw fail("port-call-outstanding");
                regularThread = Thread.currentThread(); regularDepth++;
            }
        }
        public void close() {
            synchronized (stateLock) { if (--regularDepth == 0) regularThread = null; }
        }
    }

    /**
     * Ein dauerhaftes Nutzungstoken auf demselben privaten Jobhandle. Kein neues
     * OS-Handle, kein DuplicateHandle und keine Suche. Jeder Kontrollaufruf hält
     * zusätzlich seine eigene Nutzung; CloseHandle ist bis zum letzten Token verboten.
     */
    @Override public CleanupControl prepareCleanupControl(Resource job) {
        JobUse reservation;
        synchronized (stateLock) {
            owned(job);
            if (controlPrepared) throw fail("control-already-prepared");
            reservation = useJob(job); controlPrepared = true; controlAvailable = true;
        }
        return new CleanupControl() {
            private boolean running, attempted, released;
            public void cancelStarts() { stopRequested = true; }
            public void terminate(long remaining) {
                synchronized (this) {
                    if (released || attempted || remaining <= 0) throw fail("control-not-available");
                    attempted = true; running = true;
                }
                try (JobUse use = useJob(job)) {
                    stopRequested = true;
                    if (!api.TerminateJobObject(use.pointer, 1)) throw nativeFailure("TerminateJobObject:control");
                } finally { synchronized (this) { running = false; } }
            }
            public void release() {
                synchronized (this) {
                    if (running || released) throw fail("control-still-running-or-released");
                    released = true;
                }
                controlAvailable = false;
                stopRequested = true;
                reservation.close();
            }
        };
    }

    Win64ProcessKernelCalls(ProcessRawApi api, Win64LaunchSpec spec, LongSupplier clock) {
        this(api, spec, clock, null);
    }
    Win64ProcessKernelCalls(ProcessRawApi api, Win64LaunchSpec spec, LongSupplier clock, LaunchStdio stdio) {
        this(api, spec, clock, stdio, null);
    }
    Win64ProcessKernelCalls(ProcessRawApi api, Win64LaunchSpec spec, LongSupplier clock,
                            LaunchStdio stdio, StartAuthorization authorization) {
        super(api, "Windows x64", 8);
        this.api = Objects.requireNonNull(api); this.spec = Objects.requireNonNull(spec);
        this.clock = Objects.requireNonNull(clock);
        this.stdio = stdio;
        this.authorization = authorization;
        if (authorization != null && stdio == null) throw new IllegalArgumentException("authorization-requires-stdio");
        Win64ProcessLayouts.verify();
    }

    @Override public void prepareLaunch(Resource job, Consumer<Resource> acquired) {
        try (Flight flight = new Flight()) {
            try (JobUse use = useJob(job)) {
                Pointer jobPointer = use.pointer;
                if (stopRequested) throw fail("start-cancelled");
                if (frame != null) throw fail("single-preparation");
                beforeDeadline();
                frame = new Frame(job);
                acquired.accept(frame.memory); // Alle folgenden Teilallokationen gehören bereits dem Ledger.
                if (stdio != null) stdio.prepare(jobPointer);
                try (Memory size = new Memory(8)) {
                    size.clear();
                    int attributes = stdio == null ? 1 : 2;
                    boolean first = api.InitializeProcThreadAttributeList(null, attributes, 0, size);
                    int error = first ? 0 : api.error();
                    long bytes = size.getLong(0);
                    if (first || error != 122 || bytes <= 0 || bytes > 4096) throw new Failure("attribute-size", error);
                    frame.attr = frame.buffer(bytes);
                    if (!api.InitializeProcThreadAttributeList(frame.attr, attributes, 0, size)) throw nativeFailure("attribute-init");
                    frame.initialized = true;
                    acquired.accept(frame.attributes);
                    Memory jobs = frame.buffer(8); jobs.setPointer(0, jobPointer);
                    if (!api.UpdateProcThreadAttribute(frame.attr, 0, JOB_LIST, jobs, 8, null, null))
                        throw nativeFailure("attribute-job-list");
                    frame.associated = true;
                    frame.command = bytes(spec.commandBytes()); frame.env = bytes(spec.environmentBytes());
                    frame.startup = frame.buffer(112); frame.info = frame.buffer(24);
                    frame.startup.setInt(0, 112); frame.startup.setPointer(104, frame.attr);
                    if (stdio != null) stdio.attach(frame.attr, frame.startup);
                    beforeDeadline();
                }
            }
        }
    }

    private Memory bytes(byte[] value) {
        Memory buffer = frame.buffer(value.length); buffer.write(0, value, 0, value.length); return buffer;
    }

    @Override public StartResult startSuspended(Resource job, List<Resource> preparation,
                                                Consumer<Resource> acquired) {
        try (Flight flight = new Flight()) {
            try (JobUse use = useJob(job)) {
                if (frame == null || frame.job != job || frame.attempted || !frame.associated
                    || preparation.size() != 2 || !preparation.contains(frame.memory)
                    || !preparation.contains(frame.attributes) || frame.memoryState != State.OPEN
                    || frame.attributeState != State.OPEN) throw fail("invalid-start-capability");
                frame.attempted = true; frame.acquired = acquired;
                if (stopRequested || clock.getAsLong() >= spec.startDeadline()) { frame.settled = true; return StartResult.failed(); }
                if (!controlAvailable) {
                    frame.settled = true;
                    startFailure = fail("cleanup-control-not-prepared");
                    return StartResult.failed();
                }
                if (stdio != null) {
                    if (authorization == null || !authorization.beforeCreate(job)) {
                        frame.settled = true; startFailure = fail("start-intent-or-custody-unconfirmed"); return StartResult.failed();
                    }
                    if (!stdio.canStart()) { frame.settled = true; return StartResult.failed(); }
                    stdio.beginStart();
                }
                frame.pending = true;
                // Bei einer Exception bleibt pending=true: keine angenommene Rückkehr, kein Freigeben.
                boolean created = api.CreateProcessW(new WString(spec.executable()), frame.command, null, null,
                    stdio != null, START_FLAGS, frame.env, new WString(spec.directory()), frame.startup, frame.info);
                int error = created ? 0 : api.error();
                if (!api.createReturned()) return StartResult.pending();
                return settle(created ? NativeReturn.CREATED : NativeReturn.FAILED, error);
            }
        }
    }

    /** Nur nach tatsächlicher Rückkehr; Fähigkeit gehört genau dieser Startallokation. */
    StartResult completeReturn(Resource memory, NativeReturn result, int error) {
        try (Flight flight = new Flight()) {
            if (frame == null || memory != frame.memory || !frame.pending || !api.createReturned()
                || result == NativeReturn.OUTSTANDING) throw fail("foreign-or-unreturned-completion");
            return settle(result, error);
        }
    }

    private StartResult settle(NativeReturn result, int error) {
        frame.pending = false; frame.settled = true;
        Pointer process = frame.info.getPointer(0), thread = frame.info.getPointer(8);
        long pid = Integer.toUnsignedLong(frame.info.getInt(16));
        if (valid(process)) frame.process = acquire(process, Kind.PROCESS, frame.job, pid);
        if (valid(thread)) frame.thread = acquire(thread, Kind.THREAD, frame.job, pid);
        boolean authorized, childCopiesClosed;
        try { authorized = authorization == null || authorization.created(frame.job, frame.process); }
        finally { childCopiesClosed = stdio == null || stdio.returned(); }
        if (!childCopiesClosed) {
            startFailure = fail("stdio-child-close-unconfirmed");
            return new StartResult(StartStatus.FAILED, frame.process, frame.thread);
        }
        if (!authorized) {
            startFailure = fail("late-or-unconfirmed-created-process");
            return new StartResult(StartStatus.FAILED, frame.process, frame.thread);
        }
        if (result != NativeReturn.CREATED || frame.process == null || frame.thread == null
                || pid == 0 || frame.info.getInt(20) == 0 || stopRequested || clock.getAsLong() >= spec.startDeadline()) {
            startFailure = new Failure(result != NativeReturn.CREATED ? "CreateProcessW" : "invalid-or-expired-start", error);
            return new StartResult(StartStatus.FAILED, frame.process, frame.thread);
        }
        try {
            if (!member(frame.process, frame.job) || !identity(frame.process).image().equalsIgnoreCase(spec.executable()))
                throw fail("root-not-expected-instance");
            beforeDeadline();
            if (stopRequested) throw fail("start-returned-after-stop");
            frame.suspended = true;
            return new StartResult(StartStatus.SUSPENDED, frame.process, frame.thread);
        } catch (RuntimeException failure) {
            startFailure = failure instanceof Failure f ? f : new Failure("start-identity-exception", 0);
            if (failure != startFailure) startFailure.addSuppressed(failure);
            // Keine Handles verstecken; OwnedJobAdapter übernimmt auch fehlgeschlagene Teilstarts.
            return new StartResult(StartStatus.FAILED, frame.process, frame.thread);
        }
    }

    private Resource acquire(Pointer pointer, Kind kind, Resource job, long pid) {
        Resource resource = new Resource(job.scope, kind, serial++);
        synchronized (stateLock) { processes.put(resource, new Handle(pointer, job, pid)); }
        frame.acquired.accept(resource);
        return resource;
    }

    @Override public void resume(Resource thread) {
        try (Flight flight = new Flight()) {
            Handle handle = handle(thread, Kind.THREAD);
            if (frame == null || frame.thread != thread || !frame.suspended || frame.pending || frame.resumeAttempted)
            throw fail("resume-not-authorized");
            if (stdio != null && !stdio.canResume()) throw fail("stdio-not-ready-for-resume");
            if (authorization != null && !authorization.beforeResume(frame.job, frame.process)) throw fail("resume-lease-revoked");
            beforeDeadline();
            if (!member(frame.process, frame.job)) throw fail("resume-membership");
            identity(frame.process);
            if (stopRequested) throw fail("resume-cancelled");
            frame.resumeAttempted = true;
            int previousCount = api.ResumeThread(handle.pointer);
            if (previousCount == -1) throw nativeFailure("ResumeThread");
            if (previousCount != 1) throw fail("unexpected-suspend-count");
            if (authorization != null && !authorization.afterResume(frame.job, frame.process)) throw fail("resume-lease-lost");
            if (stopRequested) throw fail("resume-returned-after-stop");
        }
    }

    @Override public Resource openProcess(Resource job, long pid) {
        try (Flight flight = new Flight()) {
            try (JobUse use = useJob(job)) {
                if (pid <= 0 || pid > 0xffff_ffffL || (frame != null && frame.pending)) throw fail("invalid-pid-or-pending");
                Pointer pointer = api.OpenProcess(QUERY_AND_SYNCHRONIZE, false, (int) pid);
                if (!valid(pointer)) throw nativeFailure("OpenProcess");
                Resource result = new Resource(job.scope, Kind.PROCESS, serial++);
                synchronized (stateLock) { processes.put(result, new Handle(pointer, job, pid)); }
                return result; // Der Owner verbucht vor jeder weiteren, potentiell fehlschlagenden Abfrage.
            }
        }
    }

    @Override public Identity identity(Resource process) {
        try (Flight flight = new Flight()) {
            Handle h = handle(process, Kind.PROCESS);
            long pid = Integer.toUnsignedLong(api.GetProcessId(h.pointer));
            if (pid == 0) throw nativeFailure("GetProcessId");
            if (pid != h.expectedPid) throw fail("pid-mismatch");
            try (Memory times = new Memory(32); Memory image = new Memory(65536)) {
                times.clear(); image.clear();
                if (!api.GetProcessTimes(h.pointer, times, times.share(8), times.share(16), times.share(24)))
                throw nativeFailure("GetProcessTimes");
                long birth=times.getLong(0);
                if(birth<=0 || (h.first!=null && (h.first.pid()!=pid || h.first.creationTime()!=birth)))throw fail("identity-changed");
                IntByReference length = new IntByReference(32768);
                String path;
                try (Memory reference = (Memory) length.getPointer()) {
                    if (!api.QueryFullProcessImageNameW(h.pointer, 0, image, length)) {
                        int error=api.error();
                        // Im realen Cleanup nachgewiesen: ERROR_GEN_FAILURE (31)
                        // bei beendeter Instanz. Nur ihr vorher gemessenes Image
                        // behalten, nachdem PID/Geburt und signalisiertes Ende
                        // auf demselben gehaltenen Handle erneut bestätigt sind.
                        if(error!=31 || h.first==null || api.WaitForSingleObject(h.pointer,0)!=WAIT_OBJECT)
                            throw new Failure("QueryFullProcessImageNameW",error);
                        IntByReference code=new IntByReference();
                        try(Memory exitReference=(Memory)code.getPointer()){
                            if(!api.GetExitCodeProcess(h.pointer,code))throw nativeFailure("GetExitCodeProcess:identity");
                        }
                        return KernelCalls.retainEndedImage(h.first,pid,birth,true,true);
                    }
                    if (length.getValue() <= 0 || length.getValue() >= 32768) throw fail("image-length");
                    path = new String(image.getCharArray(0, length.getValue()));
                }
                Win64LaunchSpec.requirePath(path);
                Identity current = new Identity(pid, times.getLong(0), path);
                if (current.creationTime() <= 0 || (h.first != null && !h.first.equals(current))) throw fail("identity-changed");
                if (h.first == null) h.first = current;
                return current;
            }
        }
    }

    @Override public boolean member(Resource process, Resource job) {
        try (Flight flight = new Flight()) {
            Pointer ownJob = owned(job); Handle h = handle(process, Kind.PROCESS);
            if (h.job != job || process.scope != job.scope) throw fail("foreign-group");
            IntByReference result = new IntByReference();
            try (Memory reference = (Memory) result.getPointer()) {
                if (!api.IsProcessInJob(h.pointer, ownJob, result)) throw nativeFailure("IsProcessInJob");
                if (result.getValue() != 0 && result.getValue() != 1) throw fail("membership-value");
                return result.getValue() == 1;
            }
        }
    }

    @Override public Observation observe(Resource job, int capacity, long remaining) {
        try (Flight flight = new Flight()) {
            try (JobUse use = useJob(job)) {
                if (capacity < 1 || capacity > 256 || remaining <= 0 || (frame != null && frame.pending))
                throw fail("observation-budget-or-start-pending");
                Pointer pointer = use.pointer;
                long[] before = accounting(pointer);
                List<Long> pids = pidList(pointer, capacity);
                long[] after = accounting(pointer);
                boolean stable = java.util.Arrays.equals(before, after) && after[1] == pids.size()
                    && after[0] >= after[1] && after[2] <= after[0];
                return new Observation(true, stable, pids.size(), pids, (int) after[1], after[0]);
            }
        }
    }

    @Override public void terminate(Resource job) {
        try (Flight flight = new Flight()) {
            try (JobUse use = useJob(job)) {
                Pointer pointer = use.pointer;
                if (frame != null && frame.pending) throw fail("start-outstanding");
                if (!api.TerminateJobObject(pointer, 1)) throw nativeFailure("TerminateJobObject");
            }
        }
    }

    @Override public boolean waitEnded(Resource process, long remaining) {
        try (Flight flight = new Flight()) {
            Handle h = handle(process, Kind.PROCESS);
            if (remaining <= 0) throw fail("wait-deadline");
            // Nanosekunden -> bounded DWORD; niemals INFINITE, niemals neue Graceperiod.
            long millis = Math.min(0xffff_fffeL, remaining / 1_000_000);
            int status = api.WaitForSingleObject(h.pointer, (int) millis);
            if (status == WAIT_TIMEOUT) return false;
            if (status == WAIT_FAILED) throw nativeFailure("WaitForSingleObject");
            if (status != WAIT_OBJECT) throw fail("unexpected-wait-result");
            IntByReference code = new IntByReference();
            try (Memory reference = (Memory) code.getPointer()) {
                if (!api.GetExitCodeProcess(h.pointer, code)) throw nativeFailure("GetExitCodeProcess");
                // Auch 259 ist ein möglicher Exitcode. Der signalisierte gehaltene Handle ist der Endbeleg.
                return true;
            }
        }
    }

    /** Exitcode nur über den noch gehaltenen, tatsächlich signalisierten Instanzhandle. */
    int exitCode(Resource process) {
        try (Flight flight = new Flight()) {
            Handle h = handle(process, Kind.PROCESS);
            if (api.WaitForSingleObject(h.pointer, 0) != WAIT_OBJECT) throw fail("exit-before-signal");
            IntByReference code = new IntByReference();
            try (Memory reference = (Memory) code.getPointer()) {
                if (!api.GetExitCodeProcess(h.pointer, code)) throw nativeFailure("GetExitCodeProcess");
                return code.getValue();
            }
        }
    }

    @Override public void close(Resource resource) {
        try (Flight flight = new Flight()) {
            synchronized (stateLock) { if (regularDepth > 1) throw fail("resource-in-use"); }
            if (resource.kind == Kind.JOB) {
                if (processes.values().stream().anyMatch(h -> h.state != State.CLOSED)
                    || (frame != null && frame.memoryState != State.CLOSED)) throw fail("dependent-resources-open");
                super.close(resource); return;
            }
            if (frame != null && (resource == frame.attributes || resource == frame.memory)) {
                if (frame.pending) throw fail("native-call-still-owns-buffers");
                if (resource == frame.attributes) {
                    if (!frame.initialized || frame.attributeState != State.OPEN) throw fail("attributes-not-open");
                    frame.attributeState = State.UNCERTAIN;
                    api.DeleteProcThreadAttributeList(frame.attr);
                    frame.attributeState = State.CLOSED;
                    if (stdio != null) stdio.attributesDeleted();
                } else {
                    if (frame.memoryState != State.OPEN || (frame.initialized && frame.attributeState != State.CLOSED))
                    throw fail("memory-not-releasable");
                    frame.memoryState = State.UNCERTAIN;
                    for (Memory buffer : frame.buffers.reversed()) buffer.close();
                    frame.retainedBuffers = 0;
                    frame.memoryState = State.CLOSED;
                }
                return;
            }
            Handle h = handle(resource, resource.kind);
            synchronized (stateLock) { h.state = State.UNCERTAIN; }
            if (!api.close(h.pointer)) throw nativeFailure("CloseHandle:process");
            synchronized (stateLock) { h.state = State.CLOSED; }
        }
    }

    private Handle handle(Resource resource, Kind kind) {
        synchronized (stateLock) {
        Handle h = processes.get(resource);
        if ((kind != Kind.PROCESS && kind != Kind.THREAD) || h == null || resource.kind != kind
                || h.state != State.OPEN) throw fail("unowned-or-closed-process");
        owned(h.job); return h;
        }
    }
    private void beforeDeadline() { if (clock.getAsLong() >= spec.startDeadline()) throw fail("start-deadline"); }
    private Failure nativeFailure(String operation) { return new Failure(operation, api.error()); }
    private static Failure fail(String operation) { return new Failure(operation, 0); }
    private static boolean valid(Pointer p) { return p != null && Pointer.nativeValue(p) != 0 && Pointer.nativeValue(p) != -1; }
    int retainedProcessHandles() { synchronized (stateLock) { return (int) processes.values().stream().filter(h -> h.state != State.CLOSED).count(); } }
    int retainedLaunchBuffers() { return frame == null ? 0 : frame.retainedBuffers; }
    Failure startFailure() { synchronized (stateLock) { return startFailure; } }
}
