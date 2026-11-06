package de.mangalib.service;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Set;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.core.Ordered;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** Der äußere Advisor läuft VOR vorhandenen @Transactional-Interceptoren.
 * Bestehende öffentliche Schreibservices teilen damit denselben Mutex/Commitrahmen.
 * Interne Selbstaufrufe laufen bereits innerhalb des geschützten äußeren Aufrufs. */
@Configuration(proxyBeanMethods = false)
@EnableTransactionManagement
public class LibraryWriteConfiguration {
    private static final Set<Class<?>> WRITERS = Set.of(MangaReiheService.class, BandService.class,
            MangaDetailsService.class, EinkaufslisteService.class, EinkaufslisteItemDetailsService.class);

    static boolean coordinates(Method method, Class<?> target) {
        return Modifier.isPublic(method.getModifiers())
                && WRITERS.stream().anyMatch(type -> type.isAssignableFrom(target))
                && (method.getName().startsWith("save") || method.getName().startsWith("create")
                    || method.getName().startsWith("update") || method.getName().startsWith("delete"));
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    DefaultPointcutAdvisor libraryWriteAdvisor(LibraryWriteCoordinator coordinator) {
        var pointcut = new StaticMethodMatcherPointcut() {
            @Override public boolean matches(Method method, Class<?> target) { return coordinates(method, target); }
        };
        MethodInterceptor advice = invocation -> coordinator.execute(() -> {
            try { return invocation.proceed(); }
            catch (Exception failure) { throw failure; }
            catch (Error failure) { throw failure; }
            catch (Throwable failure) { throw new IllegalStateException("Schreiboperation fehlgeschlagen.", failure); }
        });
        var advisor = new DefaultPointcutAdvisor(pointcut, advice);
        advisor.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return advisor;
    }
}
