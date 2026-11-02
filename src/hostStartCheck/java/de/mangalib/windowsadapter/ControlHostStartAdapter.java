package de.mangalib.windowsadapter;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

import static de.mangalib.windowsadapter.KernelCalls.*;

/**
 * Isolierter K→H-Adapter. Kein Native-Loader und kein ProcessBuilder-Fallback.
 * Admission/Fristen/Slotfreigabe liefert Authority; Handleledger und Cleanup
 * bleiben ausschließlich im vorhandenen OwnedJobAdapter.
 */
public final class ControlHostStartAdapter {
    public interface Authority {
        boolean reserveStart(Object ticket);
        boolean intentConfirmed(Object ticket);
        boolean jobPrepared(Object ticket);
        boolean resumeAllowed(Object ticket);
        boolean resumed(Object ticket, long pid, long birth, String image);
        long cleanupDeadline(Object ticket);
        boolean communicationEnded(Object ticket);
        boolean finish(Object ticket, int exitCode);
        void failed(Object ticket);
    }
    /** Symbolische, nicht aus Nummern rekonstruierte Endpunkte; keine OS-Handles. */
    static final class StdioEnd { }
    record LaunchContract(boolean atomicJobList, boolean suspended, boolean inheritAll,
                          boolean restrictedHandleList, List<StdioEnd> childEnds, String executable) {
        LaunchContract { childEnds = List.copyOf(childEnds); }
        boolean safe() {
            return atomicJobList && suspended && !inheritAll && restrictedHandleList && childEnds.size() == 3
                    && childEnds.stream().distinct().count() == 3 && executable != null && !executable.isBlank();
        }
    }
    interface HostCalls extends KernelCalls {
        LaunchContract contract();
        /** Nur nach Signal des gehaltenen Root-Prozesshandles; keine PID-Suche. */
        int exitCode(Resource root);
    }
    record Completion(boolean groupEnded, boolean resourcesClosed, boolean slotReleased, boolean quarantine) { }

    private final Object ticket;
    private final Authority authority;
    private final GuardedCalls calls;
    private final OwnedJobAdapter owned;
    private OwnedJobAdapter.StartPermit permit;
    private Completion completion;

    ControlHostStartAdapter(Object ticket, Authority authority, HostCalls calls, LongSupplier clock) {
        this.ticket = Objects.requireNonNull(ticket); this.authority = Objects.requireNonNull(authority);
        this.calls = new GuardedCalls(Objects.requireNonNull(calls));
        owned = new OwnedJobAdapter(ticket, this.calls, clock, 16);
    }

    boolean start() {
        if (!authority.reserveStart(ticket) || !authority.intentConfirmed(ticket)) { authority.failed(ticket); return false; }
        if (!owned.initialize(ticket, new Limits(true, false, 0, 0, 0)) || !authority.jobPrepared(ticket)) {
            authority.failed(ticket); return false;
        }
        permit = owned.start(ticket);
        boolean ready = permit != null && !owned.pendingStart() && calls.rootIdentity != null && !calls.cancelled;
        if (!ready && !owned.pendingStart()) authority.failed(ticket);
        return ready;
    }

    boolean startReturned(StartResult result) {
        try { calls.capture(result); }
        catch (RuntimeException failure) { authority.failed(ticket); return false; }
        boolean accepted = owned.startReturned(ticket, permit, result);
        if (!accepted || calls.cancelled) authority.failed(ticket);
        return accepted && !calls.cancelled;
    }

    boolean resume() {
        if (!authority.resumeAllowed(ticket) || !owned.resume(ticket)) { authority.failed(ticket); return false; }
        Identity root = calls.rootIdentity;
        if (root == null || !authority.resumed(ticket, root.pid(), root.creationTime(), root.image())) {
            authority.failed(ticket); return false;
        }
        return true;
    }

    /** Auch während eines blockierten regulären Aufrufs erreichbar, ohne dessen Monitor. */
    OwnedJobAdapter.CleanupReport requestCleanup() {
        var report = owned.requestControlCleanup(ticket, authority.cleanupDeadline(ticket));
        if (report.quarantineRequired()) authority.failed(ticket);
        return report;
    }

