package de.mangalib.watchdogmodel;

/** Reservierungen für eigene Bytepuffer/gehaltene Zeichen, keine JVM-Heapgarantie. */
final class BufferBudget {
    private final long limit;
    private long used, peak;
    BufferBudget(long limit) {
        if (limit < 1) throw new IllegalArgumentException("budget");
        this.limit = limit;
    }
    final class Account {
        private final long maximum;
        private long used, peak;
        Account(long maximum) {
            if (maximum < 1 || maximum > limit) throw new IllegalArgumentException("channel-budget");
            this.maximum = maximum;
        }
        Lease reserve(long bytes) {
            if (bytes < 0 || bytes > maximum - used || bytes > limit - BufferBudget.this.used) return null;
            used += bytes; BufferBudget.this.used += bytes;
            peak = Math.max(peak, used); BufferBudget.this.peak = Math.max(BufferBudget.this.peak, BufferBudget.this.used);
            return new Lease(this, bytes);
        }
        long used() { return used; }
        long peak() { return peak; }
        private void releaseGlobal(long bytes) { BufferBudget.this.release(bytes); }
    }
    static final class Lease implements AutoCloseable {
        private Account account;
        private final long bytes;
        private Lease(Account account, long bytes) { this.account = account; this.bytes = bytes; }
        @Override public void close() {
            if (account != null) {
                account.used -= bytes; account.releaseGlobal(bytes); account = null;
            }
        }
    }
    private void release(long bytes) { used -= bytes; }
    long used() { return used; }
    long peak() { return peak; }
    Account account(long maximum) { return new Account(maximum); }
}
