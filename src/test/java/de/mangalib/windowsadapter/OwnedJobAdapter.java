package de.mangalib.windowsadapter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import static de.mangalib.windowsadapter.KernelCalls.*;

/**
 * Ressourcenbaustein ausschließlich für Fake-Tests. Keine Admission, keine
 * Shutdown-/Recoverypolitik und kein automatischer Retry. Der Supervisor würde
 * Ticket, Deadline und gewünschte Aktion vorgeben; Bericht ist nie Slotfreigabe.
 */
final class OwnedJobAdapter {
    enum Ownership { OPEN, CLOSED, UNCERTAIN }
    /** Interne Diagnose: Ursache aufbewahren, niemals ungefiltert als HTTP-Antwort. */
    record Problem(String operation, int code, RuntimeException cause) { }
    record CleanupReport(boolean groupEndConfirmed, boolean resourcesClosed,
                         boolean quarantineRequired, List<Problem> problems) {
        CleanupReport { problems = List.copyOf(problems); }
    }
    enum ControlPhase { READY, RUNNING, SUCCEEDED, FAILED, OVERDUE, UNAVAILABLE }
    /** Diagnose, niemals selbst eine Admission-/Freigabeberechtigung. */
    record CleanupProgress(boolean requested, boolean operationOutstanding, ControlPhase control,
                           boolean groupEndConfirmed, boolean resourcesClosed, boolean quarantineRequired) { }

    /** Identität dieses einzelnen Startversuchs, nicht aus Tickettexten ableitbar. */
    static final class StartPermit { private StartPermit() { } }

    private final Object ticket;
    private final Scope scope = new Scope();
    private final KernelCalls kernel;
    private final LongSupplier clock;
    private final int capacity;
    private final Map<Resource, Ownership> ledger = new LinkedHashMap<>();
    private final Map<Resource, Identity> instances = new LinkedHashMap<>();
    private final List<Problem> problems = new ArrayList<>();
    private Resource job;
    private Resource root;
    private Resource thread;
    private boolean initialized;
    private volatile boolean prepared;
    private boolean attemptedStart;
    private volatile boolean pending;
    private boolean suspended;
    private boolean resumed;
    private volatile boolean cleanupRequested;
    private boolean unsafeIdentity;
    private volatile boolean incompleteCleanupObserved;
    private volatile boolean terminationRequested;
    private volatile Long cleanupDeadline;
    private volatile StartPermit permit;
    private volatile CleanupReport lastReport;
    /** Dieser Lock schützt nur Reservierungen/Snapshots, niemals Kernelaufrufe. */
    private final Object stateLock = new Object();
    private Object operation;
    private CleanupControl control;
    private ControlPhase controlPhase = ControlPhase.UNAVAILABLE;
    private boolean controlReleased, controlReleaseAttempted;
    private boolean closingResources;
    private final List<Problem> controlProblems = new ArrayList<>();

    private <T> T operation(Object owner, T rejected, Supplier<T> action) {
        Object reservation = new Object();
        synchronized (stateLock) {
            requireTicket(owner);
            if (operation != null || controlPhase == ControlPhase.RUNNING) return rejected;
            operation = reservation;
        }
        try { return action.get(); }
        finally { synchronized (stateLock) { if (operation == reservation) operation = null; } }
    }

    boolean initialize(Object owner, Limits limits) { return operation(owner, false, () -> initializeInternal(owner, limits)); }
    StartPermit start(Object owner) { return operation(owner, null, () -> startInternal(owner)); }
    boolean resume(Object owner) { return operation(owner, false, () -> resumeInternal(owner) && !cleanupRequested); }
    boolean inspect(Object owner, long deadline) { return operation(owner, false, () -> inspectInternal(owner, deadline) && !cleanupRequested); }
    boolean startReturned(Object owner, StartPermit callbackPermit, StartResult result) {
        synchronized (stateLock) {
            if (owner != ticket || callbackPermit != permit || !pending) return false;
        }
        return operation(owner, false, () -> startReturnedInternal(owner, callbackPermit, result));
    }

