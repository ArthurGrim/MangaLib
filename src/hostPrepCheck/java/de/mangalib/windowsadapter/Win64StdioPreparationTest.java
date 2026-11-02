package de.mangalib.windowsadapter;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static de.mangalib.windowsadapter.KernelCalls.*;
import static de.mangalib.windowsadapter.Win64StdioChannels.*;
import static org.junit.jupiter.api.Assertions.*;

/** Win32 endet IMMER in Raw-Fakes. JNA wird nur für begrenzten eigenen Speicher verwendet. */
class Win64StdioPreparationTest {
    static final Pointer JOB = new Pointer(0x111);
    static final String EXE = "C:\\Trusted\\java.exe", DIR = "C:\\Isolated\\probe";
    static final class Raw implements Api {
        final Map<Pointer, Integer> flags = new LinkedHashMap<>();
        final List<Pointer> closed = new ArrayList<>();
        final List<Pointer> inherited = new ArrayList<>();
        long serial = 1000, now;
        int pipes, pipeFailure, error, got = 1, written = -1, closeCalls, creates, resumes, attributeCount, exit;
        boolean setFailure, readFailure, readEof, zeroRead, closeFailure, listFailure, exited, exitFailure;
        String bad = "";
        CountDownLatch entered, release;
        Raw() { flags.put(JOB, 0); }
        void pause() {
            if (entered == null) return;
            entered.countDown();
            try { if (!release.await(3, TimeUnit.SECONDS)) throw new Failure("fake-timeout", 0); }
            catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new Failure("fake-interrupted", 0); }
        }
        public boolean CreatePipe(Pointer read, Pointer write, Pointer security, int size) {
            assertNull(security); assertEquals(1024, size); pipes++; pause();
            Pointer r = bad.equals("job") ? JOB : new Pointer(serial++);
            Pointer w = bad.equals("duplicate") ? r : new Pointer(serial++);
            read.setPointer(0, r); flags.putIfAbsent(r, 0);
            if (!bad.equals("partial")) { write.setPointer(0, w); flags.putIfAbsent(w, 0); }
            return pipes != pipeFailure && !bad.equals("partial");
        }
        public boolean SetHandleInformation(Pointer handle, int mask, int value) {
            assertEquals(INHERIT, mask); if (setFailure) return false;
            flags.put(handle, value); return true;
        }
        public boolean GetHandleInformation(Pointer handle, IntByReference value) { value.setValue(flags.getOrDefault(handle, 0)); return !bad.equals("get"); }
        public boolean ReadFile(Pointer handle, Pointer bytes, int count, IntByReference actual, Pointer overlapped) {
            assertNull(overlapped); assertTrue(count > 0 && count <= MAX_IO); pause();
            if (readEof) { error = 109; actual.setValue(0); return false; }
            if (readFailure) { error = 5; return false; }
            int read = zeroRead ? 0 : Math.min(count, got); for (int i = 0; i < read; i++) bytes.setByte(i, (byte) 65);
            actual.setValue(read); return true;
        }
        public boolean WriteFile(Pointer handle, Pointer bytes, int count, IntByReference actual, Pointer overlapped) {
            assertNull(overlapped); assertTrue(count > 0 && count <= MAX_IO); pause(); actual.setValue(written < 0 ? count : written); return true;
        }
        public boolean CloseHandle(Pointer handle) { assertNotEquals(JOB, handle); closeCalls++; if (closeFailure) return false; assertFalse(closed.contains(handle)); closed.add(handle); return true; }
        public boolean UpdateProcThreadAttribute(Pointer list, int flags, long attribute, Pointer value, long size, Pointer old, Pointer returned) {
            assertEquals(HANDLE_LIST, attribute); assertEquals(24, size); assertNull(old); assertNull(returned);
            inherited.clear(); for (int i = 0; i < 3; i++) inherited.add(value.getPointer(i * 8L));
            assertEquals(3, inherited.stream().distinct().count()); assertFalse(inherited.contains(JOB));
            inherited.forEach(p -> assertEquals(INHERIT, this.flags.get(p))); return !listFailure;
        }
        public int error() { return error; }
        Win64ProcessKernelCalls.ProcessRawApi process() {
            return (Win64ProcessKernelCalls.ProcessRawApi) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{Win64ProcessKernelCalls.ProcessRawApi.class}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "create": return JOB;
                    case "set": return true;
                    case "close": return true; // Nur synthetische Prozess-/Jobwerte, keine OS-Aufrufe.
                    case "error": return error;
                    case "createReturned": return true;
                    case "query": {
                        Pointer data = (Pointer) args[2]; int type = (int) args[1]; IntByReference size = (IntByReference) args[4];
                        if (type == 9) { data.setInt(16, 0x2000); size.setValue(144); }
                        if (type == 1) { data.setInt(36, creates); data.setInt(40, exited ? 0 : creates); data.setInt(44, exited ? creates : 0); size.setValue(48); }
                        if (type == 3) { int live = exited ? 0 : creates; data.setInt(0, live); data.setInt(4, live); if (live != 0) data.setLong(8, 41); size.setValue(8 + live * 8); }
                        return true;
                    }
                    case "InitializeProcThreadAttributeList":
                        attributeCount = (int) args[1]; assertEquals(2, attributeCount);
                        if (args[0] == null) { ((Pointer) args[3]).setLong(0, 128); error = 122; return false; } return true;
                    case "UpdateProcThreadAttribute": assertEquals(Win64ProcessLayouts.JOB_LIST, args[2]); assertEquals(JOB, ((Pointer) args[3]).getPointer(0)); return true;
                    case "DeleteProcThreadAttributeList": return null;
                    case "CreateProcessW": {
                        assertTrue((boolean) args[4]); assertEquals(Win64ProcessLayouts.START_FLAGS, args[5]);
                        Pointer startup = (Pointer) args[8], info = (Pointer) args[9];
                        assertEquals(STD_HANDLES, startup.getInt(60));
                        for (int i = 0; i < 3; i++) assertEquals(inherited.get(i), startup.getPointer(80 + i * 8L));
                        info.setPointer(0, new Pointer(0x222)); info.setPointer(8, new Pointer(0x333)); info.setInt(16, 41); info.setInt(20, 42);
                        creates++; return true;
                    }
                    case "GetProcessId": return 41;
                    case "GetProcessTimes": ((Pointer) args[1]).setLong(0, 123); return true;
                    case "QueryFullProcessImageNameW": ((Pointer) args[2]).write(0, (EXE + '\0').getBytes(StandardCharsets.UTF_16LE), 0, (EXE.length() + 1) * 2); ((IntByReference) args[3]).setValue(EXE.length()); return true;
                    case "IsProcessInJob": ((IntByReference) args[2]).setValue(1); return true;
                    case "ResumeThread": resumes++; return 1;
                    case "WaitForSingleObject": return exited ? 0 : 258;
                    case "GetExitCodeProcess": ((IntByReference) args[1]).setValue(exit); return !exitFailure;
                    case "TerminateJobObject": exited = true; return true;
                    default: throw new AssertionError("unexpected-fake-call:" + method.getName());
                }
            });
        }
    }
    static final class Fixture implements AutoCloseable {
        final Raw raw = new Raw();
        final Win64StdioChannels pipes = new Win64StdioChannels(raw, () -> raw.now, 1000);
        final Memory attributes = new Memory(128), startup = new Memory(112);
        void prepare() { attributes.clear(); startup.clear(); pipes.prepare(JOB); pipes.attach(attributes, startup); }
        @Override public void close() { pipes.attributesDeleted(); pipes.closeAll(); startup.close(); attributes.close(); }
    }

    @Test void exactChildListStartupAndNonInheritedParents() {
        try (Fixture f = new Fixture()) {
            f.prepare(); assertEquals(6, f.pipes.heldHandles()); assertEquals(3, f.raw.inherited.size());
            assertEquals(3, f.raw.flags.values().stream().filter(v -> v == INHERIT).count());
            assertEquals(STD_HANDLES, f.startup.getInt(60)); assertTrue(f.pipes.canStart());
            f.pipes.beginStart(); assertFalse(f.pipes.closeAll()); assertEquals(3, f.pipes.heldHandles());
            assertTrue(f.pipes.returned()); assertFalse(f.pipes.canResume()); // Parentenden schon geschlossen.
        }
    }
    @ParameterizedTest @ValueSource(ints = {1, 2, 3})
    void everyPartialPipeCreationRemainsInLedger(int pipe) {
        try (Fixture f = new Fixture()) {
            f.raw.pipeFailure = pipe; assertThrows(Failure.class, f::prepare);
            assertEquals(pipe * 2, f.pipes.heldHandles()); assertTrue(f.pipes.closeAll()); assertEquals(0, f.pipes.heldHandles());
        }
    }
    @ParameterizedTest @ValueSource(strings = {"job", "duplicate", "partial", "get"})
    void invalidHandlesAreNeverAdoptedAsJobAndOtherPartialEndsRemainTracked(String invalid) {
        try (Fixture f = new Fixture()) {
            f.raw.bad = invalid; assertThrows(Failure.class, f::prepare);
            f.pipes.closeAll(); assertFalse(f.raw.closed.contains(JOB)); assertFalse(f.pipes.canStart());
        }
    }
    @ParameterizedTest @ValueSource(ints = {0, 1, 2})
    void inheritanceOrHandleListFailuresPreventStart(int failure) {
        try (Fixture f = new Fixture()) {
            if (failure == 0) f.raw.flags.put(JOB, INHERIT);
            if (failure == 1) f.raw.setFailure = true;
            if (failure == 2) f.raw.listFailure = true;
            assertThrows(Failure.class, f::prepare); assertFalse(f.pipes.canStart());
        }
    }
    @Test void earlyAndRegularEofAreOnlyReadEvidenceAndNotResourceClosure() {
        for (boolean afterData : new boolean[]{false, true}) try (Fixture f = new Fixture()) {
            f.prepare(); f.pipes.beginStart(); assertTrue(f.pipes.returned());
            if (afterData) assertEquals(1, f.pipes.read(Stream.STDOUT, 7).bytes().length);
            f.raw.readEof = true; assertTrue(f.pipes.read(Stream.STDOUT, 7).eof());
            assertEquals(3, f.pipes.heldHandles()); // Keine Gruppen- oder Handleendbehauptung.
        }
    }
    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3})
    void ioErrorsZeroProgressLimitsAndDeadlinesAreTerminal(int failure) {
        try (Fixture f = new Fixture()) {
            f.prepare(); f.pipes.beginStart(); f.pipes.returned();
            if (failure == 0) f.raw.readFailure = true;
            if (failure == 1) f.raw.zeroRead = true;
            if (failure == 2) f.raw.now = 1000;
            assertThrows(Failure.class, () -> f.pipes.read(Stream.STDOUT, failure == 3 ? 513 : 7));
            assertFalse(f.pipes.canResume());
        }
    }
    @Test void stderrAndWritesCannotGrowPastFixedBudgets() {
        try (Fixture f = new Fixture()) {
            f.prepare(); f.pipes.beginStart(); f.pipes.returned(); f.raw.got = 256;
            assertEquals(256, f.pipes.read(Stream.STDERR, 256).bytes().length);
            assertThrows(Failure.class, () -> f.pipes.read(Stream.STDERR, 1));
        }
        try (Fixture f = new Fixture()) {
            f.prepare(); f.pipes.beginStart(); f.pipes.returned(); f.raw.written = 2;
            assertEquals(2, f.pipes.write(new byte[7]));
            assertThrows(Failure.class, () -> f.pipes.write(new byte[513]));
        }
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void blockedReadOrWriteDoesNotBlockCloseDecisionAndBufferRemainsHeld(boolean write) throws Exception {
        try (Fixture f = new Fixture()) {
            f.prepare(); f.pipes.beginStart(); f.pipes.returned(); f.raw.entered = new CountDownLatch(1); f.raw.release = new CountDownLatch(1);
            var failure = new AtomicReference<Throwable>();
            Thread io = new Thread(() -> { try { assertThrows(Failure.class, () -> { if (write) f.pipes.write(new byte[]{1}); else f.pipes.read(Stream.STDOUT, 7); }); } catch (Throwable error) { failure.set(error); } });
            io.setDaemon(true); io.start();
            try {
                assertTrue(f.raw.entered.await(2, TimeUnit.SECONDS)); assertEquals(1, f.pipes.inFlight());
                assertFalse(f.pipes.closeAll()); assertEquals(1, f.pipes.heldHandles());
            } finally { f.raw.release.countDown(); io.join(2500); if (io.isAlive()) { io.interrupt(); io.join(500); } }
            assertFalse(io.isAlive()); assertNull(failure.get());
            assertFalse(f.pipes.closeAll()); assertEquals(0, f.pipes.heldHandles());
            // HANDLE_LIST-Backing bleibt bis DeleteProcThreadAttributeList lebendig.
            f.pipes.attributesDeleted(); assertTrue(f.pipes.closeAll());
        }
    }
    @Test void unknownCloseIsNeverRetried() {
        try (Fixture f = new Fixture()) {
            f.prepare(); f.raw.closeFailure = true; assertFalse(f.pipes.closeAll()); int calls = f.raw.closeCalls;
            f.raw.closeFailure = false; assertFalse(f.pipes.closeAll()); assertEquals(calls, f.raw.closeCalls);
        }
    }

    @Test void blockedPipeCreationRetainsPreparationAndNeverHoldsCleanupMonitor() throws Exception {
        try (Fixture f = new Fixture()) {
            f.raw.entered = new CountDownLatch(1); f.raw.release = new CountDownLatch(1);
            var error = new AtomicReference<Throwable>();
            Thread start = new Thread(() -> { try { assertThrows(Failure.class, f::prepare); } catch (Throwable failure) { error.set(failure); } });
            start.setDaemon(true); start.start();
            try { assertTrue(f.raw.entered.await(2, TimeUnit.SECONDS)); assertFalse(f.pipes.closeAll()); assertEquals(1, f.pipes.inFlight()); }
            finally { f.raw.release.countDown(); start.join(2500); if (start.isAlive()) { start.interrupt(); start.join(500); } }
            assertFalse(start.isAlive()); assertNull(error.get()); assertFalse(f.pipes.canStart());
            assertTrue(f.pipes.closeAll()); assertEquals(0, f.pipes.heldHandles());
        }
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3})
    void exitCodeRequiresTheHeldSignaledRootAndPropagatesNativeFailure(int scenario) {
        Raw raw = new Raw(); var pipes = new Win64StdioChannels(raw, () -> raw.now, 1000);
        var root = new AtomicReference<Resource>();
        var authority = new Win64ProcessKernelCalls.StartAuthorization() {
            public boolean beforeCreate(Resource job) { return true; }
            public boolean created(Resource job, Resource held) { root.set(held); return true; }
            public boolean beforeResume(Resource job, Resource held) { return true; }
            public boolean afterResume(Resource job, Resource held) { return true; }
        };
        var spec = new Win64LaunchSpec(EXE, List.of("synthetic"), DIR, Map.of("SystemRoot", "C:\\Windows", "TEMP", DIR, "TMP", DIR), 1000);
        var port = new Win64ProcessKernelCalls(raw.process(), spec, () -> raw.now, pipes, authority);
        Object ticket = new Object(); var owned = new OwnedJobAdapter(ticket, port, () -> raw.now, 8);
        assertTrue(owned.initialize(ticket, new Limits(true, false, 0, 0, 0))); owned.start(ticket);
        try {
            assertNotNull(root.get()); raw.exited = scenario != 0; raw.exitFailure = scenario == 1; raw.exit = scenario == 2 ? 37 : 259;
            if (scenario < 2) assertThrows(Failure.class, () -> port.exitCode(root.get()));
            else assertEquals(raw.exit, port.exitCode(root.get())); // 259 nach Signal ist ein echter Exitcode, kein STILL_ACTIVE-Beweis.
        } finally {
            raw.exited = true; pipes.closeAll(); var end = owned.cleanup(ticket, 900);
            if (scenario == 1) {
                assertFalse(end.groupEndConfirmed()); assertTrue(end.quarantineRequired());
                assertFalse(end.resourcesClosed()); assertFalse(pipes.closeAll());
            } else assertTrue(pipes.closeAll());
        }
    }

    @ParameterizedTest @ValueSource(ints = {-1, 0, 1, 2, 3, 4})
    void stdioExportPreparationUsesOnlyInjectedLoaderAndNoSymbolFallback(int missing) {
        var host = new Win64NativeBindings.Host("Windows 11", "amd64", 8, 4, 2, 21, "5.17.0", "C:\\Windows", Map.of("jna.nosys", "true"));
        var resolved = new ArrayList<String>(); boolean[] closed = {false}, bound = {false};
        Win64NativeBindings.StdioLoader loader = (dll, options) -> {
            assertEquals("C:\\Windows\\System32\\kernel32.dll", dll);
            return new Win64NativeBindings.StdioModule() {
                public void resolve(String symbol) {
                    resolved.add(symbol);
                    if (missing >= 0 && symbol.equals(Win64NativeBindings.STDIO_EXPORTS.get(missing))) throw new IllegalStateException("synthetic-missing-export");
                }
                public Win64NativeBindings.StdioSymbols bind() {
                    bound[0] = true;
                    return (Win64NativeBindings.StdioSymbols) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Win64NativeBindings.StdioSymbols.class},
                            (proxy, method, args) -> { throw new AssertionError("preparation-must-not-call-win32"); });
                }
                public int lastError() { return 5; }
                public void closeAfterFailedPreparation() { closed[0] = true; }
            };
        };
        if (missing >= 0) { assertThrows(Win64NativeBindings.LoadFailure.class, () -> Win64NativeBindings.prepareStdio(host, loader)); assertTrue(closed[0]); assertFalse(bound[0]); }
        else {
            var prepared = Win64NativeBindings.prepareStdio(host, loader);
            assertNotNull(prepared.process()); assertEquals(5, prepared.stdio().error()); assertTrue(bound[0]); assertFalse(closed[0]);
            assertEquals(23, resolved.size()); assertEquals(23, resolved.stream().distinct().count());
        }
        assertEquals(Win64NativeBindings.EXPORTS, resolved.subList(0, 18));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void nativeProcessUsesTwoAttributesAndMandatoryIntentAuthority(boolean authorized) {
        Raw raw = new Raw(); var pipes = new Win64StdioChannels(raw, () -> raw.now, 1000);
        var authority = new Win64ProcessKernelCalls.StartAuthorization() {
            public boolean beforeCreate(Resource job) { return authorized; }
            public boolean created(Resource job, Resource root) { return authorized; }
            public boolean beforeResume(Resource job, Resource root) { return authorized; }
            public boolean afterResume(Resource job, Resource root) { return authorized; }
        };
        var spec = new Win64LaunchSpec(EXE, List.of("synthetic"), DIR, Map.of("SystemRoot", "C:\\Windows", "TEMP", DIR, "TMP", DIR), 1000);
        var port = new Win64ProcessKernelCalls(raw.process(), spec, () -> raw.now, pipes, authority);
        Object ticket = new Object(); var owned = new OwnedJobAdapter(ticket, port, () -> raw.now, 8);
        assertTrue(owned.initialize(ticket, new Limits(true, false, 0, 0, 0))); owned.start(ticket);
        try {
            assertEquals(2, raw.attributeCount); assertEquals(authorized ? 1 : 0, raw.creates);
            if (authorized) {
                assertEquals(3, pipes.heldHandles()); assertTrue(owned.resume(ticket)); assertEquals(1, raw.resumes);
                // Rootcapability erhalten, keine neue PID-Suche für Exitcode.
                Resource held = new Resource(new Scope(), Kind.PROCESS, 2);
                assertThrows(Failure.class, () -> port.exitCode(held));
            } else assertFalse(owned.resume(ticket));
        } finally {
            raw.exited = true; pipes.closeAll(); owned.cleanup(ticket, 900); assertTrue(pipes.closeAll());
        }
    }
}
