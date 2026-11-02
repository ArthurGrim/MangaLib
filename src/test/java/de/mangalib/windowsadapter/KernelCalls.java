package de.mangalib.windowsadapter;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Isolierter Port, KEINE native Implementierung. Operationen liefern nur eigene,
 * sofort verbuchte Ressourcen. Alle Referenzen sind Fähigkeiten, keine aus einer
 * PID oder numerischen Handleadresse rekonstruierbaren Berechtigungen.
 */
interface KernelCalls {
    enum Kind { JOB, PROCESS, THREAD, ATTRIBUTES, MEMORY }

    /** Pro Gruppe frisch erzeugte Scope-Identität, zusätzlich zum Supervisor-Ticket. */
    final class Scope { }

    /** debugValue dient allein Fake-Tests zur Wiederverwendung numerischer Werte. */
    final class Resource {
        final Scope scope;
        final Kind kind;
        final long debugValue;

        Resource(Scope scope, Kind kind, long debugValue) {
            this.scope = Objects.requireNonNull(scope);
            this.kind = Objects.requireNonNull(kind);
            this.debugValue = debugValue;
        }
    }

    record Limits(boolean killOnClose, boolean breakaway, int activeProcessCandidate,
                  long commitBytes, int cpuRateCandidate) {
        public Limits {
            if (!killOnClose || breakaway || activeProcessCandidate < 0 || commitBytes < 0
                    || cpuRateCandidate < 0 || cpuRateCandidate > 10_000) {
                throw new IllegalArgumentException("Unsafe candidate limits");
            }
        }
    }

    record Identity(long pid, long creationTime, String image) { }

    /**
     * Ein bereits gemessenes Image bleibt derselben gehaltenen, nativ beendeten
     * Instanz zugeordnet. Niemals Ersatz für erste Identifikation oder einen
     * fehlgeschlagenen Identitäts-/Endnachweis; keine PID-/Zeittoleranz.
     */
    static Identity retainEndedImage(Identity first,long pid,long birth,boolean ended,boolean exitObserved) {
        if(first==null || first.pid()!=pid || first.creationTime()!=birth || pid<=0 || birth<=0
                || first.image()==null || first.image().isBlank() || !ended || !exitObserved)
            throw new Failure("ended-identity-unconfirmed",0);
        return first;
    }

    /**
     * stable bezeichnet einen bounded konsistenten Erhebungsversuch, keine
     * tatsächlich existierende atomare Win32-Snapshot-API. Dies muss der spätere
     * native Port erst aus mehreren Abfragen belastbar ableiten.
     */
    record Observation(boolean complete, boolean stable, int assigned, List<Long> pids,
                       int active, long total) {
        public Observation { pids = List.copyOf(pids); }
    }

    enum StartStatus { PENDING, FAILED, SUSPENDED }
    record StartResult(StartStatus status, Resource process, Resource thread) {
        public StartResult { Objects.requireNonNull(status); }
        static StartResult pending() { return new StartResult(StartStatus.PENDING, null, null); }
        static StartResult failed() { return new StartResult(StartStatus.FAILED, null, null); }
    }

    /** Erwartete Portfehler; Aufrufer bewahrt Primär- UND Cleanupfehler auf. */
    final class Failure extends RuntimeException {
        final String operation;
        final int code;
        Failure(String operation, int code) {
            super(operation);
            this.operation = operation;
            this.code = code;
        }
    }

    Resource createJob(Scope scope);
    void configure(Resource job, Limits limits);
    Limits readLimits(Resource job);

    /** Vor Start vorbereitete Berechtigung; keine nachträgliche PID-/Namenssuche. */
    interface CleanupControl {
        /** Rein lokales, nicht blockierendes Stoppsignal; keine native Operation. */
        void cancelStarts();
        void terminate(long remaining);
        /** Nur die lokale Nutzungsreservierung lösen, niemals ein laufendes Handle schließen. */
        void release();
    }
    /** Alte reine Modellports haben keinen unabhängigen Kontrollpfad. */
    default CleanupControl prepareCleanupControl(Resource job) { return null; }

    /** Jede Teilallokation VOR einem möglichen folgenden Fehler an acquired melden. */
    void prepareLaunch(Resource job, Consumer<Resource> acquired);

    /**
     * Kein Worker-Code vor Rückkehr/SUSPENDED, eigene atomare Jobzuordnung und
     * kein Fallback. Auch partielle Prozess-/Threadhandles sofort melden.
     * PENDING simuliert eine noch ausstehende native Rückkehr, keine Future-Cancelgarantie.
     */
    StartResult startSuspended(Resource job, List<Resource> preparation, Consumer<Resource> acquired);
    void resume(Resource thread);
    Observation observe(Resource job, int capacity, long remaining);
    /** Begrenzter Endbeobachtungsversuch innerhalb DERSELBEN Cleanuprestfrist. */
    Observation awaitEmptyJob(Resource job, int capacity, long remaining);
    Resource openProcess(Resource job, long pid);
    Identity identity(Resource process);
    boolean member(Resource process, Resource job);
    void terminate(Resource job);
    boolean waitEnded(Resource process, long remaining);

    /** ATTRIBUTES = DeleteProcThreadAttributeList; MEMORY = Freigabe erst danach. */
    void close(Resource resource);
}
