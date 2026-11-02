package de.mangalib.windowsadapter;

import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.ptr.IntByReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static de.mangalib.windowsadapter.KernelCalls.*;
import static de.mangalib.windowsadapter.Win64ProcessLayouts.*;
import static de.mangalib.windowsadapter.Win64ProcessKernelCalls.*;
import static org.junit.jupiter.api.Assertions.*;

/** Alle Win32-Aufrufe enden im lokalen Raw-Fake, niemals Native.load oder echte Prozesse. */
class Win64ProcessBindingsTest {
    static final String EXE = "C:\\Trusted\\java.exe", DIR = "C:\\Isolated\\run";
    static final Map<String, String> ENV = Map.of("SystemRoot", "C:\\Windows", "TEMP", DIR, "TMP", DIR);
    static final class Instance {
        long pid, birth = 123;
        String image = EXE;
        boolean member = true, ended;
        Instance(long pid) { this.pid = pid; }
    }

    static final class Raw implements ProcessRawApi {
        final Pointer job = new Pointer(0x111), root = new Pointer(0x222), thread = new Pointer(0x333);
        final List<String> events = new ArrayList<>();
        final Map<Pointer, Instance> instances = new LinkedHashMap<>();
        final List<Long> pids = new ArrayList<>();
        final List<Integer> waits = new ArrayList<>();
        String failure = "", application, command, environment, directory;
        Pointer info, attributes, jobArray;
        boolean returned = true, created = true, partial, throwStart, drift, noEnd, closeOk = true, missingThread;
        int error = 5, resumes, previousCount = 1, createCalls, deletes, accountingCalls, total;
        int access, waitStatus = WAIT_OBJECT, membershipValue = 1, imageLengthOverride;
        long size = 96, nowAfterCreate, advanceOnWait;
        AtomicLong clock;
        Raw() { instances.put(root, new Instance(41)); }
        boolean ok(String operation) { events.add(operation); return !failure.equals(operation); }
        @Override public Pointer create() { events.add("job"); return job; }
        @Override public boolean set(Pointer j, int type, Pointer p, int size) {
            assertEquals(job, j); assertEquals(9, type); assertEquals(144, size); assertEquals(0x2000, p.getInt(16));
            return ok("set");
        }
        @Override public boolean query(Pointer j, int type, Pointer p, int size, IntByReference returned) {
            assertEquals(job, j);
            if (!ok("query:" + type)) return false;
            if (type == 9) { p.setInt(16, 0x2000); returned.setValue(144); }
            if (type == 1) {
                accountingCalls++;
                p.setInt(36, total); p.setInt(40, pids.size() + (drift && accountingCalls % 2 == 0 ? 1 : 0));
                p.setInt(44, pids.isEmpty() ? total : 0); returned.setValue(48);
            }
            if (type == 3) {
                p.setInt(0, pids.size());
                if (size < 8 + 8 * pids.size()) { error = 234; return false; }
                p.setInt(4, pids.size());
                for (int i = 0; i < pids.size(); i++) p.setLong(8L + 8L * i, pids.get(i));
                returned.setValue(8 + 8 * pids.size());
            }
            return true;
        }
        @Override public boolean close(Pointer p) { events.add("close:" + Pointer.nativeValue(p)); return closeOk; }
        @Override public int error() { return error; }
        @Override public boolean InitializeProcThreadAttributeList(Pointer list, int count, int flags, Pointer bytes) {
            assertEquals(1, count); assertEquals(0, flags);
            if (list == null) { events.add("size"); bytes.setLong(0, size); error = 122; return false; }
            attributes = list; return ok("init");
        }
        @Override public boolean UpdateProcThreadAttribute(Pointer list, int flags, long attribute, Pointer value,
                                                            long bytes, Pointer previous, Pointer returned) {
            assertEquals(attributes, list); assertEquals(0, flags); assertEquals(JOB_LIST, attribute);
            assertEquals(8, bytes); assertNull(previous); assertNull(returned); assertEquals(job, value.getPointer(0));
            jobArray = value; return ok("associate");
        }
        @Override public void DeleteProcThreadAttributeList(Pointer list) {
            assertEquals(attributes, list); deletes++;
            if (!ok("delete")) throw new Failure("delete", 5);
        }
        @Override public boolean CreateProcessW(WString app, Pointer cmd, Pointer ps, Pointer ts, boolean inherit,
                                                int flags, Pointer env, WString cwd, Pointer startup, Pointer pi) {
            createCalls++; events.add("create");
            assertTrue(events.indexOf("associate") < events.indexOf("create"));
            assertNull(ps); assertNull(ts); assertFalse(inherit); assertEquals(START_FLAGS, flags);
            assertEquals(112, startup.getInt(0)); assertEquals(attributes, startup.getPointer(104));
            assertEquals(job, jobArray.getPointer(0));
            assertNull(startup.getPointer(80)); assertNull(startup.getPointer(88)); assertNull(startup.getPointer(96));
            application = app.toString(); command = cmd.getWideString(0); directory = cwd.toString();
            var envText = new StringBuilder();
            for (long offset = 0;; offset += 2) {
                char c = env.getChar(offset); envText.append(c);
                if (c == 0 && env.getChar(offset + 2) == 0) { envText.append('\0'); break; }
            }
            environment = envText.toString(); info = pi;
            if (created || partial) writeHandles();
            if (nowAfterCreate > 0) clock.set(nowAfterCreate);
            if (throwStart) throw new Failure("unreturned-native-call", 7);
            return created;
        }
        void writeHandles() {
            info.setPointer(0, root); info.setPointer(8, missingThread ? null : thread);
            info.setInt(16, 41); info.setInt(20, 42);
            pids.clear(); pids.add(41L); total = 1;
        }
        @Override public boolean createReturned() { return returned; }
        @Override public int ResumeThread(Pointer t) { assertEquals(thread, t); resumes++; events.add("resume"); return previousCount; }
        @Override public Pointer OpenProcess(int rights, boolean inherit, int pid) {
            access = rights; assertFalse(inherit);
            if (!ok("open")) return null;
            Pointer pointer = new Pointer(1000L + Integer.toUnsignedLong(pid));
            instances.computeIfAbsent(pointer, p -> new Instance(Integer.toUnsignedLong(pid)));
            return pointer;
        }
        @Override public int GetProcessId(Pointer p) { return failure.equals("pid") ? 0 : (int) instances.get(p).pid; }
        @Override public boolean QueryFullProcessImageNameW(Pointer p, int flags, Pointer image, IntByReference length) {
            assertEquals(0, flags); assertEquals(32768, length.getValue());
            if (!ok("image")) return false;
            image.setWideString(0, instances.get(p).image);
            length.setValue(imageLengthOverride == 0 ? instances.get(p).image.length() : imageLengthOverride); return true;
        }
        @Override public boolean GetProcessTimes(Pointer p, Pointer born, Pointer exit, Pointer kernel, Pointer user) {
            if (!ok("times")) return false; born.setLong(0, instances.get(p).birth); return true;
        }
        @Override public boolean GetExitCodeProcess(Pointer p, IntByReference code) { code.setValue(259); return ok("exit"); }
        @Override public int WaitForSingleObject(Pointer p, int millis) {
            waits.add(millis); events.add("wait");
            if (advanceOnWait > 0) clock.addAndGet(advanceOnWait);
            return noEnd ? WAIT_TIMEOUT : waitStatus;
        }
        @Override public boolean IsProcessInJob(Pointer p, Pointer j, IntByReference member) {
            assertEquals(job, j);
            member.setValue(instances.get(p).member ? membershipValue : 0); return ok("member");
        }
        @Override public boolean TerminateJobObject(Pointer j, int code) {
            assertEquals(job, j); assertEquals(1, code);
            if (!ok("terminate")) return false;
            if (!noEnd) { pids.clear(); instances.values().forEach(i -> i.ended = true); } return true;
        }
    }