    CleanupReport cleanup(Object owner, long deadline) {
        boolean busy;
        synchronized (stateLock) {
            requireTicket(owner);
            if (cleanupDeadline == null) cleanupDeadline = deadline;
            cleanupRequested = true;
            if (lastReport != null) return lastReport;
            busy = operation != null || controlPhase == ControlPhase.RUNNING;
            if (!busy) operation = new Object();
        }
        if (busy) return requestControlCleanup(owner, deadline);
        try { return cleanupInternal(owner, deadline); }
        finally { synchronized (stateLock) { operation = null; } }
    }

    /** Vom regulären Aufruf unabhängig bedienbar; der Aufrufer stellt die Ausführungshoheit. */
    CleanupReport requestControlCleanup(Object owner, long deadline) {
        CleanupControl action = null;
        CleanupControl cancel;
        long budget = 0;
        long now = clock.getAsLong();
        synchronized (stateLock) {
            requireTicket(owner);
            if (cleanupDeadline == null) cleanupDeadline = deadline;
            cleanupRequested = true;
            if (lastReport != null && lastReport.resourcesClosed()) return lastReport;
            cancel = !controlReleaseAttempted ? control : null;
            incompleteCleanupObserved = true;
            if (!terminationRequested && control != null && now < cleanupDeadline
                    && !controlReleaseAttempted && !closingResources) {
                budget = Math.subtractExact(cleanupDeadline, now);
                terminationRequested = true;
                controlPhase = ControlPhase.RUNNING;
                lastReport = null;
                action = control;
            }
        }
        RuntimeException failure = null;
        try {
            if (cancel != null) cancel.cancelStarts();
            if (action != null) action.terminate(budget);
        } catch (RuntimeException error) { failure = error; }
        if (action != null || failure != null) {
            boolean overdue = action != null && clock.getAsLong() >= cleanupDeadline;
            synchronized (stateLock) {
                controlPhase = failure != null ? ControlPhase.FAILED : overdue ? ControlPhase.OVERDUE : ControlPhase.SUCCEEDED;
                if (failure != null) controlProblems.add(problem(failure));
                if (overdue) controlProblems.add(new Problem("control-cleanup-deadline", 0, null));
                if (lastReport != null && !lastReport.resourcesClosed()) lastReport = null;
            }
        }
        synchronized (stateLock) {
            var snapshot = new ArrayList<>(controlProblems);
            snapshot.add(new Problem("cleanup-unconfirmed", 0, null));
            return new CleanupReport(false, false, true, snapshot);
        }
    }

    CleanupProgress cleanupProgress(Object owner) {
        synchronized (stateLock) {
            requireTicket(owner);
            return new CleanupProgress(cleanupRequested, operation != null || pending, controlPhase,
                    lastReport != null && lastReport.groupEndConfirmed(), lastReport != null && lastReport.resourcesClosed(),
                    incompleteCleanupObserved || unsafeIdentity || (lastReport != null && lastReport.quarantineRequired()));
        }
    }

    OwnedJobAdapter(Object ticket, KernelCalls kernel, LongSupplier clock, int capacity) {
        this.ticket = Objects.requireNonNull(ticket);
        this.kernel = Objects.requireNonNull(kernel);
        this.clock = Objects.requireNonNull(clock);
        if (capacity < 1) throw new IllegalArgumentException("capacity");
        this.capacity = capacity;
    }

    private boolean initializeInternal(Object owner, Limits limits) {
        requireTicket(owner);
        Objects.requireNonNull(limits);
        if (job != null || initialized || cleanupRequested || !problems.isEmpty()) return false;
        return guarded(() -> {
            job = kernel.createJob(scope);
            acquire(job, Kind.JOB);
            CleanupControl preparedControl = kernel.prepareCleanupControl(job);
            synchronized (stateLock) {
                control = preparedControl;
                controlPhase = control == null ? ControlPhase.UNAVAILABLE : ControlPhase.READY;
            }
            kernel.configure(job, limits);
            if (!limits.equals(kernel.readLimits(job))) fail("limits-mismatch");
            initialized = true;
        });
    }

    private StartPermit startInternal(Object owner) {
        requireTicket(owner);
        if (!initialized || attemptedStart || cleanupRequested || !problems.isEmpty()) return null;
        attemptedStart = true;
        permit = new StartPermit();
        guarded(() -> {
            kernel.prepareLaunch(usable(job), r -> {
                if (r.kind != Kind.ATTRIBUTES && r.kind != Kind.MEMORY) fail("preparation-kind");
                acquire(r, r.kind);
            });
            prepared = true;
            // Während des nativen Aufrufs bleibt das gesamte Ledger lebendig.
            pending = true;
            StartResult result = kernel.startSuspended(usable(job), preparation(), this::acquireProcessPart);
            if (result.status() != StartStatus.PENDING) settle(result);
        });
        return permit;
    }

