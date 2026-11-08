package de.mangalib;

import java.util.Objects;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.DefaultPointcutAdvisor;

/** Testlokaler Eingriff in dieselbe Repositoryinstanz. proceed() erhält die
 * vorhandene Spring-Data-/Transaktionskette auch für abstrakte Interfacemethoden.
 * Keine Reflexionsdelegation auf das Repository, kein Mockito-callRealMethod. */
public final class T03RepositoryHook implements AutoCloseable {
    private final Advised repository;
    private final DefaultPointcutAdvisor advisor;
    private volatile boolean active = true;

    public T03RepositoryHook(Object bean, MethodInterceptor hook) {
        Objects.requireNonNull(hook);
        if (!(bean instanceof Advised advised) || advised.isFrozen())
            throw new IllegalStateException("Test-Repository benötigt einen veränderbaren Spring-Proxy.");
        repository = advised;
        advisor = new DefaultPointcutAdvisor((MethodInterceptor) invocation ->
                active ? hook.invoke(invocation) : invocation.proceed());
        repository.addAdvisor(0, advisor);
    }

    /** Nur den eigenen Advisor entfernen. Schon begonnene Aufrufe nicht abbrechen. */
    @Override public synchronized void close() {
        if (!active) return;
        active = false;
        if (!repository.removeAdvisor(advisor))
            throw new IllegalStateException("Eigener Repository-Testhook nicht mehr zugeordnet: STOP.");
    }
}
