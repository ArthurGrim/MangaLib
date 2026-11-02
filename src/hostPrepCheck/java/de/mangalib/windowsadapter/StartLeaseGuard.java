package de.mangalib.windowsadapter;

import java.util.Objects;

import static de.mangalib.windowsadapter.KernelCalls.Resource;

/**
 * Widerrufbare Start-/Resume-Capability, keine zweite Lifecycle-/Recoverypolitik.
 * Ein echter unabhängiger Custodian ist absichtlich NICHT implementiert.
 * Fakes können Erreichbarkeit nachweisen, aber weder Ownercrash noch Windows-Kill.
 */
final class StartLeaseGuard implements Win64ProcessKernelCalls.StartAuthorization {
    interface Custodian {
        /** Muss extern Ticket/Session/K/Revision und den eigenen K-Capabilitybesitz binden. */
        Object retain(StartIntentStore.Binding binding, StartIntentStore.Anchor anchor, Resource privateJob);
        boolean live(Object receipt);
        void revoke(Object receipt);
        void recordReturn(Object receipt, Resource heldProcess);
        /** Neue gehaltene Instanz NACH tatsächlicher Create-Rückkehr, niemals PID-Kill. */
        void retainLate(Object receipt, Resource heldProcess);
        // Ein echter Custodian muss Ownerende selbst beobachten; Java ownerLost ist nur ein Testereignis.
    }
    private final StartIntentStore store;
    private final StartIntentStore.Proof proof;
    private final Custodian custodian;
    private final Runnable quarantine;
    private final Object lock = new Object();
    private Resource job, process;
    private Object receipt, revokedReceipt;
    private boolean attempted, returned, revoked, revocationStarted, resumeAttempted;
    StartLeaseGuard(StartIntentStore store, StartIntentStore.Proof proof, Custodian custodian, Runnable quarantine) {
        this.store = Objects.requireNonNull(store); this.proof = proof; this.custodian = Objects.requireNonNull(custodian);
        this.quarantine = Objects.requireNonNull(quarantine);
    }
    public boolean beforeCreate(Resource privateJob) {
        synchronized (lock) { if (attempted || revoked) return false; attempted = true; job = privateJob; }
        if (privateJob == null || privateJob.kind != KernelCalls.Kind.JOB || !store.valid(proof)) { ownerLost(); return false; }
        Object held;
        try { held = custodian.retain(store.binding(), proof.anchor(), privateJob); }
        catch (RuntimeException failure) { ownerLost(); return false; }
        synchronized (lock) { receipt = held; }
        // Widerruf darf auch einen noch blockierten retain-Aufruf überholen.
        if (revoked()) { revoke(held); return false; }
        if (held == null || !live()) { ownerLost(); return false; }
        return !revoked();
    }
    public boolean created(Resource privateJob, Resource heldProcess) {
        boolean late, invalid;
        synchronized (lock) {
            invalid = privateJob != job || !attempted || returned || job == null
                    || (heldProcess != null && (heldProcess.kind != KernelCalls.Kind.PROCESS || heldProcess.scope != job.scope));
            if (!invalid) { returned = true; process = heldProcess; }
            late = revoked;
        }
        if (invalid) { ownerLost(); return false; }
        try { if (receipt != null) custodian.recordReturn(receipt, heldProcess); }
        catch (RuntimeException failure) { ownerLost(); lateReturn(heldProcess); return false; }
        if (late || revoked() || heldProcess == null || !store.valid(proof) || !live()) {
            ownerLost(); lateReturn(heldProcess);
            return false;
        }
        return true;
    }
    public boolean beforeResume(Resource privateJob, Resource heldProcess) {
        synchronized (lock) {
            if (revoked || resumeAttempted || !returned || privateJob != job || heldProcess != process) return false;
            resumeAttempted = true;
        }
        if (!store.valid(proof) || !live()) { ownerLost(); return false; }
        return !revoked();
    }
    public boolean afterResume(Resource privateJob, Resource heldProcess) {
        synchronized (lock) { if (privateJob != job || heldProcess != process || !resumeAttempted) return false; }
        if (revoked() || !store.valid(proof) || !live()) {
            ownerLost(); lateReturn(heldProcess);
            return false;
        }
        return true;
    }
    void ownerLost() {
        Object held;
        synchronized (lock) { revoked = true; if (revocationStarted) return; revocationStarted = true; held = receipt; }
        // Weder force() noch ein Storemonitor darf den unabhängigen Kontrollstopp voraussetzen.
        // Recovery eines PREPARED nach Crash ist ohnehin nie eine Startfreigabe.
        try { quarantine.run(); } catch (RuntimeException ignored) { /* Lokaler Widerruf bleibt gesetzt. */ }
        try { revoke(held); }
        finally { try { store.abort(); } catch (Exception ignored) { } }
    }
    private boolean live() {
        try { return receipt != null && custodian.live(receipt); }
        catch (RuntimeException failure) { return false; }
    }
    private void revoke(Object held) {
        synchronized (lock) { if (held == null || held == revokedReceipt) return; revokedReceipt = held; }
        try { if (held != null) custodian.revoke(held); }
        catch (RuntimeException ignored) { /* Unbestätigter Cleanup: keine Endfreigabe und kein Retry. */ }
    }
    private void lateReturn(Resource heldProcess) {
        try { if (receipt != null && heldProcess != null) custodian.retainLate(receipt, heldProcess); }
        catch (RuntimeException ignored) { /* Fähigkeit bleibt im nativen Ledger; kein angenommener Abschluss. */ }
    }
    private boolean revoked() { synchronized (lock) { return revoked; } }
}