    Completion cleanup(boolean normal) {
        if (completion != null && completion.resourcesClosed()) return completion;
        boolean communication = authority.communicationEnded(ticket);
        if (!normal || !communication) authority.failed(ticket);
        OwnedJobAdapter.CleanupReport report = owned.cleanup(ticket, authority.cleanupDeadline(ticket));
        boolean clean = normal && communication && report.groupEndConfirmed() && report.resourcesClosed()
                && !report.quarantineRequired() && calls.rootExit != null;
        boolean released = clean && authority.finish(ticket, calls.rootExit);
        if (!released) authority.failed(ticket);
        completion = new Completion(report.groupEndConfirmed(), report.resourcesClosed(), released, !released);
        return completion;
    }

    int openResources() { return owned.openResources(); }
    boolean pendingStart() { return owned.pendingStart(); }

    private final class GuardedCalls implements KernelCalls {
        final HostCalls port;
        volatile boolean cancelled;
        volatile Resource root;
        volatile Identity rootIdentity;
        volatile Integer rootExit;
        GuardedCalls(HostCalls port) { this.port = port; }
        void capture(StartResult result) {
            if (result.process() != null) {
                if (root != null && root != result.process()) throw new Failure("foreign-root-return", 0);
                root = result.process();
            }
        }
        public Resource createJob(Scope scope) {
            if (!authority.intentConfirmed(ticket)) throw new Failure("intent-required-before-create", 0);
            return port.createJob(scope);
        }
        public void configure(Resource job, Limits limits) { port.configure(job, limits); }
        public Limits readLimits(Resource job) { return port.readLimits(job); }
        public CleanupControl prepareCleanupControl(Resource job) {
            CleanupControl control = port.prepareCleanupControl(job);
            if (control == null) throw new Failure("independent-control-required", 0);
            return new CleanupControl() {
                public void cancelStarts() { cancelled = true; control.cancelStarts(); }
                public void terminate(long remaining) { cancelled = true; control.terminate(remaining); }
                public void release() { control.release(); }
            };
        }
        public void prepareLaunch(Resource job, Consumer<Resource> acquired) {
            if (!port.contract().safe()) throw new Failure("unsafe-host-launch-contract", 0);
            port.prepareLaunch(job, acquired);
        }
        public StartResult startSuspended(Resource job, List<Resource> preparation, Consumer<Resource> acquired) {
            if (cancelled || !authority.resumeAllowed(ticket) || !port.contract().safe()) return StartResult.failed();
            StartResult result = port.startSuspended(job, preparation, acquired); capture(result);
            if (cancelled && result.status() != StartStatus.PENDING)
                return new StartResult(StartStatus.FAILED, result.process(), result.thread());
            return result;
        }
        public void resume(Resource thread) {
            if (cancelled || !authority.resumeAllowed(ticket)) throw new Failure("resume-not-authorized", 0);
            port.resume(thread);
            if (cancelled || !authority.resumeAllowed(ticket)) throw new Failure("resume-returned-after-cleanup", 0);
        }
        public Identity identity(Resource process) {
            Identity identity = port.identity(process);
            if (process == root) {
                if (!port.contract().executable().equalsIgnoreCase(identity.image())) throw new Failure("unexpected-host-image", 0);
                if (rootIdentity != null && !rootIdentity.equals(identity)) throw new Failure("changed-host-instance", 0);
                rootIdentity = identity;
            }
            return identity;
        }
        public boolean member(Resource process, Resource job) { return port.member(process, job); }
        public Observation observe(Resource job, int capacity, long remaining) { return port.observe(job, capacity, remaining); }
        public Observation awaitEmptyJob(Resource job, int capacity, long remaining) { return port.awaitEmptyJob(job, capacity, remaining); }
        public Resource openProcess(Resource job, long pid) { return port.openProcess(job, pid); }
        public void terminate(Resource job) { port.terminate(job); }
        public boolean waitEnded(Resource process, long remaining) {
            boolean ended = port.waitEnded(process, remaining);
            if (ended && process == root) rootExit = port.exitCode(root);
            return ended;
        }
        public void close(Resource resource) { port.close(resource); }
    }
}
