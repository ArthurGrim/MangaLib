package de.mangalib.service;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/** Echte Spring-Transaktionssteuerung, aber nur RAM/Callbacks statt JDBC/JPA.
 * Beweist Interceptor-/Completion-Reihenfolge, niemals einen Datenbank-Commit. */
final class FakeLibraryTransactionManager extends AbstractPlatformTransactionManager {
    static final class Tx implements org.springframework.transaction.support.SmartTransactionObject {
        boolean active, rollbackOnly;
        @Override public boolean isRollbackOnly() { return rollbackOnly; }
        @Override public void flush() { }
    }
    private final ThreadLocal<Tx> current = new ThreadLocal<>();
    final List<String> events = new CopyOnWriteArrayList<>();
    final AtomicInteger begins = new AtomicInteger(), commits = new AtomicInteger(), rollbacks = new AtomicInteger();
    Runnable beforeCommit = () -> {}, beforeRollback = () -> {}, afterRollback = () -> {};
    String fault = "";

    @Override protected Object doGetTransaction() { var tx = current.get(); return tx == null ? new Tx() : tx; }
    @Override protected boolean isExistingTransaction(Object value) { return ((Tx) value).active; }
    @Override protected void doBegin(Object value, TransactionDefinition definition) {
        events.add("begin"); begins.incrementAndGet();
        if (fault.equals("begin")) throw new TransactionSystemException("synthetic begin failure");
        var tx = (Tx) value; tx.active = true; current.set(tx);
    }
    @Override protected void doCommit(DefaultTransactionStatus status) {
        events.add("commit-start"); beforeCommit.run();
        if (fault.equals("commit")) throw new TransactionSystemException("synthetic commit failure");
        commits.incrementAndGet(); events.add("commit-end");
    }
    @Override protected void doRollback(DefaultTransactionStatus status) {
        events.add("rollback-start"); beforeRollback.run();
        if (fault.equals("rollback")) throw new TransactionSystemException("synthetic rollback failure");
        rollbacks.incrementAndGet(); afterRollback.run(); events.add("rollback-end");
    }
    @Override protected void doSetRollbackOnly(DefaultTransactionStatus status) { ((Tx) status.getTransaction()).rollbackOnly = true; }
    @Override protected void doCleanupAfterCompletion(Object value) {
        events.add("cleanup"); ((Tx) value).active = false; current.remove();
        if (fault.equals("cleanup")) throw new TransactionSystemException("synthetic cleanup failure");
    }
}