    /** Späte Rückkehr nur für genau diesen Owner und Versuch; niemals automatisch Resume. */
    private boolean startReturnedInternal(Object owner, StartPermit callbackPermit, StartResult result) {
        if (owner != ticket || callbackPermit != permit || !pending) return false;
        if (Objects.requireNonNull(result).status() == StartStatus.PENDING) return false;
        lastReport = null;
        return guarded(() -> settle(result));
    }

    private boolean resumeInternal(Object owner) {
        requireTicket(owner);
        if (!suspended || resumed || pending || cleanupRequested || !problems.isEmpty()) return false;
        return guarded(() -> {
            if (!kernel.member(usable(root), usable(job))) failIdentity("root-membership");
            if (!instances.get(root).equals(kernel.identity(usable(root)))) failIdentity("root-identity");
            if (cleanupRequested) fail("resume-after-cleanup-request");
            // Auch bei Fehler kein zweiter Resumeversuch mit unklarem Suspendcount.
            resumed = true;
            kernel.resume(usable(thread));
        });
    }

    /** Dynamische eigene Population erfassen; Namen verleihen keinerlei Berechtigung. */
    private boolean inspectInternal(Object owner, long deadline) {
        requireTicket(owner);
        if (job == null || pending || cleanupRequested) return false;
        return guarded(() -> capture(checkedObservation(deadline), deadline));
    }

    /**
     * Ein begrenzter Cleanupversuch mit vom Aufrufer vorgegebener absoluter Frist.
     * Nach abgeschlossenem Versuch liefern Wiederholungen denselben Bericht.
     * Während eines Aufrufs bleibt der getrennte Kontrollpfad bedienbar.
     * Nach echter später Start­rückkehr ist nur weitere eigene Bereinigung unter
     * derselben Frist möglich; ein alter Fehler wird dadurch nicht gelöscht.
     */
    private CleanupReport cleanupInternal(Object owner, long deadline) {
        synchronized (stateLock) { problems.addAll(controlProblems); controlProblems.clear(); }
        if (control != null && !controlReleaseAttempted && !guarded(control::cancelStarts)) return report(false);
        requireTicket(owner);
        if (cleanupDeadline == null) cleanupDeadline = deadline;
        if (lastReport != null) return lastReport;
        cleanupRequested = true;
        if (pending) {
            add("start-outstanding", 0);
            return report(false);
        }
        if (job == null) return report(ledger.isEmpty());
        boolean end = guarded(() -> {
            // Auch Teilhandles eines fehlgeschlagenen Starts sind gehaltene Instanzen.
            for (Resource resource : ledger.keySet()) {
                remaining(cleanupDeadline);
                if (resource.kind == Kind.PROCESS && !instances.containsKey(resource)) remember(resource, null);
            }
            Observation before = checkedObservation(cleanupDeadline);
            capture(before, cleanupDeadline);
            if (before.active() > 0) requestTermination();
            for (var instance : instances.entrySet()) {
                remaining(cleanupDeadline);
                if (!instance.getValue().equals(kernel.identity(usable(instance.getKey())))) {
                    failIdentity("retained-identity");
                }
                if (!kernel.waitEnded(usable(instance.getKey()), remaining(cleanupDeadline))) {
                    fail("retained-not-ended");
                }
                remaining(cleanupDeadline);
            }
            Observation after = validateObservation(kernel.awaitEmptyJob(usable(job), capacity,
                    remaining(cleanupDeadline)), cleanupDeadline);
            if (after.active() != 0 || !after.pids().isEmpty()) fail("group-not-empty");
        });
        // Fehlende Listen-/Identitätsbelege verhindern Freigabe, nicht die vom
        // Owner verlangte Stoppanforderung an seinen bereits besessenen Job.
        // Niemals nach PID/Namen ausweichen; Fehlschläge bleiben separat sichtbar.
        if (!end && !terminationRequested) guarded(this::requestTermination);
        if (end) closeAfterEnd();
        return report(end);
    }

