package de.mangalib;

import de.mangalib.service.LibraryWriteCoordinator;
import java.lang.annotation.*;
import java.lang.reflect.*;
import org.junit.jupiter.api.extension.*;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;

/** Führt Fixture, Test und Prüfung innerhalb des echten Coordinators aus.
 * Ein privates Rollbacksignal verhindert ausnahmslos den Commit; die bestehenden
 * Bestandssnapshots laufen ausdrücklich erst außerhalb dieser Transaktion.
 */
final class CoordinatorRollback implements InvocationInterceptor {
    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD) @interface Before {}
    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD) @interface After {}
    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD) @interface Capture {}
    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD) @interface Preserve {}
    private static final ThreadLocal<Boolean> scope = new ThreadLocal<>();
    static boolean isActive() { return Boolean.TRUE.equals(scope.get()) && TransactionSynchronizationManager.isActualTransactionActive(); }
    static boolean isFlaggedForRollback() { return isActive(); }
    private static final class RollbackOnly extends RuntimeException {}

    @Override public void interceptTestMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> method,
                                             ExtensionContext context) throws Throwable { run(invocation, context); }
    @Override public void interceptTestTemplateMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> method,
                                                     ExtensionContext context) throws Throwable { run(invocation, context); }
    private void run(Invocation<Void> invocation, ExtensionContext context) throws Throwable {
        var application = SpringExtension.getApplicationContext(context);
        var coordinator = application.getBean(LibraryWriteCoordinator.class);
        var em = application.getBean(jakarta.persistence.EntityManager.class);
        Object instance = context.getRequiredTestInstance();
        var baseline = TestDatabaseSnapshot.capture(em);
        var eventBaseline = events(em);
        invoke(instance, Capture.class);
        Throwable[] failure = {null};
        try {
            assertThrows(RollbackOnly.class, () -> coordinator.execute(() -> {
                scope.set(true);
                try { invoke(instance, Before.class); invocation.proceed(); }
                catch (Throwable caught) { failure[0] = caught; }
                finally {
                    try { invoke(instance, After.class); }
                    catch (Throwable caught) { if (failure[0] == null) failure[0] = caught; else failure[0].addSuppressed(caught); }
                    scope.remove();
                }
                throw new RollbackOnly();
            }));
        } finally {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            em.clear();
            assertEquals(baseline, TestDatabaseSnapshot.capture(em), "Vollständiger Altbestand nach Coordinator-Rollback");
            assertEquals(eventBaseline, events(em), "Leseereignisse nach Coordinator-Rollback");
            invoke(instance, Preserve.class);
        }
        if (failure[0] != null) throw failure[0];
    }
    private java.util.List<?> events(jakarta.persistence.EntityManager em) {
        java.util.List<?> rows = em.createNativeQuery("select * from band_reading_events order by id").getResultList();
        return rows.stream()
                .map(row -> java.util.Arrays.asList(((Object[]) row).clone())).toList();
    }
    private void invoke(Object instance, Class<? extends Annotation> annotation) throws Throwable {
        for (Method method : instance.getClass().getDeclaredMethods()) if (method.isAnnotationPresent(annotation)) {
            try { method.setAccessible(true); method.invoke(instance); }
            catch (InvocationTargetException failure) { throw failure.getCause(); }
        }
    }
}