    final AtomicLong clock = new AtomicLong(100_000_000);
    final Raw raw = new Raw();
    final Win64LaunchSpec spec = spec(List.of("-cp", "C:\\Space Path\\classes", "SyntheticOnly", ""));
    final Win64ProcessKernelCalls port = new Win64ProcessKernelCalls(raw, spec, clock::get);
    final Object ticket = new Object();
    final OwnedJobAdapter owner = new OwnedJobAdapter(ticket, port, clock::get, 8);
    Win64ProcessBindingsTest() { raw.clock = clock; }
    static Win64LaunchSpec spec(List<String> args) { return new Win64LaunchSpec(EXE, args, DIR, ENV, 200_000_000); }
    void initialize() { assertTrue(owner.initialize(ticket, new Limits(true, false, 0, 0, 0))); }
    void start() { initialize(); assertNotNull(owner.start(ticket)); }
    void cleaned() {
        var report = owner.cleanup(ticket, 300_000_000);
        assertTrue(report.groupEndConfirmed(), report.toString());
        assertTrue(report.resourcesClosed(), report.toString());
        assertFalse(report.quarantineRequired(), report.toString());
        assertEquals(0, port.retainedProcessHandles()); assertEquals(0, port.retainedLaunchBuffers());
        assertEquals(0, port.outstandingBuffers()); assertEquals(0, port.outstandingHandles());
    }

