package de.mangalib.windowsadapter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Kontrollierter In-Memory-Kernel: keine JNA-Referenz, Threads, Handles oder I/O. */
final class FakeKernelCalls implements KernelCalls {
    enum Fault {
        CREATE, CONFIGURE, READ_LIMITS, MISMATCH_LIMITS, PREPARE_MEMORY, PREPARE_ATTRIBUTES,
        BEFORE_ASSIGNMENT, AFTER_ASSIGNMENT, RESUME, OBSERVE, OPEN, IDENTITY, MEMBERSHIP,
        TERMINATE, CLOSE_PROCESS, CLOSE_ATTRIBUTES, CLOSE_MEMORY, CLOSE_JOB
    }
    record Event(String operation, Resource resource, long remaining) { }
    static final class ProcessData {
        final Resource job;
        final long pid;
        long birth;
        final String image;
        boolean alive = true;
        ProcessData(Resource job, long pid, long birth, String image) {
            this.job = job;
            this.pid = pid;
            this.birth = birth;
            this.image = image;
        }
    }

    final Set<Fault> faults = EnumSet.noneOf(Fault.class);
    final List<Event> events = new ArrayList<>();
    final Map<Resource, ProcessData> handles = new IdentityHashMap<>();
    final List<ProcessData> processes = new ArrayList<>();
    final Set<Resource> closed = Collections.newSetFromMap(new IdentityHashMap<>());
    final Map<Resource, Limits> limits = new IdentityHashMap<>();
    final ArrayDeque<Observation> observations = new ArrayDeque<>();
    final Set<Resource> neverSignaled = Collections.newSetFromMap(new IdentityHashMap<>());
    long now = 100;
    long waitCost;
    long closeCost;
    long jobWaitCost;
    long nextValue = 1;
    long nextPid = 100;
    boolean pendingStart;
    boolean terminateHasNoEffect;
    Resource lastJob;
    Resource substituteJob;
    StartResult lastStart;

    @Override public Resource createJob(Scope scope) {
        events.add(new Event("create", null, 0));
        fault(Fault.CREATE);
        if (substituteJob != null) return substituteJob;
        lastJob = new Resource(scope, Kind.JOB, nextValue++);
        return lastJob;
    }

    @Override public void configure(Resource job, Limits configured) {
        check(job, "configure");
        fault(Fault.CONFIGURE);
        limits.put(job, configured);
    }

    @Override public Limits readLimits(Resource job) {
        check(job, "read-limits");
        fault(Fault.READ_LIMITS);
        if (faults.contains(Fault.MISMATCH_LIMITS)) return new Limits(true, false, 4, 0, 0);
        return limits.get(job);
    }

    @Override public void prepareLaunch(Resource job, Consumer<Resource> acquired) {
        check(job, "prepare");
        acquired.accept(resource(job, Kind.MEMORY));
        fault(Fault.PREPARE_MEMORY);
        acquired.accept(resource(job, Kind.ATTRIBUTES));
        fault(Fault.PREPARE_ATTRIBUTES);
    }

    @Override public StartResult startSuspended(Resource job, List<Resource> preparation,
                                               Consumer<Resource> acquired) {
        check(job, "start-suspended");
        if (preparation.size() != 2) throw new Failure("invalid-preparation", 1);
        if (pendingStart) return StartResult.pending();
        if (faults.contains(Fault.BEFORE_ASSIGNMENT)) return StartResult.failed();
        StartResult result = allocateStart(job);
        acquired.accept(result.process());
        acquired.accept(result.thread());
        if (faults.contains(Fault.AFTER_ASSIGNMENT)) {
            return new StartResult(StartStatus.FAILED, result.process(), result.thread());
        }
        return result;
    }

    StartResult completePending(Resource job) { return allocateStart(job); }

    private StartResult allocateStart(Resource job) {
        Resource process = addProcess(job, "C:/synthetic/java.exe");
        lastStart = new StartResult(StartStatus.SUSPENDED, process, resource(job, Kind.THREAD));
        return lastStart;
    }

    Resource addProcess(Resource job, String image) {
        var data = new ProcessData(job, nextPid++, now, image);
        processes.add(data);
        Resource handle = resource(job, Kind.PROCESS);
        handles.put(handle, data);
        return handle;
    }

    @Override public void resume(Resource thread) {
        check(thread, "resume");
        fault(Fault.RESUME);
    }

    @Override public Observation observe(Resource job, int capacity, long remaining) {
        check(job, "observe", remaining);
        fault(Fault.OBSERVE);
        return snapshot(job);
    }

    @Override public Observation awaitEmptyJob(Resource job, int capacity, long remaining) {
        check(job, "await-empty", remaining);
        fault(Fault.OBSERVE);
        now += Math.min(jobWaitCost, remaining);
        return snapshot(job);
    }

    private Observation snapshot(Resource job) {
        if (!observations.isEmpty()) return observations.removeFirst();
        var own = processes.stream().filter(p -> p.job == job).toList();
        var active = own.stream().filter(p -> p.alive).map(p -> p.pid).toList();
        return new Observation(true, true, active.size(), active, active.size(), own.size());
    }

    @Override public Resource openProcess(Resource job, long pid) {
        check(job, "open-process");
        fault(Fault.OPEN);
        // Ein Open kann mechanisch auch einen fremden Prozess liefern; member
        // muss dessen Nichtzugehörigkeit unabhängig vom Namen aufdecken.
        ProcessData data = processes.stream().filter(p -> p.pid == pid).findFirst().orElseThrow();
        Resource handle = resource(job, Kind.PROCESS);
        handles.put(handle, data);
        return handle;
    }

    @Override public Identity identity(Resource process) {
        check(process, "identity");
        fault(Fault.IDENTITY);
        var data = handles.get(process);
        return new Identity(data.pid, data.birth, data.image);
    }

    @Override public boolean member(Resource process, Resource job) {
        check(job, "member-job");
        check(process, "member-process");
        fault(Fault.MEMBERSHIP);
        return handles.get(process).job == job;
    }

    @Override public void terminate(Resource job) {
        check(job, "terminate");
        fault(Fault.TERMINATE);
        if (!terminateHasNoEffect) processes.stream().filter(p -> p.job == job).forEach(p -> p.alive = false);
    }

    @Override public boolean waitEnded(Resource process, long remaining) {
        check(process, "wait", remaining);
        now += Math.min(waitCost, remaining);
        return !handles.get(process).alive && !neverSignaled.contains(process);
    }

    @Override public void close(Resource resource) {
        check(resource, "close");
        switch (resource.kind) {
            case JOB -> fault(Fault.CLOSE_JOB);
            case PROCESS -> fault(Fault.CLOSE_PROCESS);
            case ATTRIBUTES -> fault(Fault.CLOSE_ATTRIBUTES);
            case MEMORY -> fault(Fault.CLOSE_MEMORY);
            default -> { }
        }
        closed.add(resource);
        now += closeCost;
    }

    long count(String operation) { return events.stream().filter(e -> e.operation().equals(operation)).count(); }

    private Resource resource(Resource job, Kind kind) { return new Resource(job.scope, kind, nextValue++); }
    private void check(Resource resource, String operation) { check(resource, operation, 0); }
    private void check(Resource resource, String operation, long remaining) {
        if (resource == null || closed.contains(resource)) throw new AssertionError("Use of closed/invalid fake resource");
        events.add(new Event(operation, resource, remaining));
    }
    private void fault(Fault fault) {
        if (faults.contains(fault)) throw new Failure(fault.name(), 5);
    }
}
