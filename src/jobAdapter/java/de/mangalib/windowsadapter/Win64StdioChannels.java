package de.mangalib.windowsadapter;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Injizierte Win64-stdio-Bindung mit eigenem Pipeledger. Kein Loader, Prozessstart
 * oder Lifecycleautomat. Deadlines begrenzen Prüf-/Pufferarbeit, NICHT die
 * Rückkehr eines synchron blockierenden Windows-Aufrufs.
 */
final class Win64StdioChannels implements Win64ProcessKernelCalls.LaunchStdio {
    static final long HANDLE_LIST = 0x20002;
    static final int INHERIT = 1, STD_HANDLES = 0x100, MAX_IO = 512, STDERR_LIMIT = 256;
    interface Functions extends StdCallLibrary {
        boolean CreatePipe(Pointer read, Pointer write, Pointer security, int size);
        boolean SetHandleInformation(Pointer handle, int mask, int flags);
        boolean GetHandleInformation(Pointer handle, IntByReference flags);
        boolean ReadFile(Pointer handle, Pointer bytes, int count, IntByReference read, Pointer overlapped);
        boolean WriteFile(Pointer handle, Pointer bytes, int count, IntByReference written, Pointer overlapped);
        boolean CloseHandle(Pointer handle);
        boolean UpdateProcThreadAttribute(Pointer list, int flags, long attribute, Pointer value,
                                          long bytes, Pointer previous, Pointer returned);
    }
    interface Api extends Functions { int error(); }
    enum State { OPEN, UNCERTAIN, CLOSED }
    enum Stream { STDIN, STDOUT, STDERR }
    record Read(byte[] bytes, boolean eof) { }
    private static final class End {
        final Pointer handle;
        final boolean child;
        State state = State.OPEN;
        int users;
        End(Pointer handle, boolean child) { this.handle = handle; this.child = child; }
    }
    private final Api api;
    private final LongSupplier clock;
    private final long deadline;
    private final Object lock = new Object();
    private final List<End> ledger = new ArrayList<>();
    private final End[] parent = new End[3], child = new End[3];
    private Pointer job;
    private Pointer custodianOwner;
    private boolean custodianMode;
    private Memory inherited;
    private boolean attempted, attached, preparing, pendingStart, returned, deleted, closing, failed;
    private final int[] totals = new int[3];