    @Test void verifiesAbiAndNativeSignaturesWithoutLoadingKernel32() {
        Win64ProcessLayouts.verify(); assertEquals(0x2000d, JOB_LIST); assertEquals(0x8080404, START_FLAGS);
        assertEquals(0x101000, QUERY_AND_SYNCHRONIZE);
        assertEquals(13, ProcessFunctions.class.getDeclaredMethods().length);
        assertTrue(raw.events.isEmpty());
    }

    @Test void atomicallyAssociatesBeforeCreateAndRequiresExplicitResume() {
        start(); assertEquals(1, raw.createCalls); assertEquals(0, raw.resumes);
        assertEquals(EXE, raw.application); assertEquals(DIR, raw.directory);
        assertEquals("\"C:\\Trusted\\java.exe\" \"-cp\" \"C:\\Space Path\\classes\" \"SyntheticOnly\" \"\"", raw.command);
        assertEquals("SystemRoot=C:\\Windows\0TEMP=C:\\Isolated\\run\0TMP=C:\\Isolated\\run\0\0", raw.environment);
        assertTrue(owner.resume(ticket)); assertFalse(owner.resume(ticket)); assertEquals(1, raw.resumes);
        cleaned(); assertEquals(1, raw.deletes);
        assertTrue(raw.events.indexOf("delete") > raw.events.indexOf("create"));
        assertEquals("close:273", raw.events.getLast());
    }

    @Test void preparedDllBridgeDelegatesEntireLifecycleOnlyToInjectedFakes() {
        var bridge = new ProcessBridge(raw, raw);
        var bridgedPort = new Win64ProcessKernelCalls(bridge, spec, clock::get);
        var bridgedOwner = new OwnedJobAdapter(ticket, bridgedPort, clock::get, 8);
        assertTrue(bridgedOwner.initialize(ticket, new Limits(true, false, 0, 0, 0)));
        assertNotNull(bridgedOwner.start(ticket)); assertTrue(bridgedOwner.resume(ticket));
        raw.pids.add(50L); raw.total++;
        assertTrue(bridgedOwner.inspect(ticket, 250_000_000));
        var report = bridgedOwner.cleanup(ticket, 300_000_000);
        assertTrue(report.groupEndConfirmed()); assertTrue(report.resourcesClosed()); assertFalse(report.quarantineRequired());
        assertEquals(1, raw.createCalls); assertEquals(1, raw.resumes); assertEquals(1, raw.deletes);
        for (String operation : List.of("job", "set", "query:9", "size", "init", "associate", "create",
                "resume", "open", "times", "image", "member", "terminate", "wait", "exit", "query:1", "query:3"))
            assertTrue(raw.events.contains(operation), operation);
        raw.error = 4321; assertEquals(4321, bridge.error());
        assertEquals(0, bridgedPort.retainedProcessHandles()); assertEquals(0, bridgedPort.retainedLaunchBuffers());
    }

