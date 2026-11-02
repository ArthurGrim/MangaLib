package de.mangalib.watchdogmodel;

/**
 * Ausschließlich Speicherzustand. Baseline/Aktualität sind injizierte Belege eines
 * vertrauenswürdigen Fakes, kein Nachweis von Windows-Durability oder Authentizität.
 */
final class FakeRecoveryJournal {
    enum State { UNKNOWN, CORRUPT, STALE, CLEAN, INTENT, UNCERTAIN, COMPLETED }
    record Baseline(FakeRecoveryJournal store, long revision) { }
    record End(WatchdogModel.Ticket ticket, Object outer, Object inner, long revision) { }

    private State state;
    private Object writer;
    private WatchdogModel.Ticket ticket;
    private Object outer, inner;
    private long revision = 1;
    boolean failIntent, failCompletion;

    FakeRecoveryJournal(State initial) { state = initial; }

    /** Ein CLEAN-Fake muss ausdrücklich durch das Testsetup bereitgestellt werden. */
    Baseline baseline() { return new Baseline(this, revision); }

    boolean claim(Object owner, Baseline evidence) {
        if (writer != null || evidence == null || evidence.store() != this
                || evidence.revision() != revision || state != State.CLEAN) return false;
        writer = owner;
        return true;
    }

    boolean intent(Object owner, WatchdogModel.Ticket next) {
        if (writer != owner || (state != State.CLEAN && state != State.COMPLETED) || failIntent) return false;
        ticket = next; outer = null; inner = null; state = State.INTENT; revision++;
        return true;
    }

    /** Aktueller Fake-Intent vor einem weiteren Startschritt, keine erfundene Recovery. */
    boolean confirmsIntent(Object owner, WatchdogModel.Ticket current, Object k, Object j) {
        return writer == owner && state == State.INTENT && ticket == current && outer == k && inner == j;
    }

    void bind(Object owner, WatchdogModel.Ticket current, Object k, Object j) {
        if (writer != owner || ticket != current || state != State.INTENT) throw new IllegalStateException("journal-owner");
        if (outer != null && outer != k || inner != null && inner != j) throw new IllegalStateException("journal-group");
        outer = k; inner = j;
    }

    End endCandidate(WatchdogModel.Ticket current, Object k, Object j) {
        return new End(current, k, j, revision);
    }

    boolean complete(Object owner, End end) {
        if (writer != owner || state != State.INTENT || failCompletion || end == null
                || end.ticket() != ticket || end.outer() != outer || end.inner() != inner
                || end.revision() != revision) return false;
        state = State.COMPLETED; revision++;
        return true;
    }

    void uncertain(Object owner) {
        if (writer == owner) { state = State.UNCERTAIN; revision++; }
    }

    /** Simulierter Ownerverlust: gibt nur den Writer frei, niemals eine CLEAN-Baseline. */
    void loseWriter(Object owner) {
        if (writer == owner) { uncertain(owner); writer = null; }
    }

    State state() { return state; }
}
