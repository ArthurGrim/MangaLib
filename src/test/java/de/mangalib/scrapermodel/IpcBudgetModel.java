package de.mangalib.scrapermodel;

/**
 * Reines Größen-/Envelope-Modell: keine Pipes, Bytespeicher, JSON-Parser oder I/O.
 * Ein Fehler vergiftet den Kanal; eine spätere korrekte Meldung heilt ihn nicht.
 */
final class IpcBudgetModel {
    record Limits(long requestBytes, long resultBytes, long stderrBytes, int retainedStderrBytes,
                  int entries, int depth) {
        Limits {
            if (requestBytes < 1 || resultBytes < 1 || stderrBytes < 1
                    || retainedStderrBytes < 0 || retainedStderrBytes > stderrBytes
                    || entries < 1 || depth < 1) throw new IllegalArgumentException("limits");
        }
    }

    private final Limits limits;
    private final SupervisorModel.Ticket ticket;
    private boolean failed;
    private boolean requestDeclared;
    private boolean resultDeclared;
    private boolean finished;
    private long requestRemaining;
    private long resultRemaining;
    private long stderrTotal;

    IpcBudgetModel(Limits limits, SupervisorModel.Ticket ticket) {
        this.limits = java.util.Objects.requireNonNull(limits);
        this.ticket = java.util.Objects.requireNonNull(ticket);
    }

    boolean request(long declaredBytes) {
        require(!requestDeclared && !finished && declaredBytes > 0 && declaredBytes <= limits.requestBytes());
        requestDeclared = true;
        requestRemaining = declaredBytes;
        return true;
    }

    void requestChunk(long bytes) {
        require(requestDeclared && !finished && bytes > 0 && bytes <= requestRemaining);
        requestRemaining -= bytes;
    }

    void result(int version, SupervisorModel.Ticket responseTicket, SupervisorModel.Operation operation,
                String status, long declaredBytes, int entries, int depth) {
        require(requestDeclared && requestRemaining == 0 && !resultDeclared && !finished
                && version == 1 && responseTicket == ticket && operation == ticket.operation()
                && ("SUCCESS".equals(status) || "ERROR".equals(status))
                && declaredBytes > 0 && declaredBytes <= limits.resultBytes()
                && entries >= 0 && entries <= limits.entries() && depth >= 1 && depth <= limits.depth());
        resultDeclared = true;
        resultRemaining = declaredBytes;
    }

    void resultChunk(long bytes) {
        require(resultDeclared && !finished && bytes > 0 && bytes <= resultRemaining);
        resultRemaining -= bytes;
    }

    void stderr(long bytes) {
        // Subtraktion vor Addition verhindert auch long-Überlauf und riesige Allokationen.
        require(!finished && bytes > 0 && bytes <= limits.stderrBytes() - stderrTotal);
        stderrTotal += bytes;
    }

    void end() {
        require(!finished && requestDeclared && resultDeclared && requestRemaining == 0 && resultRemaining == 0);
        finished = true;
    }

    private void require(boolean valid) {
        if (failed || !valid) {
            failed = true;
            throw new IllegalStateException("Invalid bounded IPC contract");
        }
    }

    int retainedStderrBytes() { return (int) Math.min(stderrTotal, limits.retainedStderrBytes()); }
    boolean finished() { return finished && !failed; }
    boolean failed() { return failed; }
}