    @Test void quotesEmptyUnicodeQuotesAndTrailingBackslashesExactly() {
        assertEquals("\"\"", Win64LaunchSpec.quote(""));
        assertEquals("\"Ä & 漫画\"", Win64LaunchSpec.quote("Ä & 漫画"));
        assertEquals("\"a\\\"b\"", Win64LaunchSpec.quote("a\"b"));
        assertEquals("\"x\\\\\"", Win64LaunchSpec.quote("x\\"));
        assertEquals("\"a\\\\\\\"b\"", Win64LaunchSpec.quote("a\\\"b"));
        assertThrows(IllegalArgumentException.class, () -> spec(List.of("\0")));
        assertThrows(IllegalArgumentException.class, () -> spec(List.of("x".repeat(32768))));
    }

    @ParameterizedTest @ValueSource(strings = {"java.exe", "C:java.exe", "\\\\host\\x", "C:\\x\\..\\java.exe", "C:\\x:stream", "C:\\x\\a.", "C:/java.exe"})
    void rejectsAmbiguousOrUntrustedStartPaths(String path) {
        assertThrows(IllegalArgumentException.class, () -> new Win64LaunchSpec(path, List.of(), DIR, ENV, 5));
        assertThrows(IllegalArgumentException.class, () -> new Win64LaunchSpec(EXE, List.of(), path, ENV, 5));
        assertTrue(raw.events.isEmpty());
    }

    @Test void refusesInheritedEnvironmentAndMissingOrDuplicateEnvironmentKeys() {
        var env = new LinkedHashMap<>(ENV); env.put("JAVA_TOOL_OPTIONS", "synthetic");
        assertThrows(IllegalArgumentException.class, () -> new Win64LaunchSpec(EXE, List.of(), DIR, env, 1));
        env.remove("JAVA_TOOL_OPTIONS"); env.put("temp", DIR);
        assertThrows(IllegalArgumentException.class, () -> new Win64LaunchSpec(EXE, List.of(), DIR, env, 1));
        env.remove("temp"); env.remove("TMP");
        assertThrows(IllegalArgumentException.class, () -> new Win64LaunchSpec(EXE, List.of(), DIR, env, 1));
    }

    @ParameterizedTest @ValueSource(strings = {"init", "associate"})
    void preparationFailuresNeverCallCreateAndReleaseRegisteredParts(String failure) {
        raw.failure = failure; start(); assertEquals(0, raw.createCalls); assertFalse(owner.resume(ticket));
        cleaned(); assertEquals(failure.equals("associate") ? 1 : 0, raw.deletes);
    }

    @ParameterizedTest @ValueSource(longs = {0, 4097, -1})
    void rejectsInvalidAttributeSizeBeforeAllocatingList(long size) {
        raw.size = size; start(); assertEquals(0, raw.createCalls); cleaned(); assertEquals(0, raw.deletes);
    }

    @Test void falseCreateWithoutHandlesIsNotRetried() {
        raw.created = false; start(); assertFalse(owner.resume(ticket));
        assertNull(owner.start(ticket)); assertEquals(1, raw.createCalls);
        assertEquals("CreateProcessW", port.startFailure().operation); assertEquals(122, port.startFailure().code);
        cleaned();
    }

    @Test void failedCreateWithPartialHandlesStillUsesExistingOwnerCleanup() {
        raw.created = false; raw.partial = true; start();
        assertEquals(2, port.retainedProcessHandles()); assertFalse(owner.resume(ticket));
        cleaned(); assertTrue(raw.events.contains("terminate")); assertEquals(0, raw.resumes);
    }

