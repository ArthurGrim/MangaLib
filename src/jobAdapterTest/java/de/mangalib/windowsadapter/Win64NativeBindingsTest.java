package de.mangalib.windowsadapter;

import com.sun.jna.Function;
import com.sun.jna.Library;
import de.mangalib.windowsadapter.Win64NativeBindings.Module;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static de.mangalib.windowsadapter.KernelCalls.*;
import static de.mangalib.windowsadapter.Win64NativeBindings.*;
import static org.junit.jupiter.api.Assertions.*;

/** Kein JnaLoader.open: DLL-Auflösung und sämtliche Operationen bleiben Java-Fakes. */
class Win64NativeBindingsTest {
    static Host host(String os, String arch, int pointer, int cLong, int wchar, int java, String jna,
                     String root, Map<String, String> properties) {
        return new Host(os, arch, pointer, cLong, wchar, java, jna, root, properties);
    }
    static Host host() { return host("Windows 11", "amd64", 8, 4, 2, 21, "5.17.0", "C:\\Windows", Map.of("jna.nosys", "true")); }

    final AtomicLong clock = new AtomicLong(100_000_000);
    final Win64ProcessBindingsTest.Raw raw = new Win64ProcessBindingsTest.Raw();
    final List<String> loadEvents = new ArrayList<>();
    String missing = "";
    boolean failOpen, failBind, nullModule, nullProxy, failUnload;
    int unloads, lastErrorReads;
    final Module module = new Module() {
        public void resolve(String name) {
            loadEvents.add(name);
            if (name.equals(missing)) throw new UnsatisfiedLinkError("synthetic missing symbol");
        }
        public Symbols bind() {
            loadEvents.add("bind");
            if (failBind) throw new UnsatisfiedLinkError("synthetic mapping failure");
            if (nullProxy) return null;
            return (Symbols) Proxy.newProxyInstance(Symbols.class.getClassLoader(), new Class<?>[]{Symbols.class}, (p, method, args) -> {
                String name = method.getName();
                return switch (name) {
                    case "CreateJobObjectW" -> { assertNull(args[0]); assertNull(args[1]); yield raw.create(); }
                    case "SetInformationJobObject" -> raw.set((com.sun.jna.Pointer) args[0], (int) args[1], (com.sun.jna.Pointer) args[2], (int) args[3]);
                    case "QueryInformationJobObject" -> raw.query((com.sun.jna.Pointer) args[0], (int) args[1], (com.sun.jna.Pointer) args[2], (int) args[3], (com.sun.jna.ptr.IntByReference) args[4]);
                    case "CloseHandle" -> raw.close((com.sun.jna.Pointer) args[0]);
                    case "GetLastError" -> throw new AssertionError("Must use saved thread error, never a new DLL call");
                    default -> {
                        try { yield raw.getClass().getMethod(name, method.getParameterTypes()).invoke(raw, args); }
                        catch (InvocationTargetException error) { throw error.getCause(); }
                    }
                };
            });
        }
        public int lastError() { lastErrorReads++; return raw.error; }
        public void closeAfterFailedPreparation() {
            unloads++; if (failUnload) throw new IllegalStateException("synthetic unload uncertainty");
        }
    };
    final Loader loader = (path, options) -> {
        assertEquals("C:\\Windows\\System32\\kernel32.dll", path);
        assertEquals(Map.of(Library.OPTION_CALLING_CONVENTION, Function.ALT_CONVENTION,
                Library.OPTION_CLASSLOADER, Symbols.class.getClassLoader()), options);
        loadEvents.add("open");
        if (failOpen) throw new UnsatisfiedLinkError("synthetic load failure");
        return nullModule ? null : module;
    };

    Win64NativeBindingsTest() { raw.clock = clock; }
    Win64ProcessKernelCalls port() {
        return new Win64ProcessKernelCalls(prepare(host(), loader),
                Win64ProcessBindingsTest.spec(List.of("SyntheticOnly")), clock::get);
    }
    OwnedJobAdapter owner(Object ticket, Win64ProcessKernelCalls port) {
        var owner = new OwnedJobAdapter(ticket, port, clock::get, 8);
        assertTrue(owner.initialize(ticket, new Limits(true, false, 0, 0, 0)));
        return owner;
    }

