package de.mangalib.watchdogmodel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Deterministischer Ereignis-Fake, keine native Bindung. Insbesondere K/J-Nesting,
 * Kill-on-close und Late-create sind VERTRAGSANNAHMEN, keine Windows-Evidenz.
 * Terminate registriert nur eine Anforderung; end/returnStart sind separate Ereignisse.
 */
final class FakeJobKernel {
    enum Role { S, H, W }
    enum Kind { K, J }
    enum HandleState { OPEN, CLOSED, UNCERTAIN }
    record Identity(long pid, long birth, String image) {
        Identity {
            if (pid < 1 || birth < 1 || image == null || image.isBlank()) throw new IllegalArgumentException("identity");
        }
    }
    static final class Job {
        final WatchdogModel.Ticket ticket;
        final Kind kind;
        final Role owner;
        final Job parent;
        final List<Handle> copies = new ArrayList<>();
        boolean configured, stopped, killOnCloseRequested;
        long revision;
        int terminates;
        int terminationAttempts;
        private Job(WatchdogModel.Ticket ticket, Kind kind, Role owner, Job parent) {
            this.ticket = ticket; this.kind = kind; this.owner = owner; this.parent = parent;
        }
    }
    static final class Handle {
        final Job job;
        final Role owner;
        HandleState state = HandleState.OPEN;
        int users;
        private Handle(Job job, Role owner) { this.job = job; this.owner = owner; }
    }
    static final class Instance {
        final WatchdogModel.Ticket ticket;
        final Identity identity;
        final Role role;
        final Job group;
        boolean suspended = true, alive = true, identified = true;
        private Instance(WatchdogModel.Ticket ticket, Identity identity, Role role, Job group) {
            this.ticket = ticket; this.identity = identity; this.role = role; this.group = group;
        }
    }
    static final class Start {
        final Handle handle;
        final Role role;
        boolean returned;
        private Start(Handle handle, Role role) { this.handle = handle; this.role = role; }
    }
    record Observation(Job job, long revision, boolean complete, boolean stable,
                       int active, List<Identity> members, int accounting) {
        Observation { members = List.copyOf(members); }
    }

    private final List<Job> jobs = new ArrayList<>();
    private final List<Instance> instances = new ArrayList<>();
    private final List<Start> starts = new ArrayList<>();
    private final Set<Job> started = new HashSet<>();
    boolean failClose, failTerminateJ, failTerminateK;
    int resumes;

    Handle create(WatchdogModel.Ticket ticket, Role owner, Kind kind, Instance host) {
        require(ticket != null && (kind == Kind.K ? owner == Role.S && host == null
                : owner == Role.H && host != null && instances.contains(host) && host.role == Role.H
                  && host.alive && host.identified && !host.suspended && host.ticket == ticket && host.group.kind == Kind.K
                  && host.group.configured && !host.group.stopped));
        require(jobs.stream().noneMatch(j -> j.ticket == ticket && j.kind == kind));
        Job job = new Job(ticket, kind, owner, host == null ? null : host.group);
        jobs.add(job);
        Handle handle = new Handle(job, owner); job.copies.add(handle);
        return handle;
    }

    void configure(Role owner, Handle handle) {
        access(owner, handle); handle.job.configured = true; changed(handle.job);
    }

    /** Keine Kopie in eine andere Vertrauensrolle; lokale Kopien bleiben einzeln bilanziert. */
    Handle duplicate(Role caller, Handle source, Role recipient) {
        access(caller, source); require(recipient == caller);
        Handle copy = new Handle(source.job, recipient); source.job.copies.add(copy);
        return copy;
    }

    Start begin(Role owner, Handle handle, Role processRole, Instance host) {
        access(owner, handle);
        require(handle.job.configured && !stopped(handle.job) && !started.contains(handle.job));
        require(handle.job.kind == Kind.K ? processRole == Role.H && host == null
                : processRole == Role.W && host != null && host.role == Role.H && host.alive && host.identified
                  && !host.suspended && host.ticket == handle.job.ticket && member(host, handle.job.parent));
        Start start = new Start(handle, processRole);
        starts.add(start); started.add(handle.job); handle.users++;
        changed(handle.job);
        return start;
    }

    /** Ein bereits eingetretener Start darf selbst NACH Terminate noch Erfolg liefern. */
    Instance returnStart(Start start, Identity identity) {
        require(starts.contains(start) && !start.returned);
        require(identity != null && instances.stream().noneMatch(p -> p.alive && p.identity.pid() == identity.pid()));
        Instance process = new Instance(start.handle.job.ticket, identity, start.role, start.handle.job);
        instances.add(process); start.returned = true; start.handle.users--; changed(start.handle.job);
        return process;
    }