    @Test void missingThreadNeverLosesTheReturnedProcessHandle() {
        raw.missingThread = true; start();
        assertEquals(1, port.retainedProcessHandles()); assertFalse(owner.resume(ticket));
        cleaned(); assertEquals(0, raw.resumes);
    }

    @Test void deadlineBeforeNativeCallPreventsCreate() {
        initialize(); clock.set(spec.startDeadline()); owner.start(ticket);
        assertEquals(0, raw.createCalls); assertFalse(owner.resume(ticket)); cleaned();
    }

    @Test void deadlineAfterCreateKeepsSuspendedHandlesAndNeverResumes() {
        raw.nowAfterCreate = spec.startDeadline(); start();
        assertEquals(1, raw.createCalls); assertFalse(owner.resume(ticket));
        assertEquals("invalid-or-expired-start", port.startFailure().operation); cleaned(); assertEquals(0, raw.resumes);
    }

    @Test void elapsedDeadlineBetweenPreparationAndCreateReturnsFailureWithoutNativeCall() {
        var job = port.createJob(new Scope()); var preparation = new ArrayList<Resource>();
        port.prepareLaunch(job, preparation::add); clock.set(spec.startDeadline());
        assertEquals(StartStatus.FAILED, port.startSuspended(job, preparation, r -> fail()).status());
        assertEquals(0, raw.createCalls);
        port.close(preparation.get(1)); port.close(preparation.getFirst()); port.close(job);
    }

    @Test void elapsedDeadlineBeforeResumeNeverCallsResumeThread() {
        start(); clock.set(spec.startDeadline());
        assertFalse(owner.resume(ticket)); assertEquals(0, raw.resumes); cleaned();
    }

    @ParameterizedTest @ValueSource(ints = {-1, 0, 2})
    void unexpectedResumeResultsAreNotSuccessfulOrRetried(int count) {
        raw.previousCount = count; start(); assertFalse(owner.resume(ticket)); assertFalse(owner.resume(ticket));
        assertEquals(1, raw.resumes); cleaned();
    }

    @ParameterizedTest @ValueSource(strings = {"pid", "image", "times", "member"})
    void failedPostCreateIdentityNeverResumesOrHidesHandles(String operation) {
        raw.failure = operation; start(); assertFalse(owner.resume(ticket)); assertEquals(2, port.retainedProcessHandles());
        assertNotNull(port.startFailure());
        var report = owner.cleanup(ticket, 300_000_000);
        assertTrue(report.quarantineRequired()); assertFalse(report.resourcesClosed());
        assertTrue(raw.events.contains("terminate")); assertEquals(0, raw.resumes);
    }

    @Test void wrongRootImageIsAStartFailureEvenWithMatchingPidAndBirth() {
        raw.instances.get(raw.root).image = "C:\\Other\\java.exe";
        start(); assertFalse(owner.resume(ticket)); assertEquals("root-not-expected-instance", port.startFailure().operation);
        cleaned();
    }

    @ParameterizedTest @ValueSource(ints = {-1, 32768})
    void invalidImageLengthNeverProducesAnIdentityOrResume(int length) {
        raw.imageLengthOverride = length; start();
        assertFalse(owner.resume(ticket)); assertEquals("image-length", port.startFailure().operation);
        assertTrue(owner.cleanup(ticket, 300_000_000).quarantineRequired()); assertEquals(0, raw.resumes);
    }

    @Test void ambiguousMembershipFlagIsRejectedInsteadOfTreatedAsTrue() {
        raw.membershipValue = 2; start();
        assertFalse(owner.resume(ticket)); assertEquals("membership-value", port.startFailure().operation);
        assertTrue(owner.cleanup(ticket, 300_000_000).quarantineRequired());
    }