    @Test void classInitializationAndLoaderConstructionDoNotOpenAnyModule() throws Exception {
        Class.forName(Win64NativeBindings.class.getName(), true, Win64NativeBindings.class.getClassLoader());
        assertNotNull(new JnaLoader());
        assertTrue(loadEvents.isEmpty()); assertTrue(raw.events.isEmpty());
    }

    @Test void unsafePropertiesAreRejectedBeforeReadingJnaAbiOrLoadingItsDispatch() {
        assertThrows(IllegalArgumentException.class, () -> Host.detect("Windows 11", "amd64", 21,
                "C:\\Windows", Map.of("jna.nosys", "true", "jna.boot.library.path", "synthetic"),
                () -> { fail("Must not initialize JNA"); return null; }));
        assertThrows(IllegalArgumentException.class, () -> Host.detect("Linux", "amd64", 21,
                "C:\\Windows", Map.of("jna.nosys", "true"),
                () -> { fail("Must not initialize JNA"); return null; }));
        var detected = Host.detect("Windows 11", "amd64", 21, "C:\\Windows", Map.of("jna.nosys", "true"),
                () -> new Abi(8, 4, 2, "5.17.0"));
        assertEquals(host(), detected); assertTrue(loadEvents.isEmpty());
    }

    @Test void failedDispatchInitializationDoesNotReachTheKernel32Loader() {
        var failure = assertThrows(LoadFailure.class, () -> Host.detect("Windows 11", "amd64", 21,
                "C:\\Windows", Map.of("jna.nosys", "true"),
                () -> { throw new UnsatisfiedLinkError("synthetic dispatch load failure"); }));
        assertEquals("abi-dispatch", failure.phase); assertInstanceOf(UnsatisfiedLinkError.class, failure.getCause());
        assertTrue(loadEvents.isEmpty()); assertEquals(0, unloads);
    }

    static Stream<Host> wrongHosts() {
        return Stream.of(
                host("Linux", "amd64", 8, 4, 2, 21, "5.17.0", "C:\\Windows", Map.of("jna.nosys", "true")),
                host("Darwin", "amd64", 8, 4, 2, 21, "5.17.0", "C:\\Windows", Map.of("jna.nosys", "true")),
                host("Windows 11", "aarch64", 8, 4, 2, 21, "5.17.0", "C:\\Windows", Map.of("jna.nosys", "true")),
                host("Windows 11", "x86", 4, 4, 2, 21, "5.17.0", "C:\\Windows", Map.of("jna.nosys", "true")),
                host("Windows 11", "amd64", 8, 8, 2, 21, "5.17.0", "C:\\Windows", Map.of("jna.nosys", "true")),
                host("Windows 11", "amd64", 8, 4, 4, 21, "5.17.0", "C:\\Windows", Map.of("jna.nosys", "true")),
                host("Windows 11", "amd64", 8, 4, 2, 25, "5.17.0", "C:\\Windows", Map.of("jna.nosys", "true")),
                host("Windows 11", "amd64", 8, 4, 2, 21, "5.13.0", "C:\\Windows", Map.of("jna.nosys", "true")),
                host("Windows 11", "amd64", 8, 4, 2, 21, "5.17.0", "C:\\Temporary\\Windows", Map.of("jna.nosys", "true")),
                host("Windows 11", "amd64", 8, 4, 2, 21, "5.17.0", "relative", Map.of("jna.nosys", "true")));
    }
    @ParameterizedTest @MethodSource("wrongHosts")
    void rejectsUnsupportedEnvironmentBeforeAnyDllLoad(Host bad) {
        assertThrows(IllegalArgumentException.class, () -> prepare(bad, loader));
        assertTrue(loadEvents.isEmpty()); assertTrue(raw.events.isEmpty());
    }

    @ParameterizedTest @MethodSource("overrides")
    void rejectsLoadingOverridesIncludingEmptyValues(Map<String, String> properties) {
        var h = host(); var bad = host(h.os(), h.arch(), 8, 4, 2, 21, "5.17.0", h.systemRoot(), properties);
        assertThrows(IllegalArgumentException.class, () -> prepare(bad, loader)); assertTrue(loadEvents.isEmpty());
    }
    static Stream<Map<String, String>> overrides() {
        var cases = new ArrayList<Map<String, String>>();
        cases.add(Map.of()); cases.add(Map.of("jna.nosys", "false"));
        cases.add(Map.of("jna.nosys", "true", "unknown", "synthetic"));
        for (String key : OVERRIDES) {
            cases.add(Map.of("jna.nosys", "true", key, "synthetic"));
            cases.add(Map.of("jna.nosys", "true", key, ""));
        }
        return cases.stream();
    }