    private void settle(StartResult result) {
        // Der Port meldet auch bei teilweisem Fehler jeden erworbenen Handle.
        if (result.process() != null) acquireIfNew(result.process(), Kind.PROCESS);
        if (result.thread() != null) acquireIfNew(result.thread(), Kind.THREAD);
        pending = false;
        if (result.status() == StartStatus.FAILED) { add("start-failed", 0); return; }
        root = result.process();
        thread = result.thread();
        usable(root);
        usable(thread);
        remember(root, null);
        suspended = true;
    }

    private void capture(Observation observation, long deadline) {
        for (long pid : observation.pids()) {
            remaining(deadline);
            boolean known = instances.values().stream().anyMatch(i -> i.pid() == pid);
            if (!known) {
                if (instances.size() >= capacity) failIdentity("registry-full");
                Resource process = kernel.openProcess(usable(job), pid);
                acquire(process, Kind.PROCESS);
                remaining(deadline);
                remember(process, pid);
            }
        }
    }

    private void remember(Resource process, Long expectedPid) {
        if (!kernel.member(usable(process), usable(job))) failIdentity("foreign-membership");
        Identity identity = kernel.identity(usable(process));
        if (identity == null || identity.pid() <= 0 || identity.creationTime() <= 0
                || identity.image() == null || identity.image().isBlank()
                || (expectedPid != null && identity.pid() != expectedPid)) failIdentity("missing-identity");
        if (instances.size() >= capacity && !instances.containsKey(process)) failIdentity("registry-full");
        instances.put(process, identity);
    }

    private Observation checkedObservation(long deadline) {
        Observation result = kernel.observe(usable(job), capacity, remaining(deadline));
        return validateObservation(result, deadline);
    }

    private Observation validateObservation(Observation result, long deadline) {
        if (!result.complete() || !result.stable() || result.assigned() < 0
                || result.active() < 0 || result.total() < result.active()
                || result.pids().size() > capacity || result.assigned() != result.pids().size()
                || result.active() != result.pids().size()
                || result.pids().stream().anyMatch(p -> p == null || p <= 0)
                || result.pids().stream().distinct().count() != result.pids().size()) {
            failIdentity("inconsistent-observation");
        }
        remaining(deadline);
        return result;
    }

    private void closeAfterEnd() {
        synchronized (stateLock) {
            if (controlPhase == ControlPhase.RUNNING) {
                incompleteCleanupObserved = true;
                add("control-still-running", 0);
                return;
            }
            // Ab jetzt ist die Gruppe nachgewiesen beendet. Kein neuer Kontrollaufruf
            // darf mit der anschließenden Handlefreigabe konkurrieren.
            closingResources = true;
        }
        // Endbelege wurden am offenen Job gewonnen. Attributliste vor Backing Memory.
        for (Kind kind : List.of(Kind.THREAD, Kind.PROCESS, Kind.ATTRIBUTES, Kind.MEMORY, Kind.JOB)) {
            if (kind == Kind.MEMORY && ledger.entrySet().stream()
                    .anyMatch(e -> e.getKey().kind == Kind.ATTRIBUTES && e.getValue() != Ownership.CLOSED)) continue;
            if (kind == Kind.JOB && ledger.entrySet().stream()
                    .anyMatch(e -> e.getKey() != job && e.getValue() != Ownership.CLOSED)) continue;
            if (kind == Kind.JOB && control != null && !controlReleased) {
                if (!guarded(() -> {
                    remaining(cleanupDeadline);
                    synchronized (stateLock) {
                        if (controlPhase == ControlPhase.RUNNING || controlReleaseAttempted) fail("control-not-releasable");
                        controlReleaseAttempted = true;
                    }
                    control.release();
                    synchronized (stateLock) { controlReleased = true; }
                    remaining(cleanupDeadline);
                })) continue;
            }
            for (var entry : ledger.entrySet()) {
                if (entry.getKey().kind == kind && entry.getValue() == Ownership.OPEN) {
                    boolean invoked = false;
                    try {
                        remaining(cleanupDeadline);
                        invoked = true;
                        kernel.close(usable(entry.getKey()));
                        synchronized (stateLock) { entry.setValue(Ownership.CLOSED); }
                        remaining(cleanupDeadline);
                    } catch (RuntimeException error) {
                        // Unklarer Close-Ausgang darf nicht durch erneutes Close auf
                        // einen möglicherweise wiederverwendeten Wert getestet werden.
                        synchronized (stateLock) {
                            if (invoked && entry.getValue() != Ownership.CLOSED) entry.setValue(Ownership.UNCERTAIN);
                        }
                        incompleteCleanupObserved = true;
                        record(error);
                    }
                }
            }
        }
    }