    @Test void changedPidBetweenEnumerationAndOpenCannotBecomeAnAuthorizedMember() {
        start(); raw.pids.add(50L); raw.total++;
        raw.instances.put(new Pointer(1050), new Instance(51));
        assertFalse(owner.inspect(ticket, 250_000_000));
        var report = owner.cleanup(ticket, 300_000_000);
        assertTrue(report.quarantineRequired()); assertFalse(report.resourcesClosed());
        assertTrue(raw.events.contains("terminate"));
    }

    @ParameterizedTest @ValueSource(strings = {"pid", "birth", "image"})
    void retainedHandleIdentityDetectsPidReuseAndChangedMetadata(String change) {
        start(); Instance instance = raw.instances.get(raw.root);
        switch (change) {
            case "pid" -> instance.pid++;
            case "birth" -> instance.birth++;
            default -> instance.image = "C:\\Other\\java.exe";
        }
        assertFalse(owner.resume(ticket)); assertEquals(0, raw.resumes);
        assertTrue(owner.cleanup(ticket, 300_000_000).quarantineRequired());
        assertTrue(raw.events.contains("terminate")); assertEquals(2, port.retainedProcessHandles());
    }

    @Test void capturesEveryDynamicMemberIncludingConhostWithMinimalRights() {
        start();
        for (long pid = 50; pid < 55; pid++) {
            raw.pids.add(pid); raw.total++;
            var instance = new Instance(pid); instance.image = "C:\\Windows\\System32\\conhost.exe";
            raw.instances.put(new Pointer(1000 + pid), instance);
        }
        assertTrue(owner.inspect(ticket, 250_000_000)); assertEquals(QUERY_AND_SYNCHRONIZE, raw.access);
        assertEquals(7, port.retainedProcessHandles()); // root + thread + 5 dynamische Mitglieder
        cleaned(); assertEquals(6, raw.waits.size());
    }

    @Test void conhostNameDoesNotAuthorizeForeignMembership() {
        start(); raw.pids.add(50L); raw.total++;
        var foreign = new Instance(50); foreign.image = "C:\\Windows\\System32\\conhost.exe"; foreign.member = false;
        raw.instances.put(new Pointer(1050), foreign);
        assertFalse(owner.inspect(ticket, 250_000_000));
        assertTrue(owner.cleanup(ticket, 300_000_000).quarantineRequired());
        assertTrue(raw.events.contains("terminate"));
    }

    @Test void inconsistentAccountingDoesNotProveCleanupEvenWhenListIsEmpty() {
        start(); raw.drift = true;
        assertFalse(owner.inspect(ticket, 250_000_000));
        var report = owner.cleanup(ticket, 300_000_000);
        assertFalse(report.groupEndConfirmed()); assertFalse(report.resourcesClosed()); assertTrue(report.quarantineRequired());
    }

    @Test void overCapacityPopulationCannotBeSilentlyTruncated() {
        start(); raw.pids.clear();
        for (long id = 1; id <= 9; id++) raw.pids.add(id); raw.total = 9;
        assertFalse(owner.inspect(ticket, 250_000_000)); assertTrue(owner.cleanup(ticket, 300_000_000).quarantineRequired());
    }

    @Test void incompleteTerminationRetainsResourcesAndUsesSingleRemainingBudget() {
        start(); raw.noEnd = true; clock.set(250_000_000);
        var first = owner.cleanup(ticket, 280_000_000);
        assertTrue(first.quarantineRequired()); assertFalse(first.resourcesClosed()); assertEquals(List.of(30), raw.waits);
        assertSame(first, owner.cleanup(ticket, 900_000_000)); assertEquals(List.of(30), raw.waits);
        assertEquals(0, raw.deletes);
    }