    @Test void resolvesAllExportsBeforeBindingWithoutInvokingThem() {
        var api = prepare(host(), loader);
        assertNotNull(api); assertEquals("open", loadEvents.getFirst()); assertEquals("bind", loadEvents.getLast());
        assertEquals(EXPORTS, loadEvents.subList(1, loadEvents.size() - 1));
        assertEquals(18, EXPORTS.size());
        assertEquals(EXPORTS.stream().sorted().toList(), java.util.Arrays.stream(Symbols.class.getMethods())
                .map(java.lang.reflect.Method::getName).sorted().toList());
        assertTrue(raw.events.isEmpty()); assertEquals(0, lastErrorReads); assertEquals(0, unloads);
    }

    @ParameterizedTest @ValueSource(strings = {"CreateProcessW", "CloseHandle", "WaitForSingleObject"})
    void missingExportClosesOnlyTheModuleAndNeverFallsBack(String symbol) {
        missing = symbol; var failure = assertThrows(LoadFailure.class, () -> prepare(host(), loader));
        assertEquals("exports", failure.phase); assertInstanceOf(UnsatisfiedLinkError.class, failure.getCause());
        assertEquals(1, unloads); assertEquals(1, loadEvents.stream().filter("open"::equals).count());
        assertFalse(loadEvents.contains("bind")); assertTrue(raw.events.isEmpty());
    }

    @Test void loaderFailureIsNeutralAndDoesNotCloseAnUnacquiredModule() {
        failOpen = true; var failure = assertThrows(LoadFailure.class, () -> prepare(host(), loader));
        assertEquals("load", failure.phase); assertEquals("Native binding preparation failed: load", failure.getMessage());
        assertEquals(0, unloads); assertTrue(raw.events.isEmpty());
    }

    @Test void missingModuleAndProxyCannotProducePreparedBindings() {
        nullModule = true; assertEquals("load", assertThrows(LoadFailure.class, () -> prepare(host(), loader)).phase);
        assertEquals(0, unloads);
        nullModule = false; nullProxy = true;
        assertEquals("bind", assertThrows(LoadFailure.class, () -> prepare(host(), loader)).phase);
        assertEquals(1, unloads); assertTrue(raw.events.isEmpty());
    }

    @Test void bindingAndUnloadFailuresBothRemainVisibleWithoutRetry() {
        failBind = true; failUnload = true;
        var failure = assertThrows(LoadFailure.class, () -> prepare(host(), loader));
        assertEquals("bind", failure.phase); assertInstanceOf(UnsatisfiedLinkError.class, failure.getCause());
        assertEquals(1, failure.getSuppressed().length); assertEquals(1, unloads); assertTrue(raw.events.isEmpty());
    }

    @Test void savedLastErrorRemainsSeparateFromExportedGetLastErrorFunction() {
        var api = prepare(host(), loader); raw.error = 8765;
        assertEquals(8765, api.error()); assertEquals(1, lastErrorReads); assertTrue(raw.events.isEmpty());
    }

    @Test void preparedBindingsReachExistingOwnerForASuspendedNonResumedFakeLifecycle() {
        var port = port(); Object ticket = new Object(); var owner = owner(ticket, port);
        assertNotNull(owner.start(ticket)); assertFalse(owner.pendingStart());
        assertEquals(1, raw.createCalls); assertEquals(0, raw.resumes); assertTrue(owner.inspect(ticket, 250_000_000));
        var report = owner.cleanup(ticket, 300_000_000);
        assertTrue(report.groupEndConfirmed()); assertTrue(report.resourcesClosed()); assertFalse(report.quarantineRequired());
        assertTrue(raw.events.contains("terminate")); assertTrue(raw.events.contains("wait"));
        assertEquals(0, port.retainedProcessHandles()); assertEquals(0, port.retainedLaunchBuffers());
        assertEquals(0, port.outstandingHandles()); assertEquals(0, port.outstandingBuffers());
        assertEquals(0, raw.resumes); assertEquals(0, unloads);
    }