    Win64StdioChannels(Api api, LongSupplier clock, long deadline) {
        this.api = Objects.requireNonNull(api); this.clock = Objects.requireNonNull(clock); this.deadline = deadline;
    }
    private KernelCalls.Failure fail(String reason) { synchronized (lock) { failed = true; } return new KernelCalls.Failure(reason, 0); }
    private void time() { if (clock.getAsLong() >= deadline) throw fail("stdio-deadline"); }
    private int flags(Pointer handle) {
        IntByReference flags = new IntByReference();
        try (Memory memory = (Memory) flags.getPointer()) {
            if (!api.GetHandleInformation(handle, flags)) throw fail("stdio-get-flags"); return flags.getValue();
        }
    }
    private End acquire(Pointer handle, boolean isChild) {
        if (handle == null || Pointer.nativeValue(handle) == 0 || Pointer.nativeValue(handle) == -1
                || handle.equals(job) || handle.equals(custodianOwner)) throw fail("invalid-pipe-handle");
        synchronized (lock) {
            if (ledger.stream().anyMatch(e -> e.handle.equals(handle))) throw fail("duplicate-pipe-handle");
            End end = new End(handle, isChild); ledger.add(end); return end;
        }
    }
    public void prepare(Pointer privateJob) {
        prepare(privateJob,null);
    }
    /** Getrennter C-Pfad: drei stdio-Enden + eigene reduzierte Ownerkopie, KEIN JOB_LIST. */
    void prepareCustodian(Pointer inheritedOwner) {
        if(inheritedOwner==null || Pointer.nativeValue(inheritedOwner)==0 || Pointer.nativeValue(inheritedOwner)==-1)
            throw fail("custodian-owner-invalid");
        prepare(null,inheritedOwner);
    }
    private void prepare(Pointer privateJob,Pointer inheritedOwner) {
        synchronized (lock) { if (attempted || closing || failed) throw fail("stdio-prepare-once"); attempted = preparing = true; job = privateJob; }
        try {
        time();
        if(inheritedOwner!=null) {
            custodianMode=true;custodianOwner=inheritedOwner;
            if((flags(inheritedOwner)&INHERIT)!=0 || !api.SetHandleInformation(inheritedOwner,INHERIT,INHERIT)
                    || (flags(inheritedOwner)&INHERIT)==0)throw fail("custodian-owner-inheritance");
        } else if(job==null || (flags(job)&INHERIT)!=0)throw fail("job-handle-inheritable");
        for (int stream = 0; stream < 3; stream++) {
            try (Memory read = new Memory(8); Memory write = new Memory(8)) {
                read.clear(); write.clear();
                boolean ok = api.CreatePipe(read, write, null, 1024); // Erst non-inheritable, dann exakte Child-Enden.
                Pointer r = read.getPointer(0), w = write.getPointer(0);
                End re = null, we = null; RuntimeException invalid = null;
                try { if (r != null) re = acquire(r, stream == 0); } catch (RuntimeException failure) { invalid = failure; }
                try { if (w != null) we = acquire(w, stream != 0); } catch (RuntimeException failure) { invalid = failure; }
                if (invalid != null) throw invalid; // Andere gültige Teilhandles sind trotzdem verbucht.
                if (!ok || re == null || we == null) throw fail("pipe-create");
                child[stream] = stream == 0 ? re : we; parent[stream] = stream == 0 ? we : re;
                if (!api.SetHandleInformation(parent[stream].handle, INHERIT, 0)
                        || !api.SetHandleInformation(child[stream].handle, INHERIT, INHERIT)
                        || (flags(parent[stream].handle) & INHERIT) != 0 || (flags(child[stream].handle) & INHERIT) == 0)
                    throw fail("pipe-inheritance");
                time();
            }
        }
        synchronized (lock) { if (closing) throw fail("stdio-preparation-revoked"); }
        } finally { synchronized (lock) { preparing = false; } }
    }
    public void attach(Pointer attributes, Pointer startup) {
        time(); synchronized (lock) {
            if (ledger.size() != 6 || attached || failed || closing) throw fail("stdio-not-prepared");
            preparing = true;
            inherited = new Memory(custodianMode?32:24); inherited.clear();
            for (int i = 0; i < 3; i++) inherited.setPointer(i * 8L, child[i].handle);
            if(custodianMode)inherited.setPointer(24,custodianOwner);
        }
        try {
        if (!api.UpdateProcThreadAttribute(attributes, 0, HANDLE_LIST, inherited, custodianMode?32:24, null, null)) throw fail("stdio-handle-list");
        startup.setInt(60, STD_HANDLES);
        for (int i = 0; i < 3; i++) startup.setPointer(80 + i * 8L, child[i].handle);
        synchronized (lock) { if (closing) throw fail("stdio-attachment-revoked"); attached = true; }
        } finally { synchronized (lock) { preparing = false; } }
    }
    public boolean canStart() { synchronized (lock) { return attached && !failed && !closing && !pendingStart && !returned; } }
    public void beginStart() {
        time(); synchronized (lock) {
            if (!canStart()) throw fail("stdio-start-not-available");
            pendingStart = true; for (End end : child) end.users++;
        }
    }
    public boolean returned() {
        synchronized (lock) {
            if (!pendingStart || returned) throw fail("stdio-unexpected-return");
            pendingStart = false; returned = true; for (End end : child) end.users--;
        }
        boolean closed = true; for (End end : child) closed &= close(end); return closed;
    }
    public boolean canResume() {
        synchronized (lock) { return returned && !failed && !closing && !pendingStart
                && java.util.Arrays.stream(parent).allMatch(e -> e != null && e.state == State.OPEN); }
    }
    public void attributesDeleted() { synchronized (lock) { deleted = true; releaseValue(); } }
    private void releaseValue() { if (inherited != null && deleted && !pendingStart) { inherited.close(); inherited = null; } }
    private End use(Stream stream, boolean write) {
        time(); synchronized (lock) {
            End end = parent[stream.ordinal()];
            if (end == null || end.state != State.OPEN || failed || closing || write != (stream == Stream.STDIN) || end.users != 0)
                throw fail("stdio-operation-not-available");
            end.users++; return end;
        }
    }
    private void unuse(End end) { synchronized (lock) { end.users--; } }
    Read read(Stream stream, int maximum) {
        if (maximum < 1 || maximum > MAX_IO || stream == Stream.STDIN) throw fail("stdio-read-bound");
        End end = use(stream, false);
        int limit = stream == Stream.STDERR ? STDERR_LIMIT : custodianMode?4096:MAX_IO;
        int count = Math.min(maximum, limit - totals[stream.ordinal()] + 1);
        try (Memory bytes = new Memory(count)) {
            IntByReference actual = new IntByReference();
            try (Memory reference = (Memory) actual.getPointer()) {
                boolean ok = api.ReadFile(end.handle, bytes, count, actual, null);
                int error = ok ? 0 : api.error(), got = actual.getValue(); time();
                synchronized (lock) { if (failed || closing) throw fail("stdio-read-revoked"); }
                if (!ok && error == 109 && got == 0) return new Read(new byte[0], true);
                if (!ok || got < 0 || got > count || got > limit - totals[stream.ordinal()]) throw fail("stdio-read-failed-or-overflow");
                if (got == 0) throw fail("stdio-read-no-progress");
                totals[stream.ordinal()] += got; return new Read(bytes.getByteArray(0, got), false);
            }
        } finally { unuse(end); }
    }
    int write(byte[] bytes) {
        if (bytes.length < 1 || bytes.length > MAX_IO || bytes.length > (custodianMode?4096:MAX_IO) - totals[0]) throw fail("stdio-write-bound");
        End end = use(Stream.STDIN, true);
        try (Memory memory = new Memory(bytes.length)) {
            memory.write(0, bytes, 0, bytes.length); IntByReference actual = new IntByReference();
            try (Memory reference = (Memory) actual.getPointer()) {
                boolean ok = api.WriteFile(end.handle, memory, bytes.length, actual, null); int written = actual.getValue(); time();
                synchronized (lock) { if (failed || closing) throw fail("stdio-write-revoked"); }
                if (!ok || written < 1 || written > bytes.length) throw fail("stdio-write-failed");
                totals[0] += written; return written;
            }
        } finally { unuse(end); }
    }
    private boolean close(End end) {
        synchronized (lock) {
            if (end.state == State.CLOSED) return true;
            if (end.state == State.UNCERTAIN || end.users != 0) return false;
            end.state = State.UNCERTAIN;
        }
        boolean ok;
        try { ok = api.CloseHandle(end.handle); } catch (RuntimeException failure) { synchronized (lock) { failed = true; } return false; }
        synchronized (lock) { if (ok) end.state = State.CLOSED; else failed = true; }
        return ok;
    }
    boolean closeAll() {
        List<End> own;
        synchronized (lock) { closing = true; if (preparing) return false; own = List.copyOf(ledger); }
        boolean done = true;
        for (End end : own) done &= close(end);
        synchronized (lock) { releaseValue(); return done && inherited == null; }
    }
    /** Halbabschluss ausschließlich der eigenen S-Eingabepipe; stdout/stderr bleiben für Endbelege offen. */
    boolean closeInput() {
        End end;synchronized(lock){if(pendingStart || preparing || parent[0]==null)return false;end=parent[0];}
        return close(end);
    }
    int heldHandles() { synchronized (lock) { return (int) ledger.stream().filter(e -> e.state != State.CLOSED).count(); } }
    int inFlight() { synchronized (lock) { return ledger.stream().mapToInt(e -> e.users).sum() + (preparing ? 1 : 0); } }
}
