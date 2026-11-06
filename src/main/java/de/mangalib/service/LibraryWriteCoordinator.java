package de.mangalib.service;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;

/** Eine lokale Schreibreihenfolge, einschließlich des tatsächlichen Commit/Rollback.
 * Kein Schutz für andere JVMs. Keine Netzwerk-/Scraperarbeit innerhalb dieser Grenze. */
@Component
public final class LibraryWriteCoordinator {
    private final ReentrantLock mutex;
    private final TransactionTemplate transaction;
    private final long waitNanos;
    private final ThreadLocal<Boolean> owned = new ThreadLocal<>();
    private volatile boolean completionUnconfirmed;

    @Autowired
    public LibraryWriteCoordinator(PlatformTransactionManager manager) {
        this(manager, new ReentrantLock(true), Duration.ofSeconds(5));
    }

    LibraryWriteCoordinator(PlatformTransactionManager manager, ReentrantLock mutex, Duration wait) {
        this.mutex = Objects.requireNonNull(mutex);
        transaction = new TransactionTemplate(Objects.requireNonNull(manager));
        transaction.setTimeout(30);
        if (wait.isNegative() || wait.isZero()) throw new IllegalArgumentException("Ungültige Wartefrist.");
        waitNanos = wait.toNanos();
    }

    public <T> T execute(Callable<T> work) {
        Objects.requireNonNull(work);
        if (Boolean.TRUE.equals(owned.get())) {
            if (!TransactionSynchronizationManager.isActualTransactionActive())
                throw new IllegalStateException("Schreibtransaktion fehlt.");
            return call(work);
        }
        // Ein äußeres Commit könnte sonst erst NACH Mutexfreigabe stattfinden.
        if (TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Schreiboperation benötigt eine eigene Transaktionsgrenze.");
        }
        boolean locked;
        try { locked = mutex.tryLock(waitNanos, TimeUnit.NANOSECONDS); }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new WriteBusyException();
        }
        if (!locked) throw new WriteBusyException();
        final int[] completion = {-1};
        final Throwable[] workFailure = {null};
        try {
            if (completionUnconfirmed) throw new IllegalStateException("Transaktionsabschluss nicht bestätigt.");
            return transaction.execute(status -> {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCompletion(int state) { completion[0] = state; }
                });
                owned.set(true);
                try { return call(work); }
                catch (RuntimeException | Error failure) { workFailure[0] = failure; throw failure; }
                finally { owned.remove(); }
            });
        } catch (RuntimeException | Error failure) {
            if (failure != workFailure[0]) completionUnconfirmed = true;
            throw failure;
        } finally {
            // TransactionTemplate kehrt erst nach Abschluss und Ressourcen-Cleanup zurück.
            // UNKNOWN oder fehlender Abschluss sperrt weitere Writes, kein automatischer Retry.
            if (completion[0] != TransactionSynchronization.STATUS_COMMITTED
                    && completion[0] != TransactionSynchronization.STATUS_ROLLED_BACK) completionUnconfirmed = true;
            mutex.unlock();
        }
    }

    private static <T> T call(Callable<T> work) {
        try { return work.call(); }
        catch (RuntimeException | Error failure) { throw failure; }
        catch (Exception checked) {
            // Auch checked Exceptions verhindern den Commit des gesamten Kaufs.
            throw new IllegalStateException("Schreiboperation fehlgeschlagen.", checked);
        }
    }

    public static final class WriteBusyException extends RuntimeException {
        public WriteBusyException() { super("Schreiboperation derzeit nicht verfügbar."); }
    }
}