    @Test void multipleHeldProcessesConsumeTheSameDeadlineAndLateWaitDoesNotProveTimelyCleanup() {
        start(); raw.pids.add(50L); raw.total++;
        assertTrue(owner.inspect(ticket, 250_000_000));
        clock.set(250_000_000); raw.advanceOnWait = 20_000_000;
        var report = owner.cleanup(ticket, 280_000_000);
        assertEquals(List.of(30, 10), raw.waits);
        assertFalse(report.groupEndConfirmed()); assertTrue(report.quarantineRequired());
        assertEquals(0, raw.deletes); assertTrue(port.retainedLaunchBuffers() > 0);
    }

    @Test void naturallyEndedRootStillRequiresHeldIdentityAndSignalProof() {
        start(); raw.pids.clear(); raw.instances.get(raw.root).ended = true;
        cleaned(); assertFalse(raw.events.contains("terminate")); assertEquals(List.of(200), raw.waits);
    }

    @ParameterizedTest @ValueSource(strings = {"query:1", "query:3", "open", "terminate"})
    void queryOpenAndTerminationFailuresCannotProduceAnEndCertificate(String operation) {
        start(); raw.failure = operation;
        if (operation.equals("open")) { raw.pids.add(50L); raw.total++; }
        var report = owner.cleanup(ticket, 300_000_000);
        assertFalse(report.groupEndConfirmed()); assertFalse(report.resourcesClosed()); assertTrue(report.quarantineRequired());
        assertEquals(0, raw.deletes);
    }

    @Test void emptyJobSnapshotCannotReplaceASignalFromTheHeldInstance() {
        start(); raw.pids.clear(); raw.noEnd = true;
        var report = owner.cleanup(ticket, 300_000_000);
        assertFalse(report.groupEndConfirmed()); assertTrue(report.quarantineRequired()); assertEquals(0, raw.deletes);
    }

    @Test void expiredCleanupNeverStartsANewGraceperiod() {
        start(); clock.set(300_000_000);
        var report = owner.cleanup(ticket, 300_000_000);
        assertFalse(report.groupEndConfirmed()); assertTrue(raw.waits.isEmpty());
        assertFalse(raw.events.contains("terminate")); assertEquals(0, raw.deletes);
    }

    @ParameterizedTest @ValueSource(ints = {-1, 17})
    void waitFailureOrUnknownResultIsNotAnEndProof(int status) {
        start(); raw.waitStatus = status;
        var report = owner.cleanup(ticket, 300_000_000);
        assertFalse(report.groupEndConfirmed()); assertTrue(report.quarantineRequired()); assertEquals(0, raw.deletes);
    }

    @Test void exitCodeFailureKeepsResourcesDespiteSignaledWait() {
        start(); raw.failure = "exit";
        assertFalse(owner.cleanup(ticket, 300_000_000).groupEndConfirmed()); assertEquals(0, raw.deletes);
    }

    @Test void uncertainHandleCloseIsNeverRetriedAndBlocksJobClose() {
        start(); raw.closeOk = false;
        var first = owner.cleanup(ticket, 300_000_000); assertTrue(first.groupEndConfirmed());
        assertFalse(first.resourcesClosed()); assertTrue(first.quarantineRequired());
        long attempts = raw.events.stream().filter(e -> e.startsWith("close:")).count();
        raw.closeOk = true; assertSame(first, owner.cleanup(ticket, 400_000_000));
        assertEquals(attempts, raw.events.stream().filter(e -> e.startsWith("close:")).count());
        assertFalse(raw.events.contains("close:273"));
    }

    @Test void uncertainAttributeDeletionPinsBackingMemoryAndJob() {
        start(); raw.failure = "delete";
        var report = owner.cleanup(ticket, 300_000_000);
        assertTrue(report.groupEndConfirmed()); assertFalse(report.resourcesClosed()); assertTrue(report.quarantineRequired());
        assertTrue(port.retainedLaunchBuffers() > 0); assertEquals(1, raw.deletes);
        assertFalse(raw.events.contains("close:273"));
    }