    void failStart(Start start) {
        require(starts.contains(start) && !start.returned);
        start.returned = true; start.handle.users--; changed(start.handle.job);
    }

    void resume(Role owner, Handle handle, Instance process) {
        access(owner, handle);
        require(instances.contains(process) && process.group == handle.job && process.alive && process.identified
                && process.suspended && !stopped(handle.job));
        process.suspended = false; resumes++; changed(process.group);
    }

    Instance descendant(Instance parent, Identity identity) {
        require(instances.contains(parent) && parent.alive && !parent.suspended && parent.role != Role.H);
        require(identity != null && instances.stream().noneMatch(p -> p.alive && p.identity.pid() == identity.pid()));
        Instance child = new Instance(parent.ticket, identity, Role.W, parent.group);
        child.suspended = false; instances.add(child); changed(child.group);
        return child;
    }

    void terminate(Role owner, Handle handle) {
        access(owner, handle); handle.job.terminationAttempts++;
        if (handle.job.kind == Kind.J ? failTerminateJ : failTerminateK)
            throw new IllegalStateException("simulated-termination-failed");
        handle.job.stopped = true;
        if (handle.job.terminates == 0) handle.job.terminates++;
        // Kein automatisches Prozessende: auch ein positiver API-Return ist kein Endbeleg.
    }

    void end(Instance instance) {
        require(instances.contains(instance)); instance.alive = false; changed(instance.group);
    }

    /** Explizit injiziertes Kernelereignis, nicht Folge eines Java-Timeouts. */
    void endGroup(Handle handle) {
        require(handle != null && jobs.contains(handle.job));
        instances.stream().filter(p -> member(p, handle.job)).forEach(p -> p.alive = false);
        changed(handle.job);
    }

    void crashOwner(Role owner, Handle handle) {
        access(owner, handle);
        for (Handle copy : handle.job.copies) copy.state = HandleState.CLOSED;
        handle.job.killOnCloseRequested = true;
        // Weder sofortiges Gruppenende noch Rückkehr laufender Kernelaufrufe behaupten.
    }

    Observation observe(Role owner, Handle handle) {
        access(owner, handle);
        List<Identity> members = instances.stream().filter(p -> p.alive && member(p, handle.job))
                .map(p -> p.identity).toList();
        return new Observation(handle.job, handle.job.revision, true, true, members.size(), members, members.size());
    }

    boolean provesEmpty(Role owner, Handle handle, Observation evidence) {
        access(owner, handle);
        return evidence != null && evidence.job() == handle.job && evidence.revision() == handle.job.revision
                && evidence.complete() && evidence.stable() && evidence.active() == 0
                && evidence.accounting() == 0 && evidence.members().isEmpty()
                && !pending(handle.job.ticket)
                && instances.stream().filter(p -> member(p, handle.job)).allMatch(p -> !p.alive && p.identified);
    }

    void close(Role owner, Handle handle) {
        access(owner, handle); require(handle.users == 0);
        handle.state = HandleState.UNCERTAIN;
        if (failClose) throw new IllegalStateException("simulated-close-unknown");
        handle.state = HandleState.CLOSED;
        if (handle.job.copies.stream().allMatch(h -> h.state == HandleState.CLOSED)) handle.job.killOnCloseRequested = true;
    }

    boolean pending(WatchdogModel.Ticket ticket) {
        return starts.stream().anyMatch(s -> s.handle.job.ticket == ticket && !s.returned);
    }
    boolean closed(WatchdogModel.Ticket ticket) {
        return jobs.stream().filter(j -> j.ticket == ticket)
                .flatMap(j -> j.copies.stream()).allMatch(h -> h.state == HandleState.CLOSED && h.users == 0);
    }
    boolean closed(Job job) { return job.copies.stream().allMatch(h -> h.state == HandleState.CLOSED && h.users == 0); }
    boolean member(Instance instance, Job job) {
        if (instance == null || !instances.contains(instance)) return false;
        for (Job current = instance.group; current != null; current = current.parent) if (current == job) return true;
        return false;
    }
    boolean matches(Instance instance, Identity claimed) { return instances.contains(instance) && instance.identified && instance.identity.equals(claimed); }
    private boolean stopped(Job job) {
        for (Job at = job; at != null; at = at.parent) if (at.stopped) return true;
        return false;
    }
    private void changed(Job job) { for (; job != null; job = job.parent) job.revision++; }
    private void access(Role caller, Handle handle) {
        require(handle != null && jobs.contains(handle.job) && handle.job.copies.contains(handle)
                && handle.owner == caller && handle.state == HandleState.OPEN);
    }
    private static void require(boolean valid) { if (!valid) throw new IllegalStateException("fake-kernel-contract"); }
}