    private CleanupReport report(boolean ended) {
        boolean closed = !pending && (control == null || controlReleased) && ledger.values().stream().allMatch(s -> s == Ownership.CLOSED);
        incompleteCleanupObserved |= !ended || !closed || unsafeIdentity;
        synchronized (stateLock) {
            problems.addAll(controlProblems); controlProblems.clear();
            lastReport = new CleanupReport(ended, closed,
                    incompleteCleanupObserved, problems);
        }
        return lastReport;
    }

    private List<Resource> preparation() {
        return ledger.keySet().stream().filter(r -> r.kind == Kind.ATTRIBUTES || r.kind == Kind.MEMORY).toList();
    }

    private void acquireProcessPart(Resource resource) {
        if (!pending) failIdentity("callback-without-pending-start");
        if (resource.kind != Kind.PROCESS && resource.kind != Kind.THREAD) fail("start-resource-kind");
        acquire(resource, resource.kind);
    }

    private void acquireIfNew(Resource resource, Kind kind) {
        if (!ledger.containsKey(resource)) acquire(resource, kind);
        else if (resource.kind != kind || ledger.get(resource) != Ownership.OPEN) fail("invalid-returned-resource");
    }

    private void acquire(Resource resource, Kind kind) {
        synchronized (stateLock) {
        if (resource == null || resource.scope != scope || resource.kind != kind
                || resource.debugValue <= 0 || ledger.containsKey(resource)) failIdentity("invalid-resource");
        ledger.put(resource, Ownership.OPEN);
        }
    }

    private Resource usable(Resource resource) {
        synchronized (stateLock) {
        if (resource == null || resource.scope != scope || ledger.get(resource) != Ownership.OPEN) fail("unusable-resource");
        return resource;
        }
    }

    private long remaining(long deadline) {
        long now = clock.getAsLong();
        if (now >= deadline) fail("cleanup-deadline");
        return Math.subtractExact(deadline, now);
    }

    private void requireTicket(Object owner) {
        if (owner != ticket) throw new IllegalArgumentException("Foreign owner ticket");
    }

    private boolean guarded(Runnable action) {
        try { action.run(); return true; }
        catch (RuntimeException error) { record(error); return false; }
    }

    private void failIdentity(String operation) { unsafeIdentity = true; fail(operation); }
    private static void fail(String operation) { throw new Failure(operation, 0); }
    private void add(String operation, int code) { problems.add(new Problem(operation, code, null)); }
    private void record(RuntimeException error) {
        if (error instanceof Failure failure) problems.add(new Problem(failure.operation, failure.code, error));
        else {
            unsafeIdentity = true;
            problems.add(new Problem("unexpected-kernel-failure", 0, error));
        }
    }

    private void requestTermination() {
        long budget = remaining(cleanupDeadline);
        Resource target = usable(job);
        CleanupControl action;
        synchronized (stateLock) {
            if (terminationRequested) return;
            terminationRequested = true;
            action = control;
            if (action != null) controlPhase = ControlPhase.RUNNING;
        }
        if (action == null) { kernel.terminate(target); return; }
        boolean succeeded = false;
        try { action.terminate(budget); succeeded = true; }
        finally {
            boolean overdue = clock.getAsLong() >= cleanupDeadline;
            synchronized (stateLock) {
                controlPhase = !succeeded ? ControlPhase.FAILED : overdue ? ControlPhase.OVERDUE : ControlPhase.SUCCEEDED;
            }
        }
    }

    private static Problem problem(RuntimeException error) {
        return error instanceof Failure f ? new Problem(f.operation, f.code, error)
                : new Problem("unexpected-control-failure", 0, error);
    }

    int openResources() { synchronized (stateLock) { return (int) ledger.values().stream().filter(s -> s != Ownership.CLOSED).count(); } }
    boolean pendingStart() { synchronized (stateLock) { return pending; } }
    boolean launchPrepared() { synchronized (stateLock) { return prepared; } }
}