    @Test void pendingNativeReturnPinsBuffersAndRejectsForeignAndDuplicateCompletions() {
        initialize(); raw.returned = false;
        var permit = owner.start(ticket); assertTrue(owner.pendingStart()); assertEquals(0, raw.resumes);
        var memory = preparationCapability();
        assertThrows(Failure.class, () -> port.completeReturn(memory, NativeReturn.CREATED, 0));
        assertTrue(owner.cleanup(ticket, 300_000_000).quarantineRequired());
        assertEquals(0, raw.deletes); assertTrue(port.retainedLaunchBuffers() > 0);
        raw.returned = true;
        assertThrows(Failure.class, () -> port.completeReturn(new Resource(memory.scope, Kind.MEMORY, 2), NativeReturn.CREATED, 0));
        var result = port.completeReturn(memory, NativeReturn.CREATED, 0);
        assertFalse(owner.startReturned(new Object(), permit, result));
        assertTrue(owner.startReturned(ticket, permit, result)); assertFalse(owner.resume(ticket));
        assertThrows(Failure.class, () -> port.completeReturn(memory, NativeReturn.CREATED, 0));
        var report = owner.cleanup(ticket, 900_000_000);
        assertTrue(report.groupEndConfirmed()); assertTrue(report.resourcesClosed()); assertTrue(report.quarantineRequired());
        assertEquals(0, raw.resumes); assertEquals(1, raw.createCalls);
    }

    @Test void exceptionWithUnknownNativeOutcomeRetainsBuffersUntilExplicitActualReturn() {
        raw.throwStart = true; initialize(); var permit = owner.start(ticket);
        assertTrue(owner.pendingStart()); assertEquals(0, port.retainedProcessHandles());
        var report = owner.cleanup(ticket, 300_000_000);
        assertTrue(report.quarantineRequired()); assertEquals(0, raw.deletes);
        clock.set(301_000_000); raw.throwStart = false;
        var result = port.completeReturn(preparationCapability(), NativeReturn.CREATED, 0);
        assertEquals(StartStatus.FAILED, result.status()); assertTrue(owner.startReturned(ticket, permit, result));
        assertFalse(owner.resume(ticket));
        var late = owner.cleanup(ticket, 900_000_000);
        assertFalse(late.groupEndConfirmed()); assertTrue(late.quarantineRequired());
        assertEquals(2, port.retainedProcessHandles()); assertTrue(port.retainedLaunchBuffers() > 0);
        assertFalse(raw.events.contains("terminate")); assertEquals(0, raw.resumes);
    }

    /** Nur Testreflexion auf die eigene Fähigkeit, keine Rekonstruktion aus numerischen Handles. */
    Resource preparationCapability() {
        try {
            var field = Win64ProcessKernelCalls.class.getDeclaredField("frame"); field.setAccessible(true);
            var frame = field.get(port); var memory = frame.getClass().getDeclaredField("memory"); memory.setAccessible(true);
            return (Resource) memory.get(frame);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    @Test void foreignTicketOrGroupCannotTriggerNativeOperations() {
        initialize(); int before = raw.events.size();
        assertThrows(IllegalArgumentException.class, () -> owner.start(new Object()));
        assertThrows(IllegalArgumentException.class, () -> owner.cleanup(new Object(), 300_000_000));
        var forged = new Resource(new Scope(), Kind.JOB, 1);
        assertThrows(Failure.class, () -> port.openProcess(forged, 41));
        assertThrows(Failure.class, () -> port.terminate(forged)); assertEquals(before, raw.events.size());
        cleaned();
    }

    @Test void duplicatePreparationOrMissingAtomicAttributeNeverStarts() {
        var job = port.createJob(new Scope()); var resources = new ArrayList<Resource>();
        port.prepareLaunch(job, resources::add);
        assertThrows(Failure.class, () -> port.prepareLaunch(job, resources::add));
        assertThrows(Failure.class, () -> port.startSuspended(job, List.of(resources.getFirst()), r -> fail()));
        assertEquals(0, raw.createCalls);
        port.close(resources.get(1)); port.close(resources.getFirst()); port.close(job);
    }
}