    @ParameterizedTest @ValueSource(strings = {"init", "associate"})
    void incompleteNativeInitializationNeverCallsCreate(String operation) {
        var port = port(); Object ticket = new Object(); var owner = owner(ticket, port);
        raw.failure = operation; owner.start(ticket);
        assertEquals(0, raw.createCalls); var report = owner.cleanup(ticket, 300_000_000);
        assertTrue(report.resourcesClosed()); assertFalse(report.quarantineRequired()); assertEquals(0, raw.resumes);
    }

    @Test void failedIdentityAfterSuspendedCreateRetainsHandlesAndQuarantines() {
        var port = port(); Object ticket = new Object(); var owner = owner(ticket, port);
        raw.failure = "image"; owner.start(ticket);
        assertFalse(owner.resume(ticket)); assertEquals(2, port.retainedProcessHandles());
        var report = owner.cleanup(ticket, 300_000_000);
        assertTrue(report.quarantineRequired()); assertFalse(report.resourcesClosed());
        assertTrue(raw.events.contains("terminate")); assertEquals(0, raw.resumes);
    }

    @Test void invalidOrLostCapabilityNeverCallsNativeCloseOrIdentifiesByNumericValue() {
        var port = port(); var scope = new Scope(); var job = port.createJob(scope);
        int before = raw.events.size();
        assertThrows(Failure.class, () -> port.identity(new Resource(scope, Kind.PROCESS, 123)));
        assertThrows(Failure.class, () -> port.close(new Resource(scope, Kind.JOB, job.debugValue)));
        assertEquals(before, raw.events.size()); port.close(job);
    }

    @Test void successfulTerminateWithoutSignalOrEmptyPopulationRemainsQuarantined() {
        var port = port(); Object ticket = new Object(); var owner = owner(ticket, port);
        owner.start(ticket); raw.noEnd = true;
        var report = owner.cleanup(ticket, 300_000_000);
        assertFalse(report.groupEndConfirmed()); assertFalse(report.resourcesClosed()); assertTrue(report.quarantineRequired());
        assertTrue(raw.events.contains("terminate")); assertEquals(0, raw.resumes);
    }

    @Test void completionAfterSharedCleanupDeadlineNeverResumesOrExtendsTheBudget() {
        var port = port(); Object ticket = new Object(); var owner = owner(ticket, port);
        raw.throwStart = true; var permit = owner.start(ticket); assertTrue(owner.pendingStart());
        var pending = owner.cleanup(ticket, 300_000_000); assertTrue(pending.quarantineRequired());
        // Derselbe bereits gehaltene Frame wie im bestehenden Binding-Test, kein OS-Zugriff.
        Resource memory = memory(port); clock.set(301_000_000);
        var result = port.completeReturn(memory, Win64ProcessKernelCalls.NativeReturn.CREATED, 0);
        assertFalse(owner.startReturned(new Object(), permit, result));
        assertTrue(owner.startReturned(ticket, permit, result)); assertFalse(owner.resume(ticket));
        var late = owner.cleanup(ticket, 900_000_000);
        assertTrue(late.quarantineRequired()); assertFalse(late.resourcesClosed()); assertEquals(0, raw.resumes);
        assertFalse(raw.events.contains("terminate")); assertTrue(port.retainedLaunchBuffers() > 0);
    }

    @Test void unclearResourceCloseDoesNotPermitADoubleCloseOrJobRelease() {
        var port = port(); Object ticket = new Object(); var owner = owner(ticket, port);
        owner.start(ticket); raw.closeOk = false;
        var report = owner.cleanup(ticket, 300_000_000);
        assertTrue(report.groupEndConfirmed()); assertFalse(report.resourcesClosed()); assertTrue(report.quarantineRequired());
        int events = raw.events.size(); assertSame(report, owner.cleanup(ticket, 900_000_000));
        assertEquals(events, raw.events.size()); assertFalse(raw.events.contains("close:273"));
    }

    static Resource memory(Win64ProcessKernelCalls port) {
        try {
            var field = Win64ProcessKernelCalls.class.getDeclaredField("frame"); field.setAccessible(true);
            var frame = field.get(port); var memory = frame.getClass().getDeclaredField("memory"); memory.setAccessible(true);
            return (Resource) memory.get(frame);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
}
